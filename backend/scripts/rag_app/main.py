from __future__ import annotations

import sys
import threading

from fastapi import FastAPI

from rag_app.api.routes import create_router
from rag_app.config import RAGSettings
from rag_app.pipeline.engine import RAGEngine


settings = RAGSettings.from_env()
_engine: RAGEngine | None = None
_engine_lock = threading.Lock()


def get_engine() -> RAGEngine:
    global _engine
    if _engine is None:
        with _engine_lock:
            if _engine is None:
                _engine = RAGEngine(settings)
    return _engine

app = FastAPI(title="NBA Manager RAG Service", version="0.1.0")
app.include_router(create_router(get_engine))


@app.on_event("startup")
async def preload_models():
    """Pre-load embedding and reranker models at startup to avoid first-request timeout."""
    def _preload():
        try:
            engine = get_engine()
            # Trigger model loading by calling embed_query
            engine.retriever.embeddings.load()
            print("Embedding model pre-loaded", file=sys.stderr)
            # Trigger reranker loading
            engine.retriever.reranker._load()
            print("Reranker model pre-loaded", file=sys.stderr)
        except Exception as e:
            print(f"Model pre-load failed: {e}", file=sys.stderr)

    # Run in background thread to not block startup
    threading.Thread(target=_preload, daemon=True).start()


def main() -> None:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else settings.port
    import uvicorn

    uvicorn.run(app, host=settings.host, port=port, log_level="info")


if __name__ == "__main__":
    main()
