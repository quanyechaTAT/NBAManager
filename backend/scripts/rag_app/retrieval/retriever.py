from __future__ import annotations

from dataclasses import dataclass, field

from rag_app.config import RAGSettings
from rag_app.pipeline.query_classifier import QueryClassification
from rag_app.retrieval.dedupe import dedupe_texts
from rag_app.retrieval.embeddings import SentenceTransformerEmbeddings
from rag_app.retrieval.reranker import CrossEncoderReranker
from rag_app.retrieval.vector_store import ChromaVectorStore


@dataclass
class RetrievalQuality:
    candidate_count: int = 0
    accepted_count: int = 0
    rejected_count: int = 0
    best_vector_distance: float = 0.0
    average_vector_distance: float = 0.0
    best_rerank_score: float = 0.0
    average_rerank_score: float = 0.0
    entity_coverage: float = 0.0
    directly_answerable: bool = False
    confidence_level: str = "low"
    reason: str = ""


@dataclass
class RetrievalResult:
    documents: list[str]
    metadatas: list[dict]
    distances: list[float]
    rerank_scores: list[float] = field(default_factory=list)
    quality: RetrievalQuality = field(default_factory=RetrievalQuality)


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
        quality = RetrievalQuality(candidate_count=len(docs))

        # 过滤：只保留通过阈值的文档（不回填低质量文档）
        accepted = [
            (doc, meta, dist)
            for doc, meta, dist in zip(docs, metas, distances)
            if dist <= self.settings.max_vector_distance
        ]
        quality.rejected_count = len(docs) - len(accepted)

        if not accepted:
            # 无文档通过阈值
            quality.accepted_count = 0
            quality.reason = "no_documents_within_threshold"
            quality.confidence_level = "none"
            if distances:
                quality.best_vector_distance = min(distances)
                quality.average_vector_distance = sum(distances) / len(distances)
            return RetrievalResult([], [], [], [], quality)

        docs = [x[0] for x in accepted]
        metas = [x[1] for x in accepted]
        distances = [x[2] for x in accepted]

        # Rerank
        docs, metas, distances, rerank_scores = self.reranker.rerank_with_scores(query, docs, metas, distances, top_k)

        # 更新质量指标
        quality.accepted_count = len(docs)
        quality.best_vector_distance = min(distances) if distances else 0.0
        quality.average_vector_distance = sum(distances) / len(distances) if distances else 0.0
        quality.best_rerank_score = max(rerank_scores) if rerank_scores else 0.0
        quality.average_rerank_score = sum(rerank_scores) / len(rerank_scores) if rerank_scores else 0.0

        # 过滤低 rerank 分数
        if self.settings.min_rerank_score > 0:
            filtered = [
                (d, m, dist, rs)
                for d, m, dist, rs in zip(docs, metas, distances, rerank_scores)
                if rs >= self.settings.min_rerank_score
            ]
            if filtered:
                docs = [x[0] for x in filtered]
                metas = [x[1] for x in filtered]
                distances = [x[2] for x in filtered]
                rerank_scores = [x[3] for x in filtered]
                quality.accepted_count = len(docs)

        # 置信度
        if quality.best_rerank_score >= 0.7:
            quality.confidence_level = "high"
            quality.directly_answerable = True
        elif quality.best_rerank_score >= 0.4:
            quality.confidence_level = "medium"
        else:
            quality.confidence_level = "low"

        return RetrievalResult(docs[:top_k], metas[:top_k], distances[:top_k], rerank_scores[:top_k], quality)

    def _build_filter(self, classification: QueryClassification | None) -> dict | None:
        if not classification:
            return None
        if classification.players:
            return {"type": "player"}
        if classification.teams:
            return {"team": {"$in": classification.teams}}
        return None

    def stats(self) -> dict:
        return {
            "document_count": self.store.count(),
            "embedding_model": self.settings.embedding_model,
            "reranker_model": self.settings.reranker_model,
        }

    def clear(self) -> None:
        self.store.clear()
