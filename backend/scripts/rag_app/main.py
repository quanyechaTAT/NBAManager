from __future__ import annotations

import sys
from functools import lru_cache

from fastapi import FastAPI

from rag_app.api.routes import create_router
from rag_app.config import RAGSettings
from rag_app.pipeline.engine import RAGEngine


settings = RAGSettings.from_env()


@lru_cache(maxsize=1)
def get_engine() -> RAGEngine:
    return RAGEngine(settings)

app = FastAPI(title="NBA Manager RAG Service", version="0.1.0")
app.include_router(create_router(get_engine))


def main() -> None:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else settings.port
    import uvicorn

    uvicorn.run(app, host=settings.host, port=port, log_level="info")


if __name__ == "__main__":
    main()
