from __future__ import annotations

import argparse
import time

from rag_app.config import RAGSettings
from rag_app.indexing.report import write_report
from rag_app.indexing.sources import MySQLDocumentSource
from rag_app.retrieval.embeddings import SentenceTransformerEmbeddings
from rag_app.retrieval.vector_store import ChromaVectorStore
from rag_app.structured.db import Database


def build_all_indexes(settings: RAGSettings | None = None, clear_first: bool = True, batch_size: int = 100) -> dict:
    settings = settings or RAGSettings.from_env()
    settings.ensure_runtime_dirs()
    started = time.time()

    source = MySQLDocumentSource(Database(settings))
    documents = source.load_all()

    store = ChromaVectorStore(settings, SentenceTransformerEmbeddings(settings))
    if clear_first:
        store.clear()

    for start in range(0, len(documents), batch_size):
        batch = documents[start : start + batch_size]
        store.add_texts(
            [doc.text for doc in batch],
            [doc.metadata for doc in batch],
            [doc.id for doc in batch],
        )
        print(f"Indexed {min(start + batch_size, len(documents))}/{len(documents)}")

    report = {
        "status": "ok",
        "document_count": len(documents),
        "collection_count": store.count(),
        "elapsed_seconds": round(time.time() - started, 2),
        "collection_name": settings.collection_name,
        "chroma_path": settings.chroma_path,
    }
    write_report(report)
    return report


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="Build NBA Manager RAG index")
    parser.add_argument("--no-clear", action="store_true", help="Append to the current collection instead of clearing it")
    parser.add_argument("--batch-size", type=int, default=100)
    args = parser.parse_args(argv)
    report = build_all_indexes(clear_first=not args.no_clear, batch_size=args.batch_size)
    print(report)


if __name__ == "__main__":
    main()
