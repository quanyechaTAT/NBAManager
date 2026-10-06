from __future__ import annotations

import json
import queue
import threading


def encode_sse(data: dict) -> str:
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


def stream_with_heartbeats(events, interval: float = 10):
    """Keep the connection alive during SQL/search/model waits, with bounded buffering.

    The worker owns the gated iterator until it exits, including client cancellation;
    a blocked upstream call therefore cannot release its concurrency slot prematurely.
    """
    messages = queue.Queue(maxsize=2)
    stopped = threading.Event()
    end = object()

    def publish(message):
        while not stopped.is_set():
            try:
                messages.put(message, timeout=0.1)
                return
            except queue.Full:
                pass

    def produce():
        try:
            for event in events:
                if stopped.is_set():
                    break
                publish(encode_sse(event))
        except Exception:
            publish(encode_sse({'type': 'error', 'error': '流式服务异常，请重试'}))
        finally:
            try:
                if hasattr(events, 'close'):
                    events.close()
            finally:
                publish(end)

    worker = threading.Thread(target=produce, name='rag-sse', daemon=True)
    worker.start()
    try:
        while True:
            try:
                message = messages.get(timeout=interval)
            except queue.Empty:
                yield ': heartbeat\n\n'
                continue
            if message is end:
                return
            yield message
    finally:
        stopped.set()
