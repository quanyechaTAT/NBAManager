"""Isolated-stack operational acceptance; synthetic models never contain user behavior."""
import json
import os
import time
import uuid
from datetime import datetime, timezone

import httpx
import pytest
import redis

from discovery_app.database import Database
from discovery_app.domain import FEATURE_NAMES, FEATURE_VERSION
from discovery_app.index import Index
from discovery_app.learning import train
from discovery_app.samples import TrainingGate
from discovery_stack import settings, eventually, RUNTIME, JAVA

pytestmark=pytest.mark.skipif(os.getenv('DISCOVERY_STACK_TEST')!='1',reason='Requires isolated compose stack')


@pytest.fixture(scope='module')
def stack():
    s=settings(); people=json.loads((RUNTIME/'test-users.json').read_text())
    clients=[httpx.Client(base_url=JAVA,headers={'Authorization':'Bearer '+p['token']},timeout=20) for p in people]
    yield s,Database(s),Index(s),clients
    for client in clients: client.close()


def job(stack,kind,**detail):
    s,db,index,clients=stack
    response=clients[2].post('/api/admin/discovery/jobs',json={'kind':kind,**detail}); response.raise_for_status()
    identifier=response.json()['id']
    row=eventually(lambda: next((row for row in db.rows('SELECT * FROM discovery_jobs WHERE id=%s',(identifier,))
                    if row['status'] not in ('QUEUED','RUNNING')),None),180)
    assert row['status']=='SUCCEEDED',row
    return json.loads(row['detail_json'])


def test_rebuild_then_rollback_preserves_concurrent_updates(stack):
    s,db,index,clients=stack
    previous=list(index.admin.indices.get_alias(name=index.alias))[0]
    response=clients[2].post('/api/admin/discovery/jobs',json={'kind':'REINDEX'}); response.raise_for_status()
    identifier=response.json()['id']
    row=db.rows('SELECT id,title FROM game_news ORDER BY id LIMIT 1')[0]
    key=f"NEWS:{row['id']}"; marker='并发重建 '+uuid.uuid4().hex[:8]
    try:
        db.execute('UPDATE game_news SET title=%s WHERE id=%s',(marker,row['id']))
        done=eventually(lambda: next((r for r in db.rows('SELECT * FROM discovery_jobs WHERE id=%s',(identifier,))
                       if r['status'] not in ('QUEUED','RUNNING')),None),180)
        assert done['status']=='SUCCEEDED',done
        current=list(index.admin.indices.get_alias(name=index.alias))[0]
        assert current!=previous
        eventually(lambda: index.get(key)['title']==marker)
        job(stack,'ROLLBACK_INDEX')
        assert list(index.admin.indices.get_alias(name=index.alias))==[previous]
        eventually(lambda: index.get(key)['title']==marker)
    finally:
        db.execute('UPDATE game_news SET title=%s WHERE id=%s',(row['title'],row['id']))


def test_cleared_history_rejects_delayed_client_events_and_home_is_mixed(stack):
    s,db,index,clients=stack; client=clients[0]
    response=client.get('/api/discovery/recommendations',params={'scene':'HOME'}).json()
    assert len(response['items'])==6
    assert {item['type'] for item in response['items']}=={'NEWS','POST'}
    client.delete('/api/discovery/preferences/history').raise_for_status()
    event={'eventId':str(uuid.uuid4()),'requestId':response['requestId'],'itemKey':response['items'][0]['itemKey'],
           'eventType':'CLICK','position':0,'dwellMs':0,'occurredAt':datetime.now(timezone.utc).isoformat()}
    assert client.post('/api/discovery/events',json={'events':[event]}).status_code==410


def test_real_coengagement_builds_itemcf_without_fabricated_impressions(stack):
    s,db,index,clients=stack
    news=clients[1].get('/api/discovery/search',params={'q':'库里','types':'NEWS','mode':'lexical'}).json()['items'][:2]
    assert len(news)==2
    for item in news:
        result=clients[1].post('/api/favorites',json={'targetType':'NEWS','targetId':item['id']}); result.raise_for_status()
        if not result.json()['favorited']:
            clients[1].post('/api/favorites',json={'targetType':'NEWS','targetId':item['id']}).raise_for_status()
    result=job(stack,'ITEMCF')
    assert result['items']>=2
    cache=redis.Redis.from_url(s.redis_url,decode_responses=True)
    neighbors=json.loads(cache.get(f"discovery:itemcf:{result['generation']}:{news[0]['itemKey']}"))
    assert news[1]['itemKey'] in {pair[0] for pair in neighbors}


def test_actual_synthetic_candidate_shadow_rollout_and_rollback(stack):
    s,db,index,clients=stack
    before=db.control('model',{})
    samples=[]; now=time.time()
    for group in range(60):
        for position in range(10):
            relevant=position%3==0
            values={name:0.0 for name in FEATURE_NAMES}
            values.update(quality=float(relevant),popularity=float(not relevant),freshness=float(not relevant))
            samples.append({'requestId':f'synthetic-{group}','itemKey':f'NEWS:{position+1}','userId':group+1,
                'servedAt':now-(60-group)*86400,'scene':'NEWS_FEED','position':position,
                'featureVersion':FEATURE_VERSION,'label':3 if relevant else 0,**values})
    result=train(samples,s.runtime/'models',gate=TrainingGate(days=1,impressions=100,positives=10,users=10))
    assert result['status']=='CANDIDATE'
    db.execute('INSERT INTO discovery_models(version,status,feature_version,artifact_path,artifact_sha256,metrics_json,trained_until,created_at) '
               "VALUES(%s,'CANDIDATE',%s,%s,%s,%s,UTC_TIMESTAMP(),UTC_TIMESTAMP())",
               (result['version'],FEATURE_VERSION,'models/'+result['version'],result['sha256'],json.dumps(result['metrics'])))
    try:
        job(stack,'PUBLISH',version=result['version'],traffic=0)
        def shadow_ran():
            clients[1].get('/api/discovery/recommendations',params={'scene':'NEWS_FEED'}).raise_for_status()
            value=db.control('shadow',{})
            return value.get('version')==result['version'] and value.get('successful')
        eventually(shadow_ran,30)
        for percentage in (5,20,50):
            assert job(stack,'PUBLISH',version=result['version'],traffic=percentage)['traffic']==percentage
        job(stack,'ROLLBACK_MODEL')
        assert db.control('model',{})==before
    finally:
        db.set_control('model',before)
        db.execute("UPDATE discovery_models SET status='TEST_ONLY' WHERE version=%s",(result['version'],))
