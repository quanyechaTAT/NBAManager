from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import logging
import time
import uuid

import redis
from discovery_app.domain import Candidate, Document, FEATURE_VERSION
from discovery_app.index import rrf
from discovery_app.learning import Model, experiment_assignment
from discovery_app.ranking import merge_channels, rank

log = logging.getLogger(__name__)


class CursorError(ValueError): pass


class Cursors:
    def __init__(self, cache): self.cache = cache

    @staticmethod
    def binding(request):
        keys = ('userId', 'scene', 'q', 'types', 'team', 'season', 'dateFrom', 'dateTo', 'itemKey', 'preferenceRevision', 'limit', 'mode')
        return hashlib.sha256(json.dumps({key: request.get(key) for key in keys}, sort_keys=True).encode()).hexdigest()

    def first(self, request: dict, rows: list[dict], metadata: dict):
        limit = request['limit']
        token = uuid.uuid4().hex
        try:
            if len(rows) > limit:
                self.cache.setex(f'discovery:cursor:{token}', 600, json.dumps({
                    'binding': self.binding(request), 'rows': rows, 'metadata': metadata}))
                return rows[:limit], f'{token}:{limit}'
        except redis.RedisError:
            metadata['degraded'] = True
        return rows[:limit], None

    def next(self, request: dict):
        try:
            token, offset = request['cursor'].split(':')
            offset = int(offset)
            if len(token) != 32 or not all(c in '0123456789abcdef' for c in token) or offset < 1:
                raise CursorError('Invalid cursor')
            value = self.cache.get(f'discovery:cursor:{token}')
            if not value: raise CursorError('Cursor expired; start a new request')
            snapshot = json.loads(value)
            if snapshot['binding'] != self.binding(request): raise CursorError('Cursor belongs to another context')
            rows, metadata = snapshot['rows'], snapshot['metadata']
            end = offset + request['limit']
            if offset >= len(rows): raise CursorError('Cursor offset exceeds result set')
            return {**metadata, 'items': rows[offset:end], 'nextCursor': f'{token}:{end}' if end < len(rows) else None}
        except (ValueError, KeyError, redis.RedisError) as error:
            raise CursorError(str(error)) from error


class Models:
    def __init__(self, db, directory):
        self.db, self.directory = db, directory
        self.checked, self.control, self.model = 0, {}, None

    def current(self):
        if time.monotonic()-self.checked < 2: return self.control, self.model
        self.checked = time.monotonic()
        control = self.db.control('model', {})
        version = control.get('version')
        try:
            if version and (not self.model or self.model.metadata['version'] != version):
                if not version.replace('-', '').isalnum(): raise ValueError('Invalid model version')
                loaded = Model(self.directory / version)
                if loaded.metadata['status'] != 'CANDIDATE': raise ValueError('Model did not pass offline evaluation')
                self.model = loaded
            if not version: self.model = None
            self.control = control
        except Exception:
            log.exception('Model activation failed; using the baseline')
            self.model, self.control = None, {}
        return self.control, self.model


class Serving:
    def __init__(self, db, cache, index, embedder, profiles, settings):
        self.db, self.cache, self.index, self.embedder, self.profiles = db, cache, index, embedder, profiles
        self.cursors = Cursors(cache)
        self.models = Models(db, settings.runtime / 'models')
        self.pool = ThreadPoolExecutor(max_workers=12, thread_name_prefix='discovery-recall')

    def search(self, request: dict, suggest=False):
        if request.get('cursor'): return self.cursors.next(request)
        lexical = self.pool.submit(self.index.lexical, request, 10 if suggest else 150, suggest)
        vector = None
        if not suggest and request.get('mode') != 'lexical' and request.get('q'):
            try: vector = self.embedder.encode(request['q'])
            except Exception: log.warning('Query encoder unavailable; using lexical retrieval')
        semantic = self.pool.submit(self.index.semantic, vector, request, 100, False) if vector else None
        degraded = request.get('mode') != 'lexical' and not suggest and vector is None
        lexical_hits = lexical.result()
        try: semantic_hits = semantic.result() if semantic else []
        except Exception:
            semantic_hits, degraded = [], True
        rows = rrf(lexical_hits, semantic_hits)
        metadata = {'strategyVersion': 'hybrid-rrf-v1' if semantic_hits else 'bm25-v1', 'degraded': degraded}
        page, cursor = self.cursors.first(request, rows, metadata)
        return {**metadata, 'items': page, 'nextCursor': cursor}

    @staticmethod
    def candidates(hits, semantic=False):
        return [Candidate(Document.from_index(hit['_source']), semantic=max(0., min(1., float(hit.get('_score') or 0))))
                if semantic else Candidate(Document.from_index(hit['_source'])) for hit in hits]

    def recommendations(self, request: dict):
        if request.get('cursor'): return self.cursors.next(request)
        scene, user = request['scene'], request['userId']
        kinds = ['NEWS'] if scene in ('NEWS_DETAIL', 'NEWS_FEED') else ['POST'] if scene in ('POST_DETAIL', 'COMMUNITY_FEED') else ['NEWS', 'POST']
        personalized = request.get('personalized', True)
        profile, degraded = self.profiles.get(user, request.get('preferenceRevision', 0)) if personalized else ({}, False)
        followed = set(request.get('followed', [])) if personalized and not degraded else set()
        # Existing favorites seed interests only; they never create exposure or training rows.
        if not profile and personalized and not degraded and not request.get('clearedAt'):
            seeds = self.index.documents(list(followed)[:100])
            profile = {'entities': {entity: 3.0 for doc in seeds for entity in doc.entities},
                       'seeds': [doc.item_key for doc in seeds if doc.kind in ('NEWS', 'POST')][:20]}
        tasks = {'hot': self.pool.submit(self.index.recall, request, kinds),
                 'explore': self.pool.submit(self.index.recall, request, kinds, latest=True)}
        if followed: tasks['followed'] = self.pool.submit(self.index.recall, request, kinds, entities=followed)
        seeds = ([request['itemKey']] if request.get('itemKey') else profile.get('seeds', [])[:3])
        channels = {}
        content_docs = self.index.documents(seeds)
        if content_docs:
            vector = self.index.content_vector(seeds)
            query = {**request, 'types': kinds}
            if vector: tasks['content'] = self.pool.submit(self.index.semantic, vector, query, 100)
            else:
                entities = set(entity for doc in content_docs for entity in doc.entities)
                if entities: tasks['content_entities'] = self.pool.submit(self.index.recall, request, kinds, entities=entities)
        if personalized and not degraded and seeds:
            try:
                generation = self.cache.get('discovery:itemcf:active')
                scores = {}
                if generation:
                    for value in self.cache.mget([f'discovery:itemcf:{generation}:{key}' for key in seeds]):
                        for key, score in json.loads(value or '[]'): scores[key] = max(scores.get(key, 0), score)
                channels['itemcf'] = [Candidate(doc, itemcf=scores[doc.item_key]) for doc in self.index.documents(
                    sorted(scores, key=lambda key: -scores[key])[:100])]
            except redis.RedisError: degraded = True
        for name, future in tasks.items():
            try: channels[name] = self.candidates(future.result(), semantic=name == 'content')
            except Exception:
                log.warning('Recall channel unavailable: %s', name)
                degraded = True
        if 'hot' not in channels and 'explore' not in channels:
            raise RuntimeError('No retrieval channel available')
        candidates = merge_channels(channels)
        blocked = set(request.get('blocked', [])) | set([request['itemKey']] if request.get('itemKey') else [])
        control, model = self.models.current()
        treatment = model and personalized and not degraded and experiment_assignment(user, control.get('traffic', 0), control.get('experiment', 'discovery-v1'))
        kwargs = dict(profile=profile if not degraded else {}, followed=followed if not degraded else set(), blocked=blocked,
                      scene=scene, now=time.time(), seed=f'{user}:{int(time.time()/600)}')
        # One bounded inference of the top 100; all pages share this frozen serving snapshot.
        rows, used = rank(candidates, **kwargs, limit=request['limit'], pages=5, predict=model.predict if treatment else None)
        if model and control.get('traffic', 0) == 0 and candidates:
            shadow, ok = rank(candidates, **kwargs, predict=model.predict)
            self.db.set_control('shadow', {'version': model.metadata['version'], 'at': time.time(), 'successful': ok,
                                          'overlap': len({r['itemKey'] for r in shadow} & {r['itemKey'] for r in rows[:20]})})
        metadata = {'strategyVersion': 'lambdarank-'+model.metadata['version'] if used else 'baseline-v1',
                    'modelVersion': model.metadata['version'] if used else None, 'degraded': degraded}
        page, cursor = self.cursors.first(request, rows, metadata)
        return {**metadata, 'items': page, 'nextCursor': cursor}
