from __future__ import annotations

from dataclasses import dataclass, field, asdict
from datetime import date
import hashlib
import json

from rag_app.config import RAGSettings
from rag_app.pipeline.query_classifier import QueryClassification
from rag_app.retrieval.dedupe import dedupe_texts, text_hash
from rag_app.retrieval.embeddings import SentenceTransformerEmbeddings
from rag_app.retrieval.reranker import CrossEncoderReranker
from rag_app.retrieval.vector_store import ChromaVectorStore
from rag_app.retrieval.cache import LoadingCache


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
        self._index_status = 'unchecked'
        self.query_cache = LoadingCache(settings.retrieval_cache_max_entries, settings.retrieval_cache_ttl_seconds)

    def retrieve_plan(self, plan, extra_queries=()) -> RetrievalResult:
        version = self.store.cache_version() if callable(getattr(type(self.store), 'cache_version', None)) else None
        if version is None or not hasattr(self, 'query_cache'):
            return self._retrieve_plan(plan, extra_queries)
        # No session/answer cache: resolved scope and public evidence completely determine this stage.
        key = hashlib.sha256(json.dumps({
            'version': version, 'contract': 2, 'algorithm': 'fusion-v1',
            'as_of': date.today().isoformat(),
            'collection': (self.settings.chroma_path, self.settings.collection_name),
            'question': plan.resolved_question, 'queries': plan.retrieval_queries,
            'extra': tuple(extra_queries), 'intent': plan.intent, 'scope': plan.game_scope,
            'classification': asdict(plan.classification), 'top_k': plan.top_k,
            'vector_k': self.settings.vector_k, 'max_distance': self.settings.max_vector_distance,
            'min_rerank': self.settings.min_rerank_score,
            'embedding': (self.embeddings._resolve_model_path(), self.settings.embedding_revision,
                          self.settings.model_cache_dir, self.settings.embedding_device, self.settings.embedding_fp16),
            'reranker': (self.reranker._resolve_model_path() if hasattr(self.reranker, '_resolve_model_path') else self.settings.reranker_model,
                         self.settings.reranker_revision,
                         self.settings.reranker_enabled, self.settings.reranker_device, self.reranker._available),
        }, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()
        return self.query_cache.get_or_load(key, lambda: self._retrieve_plan(plan, extra_queries),
            cacheable=lambda result: bool(result.documents) and self.store.cache_version() == version)

    def _retrieve_plan(self, plan, extra_queries=()) -> RetrievalResult:
        queries = list(dict.fromkeys((plan.retrieval_queries or (plan.resolved_question,)) + tuple(extra_queries)))[:5]
        request_k = min(60, max(plan.top_k * 3, self.settings.vector_k))
        where = self._build_filter(plan.classification)
        if plan.semantic and not plan.game_scope and where:
            # Reports can explain a mechanism that a player/team summary cannot.
            where = {'$or': [where, {'type': 'news'}, {'type': 'historical'}]}
        if callable(getattr(type(self.store), 'search_many', None)):
            batches = self.store.search_many(queries, request_k, where=where)
        else:
            batches = [self.store.search(q, request_k, where=where) for q in queries]
        candidates, seen = {}, set()
        for batch in batches:
            batch_seen = set()
            for rank, (doc, meta, distance) in enumerate(zip(batch.documents, batch.metadatas, batch.distances), 1):
                key = text_hash(doc)
                seen.add(key)
                if key in batch_seen:
                    continue
                batch_seen.add(key)
                if distance > self.settings.max_vector_distance or not plan.accepts_document(doc, meta):
                    continue
                previous = candidates.get(key)
                fusion_score = (previous[3] if previous else 0) + 1 / (60 + rank)
                if previous and previous[2] <= distance:
                    doc, meta, distance = previous[:3]
                candidates[key] = (doc, meta, distance, fusion_score)
        quality = RetrievalQuality(candidate_count=len(seen), rejected_count=len(seen) - len(candidates))
        if not candidates:
            quality.reason = 'no_scope_matching_evidence'
            quality.confidence_level = 'none'
            return RetrievalResult([], [], [], [], quality)
        fused = sorted(candidates.values(), key=lambda row: (-row[3], row[2]))[:60]
        docs, metas, distances, scores = self.reranker.rerank_with_scores(plan.resolved_question,
            [r[0] for r in fused], [r[1] for r in fused], [r[2] for r in fused], plan.top_k)
        accepted = [(d, m, dist, score) for d, m, dist, score in zip(docs, metas, distances, scores)
                    if score >= self.settings.min_rerank_score]
        quality.rejected_count += len(docs) - len(accepted)
        quality.accepted_count = len(accepted)
        if not accepted:
            quality.reason = 'no_documents_above_rerank_threshold'
            quality.confidence_level = 'none'
            return RetrievalResult([], [], [], [], quality)
        docs, metas, distances, scores = map(list, zip(*accepted))
        quality.best_vector_distance = min(distances)
        quality.average_vector_distance = sum(distances) / len(distances)
        quality.best_rerank_score = max(scores)
        quality.average_rerank_score = sum(scores) / len(scores)
        quality.confidence_level = 'high' if max(scores) >= .7 else 'medium' if max(scores) >= .4 else 'low'
        quality.directly_answerable = max(scores) >= .7
        quality.reason = 'multi_query_fusion' if len(queries) > 1 else 'single_query'
        return RetrievalResult(docs, metas, distances, scores, quality)

    def retrieve(self, query: str, top_k: int, classification: QueryClassification | None = None) -> RetrievalResult:
        request_k = max(top_k * 3, self.settings.vector_k)
        where = self._build_filter(classification)
        result = self.store.search(query, request_k, where=where)

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
            quality.rejected_count += len(docs) - len(filtered)
            docs = [x[0] for x in filtered]
            metas = [x[1] for x in filtered]
            distances = [x[2] for x in filtered]
            rerank_scores = [x[3] for x in filtered]
            quality.accepted_count = len(docs)
        if not docs:
            quality.confidence_level = 'none'
            quality.reason = 'no_documents_above_rerank_threshold'
            quality.best_rerank_score = 0.0
            quality.directly_answerable = False
            return RetrievalResult([], [], [], [], quality)

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
            return {'$and': [{'type': 'player'}, {'name': {'$in': classification.players}}]}
        if classification.query_type.value == 'match':
            return {'type': 'match'}
        if classification.teams:
            return {"team": {"$in": classification.teams}}
        return None

    @property
    def ready(self):
        return (getattr(self, '_index_status', 'ready') not in {'building', 'failed'} and
                self.embeddings._model is not None and (not self.settings.reranker_enabled or self.reranker._model is not None or not self.reranker._available))

    def stats(self) -> dict:
        return {
            "document_count": self.store.count(),
            "embedding_model": self.settings.embedding_model,
            "reranker_model": self.settings.reranker_model,
            'index_status': getattr(self, '_index_status', 'unchecked'),
            'query_cache': self.query_cache.stats(),
            'embedding_cache': self.embeddings.query_cache.stats(),
        }

    def clear(self) -> None:
        self.store.clear()
        self.query_cache.clear()

    def preload(self):
        self.embeddings.load()
        self.reranker._load()
