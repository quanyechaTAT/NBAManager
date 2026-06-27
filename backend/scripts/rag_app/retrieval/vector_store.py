from __future__ import annotations

from dataclasses import dataclass
import sys

from rag_app.config import RAGSettings
from rag_app.retrieval.embeddings import SentenceTransformerEmbeddings


@dataclass
class VectorSearchResult:
    documents: list[str]
    metadatas: list[dict]
    distances: list[float]


class ChromaVectorStore:
    def __init__(self, settings: RAGSettings, embeddings: SentenceTransformerEmbeddings):
        self.settings = settings
        self.embeddings = embeddings
        self._client = None
        self._collection = None

    def collection(self):
        if self._collection is not None:
            return self._collection

        import chromadb

        self.settings.ensure_runtime_dirs()
        self._client = chromadb.PersistentClient(path=self.settings.chroma_path)
        self._collection = self._client.get_or_create_collection(
            name=self.settings.collection_name,
            metadata={"hnsw:space": "cosine"},
        )
        print(f"Chroma collection loaded: {self.settings.collection_name}, count={self.count()}", file=sys.stderr)
        return self._collection

    def search(self, query: str, top_k: int, where: dict | None = None) -> VectorSearchResult:
        collection = self.collection()
        query_embedding = self.embeddings.embed_query(query)
        kwargs = {
            "query_embeddings": [query_embedding],
            "n_results": max(top_k, 1),
        }
        if where:
            kwargs["where"] = where
        results = collection.query(**kwargs)
        return VectorSearchResult(
            documents=(results.get("documents") or [[]])[0],
            metadatas=(results.get("metadatas") or [[]])[0],
            distances=[float(value) for value in (results.get("distances") or [[]])[0]],
        )

    def add_texts(self, documents: list[str], metadatas: list[dict], ids: list[str]) -> None:
        if not documents:
            return
        collection = self.collection()
        collection.add(
            documents=documents,
            metadatas=metadatas,
            ids=ids,
            embeddings=self.embeddings.embed_documents(documents),
        )

    def clear(self) -> None:
        if self._client is None:
            self.collection()
        try:
            self._client.delete_collection(self.settings.collection_name)
        except Exception:
            pass
        self._collection = self._client.get_or_create_collection(
            name=self.settings.collection_name,
            metadata={"hnsw:space": "cosine"},
        )

    def count(self) -> int:
        try:
            collection = self.collection()
            return int(collection.count())
        except Exception:
            return 0
