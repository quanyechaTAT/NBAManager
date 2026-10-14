from __future__ import annotations

import sys
import threading
import hashlib
import json

from rag_app.config import RAGSettings
from rag_app.retrieval.model_paths import resolve_model_path
from rag_app.retrieval.cache import LoadingCache


class SentenceTransformerEmbeddings:
    def __init__(self, settings: RAGSettings):
        self.settings = settings
        self._model = None
        self._load_lock = threading.Lock()
        self.query_cache = LoadingCache(settings.embedding_cache_max_entries, settings.embedding_cache_ttl_seconds)
        self._encode_lock = threading.Lock()

    def _resolve_model_path(self) -> str:
        return resolve_model_path(self.settings.embedding_model, self.settings.model_cache_dir)

    def load(self):
        with self._load_lock:
            return self._load_once()

    def _load_once(self):
        if self._model is not None:
            return self._model

        from sentence_transformers import SentenceTransformer

        model_path = self._resolve_model_path()
        print(f"Loading embedding model: {model_path}", file=sys.stderr)
        model = SentenceTransformer(model_path, device=self.settings.embedding_device)
        if self.settings.embedding_device == "cuda" and self.settings.embedding_fp16:
            model.half()
        self._model = model
        return self._model

    def cache_identity(self):
        dimension = (getattr(self._model, 'get_embedding_dimension', None)
                     or getattr(self._model, 'get_sentence_embedding_dimension', None))
        return (self._resolve_model_path(), self.settings.embedding_revision,
                self.settings.embedding_device, self.settings.embedding_fp16, 'normalize-v1',
                dimension() if dimension else None)

    def embed_query(self, text: str) -> list[float]:
        return self.embed_queries([text])[0]

    def embed_queries(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        model = self.load()
        identity = self.cache_identity()
        normalized = [text.strip() for text in texts]
        keys = [hashlib.sha256(json.dumps([identity, text], ensure_ascii=False).encode()).hexdigest()
                for text in normalized]
        text_by_key = dict(zip(keys, normalized))
        def encode(missing):
            with self._encode_lock:
                return model.encode([text_by_key[key] for key in missing], batch_size=32,
                                    normalize_embeddings=True).tolist()
        return self.query_cache.get_many(keys, encode)

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        model = self.load()
        with self._encode_lock:
            return model.encode(texts, batch_size=32, normalize_embeddings=True).tolist()
