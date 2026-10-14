from __future__ import annotations

from contextlib import asynccontextmanager
import hmac
import logging
import threading
import anyio.to_thread
from typing import Literal

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, model_validator
import redis

from discovery_app.config import Settings
from discovery_app.database import Database
from discovery_app.domain import SCENES, TYPES, item_key
from discovery_app.index import Index, Embedder
from discovery_app.profiles import Profiles
from discovery_app.serving import Serving, CursorError

log = logging.getLogger(__name__)


class Request(BaseModel):
    userId: int = Field(gt=0)
    scene: str = 'SEARCH'
    q: str = Field(default='', max_length=200)
    types: list[str] = Field(default_factory=list, max_length=5)
    team: str | None = None
    season: str | None = Field(default=None, max_length=16)
    dateFrom: int | None = None
    dateTo: int | None = None
    itemKey: str | None = None
    mode: Literal['hybrid', 'lexical'] = 'hybrid'
    limit: int = Field(default=20, ge=1, le=20)
    cursor: str | None = Field(default=None, max_length=100)
    personalized: bool = True
    preferenceRevision: int = Field(default=0, ge=0)
    clearedAt: str | None = None
    followed: list[str] = Field(default_factory=list, max_length=500)
    blocked: list[str] = Field(default_factory=list, max_length=1000)

    @model_validator(mode='after')
    def validate_context(self):
        if self.scene not in SCENES or not set(self.types).issubset(TYPES): raise ValueError('Invalid scene or type')
        if self.team and item_key(self.team)[0] != 'TEAM': raise ValueError('Invalid team')
        if self.itemKey:
            kind, _ = item_key(self.itemKey)
            expected = {'NEWS_DETAIL': 'NEWS', 'POST_DETAIL': 'POST'}.get(self.scene)
            if expected and kind != expected: raise ValueError('Detail type does not match scene')
        if self.scene.endswith('_DETAIL') and not self.itemKey: raise ValueError('Detail item is required')
        if self.dateFrom is not None and self.dateTo is not None and self.dateFrom >= self.dateTo:
            raise ValueError('Invalid date interval')
        return self


def create_app(settings: Settings | None = None, service=None):
    settings = settings or Settings()
    if not 1 <= settings.api_threads <= 64: raise ValueError('DISCOVERY_API_THREADS must be between 1 and 64')
    if service is None:
        db, index, embedder = Database(settings), Index(settings), Embedder(settings)
        cache = redis.Redis.from_url(settings.redis_url, decode_responses=True, socket_timeout=.15, socket_connect_timeout=.15)
        service = Serving(db, cache, index, embedder, Profiles(db, cache, index), settings)

    @asynccontextmanager
    async def lifespan(app):
        # Bound synchronous requests independently from the parallel recall executor.
        anyio.to_thread.current_default_thread_limiter().total_tokens = settings.api_threads
        def warm():
            try: service.embedder.warm()
            except Exception: log.exception('Embedding model unavailable; lexical retrieval remains available')
        threading.Thread(target=warm, daemon=True, name='discovery-warm').start()
        yield
        if hasattr(service, 'pool'): service.pool.shutdown(wait=False, cancel_futures=True)

    app = FastAPI(title='NBA Discovery (internal)', docs_url=None, redoc_url=None, lifespan=lifespan)

    async def authenticate(x_discovery_token: str = Header(default='')):
        if not settings.internal_token:
            raise HTTPException(503, 'DISCOVERY_INTERNAL_TOKEN is required')
        if not hmac.compare_digest(x_discovery_token, settings.internal_token): raise HTTPException(401, 'Invalid internal token')

    @app.get('/health')
    def health(): return {'status': 'up'}

    @app.post('/internal/search', dependencies=[Depends(authenticate)])
    def search(request: Request): return invoke(service.search, request)

    @app.post('/internal/suggest', dependencies=[Depends(authenticate)])
    def suggest(request: Request): return invoke(lambda body: service.search(body, suggest=True), request)

    @app.post('/internal/recommendations', dependencies=[Depends(authenticate)])
    def recommendations(request: Request): return invoke(service.recommendations, request)

    def invoke(function, request):
        try:
            return JSONResponse(function(request.model_dump()))
        except CursorError as error: raise HTTPException(410, str(error)) from error
        except Exception as error:
            log.exception('Discovery request failed')
            raise HTTPException(503, 'Discovery temporarily unavailable') from error

    return app


app = create_app()
