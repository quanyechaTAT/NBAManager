from __future__ import annotations

from pathlib import Path
import glob
import sys

from rag_app.config import RAGSettings


class SentenceTransformerEmbeddings:
    def __init__(self, settings: RAGSettings):
        self.settings = settings
        self._model = None

    def _resolve_model_path(self) -> str:
        cache_dir = Path(self.settings.model_cache_dir)
        pattern = cache_dir / "models--richinfoai--ritrieve_zh_v1" / "snapshots" / "*" / ""
        snapshots = glob.glob(str(pattern))
        return snapshots[0] if snapshots else self.settings.embedding_model

    def load(self):
        if self._model is not None:
            return self._model

        from sentence_transformers import SentenceTransformer

        model_path = self._resolve_model_path()
        print(f"Loading embedding model: {model_path}", file=sys.stderr)
        self._model = SentenceTransformer(model_path, device=self.settings.embedding_device)
        if self.settings.embedding_device == "cuda" and self.settings.embedding_fp16:
            self._model.half()
        return self._model

    def embed_query(self, text: str) -> list[float]:
        model = self.load()
        return model.encode([text], normalize_embeddings=True)[0].tolist()

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        model = self.load()
        return model.encode(texts, batch_size=32, normalize_embeddings=True).tolist()
