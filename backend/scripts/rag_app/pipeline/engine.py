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
from rag_app.tools.search_tool import BaiduSearchTool, format_search_results


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
    success: bool = True
    error: str = ""


class RAGEngine:
    def __init__(self, settings: RAGSettings):
        self.settings = settings
        self.classifier = QueryClassifier()
        self.evidence_gate = EvidenceGate()
        self.retriever = HybridRetriever(settings)
        self.structured = StructuredRepository(Database(settings))
        self.search_tool = BaiduSearchTool(settings.web_search_timeout_seconds)
        self.llm = LLMClient(settings)
        self.memory = ConversationMemory(settings.memory_window)

    def ask(self, question: str, top_k: int = 5, session_id: str | None = None) -> RAGAnswer:
        start = time.time()
        try:
            prepared = self.prepare(question, top_k)
            if not prepared["allowed"]:
                return self._answer(REFUSAL_MESSAGE, prepared, start, success=True)

            prompt = build_user_prompt(
                question,
                prepared["structured_text"],
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

    def stream(self, question: str, top_k: int = 5, session_id: str | None = None) -> Iterable[dict]:
        start = time.time()
        prepared = self.prepare(question, top_k)
        yield {
            "type": "sources",
            "sources": prepared["documents"],
            "metadatas": prepared["metadatas"],
            "search_results": [item.to_dict() for item in prepared["search_results"]],
            "used_tools": prepared["used_tools"],
            "structured_data": bool(prepared["structured_text"]),
        }

        if not prepared["allowed"]:
            yield {"type": "chunk", "content": REFUSAL_MESSAGE}
            yield {"type": "done", "response_time": round((time.time() - start) * 1000)}
            return

        prompt = build_user_prompt(
            question,
            prepared["structured_text"],
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

    def prepare(self, question: str, top_k: int) -> dict:
        classification = self.classifier.classify(question)
        structured_text = ""
        if classification.needs_structured_data:
            structured_text = self.structured.search(classification, top_k)

        retrieved = self.retriever.retrieve(question, top_k, classification)
        search_results = self._search_web(question, classification.needs_web_search)
        search_documents = [item.content or item.title for item in search_results]
        decision = self.evidence_gate.assess(structured_text, retrieved.documents + search_documents)

        used_tools = []
        if retrieved.documents:
            used_tools.append("rag")
        if structured_text:
            used_tools.append("sql")
        if search_results:
            used_tools.append("baidu_search")

        return {
            "classification": classification,
            "structured_text": structured_text,
            "documents": retrieved.documents,
            "metadatas": retrieved.metadatas,
            "distances": retrieved.distances,
            "search_results": search_results,
            "used_tools": used_tools,
            "allowed": decision.allowed,
        }

    def stats(self) -> dict:
        stats = self.retriever.stats()
        stats.update(
            {
                "collection_name": self.settings.collection_name,
                "llm_model": self.settings.llm_model,
                "device": self.settings.embedding_device,
                "web_search_enabled": self.settings.web_search_enabled,
                "web_search_provider": "baidu",
            }
        )
        return stats

    def clear_index(self) -> None:
        self.retriever.clear()

    @property
    def model_name(self) -> str:
        return f"{self.settings.embedding_model} + {self.settings.llm_model}"

    def _search_web(self, question: str, enabled_by_query: bool):
        if not self.settings.web_search_enabled or not enabled_by_query:
            return []
        try:
            return self.search_tool.search(question, self.settings.web_search_top_k)
        except Exception:
            return []

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
            success=success,
        )

    def _fallback_answer(self, structured_text: str, documents: list[str], search_results: list | None = None) -> str:
        search_results = search_results or []
        if structured_text:
            return f"根据结构化数据：\n{structured_text}"
        if documents:
            return "根据参考资料：\n" + "\n".join(f"- {doc}" for doc in documents[:3])
        if search_results:
            return "根据百度搜索结果：\n" + "\n".join(
                f"- {item.title}：{item.content or item.url}" for item in search_results[:3]
            )
        return REFUSAL_MESSAGE
