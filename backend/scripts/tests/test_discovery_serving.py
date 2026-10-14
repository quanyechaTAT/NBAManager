from dataclasses import replace
from unittest.mock import Mock
import json
import time

from fastapi.testclient import TestClient
import pytest
import redis

from discovery_app.api import create_app
from discovery_app.config import Settings
from discovery_app.domain import Document
from discovery_app.index import rrf
from discovery_app.profiles import apply_event
from discovery_app.serving import Cursors, CursorError, Serving


class Cache:
    def __init__(self): self.values = {}
    def get(self, key): return self.values.get(key)
    def setex(self, key, ttl, value): self.values[key] = value
    def mget(self, keys): return [self.get(key) for key in keys]


def test_cursor_freezes_rows_and_binds_identity_query_scene_and_preference_revision():
    cache = Cache(); cursors = Cursors(cache)
    request = {'userId': 7, 'scene': 'SEARCH', 'q': '库里', 'types': ['PLAYER'], 'limit': 2, 'preferenceRevision': 0}
    rows = [{'itemKey': f'PLAYER:{index}'} for index in range(1, 6)]
    first, token = cursors.first(request, rows, {'strategyVersion': 'bm25-v1'})
    rows.reverse()
    second = cursors.next({**request, 'cursor': token})
    assert first == [{'itemKey': 'PLAYER:1'}, {'itemKey': 'PLAYER:2'}]
    assert second['items'] == [{'itemKey': 'PLAYER:3'}, {'itemKey': 'PLAYER:4'}]
    for change in ({'userId': 8}, {'q': '詹姆斯'}, {'scene': 'HOME'}, {'preferenceRevision': 1}):
        with pytest.raises(CursorError): cursors.next({**request, **change, 'cursor': token})
    cache.values.clear()
    with pytest.raises(CursorError): cursors.next({**request, 'cursor': token})


def test_redis_cursor_failure_returns_first_page_without_a_broken_next_link():
    cache = Mock(); cache.setex.side_effect = redis.ConnectionError('offline')
    metadata = {}
    rows, token = Cursors(cache).first({'limit': 1}, [{'itemKey': 'NEWS:1'}, {'itemKey': 'NEWS:2'}], metadata)
    assert len(rows) == 1 and token is None and metadata['degraded']


def test_rrf_combines_independent_retrieval_orders_and_deduplicates():
    rows = rrf([{'_id': 'NEWS:1'}, {'_id': 'NEWS:2'}], [{'_id': 'NEWS:2'}, {'_id': 'NEWS:3'}])
    assert rows[0]['itemKey'] == 'NEWS:2'
    assert len(rows) == 3


def test_private_api_requires_token_and_validates_identity_types_and_detail_context():
    service = Mock()
    service.search.return_value = {'items': [], 'degraded': False}
    client = TestClient(create_app(replace(Settings(), internal_token='test-only'), service))
    assert client.post('/internal/search', json={'userId': 1}).status_code == 401
    headers = {'X-Discovery-Token': 'test-only'}
    assert client.post('/internal/search', json={'userId': 1}, headers=headers).status_code == 200
    for body in ({'userId': 0}, {'userId': 1, 'types': ['USER']}, {'userId': 1, 'scene': 'NEWS_DETAIL'},
                 {'userId': 1, 'scene': 'POST_DETAIL', 'itemKey': 'NEWS:1'}):
        assert client.post('/internal/search', json=body, headers=headers).status_code == 422
    assert TestClient(create_app(replace(Settings(), internal_token=''), service)).post(
        '/internal/search', json={'userId': 1}).status_code == 503


def test_model_registry_initialization_finishes_before_accepting_requests():
    ready=[]; service=Mock()
    service.models.current.side_effect=lambda:ready.append(True)
    service.recommendations.side_effect=lambda request: {'items':[],'degraded':not bool(ready)}
    with TestClient(create_app(replace(Settings(),internal_token='test-only'),service)) as client:
        assert ready==[True]
        response=client.post('/internal/recommendations',headers={'X-Discovery-Token':'test-only'},
                             json={'userId':1,'scene':'HOME'})
        assert response.status_code==200 and response.json()['degraded'] is False


def test_unavailable_model_registry_during_startup_does_not_disable_lexical_search():
    service=Mock(); service.models.current.side_effect=RuntimeError('Unavailable model registry')
    service.search.return_value={'items':[],'strategyVersion':'bm25-v1','degraded':False}
    with TestClient(create_app(replace(Settings(),internal_token='test-only'),service)) as client:
        response=client.post('/internal/search',headers={'X-Discovery-Token':'test-only'},
                             json={'userId':1,'q':'test','mode':'lexical'})
        assert response.status_code==200 and response.json()['strategyVersion']=='bm25-v1'


def test_reading_interest_decay_and_exposure_windows_are_bounded():
    profile = {}
    event = {'received_at': 1000000., 'event_type': 'IMPRESSION', 'item_key': 'NEWS:1'}
    apply_event(profile, event, ['TEAM:1'])
    assert profile['entities']['TEAM:1'] == 0
    assert profile['exposures']['NEWS:1'] == 1
    apply_event(profile, {**event, 'event_type': 'READ', 'dwell_ms': 15000}, ['TEAM:1'])
    assert profile['entities']['TEAM:1'] == 1
    apply_event(profile, {**event, 'received_at': 1000000.+8*86400, 'item_key': 'NEWS:2'}, [])
    assert 'NEWS:1' not in profile['exposures'] and 'NEWS:1' not in profile['reads']
    assert 0 < profile['entities']['TEAM:1'] < .4


def test_opt_out_does_not_use_saved_interests_following_or_itemcf():
    db, profiles, index, embedder = Mock(), Mock(), Mock(), Mock()
    db.control.return_value = {}
    embedder.encode.return_value = None
    index.documents.return_value = []
    docs = [Document('NEWS:1', '今天的新闻', published_at=time.time()), Document('POST:2', '球迷讨论', published_at=time.time())]
    index.recall.return_value = [{'_id': doc.item_key, '_source': doc.as_index()} for doc in docs]
    service = Serving(db, Cache(), index, embedder, profiles, Settings())
    result = service.recommendations({'userId': 1, 'scene': 'HOME', 'limit': 6, 'personalized': False,
                                      'followed': ['TEAM:2'], 'blocked': ['NEWS:1']})
    assert [row['itemKey'] for row in result['items']] == ['POST:2']
    assert result['items'][0]['features']['entity_interest'] == 0
    profiles.get.assert_not_called()
    service.pool.shutdown()


def test_suggestions_are_a_single_page_without_redis_or_recall_worker_contention():
    from concurrent.futures import ThreadPoolExecutor
    import threading
    cache=Mock(); cache.setex.side_effect=AssertionError('Autocomplete must not allocate an unused cursor')
    index=Mock(); index.lexical.return_value=[{'_id':f'NEWS:{i}'} for i in range(1,13)]
    service=Serving(Mock(),cache,index,Mock(),Mock(),Settings())
    service.pool.shutdown()
    service.pool=ThreadPoolExecutor(max_workers=1)
    occupied=threading.Event(); release=threading.Event()
    def block(): occupied.set(); release.wait(2)
    service.pool.submit(block)
    try:
        assert occupied.wait(.5)
        with ThreadPoolExecutor(max_workers=1) as requests:
            response=requests.submit(service.search,{'userId':1,'scene':'SEARCH','q':'库里','limit':8},True).result(.5)
        assert len(response['items'])==8 and response['nextCursor'] is None and not response['degraded']
        cache.setex.assert_not_called()
        service.embedder.encode.assert_not_called()
    finally:
        release.set(); service.pool.shutdown()


def test_home_ranks_only_the_visible_page_and_does_not_allocate_a_cursor():
    cache=Mock(); cache.setex.side_effect=AssertionError('Home has no load-more control')
    db,index=Mock(),Mock(); db.control.return_value={}; index.documents.return_value=[]
    docs=[Document(f'{"NEWS" if n%2 else "POST"}:{n}','内容',published_at=time.time()) for n in range(1,41)]
    index.recall.return_value=[{'_id':doc.item_key,'_source':doc.as_index()} for doc in docs]
    service=Serving(db,cache,index,Mock(),Mock(),Settings())
    try:
        response=service.recommendations({'userId':1,'scene':'HOME','limit':6,'personalized':False})
        assert len(response['items'])==6 and response['nextCursor'] is None
        assert {row['itemKey'].split(':')[0] for row in response['items']}=={'NEWS','POST'}
        cache.setex.assert_not_called()
    finally: service.pool.shutdown()
