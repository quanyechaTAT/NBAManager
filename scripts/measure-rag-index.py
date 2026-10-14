"""Measure incremental ingestion using SQL documents and local models in an isolated index."""
import argparse
from dataclasses import replace
import json
import os
from pathlib import Path
import sys
import time
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend/scripts'))
from dotenv import load_dotenv
from rag_app.config import RAGSettings
from rag_app.indexing.sources import MySQLDocumentSource
from rag_app.structured.db import Database
from rag_app.retrieval.embeddings import SentenceTransformerEmbeddings
from rag_app.retrieval.vector_store import ChromaVectorStore


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sample-size', type=int, default=100)
    args = parser.parse_args()
    load_dotenv(ROOT / '.env')
    os.environ['HF_HUB_OFFLINE'] = '1'
    evidence = ROOT / 'test-evidence/performance'
    directory = evidence / ('index-probe-' + uuid4().hex)
    settings = replace(RAGSettings.from_env(), chroma_path=str(directory), collection_name='nba_performance_probe')
    documents = MySQLDocumentSource(Database(settings)).load_all()
    sample = documents[:max(1, min(1000, args.sample_size))]
    embeddings = SentenceTransformerEmbeddings(settings)
    started = time.perf_counter(); embeddings.load()
    model_ms = round((time.perf_counter() - started) * 1000, 2)
    store = ChromaVectorStore(settings, embeddings)
    runs = []
    for _ in range(2):
        started = time.perf_counter()
        with store.build_lock(): result = store.replace_published(sample, clear_first=True, batch_size=100)
        runs.append({'ms': round((time.perf_counter() - started) * 1000, 2), **result})
    assert runs[0]['embedded_documents'] == len(sample)
    assert runs[1]['embedded_documents'] == 0 and runs[1]['reused_vectors'] == len(sample)
    report = {'isolated_index': str(directory), 'sql_document_count': len(documents),
              'sample_count': len(sample), 'model_load_ms': model_ms, 'device': settings.embedding_device,
              'production_index_unchanged': True, 'runs': runs}
    evidence.mkdir(parents=True, exist_ok=True)
    (evidence / 'phase3-index.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
