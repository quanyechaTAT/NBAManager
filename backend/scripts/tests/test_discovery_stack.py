"""Real MySQL/CDC/Kafka/OpenSearch/Redis/Java acceptance. Opt in with DISCOVERY_STACK_TEST=1."""
import json
import os
import time
import uuid
from datetime import datetime, timezone

import httpx
import pytest

from discovery_app.database import Database
from discovery_app.index import Index
from discovery_app.worker import Worker
from discovery_app.serving import ITEMCF_NEIGHBORS
from discovery_stack import settings, eventually, RUNTIME, JAVA

pytestmark=pytest.mark.skipif(os.getenv('DISCOVERY_STACK_TEST')!='1',reason='Requires isolated compose stack')


@pytest.fixture(scope='module')
def stack():
    s=settings(); db=Database(s); index=Index(s)
    people=json.loads((RUNTIME/'test-users.json').read_text())
    clients=[httpx.Client(base_url=JAVA,headers={'Authorization':'Bearer '+person['token']},timeout=20) for person in people]
    yield db,index,people,clients
    for client in clients: client.close()


def test_itemcf_neighbors_follow_one_active_generation_in_redis(stack):
    import redis
    cache=redis.Redis.from_url(settings().redis_url,decode_responses=True)
    generation='acceptance-'+uuid.uuid4().hex
    active='discovery:acceptance:itemcf:'+generation
    first=f'discovery:itemcf:{generation}:NEWS:1'
    second=f'discovery:itemcf:{generation}:NEWS:2'
    try:
        assert cache.eval(ITEMCF_NEIGHBORS,1,active,'NEWS:1')==[]
        cache.setex(active,60,generation)
        cache.setex(first,60,json.dumps([['POST:3',.5]]))
        cache.setex(second,60,json.dumps([['POST:4',.9]]))
        values=cache.eval(ITEMCF_NEIGHBORS,1,active,'NEWS:1','NEWS:2','NEWS:999')
        assert [json.loads(value) if value else None for value in values]==[[['POST:3',.5]],[['POST:4',.9]],None]
        cache.setex(active,60,generation+'-empty')
        assert cache.eval(ITEMCF_NEIGHBORS,1,active,'NEWS:1')==[None]
    finally:
        cache.delete(active,first,second); cache.close()


def test_migrations_and_all_five_types_with_bilingual_alias_filters_and_real_targets(stack):
    db,index,people,clients=stack; client=clients[0]
    assert db.rows("SELECT success FROM flyway_schema_history WHERE version='20261014.01'")[0]['success']
    for kind,query in [('PLAYER','库里'),('TEAM','Lakers'),('MATCH','湖人'),('NEWS','库里'),('POST','库里')]:
        response=client.get('/api/discovery/search',params={'q':query,'types':kind,'mode':'lexical'})
        assert response.status_code==200,response.text
        data=response.json()
        assert not data['degraded'],data
        assert data['items'] and all(item['type']==kind for item in data['items'])
        assert all('features' not in item and 'score' not in item for item in data['items'])
        if kind=='MATCH':
            target=data['items'][0]['target']
            assert 'recordId' in target['query'] and 'gameId' not in target['query']
            assert client.get('/api/match-records/id/'+target['query']['recordId']).status_code==200
    team=db.rows("SELECT id FROM teams WHERE abbreviation='GSW'")[0]['id']
    data=client.get('/api/discovery/search',params={'q':'','types':'PLAYER','team':f'TEAM:{team}','mode':'lexical'}).json()
    assert len(data['items'])==1 and '库里' in data['items'][0]['title']
    assert client.get('/api/discovery/suggest',params={'q':'库'}).json()['items']


def test_snapshot_cursor_identity_and_deletion_are_checked_again_before_response(stack):
    db,index,people,clients=stack; client,other=clients[:2]
    parameters={'q':'示例','types':'POST','mode':'lexical','limit':3}
    first=client.get('/api/discovery/search',params=parameters).json()
    assert first['nextCursor']
    next_params={**parameters,'cursor':first['nextCursor']}
    assert other.get('/api/discovery/search',params=next_params).status_code==410
    second=client.get('/api/discovery/search',params=next_params).json()
    assert not {item['itemKey'] for item in first['items']} & {item['itemKey'] for item in second['items']}
    # A delete in the source must be filtered even before the CDC consumer catches up.
    deleted=second['items'][0]
    db.execute('DELETE FROM posts WHERE id=%s',(deleted['id'],))
    again=client.get('/api/discovery/search',params=next_params).json()
    assert deleted['itemKey'] not in {item['itemKey'] for item in again['items']}
    eventually(lambda: index.get(deleted['itemKey']).get('deleted'))


def test_real_request_attribution_idempotency_trusted_favorites_and_profile_updates(stack):
    db,index,people,clients=stack; client,other=clients[:2]
    response=client.get('/api/discovery/recommendations',params={'scene':'NEWS_FEED'}).json()
    assert response['strategyVersion']=='baseline-v1' and response['items']
    item=response['items'][0]
    context={'requestId':response['requestId'],'itemKey':item['itemKey'],'position':0,'dwellMs':0,
             'occurredAt':datetime.now(timezone.utc).isoformat()}
    event={**context,'eventId':str(uuid.uuid4()),'eventType':'IMPRESSION'}
    assert other.post('/api/discovery/events',json={'events':[event]}).status_code==404
    assert client.post('/api/discovery/events',json={'events':[event]}).json()['accepted']==1
    assert client.post('/api/discovery/events',json={'events':[event]}).json()['duplicates']==1
    assert client.post('/api/discovery/events',json={'events':[{**event,'eventId':str(uuid.uuid4()),'eventType':'FAVORITE'}]}).status_code==400
    click={**context,'eventId':str(uuid.uuid4()),'eventType':'CLICK'}
    assert client.post('/api/discovery/events',json={'events':[click]}).json()['accepted']==1
    action=client.post('/api/favorites',json={'targetType':'NEWS','targetId':item['id']})
    assert action.status_code==200
    kind='FAVORITE' if action.json()['favorited'] else 'UNFAVORITE'
    events=db.rows('SELECT * FROM discovery_events WHERE user_id=%s AND request_id=%s AND event_type=%s',
                   (people[0]['id'],response['requestId'],kind))
    assert len(events)==1
    eventually(lambda: db.rows('SELECT * FROM discovery_processed_events WHERE event_id=%s',(events[0]['event_id'],)))
    snapshot=db.rows('SELECT items_json FROM discovery_requests WHERE request_id=%s',(response['requestId'],))[0]
    assert len(json.loads(snapshot['items_json'])[0]['features'])==13


def test_dismiss_undo_opt_out_clear_and_cursor_invalidation(stack):
    db,index,people,clients=stack; client=clients[0]
    initial=client.get('/api/discovery/recommendations',params={'scene':'NEWS_FEED'}).json()
    key=initial['items'][0]['itemKey']
    assert client.post('/api/discovery/feedback',json={'itemKey':key,'active':True}).status_code==200
    assert key not in {row['itemKey'] for row in client.get('/api/discovery/recommendations',params={'scene':'NEWS_FEED'}).json()['items']}
    client.post('/api/discovery/feedback',json={'itemKey':key,'active':False}).raise_for_status()
    prefs=client.put('/api/discovery/preferences',json={'personalized':False}).json()
    assert not prefs['personalized']
    if initial['nextCursor']:
        assert client.get('/api/discovery/recommendations',params={'scene':'NEWS_FEED','cursor':initial['nextCursor']}).status_code==410
    disabled=client.get('/api/discovery/recommendations',params={'scene':'HOME'}).json()
    rows=json.loads(db.rows('SELECT items_json FROM discovery_requests WHERE request_id=%s',(disabled['requestId'],))[0]['items_json'])
    assert all(row['features']['entity_interest']==0 and row['features']['followed_entity']==0 for row in rows)
    cleared=client.delete('/api/discovery/preferences/history').json()
    assert not cleared['personalized'] and cleared['clearedAt']
    client.put('/api/discovery/preferences',json={'personalized':True}).raise_for_status()


def test_direct_python_translation_delete_duplicate_and_stale_vector_guards(stack):
    db,index,people,clients=stack
    row=db.rows('SELECT id,title FROM game_news ORDER BY id DESC LIMIT 1')[0]
    key=f"NEWS:{row['id']}"; marker='CDC translation '+uuid.uuid4().hex[:8]
    before=index.get(key)
    db.execute('UPDATE game_news SET title=%s,title_en=%s WHERE id=%s',(marker,marker,row['id']))
    eventually(lambda: index.get(key).get('title')==marker)
    # An encoder finishing the old text must not overwrite the new revision's representation.
    index.vector(key,before['content_hash'],[.1]*settings().embedding_dim)
    after=index.get(key)
    assert after.get('embedding_hash')!=before['content_hash']
    worker=Worker(settings())
    stale_envelope={'source':{'table':'game_news'},'after':{'id':row['id'],'title':'OLD TITLE'}}
    worker.process(stale_envelope); worker.process(stale_envelope)
    assert index.get(key)['title']==marker
    db.execute('UPDATE game_news SET title=%s WHERE id=%s',(row['title'],row['id']))


def test_training_gate_and_admin_operations_are_real_jobs(stack):
    db,index,people,clients=stack
    assert clients[0].get('/api/admin/discovery/status').status_code==403
    response=clients[2].post('/api/admin/discovery/jobs',json={'kind':'TRAIN'})
    response.raise_for_status(); identifier=response.json()['id']
    row=eventually(lambda: next((row for row in db.rows('SELECT * FROM discovery_jobs WHERE id=%s',(identifier,)) if row['status'] not in ('QUEUED','RUNNING')),None),60)
    assert row['status']=='SUCCEEDED',row
    assert json.loads(row['detail_json'])['status']=='INSUFFICIENT_DATA'
