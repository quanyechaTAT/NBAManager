from concurrent.futures import CancelledError, ThreadPoolExecutor
import threading
import time
import pytest
from unittest.mock import Mock
from discovery_app.config import Settings
from discovery_app.index import Index, QueryCache
from discovery_app.serving import Serving


def test_content_vector_is_normalized_and_avoids_loading_offline_numeric_dependencies():
    import subprocess
    import sys
    # A fresh worker is the relevant boundary: a previously imported NumPy hides the first-request cost.
    script='''
from dataclasses import replace
import math,sys
from unittest.mock import Mock
from discovery_app.config import Settings
from discovery_app.index import Index
index=Index(replace(Settings(),embedding_dim=2)); index.client=Mock()
index.client.mget.return_value={'docs':[
    {'found':True,'_source':{'embedding':[1.,0.],'content_hash':'a','embedding_hash':'a'}},
    {'found':True,'_source':{'embedding':[0.,1.],'content_hash':'b','embedding_hash':'b'}},
    {'found':True,'_source':{'embedding':[100.,0.],'content_hash':'new','embedding_hash':'old'}}]}
vector=index.content_vector(['NEWS:1','NEWS:2','NEWS:3'])
assert all(math.isclose(value,1/math.sqrt(2),rel_tol=1e-12) for value in vector)
assert 'numpy' not in sys.modules
'''
    subprocess.run([sys.executable,'-c',script],check=True,capture_output=True,text=True)


def test_content_vector_handles_empty_cancelled_and_malformed_embeddings():
    from dataclasses import replace
    index=Index(replace(Settings(),embedding_dim=2)); index.client=Mock()
    def result(vectors):
        return {'docs':[{'found':True,'_source':{'embedding':vector,'content_hash':'h','embedding_hash':'h'}} for vector in vectors]}
    index.client.mget.return_value={'docs':[{'found':False}]}
    assert index._content_vector(['NEWS:1']) is None
    index.client.mget.return_value=result([[1.,0.],[-1.,0.]])
    assert index._content_vector(['NEWS:1','NEWS:2']) is None
    for vectors in ([[1.,0.],[0.,1.,0.]],[[float('nan'),1.]]):
        index.client.mget.return_value=result(vectors)
        with pytest.raises(ValueError,match='Invalid content embedding'): index._content_vector(['NEWS:1','NEWS:2'])


def test_concurrent_identical_queries_share_a_single_lookup():
    cache=QueryCache(); entered=threading.Event(); release=threading.Event(); calls=[]
    def loader():
        calls.append(1); entered.set(); release.wait(.4); return ['NEWS:1']
    with ThreadPoolExecutor(max_workers=10) as pool:
        first=pool.submit(cache.get,'same',loader); assert entered.wait(.2)
        rest=[pool.submit(cache.get,'same',loader) for _ in range(9)]
        release.set()
        assert first.result()==['NEWS:1']
        assert all(result.result()==['NEWS:1'] for result in rest)
    assert len(calls)==1


def test_filters_have_distinct_keys_and_expired_results_refresh(monkeypatch):
    now=[0.0]; monkeypatch.setattr(time,'monotonic',lambda:now[0]); cache=QueryCache(ttl=1,capacity=2)
    assert cache.get('TEAM:1',lambda:['NEWS:1'])==['NEWS:1']
    assert cache.get('TEAM:2',lambda:['NEWS:2'])==['NEWS:2']
    now[0]=2
    assert cache.get('TEAM:1',lambda:['NEWS:3'])==['NEWS:3']
    cache.get('TEAM:3',lambda:['NEWS:4'])
    assert len(cache.values)==2 and 'TEAM:2' not in cache.values


def test_failures_do_not_poison_the_cache_or_future_requests():
    cache=QueryCache()
    with pytest.raises(RuntimeError): cache.get('q',lambda:(_ for _ in ()).throw(RuntimeError('offline')))
    assert cache.get('q',lambda:['NEWS:1'])==['NEWS:1']


def test_cached_retrieval_completes_while_executor_is_busy():
    cache=QueryCache(); release=threading.Event(); entered=threading.Event()
    cache.get('cached',lambda:['NEWS:1'])
    with ThreadPoolExecutor(max_workers=1) as pool:
        pool.submit(lambda:(entered.set(),release.wait(2)))
        assert entered.wait(1)
        try:
            result=cache.submit('cached',lambda:pytest.fail('Cache hit must not reload'),pool)
            assert result.result(.1)==['NEWS:1']
        finally: release.set()


def test_identical_inflight_queries_leave_executor_capacity_for_other_queries():
    cache=QueryCache(); release=threading.Event(); entered=threading.Event(); calls=[]
    def loader():
        calls.append(1); entered.set(); release.wait(2); return ['NEWS:1']
    with ThreadPoolExecutor(max_workers=2) as pool:
        first=cache.submit('popular',loader,pool)
        assert entered.wait(1)
        try:
            followers=[cache.submit('popular',loader,pool) for _ in range(30)]
            assert cache.submit('unrelated',lambda:['POST:2'],pool).result(.2)==['POST:2']
        finally: release.set()
        assert first.result(1)==['NEWS:1']
        assert all(future.result(1)==['NEWS:1'] for future in followers)
    assert len(calls)==1


def test_async_failure_and_executor_rejection_allow_a_fresh_retry():
    cache=QueryCache()
    with ThreadPoolExecutor(max_workers=1) as pool:
        failed=cache.submit('offline',lambda:(_ for _ in ()).throw(RuntimeError('offline')),pool)
        with pytest.raises(RuntimeError,match='offline'): failed.result(1)
        assert cache.submit('offline',lambda:['NEWS:1'],pool).result(1)==['NEWS:1']
    rejected=cache.submit('closed',lambda:['POST:2'],pool)
    with pytest.raises(RuntimeError): rejected.result(1)
    assert cache.get('closed',lambda:['POST:3'])==['POST:3']


def test_cancelled_queued_retrieval_does_not_leave_waiters_or_poison_retry():
    cache=QueryCache(); release=threading.Event(); entered=threading.Event()
    pool=ThreadPoolExecutor(max_workers=1)
    pool.submit(lambda:(entered.set(),release.wait(2)))
    assert entered.wait(1)
    try:
        queued=cache.submit('queued',lambda:['NEWS:1'],pool)
        pool.shutdown(wait=False,cancel_futures=True)
        with pytest.raises(CancelledError): queued.result(.2)
        assert cache.get('queued',lambda:['NEWS:2'])==['NEWS:2']
    finally:
        release.set(); pool.shutdown()


def test_cached_documents_do_not_share_request_candidates_and_refresh_after_expiry(monkeypatch):
    now=[0.0]; monkeypatch.setattr(time,'monotonic',lambda:now[0])
    index=Index(Settings()); index.client=Mock()
    def hits(views):
        return {'hits':{'hits':[{'_id':'NEWS:1','fields':{'views':[views],'entities':['TEAM:1'],
            'published_at':['1700000000000']}}]}}
    index.client.search.side_effect=[hits(10),hits(20)]
    query={'match_all':{}}
    first=Serving.candidates(index.search(query,documents=True))[0]
    second=Serving.candidates(index.search(query,documents=True))[0]
    assert first.document is second.document
    first.channels.add('followed'); first.semantic=.9
    assert second.channels==set() and second.semantic==0
    assert second.document.published_at==1700000000 and second.document.views==10
    now[0]=6
    refreshed=Serving.candidates(index.search(query,documents=True))[0]
    assert refreshed.document.views==20 and refreshed.document is not first.document
    assert index.client.search.call_count==2


def test_semantic_cache_binds_vector_values_filters_size_and_projection():
    index=Index(Settings()); index.client=Mock()
    index.client.search.return_value={'hits':{'hits':[]}}
    vector=[.1,.2,.3]
    index.semantic(vector,{'types':['NEWS']})
    index.semantic(list(vector),{'types':['NEWS']})
    assert index.client.search.call_count==1
    index.semantic([.1,.2,.4],{'types':['NEWS']})
    index.semantic(vector,{'types':['POST']})
    index.semantic(vector,{'types':['NEWS']},size=20)
    index.semantic(vector,{'types':['NEWS']},documents=False)
    assert index.client.search.call_count==5
