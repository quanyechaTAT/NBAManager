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
        try:
            self._client = chromadb.PersistentClient(path=self.settings.chroma_path)
            self._collection = self._client.get_or_create_collection(
                name=self.settings.collection_name,
                metadata={"hnsw:space": "cosine"},
            )
        except KeyError:
            # chromadb 进程级系统缓存偶发竞态（KeyError: <chroma_path>），
            # 重建客户端实例后重试一次；仍失败则向上抛由引擎降级
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
        contract = {"data_contract_version": 2}
        kwargs["where"] = {"$and": [where, contract]} if where else contract
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

    def search_many(self, queries: list[str], top_k: int, where: dict | None = None) -> list[VectorSearchResult]:
        """One embedding batch and one read-only collection request for all variants."""
        if not queries:
            return []
        collection = self.collection()
        contract = {'data_contract_version': 2}
        results = collection.query(query_embeddings=self.embeddings.embed_documents(queries),
            n_results=max(1, top_k), where={'$and': [where, contract]} if where else contract)
        documents = results.get('documents') or [[] for _ in queries]
        metadatas = results.get('metadatas') or [[] for _ in queries]
        distances = results.get('distances') or [[] for _ in queries]
        return [VectorSearchResult(documents[i], metadatas[i], [float(v) for v in distances[i]]) for i in range(len(queries))]

    def upsert_texts(self, documents, metadatas, ids):
        if documents:
            self.collection().upsert(documents=documents, metadatas=metadatas, ids=ids,
                                     embeddings=self.embeddings.embed_documents(documents))

    def has_published_index(self):
        return bool(self.collection().get(where={'data_contract_version': 2}, include=[], limit=1)['ids'])

    def prune_published(self, retained_ids):
        retained = set(retained_ids)
        current = self.collection().get(where={'data_contract_version': 2}, include=[])['ids']
        stale = [identifier for identifier in current if identifier not in retained]
        for start in range(0, len(stale), 1000):
            self.collection().delete(ids=stale[start:start + 1000])
        return len(stale)

    def clear(self) -> None:
        # Keep the collection identity valid for all readers in this process.
        collection = self.collection()
        identifiers = collection.get(include=[])['ids']
        for start in range(0, len(identifiers), 1000):
            collection.delete(ids=identifiers[start:start + 1000])

    def count(self) -> int:
        try:
            collection = self.collection()
            return int(collection.count())
        except Exception:
            return 0
