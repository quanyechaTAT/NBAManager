# -*- coding: utf-8 -*-
"""第 9 轮 P1-2 回归：RAG `/stream` 必须与 `/query` 共用同一个饱和闸门。

## 缺陷
`/query`（GET+POST）有 `_QUERY_LIMIT = 8` 的准入闸门，`/stream`（GET+POST）没有——
测试方实测 14 并发直打 `/stream` 得到 `{200: 14}`、一个 429 都没有，可绕过 P2-1 的保护，
重新引入 python 侧无界排队（第 3 轮实测这类排队把 RAG 进程内存从 3.4GB 推到 6GB）。

## 微妙的点（本文件主要就在测这个）
`StreamingResponse` 是**惰性消费**的：视图函数返回时 body 一个字节都还没产出。
若在返回 `StreamingResponse` 之前就归还闸门，闸门立刻归零、上限形同虚设——
"看起来接了闸门"但实际不生效。因此闸门必须在**生成器内部**取，
并在生成器真正结束（正常结束 / 异常 / 客户端断开触发 GeneratorExit）时归还。

为此把闸门从闭包 dict 抽成了模块级 `SaturationGate`：闭包状态无法从外部断言，
"闸门有没有泄漏"就只能靠并发时序去猜；抽出来后 enter/leave/reset 可被精确锁定。
"""
from __future__ import annotations

import sys
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rag_app.api.routes import create_router, _QUERY_GATE, _QUERY_LIMIT


class _StreamEngine:
    def __init__(self, chunks=None, on_first_yield=None):
        self.chunks = chunks if chunks is not None else [{"type": "chunk", "content": "x"}]
        self.on_first_yield = on_first_yield
        self.stream_calls = 0
        self.ask_calls = 0

    def stream(self, question, top_k, session_id=None, force_web_search=False, conversation_history=None):
        self.stream_calls += 1
        if self.on_first_yield is not None:
            self.on_first_yield()
        for chunk in self.chunks:
            yield chunk

    def ask(self, question, top_k, session_id=None, force_web_search=False, conversation_history=None):
        self.ask_calls += 1
        return SimpleNamespace(
            success=True, answer="ok", sources=[], metadatas=[], distances=[],
            response_time=1, model="test", structured_data=False, search_results=[],
            used_tools=[], tool_trace=[], search_execution={}, retrieval_quality={}, error="",
        )


@pytest.fixture(autouse=True)
def _clean_gate():
    """每个测试前后都清空闸门，避免测试间互相污染（这是闭包版本做不到的）。"""
    _QUERY_GATE.reset()
    yield
    _QUERY_GATE.reset()


def _client(engine) -> TestClient:
    app = FastAPI()
    app.include_router(create_router(lambda: engine))
    return TestClient(app)


# --------------------------------------------------------------- 基础接线
@pytest.mark.parametrize("method", ["post", "get"])
def test_stream_paths_work_when_not_saturated(method):
    engine = _StreamEngine(chunks=[{"type": "chunk", "content": "答案"}, {"type": "done"}])
    client = _client(engine)

    if method == "post":
        response = client.post("/stream", json={"question": "湖人今天赢了吗", "top_k": 5})
    else:
        response = client.get("/stream", params={"question": "湖人今天赢了吗", "top_k": 5})

    assert response.status_code == 200
    assert "答案" in response.text and "done" in response.text
    assert engine.stream_calls == 1


def test_gate_is_released_after_stream_completes():
    """闸门必须被归还：连续多次请求不能因计数泄漏把自己锁死。"""
    engine = _StreamEngine()
    client = _client(engine)

    for i in range(_QUERY_LIMIT * 3):
        response = client.post("/stream", json={"question": f"问题{i}", "top_k": 5})
        assert "繁忙" not in response.text, f"第 {i + 1} 次请求被误判为过载（计数泄漏）"

    assert engine.stream_calls == _QUERY_LIMIT * 3
    assert _QUERY_GATE.active == 0, "全部流结束后在途数必须回到 0"


def test_gate_held_while_stream_is_being_consumed():
    """核心断言：流式消费期间闸门必须保持占用。

    在 engine 开始产出（即流式 body 真正开始消费）的瞬间读 active：
    - 正确实现：本请求已占额度 → active >= 1；
    - 错误实现（返回 StreamingResponse 前就 leave）：此刻 active 为 0，闸门形同虚设。
    """
    observed = {}

    def probe():
        observed["active_during_stream"] = _QUERY_GATE.active

    engine = _StreamEngine(chunks=[{"type": "chunk", "content": "probe"}], on_first_yield=probe)
    client = _client(engine)

    response = client.post("/stream", json={"question": "探测", "top_k": 5})

    assert "probe" in response.text
    assert observed.get("active_during_stream", 0) >= 1, (
        "流式消费期间闸门未被占用——说明闸门在返回 StreamingResponse 前就被释放了"
    )
    assert _QUERY_GATE.active == 0


# --------------------------------------------------------------- 饱和行为
def test_saturated_gate_returns_overload_error_event_not_an_answer():
    """超出上限时不能再是正常回答，必须明确表达过载。

    流式响应的状态码在惰性消费开始前就已确定，无法再改，
    因此过载以 SSE error 事件表达（与既有 _safe_stream 的错误事件同构）。
    """
    engine = _StreamEngine()
    client = _client(engine)

    for _ in range(_QUERY_LIMIT):
        assert _QUERY_GATE.enter() is True

    response = client.post("/stream", json={"question": "过载探测", "top_k": 5})

    assert "繁忙" in response.text or "overloaded" in response.text, (
        f"闸门占满时仍返回了正常回答: {response.text[:200]}"
    )
    assert engine.stream_calls == 0, "被拒绝的请求不应真的触达引擎"


def test_query_and_stream_share_one_counter():
    """/query 与 /stream 共用同一份额度，否则总并发可被放大成 2×limit。"""
    engine = _StreamEngine()
    client = _client(engine)

    for _ in range(_QUERY_LIMIT):
        assert _QUERY_GATE.enter() is True

    # 注意 question 有 min_length=2（schemas.QueryRequest），太短会先 422、
    # 测不到闸门——这里用合法长度的问题
    body = {"question": "湖人今天赢了吗", "top_k": 5}
    query_resp = client.post("/query", json=body)
    stream_resp = client.post("/stream", json=body)

    assert query_resp.status_code == 429, f"/query 应 429，实际 {query_resp.status_code}"
    assert "繁忙" in stream_resp.text or "overloaded" in stream_resp.text
    assert engine.ask_calls == 0 and engine.stream_calls == 0


def test_rejected_stream_does_not_leak_quota():
    """被拒绝的请求绝不能占用额度（否则闸门会被逐步吃满、永久锁死）。"""
    engine = _StreamEngine()
    client = _client(engine)

    for _ in range(_QUERY_LIMIT):
        assert _QUERY_GATE.enter() is True
    before = _QUERY_GATE.active

    for _ in range(5):
        client.post("/stream", json={"question": "过载", "top_k": 5})

    assert _QUERY_GATE.active == before, "被拒绝的请求泄漏了额度"

    # 释放后应恢复正常
    for _ in range(_QUERY_LIMIT):
        _QUERY_GATE.leave()
    assert "繁忙" not in client.post("/stream", json={"question": "恢复", "top_k": 5}).text


def test_gate_survives_engine_exception_and_releases_quota():
    """引擎在流出过程中抛异常时，闸门也必须归还（_safe_stream 会转成 error 事件）。"""

    class _BoomEngine(_StreamEngine):
        def stream(self, question, top_k, session_id=None, force_web_search=False, conversation_history=None):
            self.stream_calls += 1
            yield {"type": "chunk", "content": "部分"}
            raise RuntimeError("boom")

    engine = _BoomEngine()
    client = _client(engine)

    response = client.post("/stream", json={"question": "异常探测", "top_k": 5})

    assert "部分" in response.text
    assert _QUERY_GATE.active == 0, "异常路径泄漏了闸门额度"
    # 后续请求仍能正常工作
    assert "繁忙" not in client.post("/stream", json={"question": "恢复", "top_k": 5}).text


# --------------------------------------------------------------- 结构性锁定
def test_stream_impl_reuses_the_shared_gate():
    """结构性断言：GET/POST 两条 stream 路由都必须套 _gated_stream。

    闭包/生成器的内部时序无法完全靠行为测试覆盖（例如客户端提前断开），
    因此再直接读源码锁一遍"闸门在 finally 内归还、且包住 yield"。
    """
    source = (Path(__file__).resolve().parents[1] / "rag_app" / "api" / "routes.py").read_text(
        encoding="utf-8"
    )

    assert "_gated_stream" in source, "缺少 _gated_stream 包装器"
    assert source.count("_gated_stream(") >= 2, "GET/POST 两条 stream 路由都应套闸门"

    gated_body = source.split("def _gated_stream", 1)[1].split("@router", 1)[0]
    assert "_enter_query_gate()" in gated_body, "闸门未接入 _gated_stream"
    assert "finally:" in gated_body, "闸门释放必须在 finally，以覆盖异常/客户端断开"
    assert "_leave_query_gate()" in gated_body
    assert "yield" in gated_body, "闸门必须包住生成器的产出过程"


def test_overloaded_stream_is_a_json_error_event_not_a_double_encoded_string():
    import json
    engine = _StreamEngine()
    client = _client(engine)
    for _ in range(_QUERY_LIMIT):
        assert _QUERY_GATE.enter()
    response = client.get('/stream', params={'question': '湖人比赛'})
    data = next(line.removeprefix('data: ') for line in response.text.splitlines() if line.startswith('data: '))
    event = json.loads(data)
    assert isinstance(event, dict) and event['type'] == 'error' and event['overloaded']
