from concurrent.futures import ThreadPoolExecutor
import threading
from unittest.mock import Mock

from discovery_app.config import Settings
from discovery_app.index import Embedder


def test_concurrent_identical_cold_queries_share_one_encoding():
    embedder=Embedder(Settings())
    entered,contended,release=threading.Event(),threading.Event(),threading.Event()
    lock=threading.Lock()
    class ObservedLock:
        def acquire(self,*args,**kwargs):
            if lock.locked(): contended.set()
            return lock.acquire(*args,**kwargs)
        def release(self): lock.release()
    embedder.lock=ObservedLock()
    vector=Mock(); vector.tolist.return_value=[.5,.5]
    def encode(*args,**kwargs):
        entered.set(); assert release.wait(1)
        return vector
    embedder.model=Mock(); embedder.model.encode.side_effect=encode
    with ThreadPoolExecutor(max_workers=2) as pool:
        first=pool.submit(embedder.encode,'new query')
        try:
            assert entered.wait(.5)
            second=pool.submit(embedder.encode,'new query')
            assert contended.wait(.5)
        finally: release.set()
        assert first.result(.5)==[.5,.5]
        assert second.result(.5)==[.5,.5]
    assert embedder.model.encode.call_count==1


def test_online_encoding_wait_is_bounded_and_cached_queries_do_not_wait():
    embedder=Embedder(Settings()); embedder.model=Mock()
    embedder.cache['cached']=[.5,.5]
    embedder.lock.acquire()
    with ThreadPoolExecutor(max_workers=1) as pool:
        try:
            assert pool.submit(embedder.encode,'cached').result(.5)==[.5,.5]
            assert pool.submit(embedder.encode,'uncached').result(.5) is None
        finally: embedder.lock.release()
    embedder.model.encode.assert_not_called()
