from __future__ import annotations

import glob
from pathlib import Path
import sys

from rag_app.config import RAGSettings


class CrossEncoderReranker:
    def __init__(self, settings: RAGSettings):
        self.settings = settings
        self._model = None
        self._available = settings.reranker_enabled

    def _resolve_model_path(self) -> str:
        cache_dir = Path(self.settings.model_cache_dir)
        pattern = cache_dir / "models--BAAI--bge-reranker-v2-m3" / "snapshots" / "*" / ""
        snapshots = glob.glob(str(pattern))
        if snapshots:
            return snapshots[0]
        direct_path = cache_dir / "BAAI--bge-reranker-v2-m3"
        if direct_path.exists() and (direct_path / "config.json").exists():
            return str(direct_path)
        return self.settings.reranker_model

    def _load(self):
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
