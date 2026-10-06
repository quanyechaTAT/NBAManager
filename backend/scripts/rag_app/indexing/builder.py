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

    source = MySQLDocumentSource(Database(settings))
    documents = source.load_all()

    store = store or ChromaVectorStore(settings, SentenceTransformerEmbeddings(settings))
    batch_size = max(1, min(1000, batch_size))
    progress = {'status': 'building', 'collection_name': settings.collection_name,
                'chroma_path': settings.chroma_path, 'data_contract_version': 2,
                'document_count': len(documents)}
    write_report(progress)
    try:
        if clear_first:
            store.clear()
        for start in range(0, len(documents), batch_size):
            batch = documents[start : start + batch_size]
            store.upsert_texts(
                [doc.text for doc in batch],
                [{**{key:value for key,value in doc.metadata.items() if value is not None}, "data_contract_version": 2} for doc in batch],
                [doc.id for doc in batch],
            )
            print(f"Indexed {min(start + batch_size, len(documents))}/{len(documents)}", flush=True)
        removed = store.prune_published([doc.id for doc in documents])
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
        "removed_stale_published": removed,
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
    engine.retriever._index_status = 'building'
    try:
        report = build_all_indexes(engine.settings, clear_first=False, store=store)
        engine.retriever._index_status = 'ready'
        return report
    except Exception:
        engine.retriever._index_status = 'failed'
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
