from __future__ import annotations

import argparse
import time

from rag_app.config import RAGSettings
from rag_app.indexing.report import write_report, read_report
from rag_app.indexing.sources import MySQLDocumentSource
from rag_app.retrieval.embeddings import SentenceTransformerEmbeddings
from rag_app.retrieval.vector_store import ChromaVectorStore
from rag_app.structured.db import Database


def build_all_indexes(settings: RAGSettings | None = None, clear_first: bool = True, batch_size: int = 100,
                      *, store=None) -> dict:
    settings = settings or RAGSettings.from_env()
    settings.ensure_runtime_dirs()
    started = time.time()

    store = store or ChromaVectorStore(settings, SentenceTransformerEmbeddings(settings))
    batch_size = max(1, min(1000, batch_size))
    with store.build_lock():
        return _build_snapshot(settings, store, clear_first, batch_size, started)


def _build_snapshot(settings, store, clear_first, batch_size, started):
    documents = MySQLDocumentSource(Database(settings)).load_all()
    progress = {'status': 'building', 'collection_name': settings.collection_name,
                'chroma_path': settings.chroma_path, 'data_contract_version': 2,
                'document_count': len(documents)}
    write_report(progress)
    try:
        result = store.replace_published(documents, clear_first, batch_size)
    except Exception as exc:
        write_report({**progress, 'status': 'failed', 'error_type': type(exc).__name__})
        raise
    report = {
        "status": "ok",
        "document_count": len(documents),
        "collection_count": store.count(),
        "elapsed_seconds": round(time.time() - started, 2),
        "collection_name": settings.collection_name,
        "chroma_path": settings.chroma_path,
        **result,
        'data_contract_version': 2,
    }
    write_report(report)
    return report


def ensure_index_contract(engine):
    """Migrate legacy metadata from validated SQL, never by blessing old documents."""
    if not getattr(engine.settings, 'auto_build_index', True):
        return {'status': 'disabled'}
    store = engine.retriever.store
    previous = read_report()
    incomplete = (previous.get('collection_name') == engine.settings.collection_name and
                  previous.get('chroma_path') == engine.settings.chroma_path and
                  previous.get('status') in {'building', 'failed'})
    if store.has_published_index() and not incomplete:
        engine.retriever._index_status = 'ready'
        return {'status': 'current'}
    usable = store.has_published_index()
    engine.retriever._index_status = 'rebuilding' if usable else 'building'
    try:
        report = build_all_indexes(engine.settings, clear_first=False, store=store)
        engine.retriever._index_status = 'ready'
        return report
    except Exception:
        engine.retriever._index_status = 'ready' if usable else 'failed'
        raise


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="Build NBA Manager RAG index")
    parser.add_argument("--no-clear", action="store_true", help="Upsert publishable records while retaining legacy entries")
    parser.add_argument("--batch-size", type=int, default=100)
    args = parser.parse_args(argv)
    report = build_all_indexes(clear_first=not args.no_clear, batch_size=args.batch_size)
    print(report)


if __name__ == "__main__":
    main()
