from __future__ import annotations

from collections.abc import Callable

from fastapi import HTTPException, APIRouter, Body, Query
from fastapi.responses import StreamingResponse

import sys
import threading
import time
from rag_app.api.schemas import QueryRequest, QueryResponse
from rag_app.api.sse import encode_sse
from rag_app.indexing.builder import build_all_indexes
from rag_app.pipeline.engine import RAGEngine


class SaturationGate:
    """在途请求饱和闸门（第 8 轮 P2-1；第 9 轮 P1-2 起被 /query 与 /stream 共用）。

    语义：`enter()` 在未超限时占用一个额度并返回 True；超限返回 False（调用方回 429）。
    `leave()` 归还额度。计数是**进程级**的，`/query` 与 `/stream` 共用同一份，
    因此总并发不会因为"两条入口各算各的"被放大。

    为什么单独抽成类（而不是 create_router 内的闭包 dict）：
    闭包状态无法从外部断言，导致"闸门接没接、计数泄没泄漏"只能靠并发时序去猜。
    抽出来后测试可以直接 enter/leave/reset，行为可被精确锁定。
    """

    def __init__(self, limit: int):
        self._limit = limit
        self._lock = threading.Lock()
        self._active = 0

    @property
    def limit(self) -> int:
        return self._limit

    @property
    def active(self) -> int:
        with self._lock:
            return self._active

    def enter(self) -> bool:
        with self._lock:
            if self._active >= self._limit:
                return False
            self._active += 1
            return True

    def leave(self) -> None:
        with self._lock:
            if self._active > 0:
                self._active -= 1

    def reset(self) -> None:
        """仅供测试：清空在途计数。"""
        with self._lock:
            self._active = 0


# 第 8 轮 P2-1：饱和快速失败——在途+排队超过上限即 429，与 java bulkhead
# 语义对齐，避免 python 侧无界排队把内存与延迟拖垮（此前请求串行且无上限）。
# 第 9 轮 P1-2：/stream 也接入同一闸门（此前只有 /query）。
_QUERY_LIMIT = 8
_QUERY_GATE = SaturationGate(_QUERY_LIMIT)


def _enter_query_gate() -> bool:
    return _QUERY_GATE.enter()


def _leave_query_gate() -> None:
    _QUERY_GATE.leave()


def create_router(engine_provider: Callable[[], RAGEngine]) -> APIRouter:
    router = APIRouter()

    def engine() -> RAGEngine:
        return engine_provider()

    @router.get("/health")
    def health():
        current = engine()
        embedding_loaded = current.retriever.embeddings._model is not None
        reranker_required = bool(current.settings.reranker_enabled)
        reranker_loaded = current.retriever.reranker._model is not None
        model_loaded = embedding_loaded and (not reranker_required or reranker_loaded)
        if not model_loaded:
            # 模型仍在后台预加载：返回 503 让 Java 拉起循环继续等待，
            # 避免首个请求打到未就绪的模型（表现为流异常中断）
            from fastapi.responses import JSONResponse
            return JSONResponse(
                status_code=503,
                content={"status": "loading", "model_loaded": False},
            )
        return {
            "status": "ok",
            "model_loaded": True,
        }

    @router.get("/query", response_model=QueryResponse)
    def query_get(
        question: str,
        top_k: int = Query(5, ge=1, le=20),
        session_id: str | None = None,
        force_web_search: bool = False,
    ):
        if not _enter_query_gate():
            raise HTTPException(status_code=429, detail="RAG 服务繁忙，请稍后重试")
        try:
            return _to_response(engine().ask(question, top_k, session_id, force_web_search))
        finally:
            _leave_query_gate()

    @router.post("/query", response_model=QueryResponse)
    def query_post(request: QueryRequest):
        if not _enter_query_gate():
            raise HTTPException(status_code=429, detail="RAG 服务繁忙，请稍后重试")
        try:
            return _to_response(engine().ask(
                request.question,
                request.top_k,
                request.session_id,
                request.force_web_search,
                _conversation_history(request),
            ))
        finally:
            _leave_query_gate()

    def _safe_stream(gen):
        """包装引擎流：任何后端异常都转成 error 事件优雅收尾，绝不静默断流"""
        try:
            for event in gen:
                yield event
        except Exception as exc:  # noqa: BLE001
            yield {"type": "error", "error": f"服务内部错误: {exc}"}

    def _gated_stream(gen):
        """给流式响应套饱和闸门（第 9 轮 P1-2）。

        为什么必须在**生成器内部**取/放闸门：
        `StreamingResponse` 是惰性消费的——视图函数返回时body一个字节都还没产出。
        若在返回 `StreamingResponse` 之前 `_leave_query_gate()`，闸门立刻归零，
        并发上限形同虚设（这正是 `/stream` 此前能 14 并发全 200 的原因）。
        因此进入生成器时取闸门、`finally` 归还——覆盖正常结束、异常与客户端断开
        （断开时 asyncio 会向生成器抛 `GeneratorExit`，同样走 `finally`）。

        与 `/query` 共用同一个 `_QUERY_LIMIT` 计数器：两条入口共享同一份在途额度，
        避免"各算各的"把总并发放大成 2×limit。
        """
        if not _enter_query_gate():
            # 流式响应的头部在惰性消费开始前就已确定，无法再改状态码，
            # 因此以 SSE error 事件表达"过载"（与 _safe_stream 的错误事件同构），
            # 客户端按 error 处理而非把它当成正常回答。
            yield encode_sse({"type": "error", "error": "RAG 服务繁忙，请稍后重试", "overloaded": True})
            return
        try:
            for event in gen:
                yield event
        finally:
            # 必须在生成器真正结束（含 GeneratorExit/异常）时才归还
            _leave_query_gate()

    @router.get("/stream")
    def stream_get(
        question: str,
        top_k: int = Query(5, ge=1, le=20),
        session_id: str | None = None,
        force_web_search: bool = False,
    ):
        return StreamingResponse(
            (encode_sse(event) for event in _gated_stream(
                _safe_stream(engine().stream(question, top_k, session_id, force_web_search)))),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
        )

    @router.post("/stream")
    def stream_post(request: QueryRequest):
        return StreamingResponse(
            (encode_sse(event) for event in _gated_stream(
                _safe_stream(engine().stream(
                    request.question,
                    request.top_k,
                    request.session_id,
                    request.force_web_search,
                    _conversation_history(request),
                )))),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "Connection": "keep-alive"},
        )

    @router.get("/stats")
    def stats():
        return engine().stats()

    _rebuilding = {"flag": False}

    @router.post("/rebuild")
    def rebuild(body: dict = Body(default_factory=dict)):
        # 索引重建（2059+ 条文档的 embedding）需要数十秒，且 build 期间会占用 worker：
        # 改为后台线程执行并立即返回，防重入避免并发 clear/rebuild 竞争
        if _rebuilding["flag"]:
            return {"status": "busy", "message": "index rebuild in progress"}
        clear_first = bool(body.get("clear_first", True))

        def _run():
            try:
                _rebuilding["flag"] = True
                build_all_indexes(engine().settings, clear_first=clear_first)
                print("[rebuild] index rebuild finished", flush=True)
            except Exception as exc:
                print(f"[rebuild] index rebuild failed: {exc}", file=sys.stderr, flush=True)
            finally:
                _rebuilding["flag"] = False

        threading.Thread(target=_run, daemon=True, name="index-rebuild").start()
        return {"status": "started", "message": "index rebuild started in background"}

    return router


def _conversation_history(request: QueryRequest) -> list[dict]:
    history = []
    for turn in request.conversation_history:
        if hasattr(turn, "model_dump"):
            history.append(turn.model_dump())
        else:
            history.append(turn.dict())
    return history


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
        tool_trace=answer.tool_trace or [],
        search_execution=answer.search_execution or {},
        retrieval_quality=answer.retrieval_quality or {},
        error=answer.error,
    )
