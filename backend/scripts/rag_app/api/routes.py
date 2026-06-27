from __future__ import annotations

from collections.abc import Callable

from fastapi import APIRouter, Body, Query
from fastapi.responses import StreamingResponse

from rag_app.api.schemas import QueryRequest, QueryResponse
from rag_app.api.sse import encode_sse
from rag_app.indexing.builder import build_all_indexes
from rag_app.pipeline.engine import RAGEngine


def create_router(engine_provider: Callable[[], RAGEngine]) -> APIRouter:
    router = APIRouter()

    def engine() -> RAGEngine:
        return engine_provider()

    @router.get("/health")
    def health():
        return {"status": "ok", "model_loaded": False}

    @router.get("/query", response_model=QueryResponse)
    def query_get(question: str, top_k: int = Query(5, ge=1, le=20), session_id: str | None = None):
        return _to_response(engine().ask(question, top_k, session_id))

    @router.post("/query", response_model=QueryResponse)
    def query_post(request: QueryRequest):
        return _to_response(engine().ask(request.question, request.top_k, request.session_id))

    @router.get("/stream")
    def stream_get(question: str, top_k: int = Query(5, ge=1, le=20), session_id: str | None = None):
        return StreamingResponse(
            (encode_sse(event) for event in engine().stream(question, top_k, session_id)),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
        )

    @router.post("/stream")
    def stream_post(request: QueryRequest):
        return StreamingResponse(
            (encode_sse(event) for event in engine().stream(request.question, request.top_k, request.session_id)),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
        )

    @router.get("/stats")
    def stats():
        return engine().stats()

    @router.post("/rebuild")
    def rebuild(body: dict = Body(default_factory=dict)):
        clear_first = bool(body.get("clear_first", True))
        try:
            report = build_all_indexes(engine().settings, clear_first=clear_first)
            return {"status": "ok", "message": "index rebuilt", "report": report}
        except Exception as exc:
            return {"status": "error", "message": str(exc)}

    return router


def _to_response(answer) -> QueryResponse:
    return QueryResponse(
        success=answer.success,
        answer=answer.answer,
        sources=answer.sources,
        metadatas=answer.metadatas,
        distances=answer.distances,
        response_time=answer.response_time,
        model=answer.model,
        structured_data=answer.structured_data,
        search_results=answer.search_results or [],
        used_tools=answer.used_tools or [],
        error=answer.error,
    )
