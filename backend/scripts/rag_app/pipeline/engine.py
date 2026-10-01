from __future__ import annotations

from dataclasses import dataclass
import re
import sys
import time
from typing import Iterable

from rag_app.config import RAGSettings
from rag_app.llm.client import LLMClient
from rag_app.llm.prompts import build_user_prompt
from rag_app.memory.store import ConversationMemory
from rag_app.pipeline.evidence_gate import (
    EvidenceGate,
    INVALID_QUESTION_MESSAGE,
    OUT_OF_SCOPE_MESSAGE,
    REFUSAL_MESSAGE,
    STALE_DATA_MESSAGE,
)
from rag_app.pipeline.output_cleaner import clean_repetition
from rag_app.pipeline.query_classifier import QueryClassifier, QueryType
from rag_app.retrieval.formatter import format_documents
from rag_app.retrieval.retriever import HybridRetriever, RetrievalQuality, RetrievalResult
from rag_app.structured.db import Database
from rag_app.structured.repository import StructuredRepository
from rag_app.tools.search_tool import SearchService, SearchExecution, SearchQueryRewriter, format_search_results


@dataclass
class RAGAnswer:
    answer: str
    sources: list[str]
    metadatas: list[dict]
    distances: list[float]
    response_time: int
    model: str
    structured_data: bool = False
    search_results: list[dict] | None = None
    used_tools: list[str] | None = None
    tool_trace: list[dict] | None = None
    search_execution: dict | None = None
    retrieval_quality: dict | None = None
    success: bool = True
    error: str = ""


class RAGEngine:
    def __init__(self, settings: RAGSettings):
        self.settings = settings
        self.classifier = QueryClassifier()
        self.evidence_gate = EvidenceGate()
        self.retriever = HybridRetriever(settings)
        self.structured = StructuredRepository(Database(settings))
        self.search_service = SearchService(settings)
        self.query_rewriter = SearchQueryRewriter()
        self.llm = LLMClient(settings)
        self.memory = ConversationMemory(settings.memory_window)

    def ask(self, question: str, top_k: int = 5, session_id: str | None = None,
            force_web_search: bool = False,
            conversation_history: list[dict] | None = None) -> RAGAnswer:
        start = time.time()
        try:
            self._hydrate_memory(session_id, conversation_history)
            prepared = self.prepare(question, top_k, force_web_search, session_id)
            if prepared["direct_answer"]:
                answer = prepared["direct_answer"]
                self.memory.add(session_id, question, answer)
                return self._answer(answer, prepared, start, success=True)
            if not prepared["allowed"]:
                answer = prepared["refusal_message"] or REFUSAL_MESSAGE
                self.memory.add(session_id, question, answer)
                return self._answer(answer, prepared, start, success=True)

            budget = self._response_token_budget(prepared["classification"], prepared["resolved_question"])
            prompt = self._build_prompt(prepared, session_id, budget)
            answer = self.llm.complete(prompt, max_tokens=budget)
            if not answer:
                answer = self._fallback_answer(
                    prepared["structured_text"],
                    prepared["documents"],
                    prepared["search_results"],
                )
            answer = clean_repetition(answer)
            self.memory.add(session_id, question, answer)
            return self._answer(answer, prepared, start, success=True)
        except Exception as exc:
            return RAGAnswer(
                answer="",
                sources=[],
                metadatas=[],
                distances=[],
                response_time=round((time.time() - start) * 1000),
                model=self.model_name,
                success=False,
                error=str(exc),
            )

    def stream(self, question: str, top_k: int = 5, session_id: str | None = None,
               force_web_search: bool = False,
               conversation_history: list[dict] | None = None) -> Iterable[dict]:
        start = time.time()
        self._hydrate_memory(session_id, conversation_history)
        prepared = self.prepare(question, top_k, force_web_search, session_id)
        yield {
            "type": "sources",
            "sources": prepared["documents"],
            "metadatas": prepared["metadatas"],
            "search_results": [item.to_dict() for item in prepared["search_results"]],
            "used_tools": prepared["used_tools"],
            "tool_trace": prepared["tool_trace"],
            "structured_data": bool(prepared["structured_text"]),
            "search_execution": prepared["search_execution"],
            "retrieval_quality": prepared["retrieval_quality"],
        }

        if prepared["direct_answer"]:
            answer = prepared["direct_answer"]
            yield {"type": "chunk", "content": answer}
            self.memory.add(session_id, question, answer)
            yield {"type": "done", "response_time": round((time.time() - start) * 1000)}
            return

        if not prepared["allowed"]:
            answer = prepared["refusal_message"] or REFUSAL_MESSAGE
            yield {"type": "chunk", "content": answer}
            self.memory.add(session_id, question, answer)
            yield {"type": "done", "response_time": round((time.time() - start) * 1000)}
            return

        budget = self._response_token_budget(prepared["classification"], prepared["resolved_question"])
        prompt = self._build_prompt(prepared, session_id, budget)
        full_answer = ""
        streamed = False
        for chunk in self.llm.stream(prompt, max_tokens=budget):
            streamed = True
            full_answer += chunk
            yield {"type": "chunk", "content": chunk}

        if not streamed:
            full_answer = self._fallback_answer(
                prepared["structured_text"],
                prepared["documents"],
                prepared["search_results"],
            )
            yield {"type": "chunk", "content": full_answer}

        cleaned = clean_repetition(full_answer)
        self.memory.add(session_id, question, cleaned)
        yield {"type": "done", "response_time": round((time.time() - start) * 1000)}

    def prepare(self, question: str, top_k: int, force_web_search: bool = False,
                session_id: str | None = None) -> dict:
        resolved_question = self._resolve_question(question, session_id)
        classification = self.classifier.classify(resolved_question)
        tool_trace = []

        if not self._is_valid_question(question):
            return self._empty_prepared(
                classification, question, resolved_question,
                allowed=False, refusal_message=INVALID_QUESTION_MESSAGE,
            )
        if self.evidence_gate.is_out_of_scope(resolved_question):
            return self._empty_prepared(
                classification, question, resolved_question,
                allowed=False, refusal_message=OUT_OF_SCOPE_MESSAGE,
            )

        # 1. 结构化数据
        structured_text = ""
        if classification.needs_structured_data:
            structured_text = self.structured.search(classification, top_k, resolved_question)
            tool_trace.append({"tool": "sql", "query_type": classification.query_type.value})
        if not structured_text:
            structured_text = self.structured.search_by_keywords(resolved_question, top_k)
            if structured_text:
                tool_trace.append({"tool": "sql", "query_type": "keyword"})

        used_tools: list[str] = ["sql"] if structured_text else []
        if self._can_answer_direct(classification, resolved_question, structured_text, force_web_search):
            return self._empty_prepared(
                classification, question, resolved_question,
                structured_text=structured_text,
                direct_answer=structured_text,
                allowed=True,
                used_tools=used_tools,
                tool_trace=tool_trace,
            )

        # 2. 时效问题直接使用联网证据，避免旧向量资料污染答案。
        web_first = (
            force_web_search
            or self.settings.force_web_search
            or classification.needs_web_search
        ) and self.settings.search_enabled
        if web_first:
            quality = RetrievalQuality(
                confidence_level="not_needed",
                reason="freshness_web_first",
            )
            retrieved = RetrievalResult([], [], [], [], quality)
            tool_trace.append({"tool": "rag", "skipped": True, "reason": "freshness_web_first"})
        else:
            try:
                retrieved = self.retriever.retrieve(resolved_question, top_k, classification)
            except Exception as exc:
                # 向量库故障时降级为空证据（联网/结构化路径仍可作答），绝不让流中断
                print(f"RAG retrieve failed, degrading to empty evidence: {exc}", file=sys.stderr)
                quality = RetrievalQuality(
                    confidence_level="unavailable",
                    reason="retrieval_error",
                )
                retrieved = RetrievalResult([], [], [], [], quality)
            tool_trace.append({
                "tool": "rag",
                "accepted": retrieved.quality.accepted_count,
                "rejected": retrieved.quality.rejected_count,
                "best_distance": round(retrieved.quality.best_vector_distance, 3),
                "best_rerank_score": round(retrieved.quality.best_rerank_score, 3),
                "confidence": retrieved.quality.confidence_level,
            })

        # 3. 联网搜索决策
        should_search = (
            force_web_search
            or self.settings.force_web_search
            or classification.needs_web_search
            or (
                retrieved.quality.accepted_count == 0
                and not structured_text.strip()
                and getattr(self.settings, "allow_general_web_search", False)
            )
        )

        search_results = []
        search_execution = SearchExecution()
        search_attempted = False
        search_success = False
        if should_search and self.settings.search_enabled:
            search_attempted = True
            # 查询改写
            queries = self.query_rewriter.rewrite(resolved_question)
            all_results = []
            search_query = self._select_search_query(resolved_question, queries)
            results, execution = self.search_service.search(search_query, self.settings.search_top_k)
            all_results.extend(results)
            search_execution = execution
            # 去重
            seen_urls = set()
            for r in all_results:
                if r.url not in seen_urls:
                    seen_urls.add(r.url)
                    search_results.append(r)
            search_results = search_results[:self.settings.search_top_k]
            search_success = len(search_results) > 0

            # 只要尝试过联网，就必须记录到 used_tools
            used_tools.append("web_search")
            tool_trace.append({
                "tool": "web_search",
                "providers_tried": search_execution.attempted_providers,
                "provider_results": [
                    {"provider": pr.provider, "status": pr.status, "count": pr.result_count, "error": pr.error}
                    for pr in search_execution.provider_results
                ],
                "successful_provider": search_execution.successful_provider,
                "status": search_execution.status,
                "result_count": len(search_results),
                "elapsed_ms": search_execution.elapsed_ms,
            })

        # 4. 证据融合与决策
        search_documents = [r.snippet or r.title for r in search_results]
        all_documents = retrieved.documents + search_documents
        decision = self.evidence_gate.assess(
            structured_text, all_documents, resolved_question,
            search_attempted=search_attempted, search_success=search_success,
        )

        # 5. 构建 used_tools（web_search 在步骤 3 中已添加）
        if retrieved.documents:
            used_tools.append("rag")
        if structured_text and "sql" not in used_tools:
            used_tools.append("sql")

        direct_web_answer = ""
        if decision.allowed:
            direct_web_answer = self._scoreboard_answer(classification, search_results, resolved_question)
            if not direct_web_answer and decision.reason == "structured_stale_fallback":
                direct_web_answer = f"{STALE_DATA_MESSAGE}\n{structured_text}"

        return {
            "classification": classification,
            "question": question,
            "resolved_question": resolved_question,
            "structured_text": structured_text,
            "direct_answer": direct_web_answer,
            "refusal_message": decision.message,
            "documents": retrieved.documents,
            "metadatas": retrieved.metadatas,
            "distances": retrieved.distances,
            "rerank_scores": retrieved.rerank_scores,
            "search_results": search_results,
            "used_tools": used_tools,
            "tool_trace": tool_trace,
            "allowed": decision.allowed,
            "search_execution": {
                "attempted": search_execution.attempted,
                "status": search_execution.status,
                "reasons": search_execution.reasons,
                "providers": search_execution.attempted_providers,
                "successful": search_execution.successful_provider,
                "result_count": len(search_results),
                "elapsed_ms": search_execution.elapsed_ms,
            },
            "retrieval_quality": {
                "candidate_count": retrieved.quality.candidate_count,
                "accepted_count": retrieved.quality.accepted_count,
                "rejected_count": retrieved.quality.rejected_count,
                "best_distance": round(retrieved.quality.best_vector_distance, 3),
                "confidence": retrieved.quality.confidence_level,
                "reason": retrieved.quality.reason,
            },
        }

    def _hydrate_memory(self, session_id: str | None,
                        conversation_history: list[dict] | None) -> None:
        if session_id and conversation_history:
            self.memory.replace(session_id, conversation_history)

    def _empty_prepared(
        self,
        classification,
        question: str,
        resolved_question: str,
        *,
        structured_text: str = "",
        direct_answer: str = "",
        allowed: bool,
        refusal_message: str = "",
        used_tools: list[str] | None = None,
        tool_trace: list[dict] | None = None,
    ) -> dict:
        return {
            "classification": classification,
            "question": question,
            "resolved_question": resolved_question,
            "structured_text": structured_text,
            "direct_answer": direct_answer,
            "refusal_message": refusal_message,
            "documents": [],
            "metadatas": [],
            "distances": [],
            "rerank_scores": [],
            "search_results": [],
            "used_tools": used_tools or [],
            "tool_trace": tool_trace or [],
            "allowed": allowed,
            "search_execution": {
                "attempted": False,
                "status": "not_requested",
                "reasons": [],
                "providers": [],
                "successful": "",
                "result_count": 0,
                "elapsed_ms": 0,
            },
            "retrieval_quality": {
                "candidate_count": 0,
                "accepted_count": 0,
                "rejected_count": 0,
                "best_distance": 0.0,
                "confidence": "not_needed",
                "reason": "direct_answer" if direct_answer else "short_circuit",
            },
        }

    def _resolve_question(self, question: str, session_id: str | None) -> str:
        if not session_id or not re.search(r"他(?!们)|这名球员|该球员|他们|该队|这支球队|这队", question):
            return question

        current = self.classifier.classify(question)
        if current.players or current.teams:
            return question

        previous_player = ""
        previous_team = ""
        for item in reversed(self.memory.get(session_id)):
            if item.get("role") != "user":
                continue
            previous = self.classifier.classify(str(item.get("content") or ""))
            if not previous_player and previous.players:
                previous_player = previous.players[0]
            if not previous_team and previous.teams:
                previous_team = previous.teams[0]
            if previous_player or previous_team:
                break

        resolved = question
        if previous_team and re.search(r"他们|该队|这支球队|这队", resolved):
            resolved = re.sub(r"他们|该队|这支球队|这队", previous_team, resolved)
        if previous_player and re.search(r"他(?!们)|这名球员|该球员", resolved):
            resolved = re.sub(r"他(?!们)|这名球员|该球员", previous_player, resolved)
        return resolved

    @staticmethod
    def _select_search_query(question: str, candidates: list[str]) -> str:
        if not candidates:
            return question
        q = question.lower()
        intent_terms: list[str] = []
        if re.search(r"伤病|受伤|伤情|injury", q):
            intent_terms = ["injury report", "injury"]
        elif re.search(r"交易|trade", q):
            intent_terms = ["trade"]
        elif re.search(r"新闻|资讯|报道|news", q):
            intent_terms = ["news"]
        elif re.search(r"选秀|draft", q):
            intent_terms = ["draft"]
        for term in intent_terms:
            for candidate in candidates:
                if term in candidate.lower():
                    return candidate
        return candidates[0]

    def _can_answer_direct(self, classification, question: str, structured_text: str,
                           force_web_search: bool) -> bool:
        if not structured_text or force_web_search or self.settings.force_web_search:
            return False
        if re.search(
            r"分析|为什么|原因|影响|展望|预测|如何评价|评价|解读|"
            r"优势|劣势|谁更好|谁更强|建议|详细|全面|对比|比较",
            question,
        ):
            return False
        if classification.query_type in {QueryType.TEAM, QueryType.PLAYER, QueryType.RANKING}:
            return True
        if classification.query_type == QueryType.MATCH and classification.teams:
            return not re.search(r"今天|今日|明天|实时|正在|未开始", question)
        if re.search(r"20\d{2}年.*(?:选秀|draft)|(?:选秀|draft).*20\d{2}年", question, re.IGNORECASE):
            return True
        return not classification.needs_web_search and classification.query_type != QueryType.NEWS

    @staticmethod
    def _is_valid_question(question: str) -> bool:
        stripped = question.strip()
        if len(stripped) < 2 or not re.search(r"[A-Za-z0-9\u4e00-\u9fff]", stripped):
            return False
        # 纯英文/数字输入必须包含 NBA 领域词，避免向量库对随机字符串硬召回。
        if not re.search(r"[\u4e00-\u9fff]", stripped):
            return bool(re.search(
                r"\bnba\b|basketball|player|team|game|score|stats?|"
                r"ranking|trade|injury|draft|season|curry|lakers|warriors",
                stripped,
                re.IGNORECASE,
            ))
        return True

    def _response_token_budget(self, classification, question: str) -> int:
        hard_cap = min(max(1, int(self.settings.llm_max_tokens)), 1024)
        analysis_question = classification.query_type == QueryType.NEWS or re.search(
            r"分析|为什么|原因|影响|展望|预测|如何评价|详细|全面", question
        )
        if analysis_question:
            extra = 128 if (
                len(re.findall(r"[?？；;]", question)) >= 2
                or re.search(r"分别|同时回答|还要", question)
            ) else 0
            return min(768 + extra, hard_cap)

        list_question = classification.query_type in {QueryType.MATCH, QueryType.RANKING} or re.search(
            r"对比|比较|排行|排名|哪些|所有|全部|列表|列出|"
            r"前\d+|前[一二三四五六七八九十]", question
        )
        if list_question:
            requested = self._requested_output_items(question)
            explicit_count = bool(re.search(
                r"(?:前|top)\s*(?:\d+|[一二三四五六七八九十])", question, re.IGNORECASE
            ))
            if explicit_count:
                return min(272 + requested * 48, hard_cap)
            return min(512, hard_cap)
        return min(256, hard_cap)

    @staticmethod
    def _requested_output_items(question: str) -> int:
        number_map = {
            "一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5,
            "六": 6, "七": 7, "八": 8, "九": 9, "十": 10,
        }
        match = re.search(
            r"(?:前|top)\s*(\d+|[一二两三四五六七八九十])",
            question,
            re.IGNORECASE,
        )
        if match:
            raw = match.group(1)
            return max(1, min(number_map.get(raw, int(raw) if raw.isdigit() else 3), 10))
        if re.search(r"所有|全部|各项|每个", question):
            return 10
        return 3

    def _build_prompt(self, prepared: dict, session_id: str | None, budget: int) -> str:
        documents = [str(item)[:700] for item in prepared["documents"][:3]]
        is_bounded_list = (
            prepared["classification"].query_type == QueryType.MATCH
            or bool(re.search(r"哪些|所有|全部|几场|列表|列出", prepared["resolved_question"]))
        )
        search_limit = 5 if is_bounded_list else 3
        search_results = prepared["search_results"][:search_limit]
        char_limit = max(220, min(900, round(budget * 0.9)))
        return build_user_prompt(
            prepared["resolved_question"],
            prepared["structured_text"][:3000],
            format_documents(documents),
            format_search_results(search_results),
            conversation_context=self.memory.recent_context(session_id),
            response_budget_hint=f"请在 {char_limit} 个中文字符内完整回答。",
        )

    _ANALYSIS_INTENT = re.compile(
        r"分析|看点|关键|因素|评价|展望|预测|解读|解析|复盘|为什么|为何|怎么看待|怎么评价|胜负走向"
    )

    @staticmethod
    def _scoreboard_answer(classification, search_results: list, question: str = "") -> str:
        if (
            classification.query_type != QueryType.MATCH
            or not search_results
            or any(item.provider != "espn_scoreboard" for item in search_results)
        ):
            return ""

        # 分析/解读类问题不直接罗列比分，交给 LLM 结合比赛数据生成分析
        schedule_note = next(
            (item for item in search_results if item.title == "NBA 近期赛程说明"), None
        )
        if schedule_note is None and RAGEngine._ANALYSIS_INTENT.search(question or ""):
            return ""

        # 日期明确但无比赛：诚实回答"没有比赛"，附带近期赛程预告
        if schedule_note is not None:
            non_note = [item for item in search_results if item is not schedule_note]
            if non_note:
                lines = RAGEngine._format_scoreboard_lines(non_note)
                return schedule_note.snippet + "\n" + "\n".join(lines)
            return schedule_note.snippet

        team_names = {english: chinese for chinese, english in SearchQueryRewriter.TEAM_EN.items()}
        status_names = {
            "final": "已结束",
            "scheduled": "未开始",
            "in progress": "进行中",
            "halftime": "中场",
            "postponed": "已延期",
        }
        lines = RAGEngine._format_scoreboard_lines(search_results, team_names, status_names)
        return "\n".join([f"查询到 {len(search_results)} 场 NBA 比赛："] + lines)

    @staticmethod
    def _format_scoreboard_lines(search_results: list, team_names=None, status_names=None) -> list:
        team_names = team_names or {english: chinese for chinese, english in SearchQueryRewriter.TEAM_EN.items()}
        status_names = status_names or {
            "final": "已结束",
            "scheduled": "未开始",
            "in progress": "进行中",
            "halftime": "中场",
            "postponed": "已延期",
        }
        lines = []
        for index, item in enumerate(search_results, start=1):
            away, separator, home = item.title.partition(" vs ")
            parsed = None
            if separator and item.snippet.startswith(away):
                remainder = item.snippet[len(away):].strip()
                parsed = re.match(r"^(\d+)\s*-\s*(\d+)\s+(.+?)\s+\(([^)]+)\)$", remainder)
            if parsed:
                away_score, home_score, parsed_home, status = parsed.groups()
                home = parsed_home or home
                away_name = team_names.get(away, away)
                home_name = team_names.get(home, home)
                status_name = status_names.get(status.lower(), status)
                lines.append(
                    f"{index}. {away_name} {away_score}:{home_score} {home_name}（{status_name}）"
                )
            else:
                lines.append(f"{index}. {item.title}：{item.snippet}")
        return lines

    def stats(self) -> dict:
        stats = self.retriever.stats()
        stats.update({
            "collection_name": self.settings.collection_name,
            "llm_model": self.settings.llm_model,
            "device": self.settings.embedding_device,
            "search_enabled": self.settings.search_enabled,
            "search_providers": self.settings.search_providers,
        })
        return stats

    def clear_index(self) -> None:
        self.retriever.clear()

    @property
    def model_name(self) -> str:
        return f"{self.settings.embedding_model} + {self.settings.llm_model}"

    def _answer(self, answer: str, prepared: dict, start: float, success: bool) -> RAGAnswer:
        return RAGAnswer(
            answer=answer,
            sources=prepared["documents"],
            metadatas=prepared["metadatas"],
            distances=prepared["distances"],
            response_time=round((time.time() - start) * 1000),
            model=self.model_name,
            structured_data=bool(prepared["structured_text"]),
            search_results=[item.to_dict() for item in prepared["search_results"]],
            used_tools=prepared["used_tools"],
            tool_trace=prepared.get("tool_trace"),
            search_execution=prepared.get("search_execution"),
            retrieval_quality=prepared.get("retrieval_quality"),
            success=success,
        )

    def _fallback_answer(self, structured_text: str, documents: list[str],
                         search_results: list | None = None) -> str:
        search_results = search_results or []
        if search_results:
            return "根据最新搜索结果：\n" + "\n".join(
                f"- {item.title}：{item.snippet or item.url}" for item in search_results[:3]
            )
        if structured_text:
            return f"根据结构化数据：\n{structured_text}"
        if documents:
            return "根据参考资料：\n" + "\n".join(f"- {doc}" for doc in documents[:3])
        return REFUSAL_MESSAGE
