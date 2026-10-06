from __future__ import annotations

import sys
import threading

from rag_app.config import RAGSettings
from rag_app.retrieval.model_paths import resolve_model_path


class CrossEncoderReranker:
    def __init__(self, settings: RAGSettings):
        self.settings = settings
        self._model = None
        self._available = settings.reranker_enabled
        self._load_lock = threading.Lock()

    def _resolve_model_path(self) -> str:
        return resolve_model_path(self.settings.reranker_model, self.settings.model_cache_dir)

    def _load(self):
        with self._load_lock:
            return self._load_once()

    def _load_once(self):
        if not self._available:
            return None
        if self._model is not None:
            return self._model
        try:
            from sentence_transformers import CrossEncoder
            model_path = self._resolve_model_path()
            print(f"Loading reranker model: {model_path}", file=sys.stderr)
            self._model = CrossEncoder(model_path, device=self.settings.reranker_device)
            return self._model
        except Exception as exc:
            self._available = False
            print(f"Reranker disabled: {exc}", file=sys.stderr)
            return None

    def rerank(self, query: str, documents: list[str], metadatas: list[dict],
               distances: list[float], top_k: int):
        """向后兼容：不返回 rerank_scores"""
        docs, metas, dists, _ = self.rerank_with_scores(query, documents, metadatas, distances, top_k)
        return docs, metas, dists

    def rerank_with_scores(self, query: str, documents: list[str], metadatas: list[dict],
                           distances: list[float], top_k: int):
        """返回 (docs, metas, distances, rerank_scores)"""
        model = self._load()
        if not model or not documents:
            scores = [0.0] * len(documents)
            return documents[:top_k], metadatas[:top_k], distances[:top_k], scores[:top_k]

        scores = model.predict([[query, doc] for doc in documents])
        rows = sorted(
            zip(documents, metadatas, distances, scores),
            key=lambda item: float(item[3]),
            reverse=True,
        )
        rows = rows[:top_k]
        return (
            [r[0] for r in rows],
            [r[1] for r in rows],
            [float(r[2]) for r in rows],
            [float(r[3]) for r in rows],
        )
