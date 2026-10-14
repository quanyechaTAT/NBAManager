from __future__ import annotations

from collections import OrderedDict
from concurrent.futures import Future
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import threading
import time

from opensearchpy import OpenSearch, NotFoundError
from discovery_app.config import Settings
from discovery_app.domain import Document


class QueryCache:
    """One-second, bounded shared retrieval cache; values are read-only to callers.

    Only index queries are cached, never authorization, profiles, personalized ranks or hydration.
    Coalescing concurrent misses prevents popular queries from occupying every recall worker.
    """
    def __init__(self, ttl=1.0, capacity=256):
        self.ttl,self.capacity=ttl,capacity
        self.lock=threading.Lock(); self.values=OrderedDict(); self.pending={}

    def get(self, key, loader):
        with self.lock:
            entry=self.values.get(key)
            if entry and entry[0]>time.monotonic():
                self.values.move_to_end(key); return entry[1]
            future=self.pending.get(key); owner=future is None
            if owner: future=Future(); self.pending[key]=future
        if not owner: return future.result(timeout=.5)
        try:
            value=loader()
            with self.lock:
                self.values[key]=(time.monotonic()+self.ttl,value); self.values.move_to_end(key)
                while len(self.values)>self.capacity: self.values.popitem(last=False)
            future.set_result(value)
            return value
        except Exception as error:
            future.set_exception(error); raise
        finally:
            with self.lock: self.pending.pop(key,None)


class Embedder:
    def __init__(self, settings: Settings):
        self.settings, self.model = settings, None
        self.lock = threading.Lock()
        self.cache_lock = threading.Lock()
        self.cache = OrderedDict()

    def warm(self):
        if not self.settings.embedding_enabled: return
        with self.lock:
            if self.model is None:
                from sentence_transformers import SentenceTransformer
                # Share only immutable model weights with RAG, never its Chroma index or retrieval state.
                supplied = Path(self.settings.embedding_model)
                cache = Path(self.settings.model_cache)
                repository = cache / ('models--'+self.settings.embedding_model.replace('/', '--'))
                reference = repository / 'refs' / 'main'
                model_path = str(supplied) if supplied.is_dir() else self.settings.embedding_model
                if reference.is_file():
                    snapshot = repository / 'snapshots' / reference.read_text().strip()
                    if (snapshot / 'config.json').is_file(): model_path = str(snapshot)
                direct = cache / self.settings.embedding_model.replace('/', '--')
                if model_path == self.settings.embedding_model and (direct / 'config.json').is_file(): model_path = str(direct)
                model = SentenceTransformer(model_path, trust_remote_code=False)
                import torch
                torch.set_num_threads(2)
                dimension = getattr(model, 'get_embedding_dimension', None) or model.get_sentence_embedding_dimension
                if dimension() != self.settings.embedding_dim:
                    raise ValueError('Embedding dimension does not match the index contract')
                model.max_seq_length = min(model.max_seq_length, 512)
                if str(model.device).startswith('cuda'): model.half()
                model.encode('NBA', normalize_embeddings=True)
                self.model = model

    def encode(self, text: str, *, wait=False) -> list[float] | None:
        if self.model is None: return None
        with self.cache_lock:
            if text in self.cache:
                self.cache.move_to_end(text)
                return self.cache[text]
        # Online overload falls back to BM25 instead of queueing behind a large document encoding.
        if not self.lock.acquire(blocking=wait): return None
        try:
            vector = self.model.encode(text[:6000], normalize_embeddings=True).tolist()
            with self.cache_lock:
                self.cache[text] = vector
                if len(self.cache) > 256: self.cache.popitem(last=False)
            return vector
        finally: self.lock.release()


class Index:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = OpenSearch(settings.search_url, timeout=0.35, max_retries=0, retry_on_timeout=False,
                                 pool_maxsize=32, http_compress=True)
        self.admin = OpenSearch(settings.search_url, timeout=60, max_retries=2)
        self.alias = settings.index_alias
        self.queries = QueryCache()

    def create(self, name: str):
        keyword = {'type': 'keyword'}
        properties = {name: keyword for name in ('item_key', 'kind', 'entities', 'team_keys', 'category', 'season',
                                                 'source', 'author_id', 'content_hash', 'embedding_hash')}
        properties.update({name: {'type': 'text', 'analyzer': 'cjk', 'fields': {'raw': keyword}}
                           for name in ('title', 'title_en', 'aliases', 'text')})
        properties.update({name: {'type': 'long'} for name in ('views', 'likes', 'favorites', 'revision')})
        properties.update({'published_at': {'type': 'date', 'format': 'epoch_millis'},
                           'deleted': {'type': 'boolean'}, 'featured': {'type': 'boolean'},
                           'embedding': {'type': 'knn_vector', 'dimension': self.settings.embedding_dim,
                                         'method': {'name': 'hnsw', 'engine': 'lucene', 'space_type': 'cosinesimil'}}})
        self.admin.indices.create(index=name, body={'settings': {'index.knn': True, 'number_of_shards': 1,
                                                          'number_of_replicas': 0, 'refresh_interval': '1s'},
                                              'mappings': {'dynamic': 'strict', 'properties': properties}})

    def ensure(self):
        if self.admin.indices.exists_alias(name=self.alias): return
        name = self.alias + '-initial'
        try: self.create(name)
        except Exception:
            if not self.admin.indices.exists(index=name): raise
        self.admin.indices.put_alias(index=name, name=self.alias)

    def get(self, key: str, index=None):
        try: return self.client.get(index=index or self.alias, id=key)['_source']
        except NotFoundError: return None

    def documents(self, keys: list[str]) -> list[Document]:
        if not keys: return []
        identifiers=tuple(keys[:300])
        result = self.queries.get(('documents',identifiers),lambda:
            self.client.mget(index=self.alias, body={'ids': list(identifiers)}, _source_excludes=['embedding']))
        return [Document.from_index(row['_source']) for row in result['docs'] if row.get('found') and not row['_source'].get('deleted')]

    def content_vector(self, keys: list[str]):
        if not keys: return None
        return self.queries.get(('content-vector',tuple(keys[:3])),lambda:self._content_vector(keys[:3]))

    def _content_vector(self, keys: list[str]):
        import numpy as np
        result=self.client.mget(index=self.alias,body={'ids': keys[:3]}, _source_includes=['embedding','embedding_hash','content_hash','deleted'])
        vectors=[row['_source']['embedding'] for row in result['docs'] if row.get('found')
                 and not row['_source'].get('deleted') and row['_source'].get('embedding')
                 and row['_source'].get('embedding_hash')==row['_source'].get('content_hash')]
        if not vectors: return None
        vector=np.mean(np.asarray(vectors),axis=0)
        norm=np.linalg.norm(vector)
        return (vector/norm).tolist() if norm else None

    def write(self, doc: Document, index=None, embedding=None):
        target = index or self.alias
        body = doc.as_index()
        if embedding is not None and not doc.deleted:
            body.update(embedding=embedding, embedding_hash=body['content_hash'])
        self.admin.update(index=target, id=doc.item_key, retry_on_conflict=3, body={
            'scripted_upsert': True, 'upsert': {}, 'script': {'lang': 'painless', 'params': {'doc': body},
            'source': "if (ctx._source.containsKey('revision') && ctx._source.revision > params.doc.revision) { "
                "ctx.op='none'; } else { def old=ctx._source; ctx._source=params.doc; "
                "if (!params.doc.deleted && old.containsKey('embedding') && old.embedding_hash == params.doc.content_hash) { "
                "ctx._source.embedding=old.embedding; ctx._source.embedding_hash=old.embedding_hash; } }"}})

    def vector(self, key: str, content_hash: str, vector: list[float], index=None):
        # Atomic source-hash guard prevents slow encoders from overwriting newly edited or deleted content.
        return self.admin.update(index=index or self.alias, id=key, body={'script': {
            'lang': 'painless', 'source': "if (!ctx._source.deleted && ctx._source.content_hash == params.hash) { "
                "ctx._source.embedding = params.vector; ctx._source.embedding_hash = params.hash; } else { ctx.op='none'; }",
            'params': {'hash': content_hash, 'vector': vector}}}, retry_on_conflict=3)

    @staticmethod
    def filters(request: dict, kinds=None):
        filters = [{'term': {'deleted': False}}]
        types = kinds or request.get('types')
        if types: filters.append({'terms': {'kind': types}})
        if request.get('team'): filters.append({'term': {'team_keys': request['team']}})
        if request.get('season'): filters.append({'term': {'season': request['season']}})
        dates = {}
        if request.get('dateFrom'): dates['gte'] = int(request['dateFrom'])
        if request.get('dateTo'): dates['lt'] = int(request['dateTo'])
        if dates: filters.append({'range': {'published_at': dates}})
        return filters

    def search(self, query: dict, size=100, *, sort=None, documents=False) -> list[dict]:
        # Source filtering still decodes the entire 1792-dimensional vector for every hit.
        # Retrieval needs IDs; ranking reads only compact doc values, not full stored source JSON.
        body = {'size': min(size, 300), 'query': query, '_source': False, 'track_total_hits': False}
        fields = ['entities','team_keys','category','season','source','author_id','views','likes','favorites','revision','deleted','featured']
        if documents: body['docvalue_fields'] = fields + [{'field':'published_at','format':'epoch_millis'}]
        if sort: body['sort'] = sort
        key=hashlib.sha256(json.dumps(body,sort_keys=True,separators=(',',':')).encode()).digest()
        def retrieve():
            hits = self.client.search(index=self.alias, body=body)['hits']['hits']
            if documents:
                for hit in hits:
                    values=hit.get('fields', {})
                    hit['_source']={'item_key':hit['_id'],'title':'',**{key:(value if key in ('entities','team_keys') else value[0])
                        for key,value in values.items() if value}}
            return hits
        return self.queries.get(key,retrieve)

    def lexical(self, request: dict, size=100, suggest=False):
        q = request.get('q', '').strip()
        must = [] if not q else [{'multi_match': {'query': q, 'type': 'bool_prefix' if suggest else 'best_fields',
                    'fields': ['title^5', 'title_en^4', 'aliases^5'] + ([] if suggest else ['text']), 'operator': 'and'}}]
        query = {'bool': {'filter': self.filters(request), 'must': must}}
        return self.search(query, size=size, sort=None if q else [{'published_at': 'desc'}, {'item_key': 'asc'}])

    def semantic(self, vector: list[float], request: dict, size=100, documents=True):
        return self.search({'knn': {'embedding': {'vector': vector, 'k': size,
                            'filter': {'bool': {'filter': self.filters(request)}}}}}, size=size,documents=documents)

    def recall(self, request: dict, kinds: list[str], *, entities=None, latest=False):
        filters = self.filters(request, kinds)
        if entities: filters.append({'terms': {'entities': list(entities)[:100]}})
        sort = [{'published_at': 'desc'}, {'item_key': 'asc'}] if latest else [
            {'favorites': 'desc'}, {'likes': 'desc'}, {'views': 'desc'}, {'published_at': 'desc'}, {'item_key': 'asc'}]
        if not latest: filters.append({'range': {'published_at': {'gte': int(time.time()/60)*60000-30*86400000}}})
        return self.search({'bool': {'filter': filters}}, size=100, sort=sort,documents=True)


def rrf(*rankings: list[dict]) -> list[dict]:
    merged = {}
    for ranking in rankings:
        for position, hit in enumerate(ranking):
            key = hit['_id']
            if key not in merged: merged[key] = {'itemKey': key, 'score': 0.0}
            merged[key]['score'] += 1.0 / (60 + position + 1)
    return sorted(merged.values(), key=lambda row: (-row['score'], row['itemKey']))
