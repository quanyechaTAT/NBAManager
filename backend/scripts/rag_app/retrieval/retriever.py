from __future__ import annotations

from dataclasses import dataclass

from rag_app.config import RAGSettings
from rag_app.pipeline.query_classifier import QueryClassification
from rag_app.retrieval.dedupe import dedupe_texts
from rag_app.retrieval.embeddings import SentenceTransformerEmbeddings
from rag_app.retrieval.reranker import CrossEncoderReranker
from rag_app.retrieval.vector_store import ChromaVectorStore


@dataclass
class RetrievalResult:
    documents: list[str]
    metadatas: list[dict]
    distances: list[float]


class HybridRetriever:
    def __init__(self, settings: RAGSettings):
        self.settings = settings
        self.embeddings = SentenceTransformerEmbeddings(settings)
        self.store = ChromaVectorStore(settings, self.embeddings)
        self.reranker = CrossEncoderReranker(settings)

    def retrieve(self, query: str, top_k: int, classification: QueryClassification | None = None) -> RetrievalResult:
        request_k = max(top_k * 3, self.settings.vector_k)
        where = self._build_filter(classification)
        result = self.store.search(query, request_k, where=where)
        if where and not result.documents:
            result = self.store.search(query, request_k, where=None)
        docs, metas, distances = dedupe_texts(result.documents, result.metadatas, result.distances)
        docs, metas, distances = self._filter_by_distance(docs, metas, distances, top_k)
        docs, metas, distances = self.reranker.rerank(query, docs, metas, distances, top_k)
        return RetrievalResult(docs[:top_k], metas[:top_k], distances[:top_k])

    def _build_filter(self, classification: QueryClassification | None) -> dict | None:
        if not classification or not classification.teams:
            return None
        return {"team": {"$in": classification.teams}}

    def _filter_by_distance(self, docs: list[str], metas: list[dict], distances: list[float], top_k: int):
        filtered = [
            (doc, meta, distance)
            for doc, meta, distance in zip(docs, metas, distances)
            if distance <= self.settings.distance_threshold
        ]
        if len(filtered) < 1:
            filtered = list(zip(docs, metas, distances))[:top_k]
        return [x[0] for x in filtered], [x[1] for x in filtered], [x[2] for x in filtered]

    def stats(self) -> dict:
        return {
            "document_count": self.store.count(),
            "embedding_model": self.settings.embedding_model,
            "reranker_model": self.settings.reranker_model,
        }

    def clear(self) -> None:
        self.store.clear()
