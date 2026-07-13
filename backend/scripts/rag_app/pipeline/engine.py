from __future__ import annotations

from dataclasses import dataclass
import time
from typing import Iterable

from rag_app.config import RAGSettings
from rag_app.llm.client import LLMClient
from rag_app.llm.prompts import build_user_prompt
from rag_app.memory.store import ConversationMemory
from rag_app.pipeline.evidence_gate import EvidenceGate, REFUSAL_MESSAGE
from rag_app.pipeline.output_cleaner import clean_repetition
from rag_app.pipeline.query_classifier import QueryClassifier
from rag_app.retrieval.formatter import format_documents
from rag_app.retrieval.retriever import HybridRetriever
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
            force_web_search: bool = False) -> RAGAnswer:
        start = time.time()
        try:
            prepared = self.prepare(question, top_k, force_web_search)
            if not prepared["allowed"]:
                return self._answer(REFUSAL_MESSAGE, prepared, start, success=True)

            structured_text = prepared["structured_text"]
            if prepared["classification"].query_type.value == "ranking" and structured_text:
                structured_text = (
                    "【重要：以下排名数据是权威数据源，必须优先使用，不要用参考资料中的个别球员数据替代】\n"
                    + structured_text
                )

            prompt = build_user_prompt(
                question,
                structured_text,
                format_documents(prepared["documents"]),
                format_search_results(prepared["search_results"]),
            )
            answer = self.llm.complete(prompt)
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
               force_web_search: bool = False) -> Iterable[dict]:
        start = time.time()
        prepared = self.prepare(question, top_k, force_web_search)
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

        if not prepared["allowed"]:
            yield {"type": "chunk", "content": REFUSAL_MESSAGE}
            yield {"type": "done", "response_time": round((time.time() - start) * 1000)}
            return

        structured_text = prepared["structured_text"]
        if prepared["classification"].query_type.value == "ranking" and structured_text:
            structured_text = (
                "【重要：以下排名数据是权威数据源，必须优先使用，不要用参考资料中的个别球员数据替代】\n"
                + structured_text
            )

        prompt = build_user_prompt(
            question,
            structured_text,
            format_documents(prepared["documents"]),
            format_search_results(prepared["search_results"]),
        )
        full_answer = ""
        streamed = False
        for chunk in self.llm.stream(prompt):
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

    def prepare(self, question: str, top_k: int, force_web_search: bool = False) -> dict:
        classification = self.classifier.classify(question)
        tool_trace = []

        # 1. 结构化数据
        structured_text = ""
        if classification.needs_structured_data:
            structured_text = self.structured.search(classification, top_k, question)
            tool_trace.append({"tool": "sql", "query_type": classification.query_type.value})
        keyword_text = self.structured.search_by_keywords(question, top_k)
        if keyword_text:
            structured_text = (structured_text + "\n\n" + keyword_text).strip()

        # 2. 向量检索
        retrieved = self.retriever.retrieve(question, top_k, classification)
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
            or retrieved.quality.accepted_count == 0
            or (retrieved.quality.confidence_level == "none" and not structured_text.strip())
        )

        search_results = []
        search_execution = SearchExecution()
        if should_search and self.settings.search_enabled:
            # 查询改写
            queries = self.query_rewriter.rewrite(question)
            all_results = []
            for q in queries[:2]:  # 最多尝试 2 个改写查询
                results, execution = self.search_service.search(q, self.settings.search_top_k)
                all_results.extend(results)
                search_execution = execution
                if results:
                    break
            # 去重
            seen_urls = set()
            for r in all_results:
                if r.url not in seen_urls:
                    seen_urls.add(r.url)
                    search_results.append(r)
            search_results = search_results[:self.settings.search_top_k]
            tool_trace.append({
                "tool": "web_search",
                "providers_tried": search_execution.attempted_providers,
                "successful_provider": search_execution.successful_provider,
                "status": search_execution.status,
                "result_count": len(search_results),
                "elapsed_ms": search_execution.elapsed_ms,
            })

        # 4. 证据融合与决策
        search_documents = [r.snippet or r.title for r in search_results]
        all_documents = retrieved.documents + search_documents
        decision = self.evidence_gate.assess(structured_text, all_documents, question)

        # 5. 构建 used_tools
        used_tools = []
        if retrieved.documents:
            used_tools.append("rag")
        if structured_text:
            used_tools.append("sql")
        if search_results:
            providers = set(r.provider for r in search_results)
            for p in providers:
                used_tools.append(p)

        return {
            "classification": classification,
            "structured_text": structured_text,
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
        if structured_text:
            return f"根据结构化数据：\n{structured_text}"
        if documents:
            return "根据参考资料：\n" + "\n".join(f"- {doc}" for doc in documents[:3])
        if search_results:
            return "根据搜索结果：\n" + "\n".join(
                f"- {item.title}：{item.snippet or item.url}" for item in search_results[:3]
            )
        return REFUSAL_MESSAGE
