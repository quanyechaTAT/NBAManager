"""Upgrade a legacy index only from publishable records, reusing loaded models."""
from pathlib import Path
from types import SimpleNamespace
import sys
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from rag_app.config import RAGSettings
from rag_app.indexing import builder
from rag_app.indexing.documents import IndexDocument
from rag_app.retrieval.vector_store import ChromaVectorStore


class Collection:
    def __init__(self):
        self.rows = {'legacy': {'type': 'player'}, 'stale': {'data_contract_version': 2}}
        self.upserts = []

    def get(self, where=None, include=None, limit=None):
        ids = [key for key, meta in self.rows.items() if not where or meta.get('data_contract_version') == 2]
        return {'ids': ids[:limit] if limit else ids}

    def upsert(self, **kwargs):
        self.upserts.append(kwargs)
        self.rows.update(zip(kwargs['ids'], kwargs['metadatas']))

    def delete(self, ids):
        for key in ids:
            del self.rows[key]

    def count(self):
        return len(self.rows)


def store():
    result = ChromaVectorStore(RAGSettings(), SimpleNamespace(embed_documents=lambda docs: [[1]] * len(docs)))
    result._collection = Collection()
    return result


def test_index_refresh_upserts_existing_ids_and_removes_unpublished_stale_rows(monkeypatch):
    vector_store = store()
    doc = IndexDocument('team:1', '马刺已核实球队数据', {'type': 'team', 'team': '马刺', 'quality': 'PARTIAL'})
    monkeypatch.setattr(builder, 'MySQLDocumentSource', lambda _: SimpleNamespace(load_all=lambda: [doc]))
    monkeypatch.setattr(builder, 'write_report', lambda _: None)
    report = builder.build_all_indexes(RAGSettings(), clear_first=False, store=vector_store)
    assert vector_store._collection.rows['team:1']['data_contract_version'] == 2
    assert vector_store._collection.rows['legacy'] == {'type': 'player'}
    assert 'stale' not in vector_store._collection.rows
    assert report['removed_stale_published'] == 1
    assert len(vector_store._collection.upserts) == 1


def test_source_failure_preserves_collection_before_any_mutation(monkeypatch):
    vector_store = store()
    original = dict(vector_store._collection.rows)
    def fail():
        raise ConnectionError()
    monkeypatch.setattr(builder, 'MySQLDocumentSource', lambda _: SimpleNamespace(load_all=fail))
    with pytest.raises(ConnectionError):
        builder.build_all_indexes(RAGSettings(), store=vector_store)
    assert vector_store._collection.rows == original


def test_clear_keeps_existing_reader_collection_identity():
    vector_store = store()
    handle = vector_store.collection()
    vector_store.clear()
    assert vector_store.collection() is handle
    assert handle.rows == {}


def test_bootstrap_skips_current_index_and_marks_legacy_migration(monkeypatch):
    monkeypatch.setattr(builder, 'read_report', lambda: {})
    vector_store = store()
    engine = SimpleNamespace(settings=RAGSettings(), retriever=SimpleNamespace(store=vector_store))
    calls = []
    def rebuild(settings, **kwargs):
        calls.append(kwargs)
        assert engine.retriever._index_status == 'building'
        return {'status': 'ok'}
    monkeypatch.setattr(builder, 'build_all_indexes', rebuild)
    assert builder.ensure_index_contract(engine)['status'] == 'current'
    assert calls == []
    vector_store._collection.rows.pop('stale')
    assert builder.ensure_index_contract(engine)['status'] == 'ok'
    assert calls == [{'clear_first': False, 'store': vector_store}]
    assert engine.retriever._index_status == 'ready'


def test_bootstrap_fault_preserves_failure_state(monkeypatch):
    monkeypatch.setattr(builder, 'read_report', lambda: {})
    vector_store = store()
    vector_store._collection.rows.pop('stale')
    engine = SimpleNamespace(settings=RAGSettings(), retriever=SimpleNamespace(store=vector_store))
    def fail(*args, **kwargs):
        raise TimeoutError()
    monkeypatch.setattr(builder, 'build_all_indexes', fail)
    with pytest.raises(TimeoutError):
        builder.ensure_index_contract(engine)
    assert engine.retriever._index_status == 'failed'


def test_partial_failed_upgrade_retries_even_with_some_published_documents(monkeypatch):
    engine = SimpleNamespace(settings=RAGSettings(), retriever=SimpleNamespace(store=store()))
    monkeypatch.setattr(builder, 'read_report', lambda: {'status': 'failed',
        'collection_name': engine.settings.collection_name, 'chroma_path': engine.settings.chroma_path})
    calls = []
    monkeypatch.setattr(builder, 'build_all_indexes', lambda *args, **kwargs: calls.append(kwargs) or {'status': 'ok'})
    assert builder.ensure_index_contract(engine)['status'] == 'ok'
    assert len(calls) == 1


def test_embedding_failure_records_retryable_report_and_does_not_prune(monkeypatch):
    vector_store = store()
    doc = IndexDocument('team:1', '马刺球队数据', {'type': 'team'})
    monkeypatch.setattr(builder, 'MySQLDocumentSource', lambda _: SimpleNamespace(load_all=lambda: [doc]))
    reports = []
    monkeypatch.setattr(builder, 'write_report', reports.append)
    def fail(*args):
        raise RuntimeError()
    monkeypatch.setattr(vector_store, 'upsert_texts', fail)
    with pytest.raises(RuntimeError):
        builder.build_all_indexes(RAGSettings(), clear_first=False, store=vector_store)
    assert 'stale' in vector_store._collection.rows
    assert [report['status'] for report in reports] == ['building', 'failed']
