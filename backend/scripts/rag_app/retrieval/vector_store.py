from __future__ import annotations

from dataclasses import dataclass
import sys
import hashlib
import json
import threading
import time
from uuid import uuid4

from rag_app.config import RAGSettings
from rag_app.retrieval.embeddings import SentenceTransformerEmbeddings
from rag_app.retrieval.index_version import IndexVersion


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
        self._collection_name = None
        self._collection_lock = threading.RLock()
        self._version = IndexVersion(settings.chroma_path, settings.collection_name)

    def cache_version(self):
        return self._version.token()

    def collection(self):
        # Injected unit-test stores have no persistent client.
        if self._collection is not None and self._client is None:
            return self._collection
        with self._collection_lock:
            name = self._version.active_collection(self.settings.collection_name)
            if self._collection is not None and self._collection_name == name:
                return self._collection
            if self._client is None:
                import chromadb
                self.settings.ensure_runtime_dirs()
                try:
                    self._client = chromadb.PersistentClient(path=self.settings.chroma_path)
                except KeyError:
                    self._client = chromadb.PersistentClient(path=self.settings.chroma_path)
            self._collection = self._client.get_or_create_collection(name=name, metadata={"hnsw:space": "cosine"})
            self._collection_name = name
            print(f"Chroma collection loaded: {name}, count={self._collection.count()}", file=sys.stderr)
            return self._collection

    def build_lock(self):
        return self._version.writer()

    def replace_published(self, documents, clear_first=True, batch_size=100):
        """Construct a complete snapshot; reuse vectors, then atomically publish its pointer.

        Caller holds build_lock across source loading and publication. The old collection
        remains readable, even if encoding, validation or publication fails.
        """
        source = self.collection()
        previous = self._collection_name or self.settings.collection_name
        owner = hashlib.sha256(self.settings.collection_name.encode()).hexdigest()[:16]
        name = f'nba-snapshot-{owner}-{uuid4().hex}'
        target = self._client.create_collection(name=name, metadata={'hnsw:space': 'cosine',
            'nba_snapshot_owner': owner, 'created_at': time.time()})
        identifiers = [doc.id for doc in documents]
        if len(set(identifiers)) != len(identifiers):
            self._client.delete_collection(name)
            raise ValueError('duplicate index document identifiers')
        embedded = reused = retained_legacy = 0
        try:
            old_ids = source.get(where={'data_contract_version': 2}, include=[])['ids']
            retained = set(identifiers)
            removed = len(set(old_ids) - retained)
            load = getattr(self.embeddings, 'load', None)
            if load: load()
            identity = getattr(self.embeddings, 'cache_identity', lambda: (
                self.settings.embedding_model, self.settings.embedding_revision, 'normalize-v1'))()
            profile = hashlib.sha256(json.dumps(identity, default=str).encode()).hexdigest()
            if not clear_first:
                # Legacy rows remain unqueryable by the publication contract.
                for offset in range(0, source.count(), batch_size):
                    rows = source.get(limit=batch_size, offset=offset, include=['documents', 'metadatas', 'embeddings'])
                    indexes = [i for i, meta in enumerate(rows['metadatas'])
                               if (meta or {}).get('data_contract_version') != 2 and rows['ids'][i] not in retained]
                    if indexes:
                        target.upsert(ids=[rows['ids'][i] for i in indexes],
                            documents=[rows['documents'][i] for i in indexes],
                            metadatas=[rows['metadatas'][i] or {'legacy': True} for i in indexes],
                            embeddings=[rows['embeddings'][i] for i in indexes])
                        retained_legacy += len(indexes)
            for start in range(0, len(documents), batch_size):
                batch = documents[start:start + batch_size]
                ids = [doc.id for doc in batch]
                old = source.get(ids=ids, include=['documents', 'metadatas', 'embeddings'])
                by_id = {identifier: i for i, identifier in enumerate(old['ids'])}
                vectors = [None] * len(batch)
                changed = []
                for i, doc in enumerate(batch):
                    index = by_id.get(doc.id)
                    if (index is not None and old['documents'][index] == doc.text
                            and (old['metadatas'][index] or {}).get('_embedding_profile') == profile):
                        vectors[i] = old['embeddings'][index]
                        reused += 1
                    else:
                        changed.append(i)
                if changed:
                    encoded = self.embeddings.embed_documents([batch[i].text for i in changed])
                    if len(encoded) != len(changed): raise ValueError('embedding count mismatch')
                    for i, vector in zip(changed, encoded): vectors[i] = vector
                    embedded += len(changed)
                target.upsert(ids=ids, documents=[doc.text for doc in batch], embeddings=vectors,
                    metadatas=[{**{k:v for k,v in doc.metadata.items() if v is not None},
                        'data_contract_version': 2, '_embedding_profile': profile} for doc in batch])
                print(f'Indexed {min(start + batch_size, len(documents))}/{len(documents)}; encoded={embedded}', flush=True)
            if target.count() != len(documents) + retained_legacy:
                raise ValueError('index snapshot count mismatch')
            self._version.publish_snapshot(name)
        except BaseException:
            self._client.delete_collection(name)
            raise
        # Readers refresh their handle on the next request; in-flight readers retain theirs.
        # Keep the prior generation and a 10-minute grace for other processes' old handles.
        try:
            collections = self._client.list_collections()
        except Exception as exc:
            print(f'Index cleanup deferred: {type(exc).__name__}', file=sys.stderr)
            collections = []
        for collection in collections:
            old_name = collection if isinstance(collection, str) else collection.name
            if old_name in {name, previous} or not old_name.startswith(f'nba-snapshot-{owner}-'): continue
            try:
                metadata = self._client.get_collection(old_name).metadata or {}
                if metadata.get('nba_snapshot_owner') == owner and metadata.get('created_at', time.time()) < time.time() - 600:
                    self._client.delete_collection(old_name)
            except Exception as exc:
                print(f'Index cleanup deferred: {type(exc).__name__}', file=sys.stderr)
        return {'embedded_documents': embedded, 'reused_vectors': reused,
                'removed_stale_published': removed, 'retained_legacy': retained_legacy,
                'collection_count': target.count(), 'snapshot': name}

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
        with self._version.writing():
            collection = self.collection()
            collection.add(
                documents=documents, metadatas=metadatas, ids=ids,
                embeddings=self.embeddings.embed_documents(documents),
            )

    def search_many(self, queries: list[str], top_k: int, where: dict | None = None) -> list[VectorSearchResult]:
        """One embedding batch and one read-only collection request for all variants."""
        if not queries:
            return []
        collection = self.collection()
        contract = {'data_contract_version': 2}
        embed = getattr(self.embeddings, 'embed_queries', self.embeddings.embed_documents)
        results = collection.query(query_embeddings=embed(queries),
            n_results=max(1, top_k), where={'$and': [where, contract]} if where else contract)
        documents = results.get('documents') or [[] for _ in queries]
        metadatas = results.get('metadatas') or [[] for _ in queries]
        distances = results.get('distances') or [[] for _ in queries]
        return [VectorSearchResult(documents[i], metadatas[i], [float(v) for v in distances[i]]) for i in range(len(queries))]

    def upsert_texts(self, documents, metadatas, ids):
        if documents:
            with self._version.writing():
                self.collection().upsert(documents=documents, metadatas=metadatas, ids=ids,
                                         embeddings=self.embeddings.embed_documents(documents))

    def has_published_index(self):
        return bool(self.collection().get(where={'data_contract_version': 2}, include=[], limit=1)['ids'])

    def prune_published(self, retained_ids):
        retained = set(retained_ids)
        current = self.collection().get(where={'data_contract_version': 2}, include=[])['ids']
        stale = [identifier for identifier in current if identifier not in retained]
        if stale:
            with self._version.writing():
                for start in range(0, len(stale), 1000):
                    self.collection().delete(ids=stale[start:start + 1000])
        return len(stale)

    def clear(self) -> None:
        # Keep the collection identity valid for all readers in this process.
        with self._version.writing():
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
