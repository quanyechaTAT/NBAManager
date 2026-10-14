from concurrent.futures import ThreadPoolExecutor
import threading
import time
import pytest
from discovery_app.index import QueryCache


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
