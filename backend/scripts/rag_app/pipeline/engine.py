from __future__ import annotations

from dataclasses import dataclass, field
from rag_app.structured.provenance import source_details
import re
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
from rag_app.pipeline.game_scope import game_context, explicit_game_date
from rag_app.pipeline.follow_ups import build_follow_ups
from rag_app.pipeline.planning import QueryPlan, ANALYSIS_INTENT
from rag_app.pipeline.query_planner import QueryPlanner
from rag_app.pipeline.evidence import EvidenceResolver, EvidenceBundle
from rag_app.pipeline.evidence_gate import GateDecision
from rag_app.retrieval.formatter import format_documents
from rag_app.retrieval.retriever import HybridRetriever
from rag_app.structured.db import Database
from rag_app.structured.repository import StructuredRepository
from rag_app.tools.search_tool import SearchService, SearchQueryRewriter, format_search_results


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
    sourceDetails: list[dict] = field(default_factory=list)
    follow_ups: list[str] = field(default_factory=list)


class RAGEngine:
    def __init__(self, settings: RAGSettings, *, structured=None, retriever=None,
                 search_service=None, llm=None, memory=None, query_rewriter=None, query_planner=None):
        self.settings = settings
        self.classifier = QueryClassifier()
        self.evidence_gate = EvidenceGate()
        self.retriever = retriever or HybridRetriever(settings)
        self.structured = structured or StructuredRepository(Database(settings))
        self.search_service = search_service or SearchService(settings)
        self.query_rewriter = query_rewriter or SearchQueryRewriter()
        self.llm = llm or LLMClient(settings)
        self.query_planner = query_planner or QueryPlanner(settings, self.llm, self.query_rewriter)
        self.memory = memory or ConversationMemory(getattr(settings, 'memory_window', 5),
            max_sessions=getattr(settings, 'memory_max_sessions', 256),
            ttl_seconds=getattr(settings, 'memory_ttl_seconds', 3600))
        self.evidence = EvidenceResolver(settings, self.structured, self.retriever, self.search_service,
                                         self.evidence_gate, self.query_rewriter, self.query_planner)

    def ask(self, question: str, top_k: int = 5, session_id: str | None = None,
            force_web_search: bool = False,
            conversation_history: list[dict] | None = None) -> RAGAnswer:
        with self.memory.turn(session_id) as granted:
            if not granted:
                return RAGAnswer('', [], [], [], 0, self.model_name, success=False,
                                 error='该会话的上一条回答尚未结束，请稍后再问')
            return self._ask(question, top_k, session_id, force_web_search, conversation_history)

    def _ask(self, question: str, top_k: int, session_id: str | None,
             force_web_search: bool, conversation_history: list[dict] | None) -> RAGAnswer:
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
                error='服务暂时不可用，请稍后重试',
            )

    def stream(self, question: str, top_k: int = 5, session_id: str | None = None,
               force_web_search: bool = False,
               conversation_history: list[dict] | None = None) -> Iterable[dict]:
        with self.memory.turn(session_id) as granted:
            if not granted:
                yield {'type': 'error', 'error': '该会话的上一条回答尚未结束，请稍后再问', 'code': 'CONVERSATION_BUSY'}
                return
            yield from self._stream(question, top_k, session_id, force_web_search, conversation_history)

    def _stream(self, question: str, top_k: int, session_id: str | None,
                force_web_search: bool, conversation_history: list[dict] | None) -> Iterable[dict]:
        start = time.time()
        self._hydrate_memory(session_id, conversation_history)
        yield {'type': 'status', 'message': '正在检索比赛和数据来源...'}
        prepared = self.prepare(question, top_k, force_web_search, session_id)
        yield {
            "type": "sources",
            "sources": prepared["documents"],
            "metadatas": prepared["metadatas"],
            "sourceDetails": self._source_details(prepared),
            "search_results": [item.to_dict() for item in prepared["search_results"]],
            "used_tools": prepared["used_tools"],
            "tool_trace": prepared["tool_trace"],
            "structured_data": bool(prepared["structured_text"]),
            "search_execution": prepared["search_execution"],
            "retrieval_quality": prepared["retrieval_quality"],
            "query_plan": prepared['query_plan'],
        }

        if prepared["direct_answer"]:
            answer = prepared["direct_answer"]
            yield {"type": "chunk", "content": answer}
            self.memory.add(session_id, question, answer)
            yield {"type": "done", "response_time": round((time.time() - start) * 1000), "follow_ups": build_follow_ups(prepared, answer)}
            return

        if not prepared["allowed"]:
            answer = prepared["refusal_message"] or REFUSAL_MESSAGE
            yield {"type": "chunk", "content": answer}
            self.memory.add(session_id, question, answer)
            yield {"type": "done", "response_time": round((time.time() - start) * 1000), "follow_ups": []}
            return

        budget = self._response_token_budget(prepared["classification"], prepared["resolved_question"])
        prompt = self._build_prompt(prepared, session_id, budget)
        full_answer = ""
        streamed = False
        degraded = False
        try:
            for chunk in self.llm.stream(prompt, max_tokens=budget):
                streamed = True
                full_answer += chunk
                yield {"type": "chunk", "content": chunk}
        except RuntimeError as exc:
            if streamed:
                yield {'type': 'error', 'error': str(exc)}
                return
            degraded = True
            yield {'type': 'status', 'message': '模型暂不可用，正在整理已核实的数据...'}

        if not streamed:
            degraded = True
            full_answer = self._fallback_answer(
                prepared["structured_text"],
                prepared["documents"],
                prepared["search_results"],
            )
            yield {"type": "chunk", "content": full_answer}

        cleaned = clean_repetition(full_answer)
        self.memory.add(session_id, question, cleaned)
        yield {"type": "done", "response_time": round((time.time() - start) * 1000), "follow_ups": build_follow_ups(prepared, cleaned), 'degraded': degraded}

    def prepare(self, question: str, top_k: int, force_web_search: bool = False,
                session_id: str | None = None) -> dict:
        resolved = self._resolve_question(question, session_id)
        classification = self.classifier.classify(resolved)
        plan = QueryPlan.create(question, resolved, classification, top_k,
                               force_web_search or self.settings.force_web_search)
        if not self._is_valid_question(question):
            bundle = EvidenceBundle(decision=GateDecision(False, 'invalid_question', INVALID_QUESTION_MESSAGE))
        elif self.evidence_gate.is_out_of_scope(resolved):
            bundle = EvidenceBundle(decision=GateDecision(False, 'out_of_scope', OUT_OF_SCOPE_MESSAGE))
        else:
            plan = self.query_planner.plan(question, resolved, classification, top_k,
                force_web_search or self.settings.force_web_search)
            bundle = self.evidence.resolve(plan)
            if bundle.decision.allowed and not bundle.direct_answer:
                bundle.direct_answer = self._scoreboard_answer(classification, bundle.search_results, resolved)
                if not bundle.direct_answer and bundle.decision.reason == 'structured_stale_fallback':
                    bundle.direct_answer = f'{STALE_DATA_MESSAGE}\n{bundle.structured_text}'
        prepared = bundle.prepared(plan)
        prepared['recent_questions'] = [item['content'] for item in self.memory.get(session_id) if item.get('role') == 'user']
        return prepared

    def _hydrate_memory(self, session_id: str | None,
                        conversation_history: list[dict] | None) -> None:
        if session_id and conversation_history:
            self.memory.hydrate(session_id, conversation_history)

    def _resolve_question(self, question: str, session_id: str | None) -> str:
        if session_id and re.search(r'这场|该场|这次比赛|这两队|双方|这项|这一点|数据来源|依据', question):
            current = self.classifier.classify(question)
            current_scope = game_context(question, current.teams)
            if len(current.teams) >= 2 and any(current_scope.get(key) for key in ('date', 'season', 'year')):
                return question
            history = self.memory.get(session_id)
            for index in range(len(history) - 1, -1, -1):
                item = history[index]
                if item.get('role') != 'user' or item.get('content') == question:
                    continue
                previous = self.classifier.classify(str(item.get('content') or ''))
                if len(previous.teams) >= 2:
                    if current.teams and set(current.teams) != set(previous.teams):
                        break
                    selected_date = explicit_game_date(question) or explicit_game_date(str(item.get('content') or ''))
                    if not selected_date:
                        for reply in history[index + 1:]:
                            if reply.get('role') == 'assistant':
                                selected_date = explicit_game_date(str(reply.get('content') or ''))
                                if selected_date:
                                    break
                    subject = '对'.join(previous.teams)
                    resolved = re.sub(r'这场比赛|该场比赛|这次比赛|这场|该场|这两队', subject + '的比赛', question).replace('双方', '两队')
                    if subject not in resolved:
                        resolved = f'{subject}的比赛：{resolved}'
                    return (selected_date + ' ' if selected_date else '') + resolved
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

    def _is_valid_question(self, question: str) -> bool:
        stripped = question.strip()
        if len(stripped) < 2 or not re.search(r"[A-Za-z0-9\u4e00-\u9fff]", stripped):
            return False
        # 纯英文/数字输入必须包含 NBA 领域词，避免向量库对随机字符串硬召回。
        if not re.search(r"[\u4e00-\u9fff]", stripped):
            classification = self.classifier.classify(stripped)
            if classification.teams or classification.players:
                return True
            return bool(re.search(
                r"\bnba\b|basketball|player|team|game|score|stats?|"
                r"ranking|trade|injury|draft|season|curry|lakers|warriors",
                stripped,
                re.IGNORECASE,
            ))
        return True

    def _response_token_budget(self, classification, question: str) -> int:
        hard_cap = min(max(1, int(self.settings.llm_max_tokens)), 4096)
        analysis_question = classification.query_type == QueryType.NEWS or QueryPlanner.classify_intent(question) in {'analysis', 'explanation', 'comparison'}
        if analysis_question:
            if classification.query_type == QueryType.MATCH and len(classification.teams) >= 2:
                return min(2048 if re.search(r'深入|深度|详细|全面|复盘', question) else 1536, hard_cap)
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
        documents = []
        for index, item in enumerate(prepared['documents'][:3]):
            metadata = prepared['metadatas'][index] if index < len(prepared['metadatas']) else {}
            attribution = {key: str(metadata.get(key) or 'UNKNOWN')[:300]
                           for key in ('type', 'source', 'quality', 'season', 'source_url')}
            documents.append(f'[Source metadata: {attribution}]\n{str(item)[:700]}')
        is_bounded_list = (
            prepared["classification"].query_type == QueryType.MATCH
            or bool(re.search(r"哪些|所有|全部|几场|列表|列出", prepared["resolved_question"]))
        )
        search_limit = 5 if is_bounded_list else 3
        search_results = prepared["search_results"][:search_limit]
        char_limit = max(220, min(1600, round(budget * 0.9)))
        return build_user_prompt(
            prepared["resolved_question"],
            prepared["structured_text"][:3000],
            format_documents(documents),
            format_search_results(search_results, max_total_chars=3000, max_snippet_chars=2400) if any(item.provider == 'espn_archive' for item in search_results) else format_search_results(search_results),
            conversation_context=self.memory.recent_context(session_id),
            response_budget_hint=f"请在 {char_limit} 个中文字符内完整回答。",
            analysis_questions=tuple(prepared.get('query_plan', {}).get('sub_questions', [])),
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
        if schedule_note is None and ANALYSIS_INTENT.search(question or ""):
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
            sourceDetails=self._source_details(prepared),
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
            follow_ups=build_follow_ups(prepared, answer),
        )

    @staticmethod
    def _source_details(prepared: dict) -> list[dict]:
        metadata = list(prepared['metadatas'])
        metadata.extend({'title': item.title, 'source_url': item.url, 'source': item.provider,
                         'synced_at': item.fetched_at, 'quality': 'OBSERVED' if item.provider in ('espn_archive', 'espn_scoreboard') else 'UNVERIFIED'}
                        for item in prepared['search_results'])
        return source_details(metadata)

    def _fallback_answer(self, structured_text: str, documents: list[str],
                         search_results: list | None = None) -> str:
        search_results = search_results or []
        if search_results:
            heading = "根据已核实的比赛数据：" if all(item.provider == "espn_archive" for item in search_results) else "根据搜索结果："
            return heading + "\n" + "\n".join(
                f"- {item.title}：{item.snippet or item.url}" for item in search_results[:3]
            )
        if structured_text:
            return f"根据结构化数据：\n{structured_text}"
        if documents:
            return "根据参考资料：\n" + "\n".join(f"- {doc}" for doc in documents[:3])
        return REFUSAL_MESSAGE
