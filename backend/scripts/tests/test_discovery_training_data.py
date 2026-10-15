"""Real SQL selection on explicitly synthetic, non-CDC training fixtures in the isolated DB."""
from datetime import datetime, timedelta, timezone
import json
import os
import uuid

import pytest

from discovery_app.domain import FEATURE_NAMES, FEATURE_VERSION
from discovery_app.worker import Worker
from discovery_stack import settings

pytestmark=pytest.mark.skipif(os.getenv('DISCOVERY_STACK_TEST')!='1',reason='Requires isolated compose stack')


def test_sql_loads_only_mature_exposed_recommendations_and_preserves_serving_features(monkeypatch):
    worker=Worker(settings()); db=worker.db
    user=2_000_000_000+uuid.uuid4().int%1_000_000_000
    users=[user,user+1,user+2]
    assert not db.rows('SELECT user_id FROM discovery_preferences WHERE user_id IN (%s,%s,%s)',tuple(users))
    before=db.control('training',{})
    now=datetime.now(timezone.utc).replace(tzinfo=None); mature=now-timedelta(days=2)
    cases=[('eligible',user,'NEWS_FEED',mature,True,1),('search',user,'SEARCH',mature,True,1),
           ('unfinished',user,'NEWS_FEED',now-timedelta(hours=1),True,1),
           ('unseen',user,'NEWS_FEED',mature,False,1),('late',user,'NEWS_FEED',mature,True,1500),
           ('disabled',user+1,'NEWS_FEED',mature,True,1),('cleared',user+2,'NEWS_FEED',mature,True,1)]
    identifiers={name:str(uuid.uuid4()) for name,*_ in cases}
    features={name:0.0 for name in FEATURE_NAMES}; features['quality']=.37
    loaded=[]; samples=[]; original=db.rows
    def record_rows(sql,args=()):
        rows=original(sql,args)
        loaded.extend(row['request_id'] for row in rows if 'items_json' in row)
        return rows
    def capture(rows,*args,**kwargs):
        samples.extend(rows); return {'status':'INSUFFICIENT_DATA'}
    monkeypatch.setattr(db,'rows',record_rows)
    monkeypatch.setattr('discovery_app.worker.train',capture)
    try:
        with db.connection() as connection,connection.cursor() as cursor:
            cursor.execute('SET SESSION sql_log_bin=0')
            try:
                for identifier,enabled,cleared in ((user,True,None),(user+1,False,None),(user+2,True,now)):
                    cursor.execute('INSERT INTO discovery_preferences(user_id,personalized,cleared_at,updated_at,revision) VALUES(%s,%s,%s,%s,0)',
                                   (identifier,enabled,cleared,now))
                for name,owner,scene,created,exposed,delay in cases:
                    request=identifiers[name]
                    cursor.execute('INSERT INTO discovery_requests(request_id,user_id,scene,strategy_version,feature_version,context_json,items_json,created_at) '
                        'VALUES(%s,%s,%s,%s,%s,%s,%s,%s)',(request,owner,scene,'test-only',FEATURE_VERSION,
                        json.dumps({'personalized':True}),json.dumps([{'itemKey':'NEWS:1','features':features}]),created))
                    for kind in (['IMPRESSION','CLICK'] if exposed else ['CLICK']):
                        occurred=created+timedelta(minutes=delay)
                        cursor.execute('INSERT INTO discovery_events(event_id,user_id,request_id,item_key,event_type,position,dwell_ms,occurred_at,received_at) '
                            'VALUES(%s,%s,%s,%s,%s,0,0,%s,%s)',(str(uuid.uuid4()),owner,request,'NEWS:1',kind,occurred,occurred))
                connection.commit()
            finally: cursor.execute('SET SESSION sql_log_bin=1')
        worker.training()
        fixture=set(identifiers.values())
        assert set(loaded)&fixture=={identifiers['eligible']}
        selected=[row for row in samples if row['requestId'] in fixture]
        assert len(selected)==1 and selected[0]['label']==1
        assert selected[0]['quality']==.37 and selected[0]['featureVersion']==FEATURE_VERSION
    finally:
        # Exact fixture IDs only; suppression prevents synthetic impressions from entering live profiles.
        with db.connection() as connection,connection.cursor() as cursor:
            cursor.execute('SET SESSION sql_log_bin=0')
            try:
                placeholders=','.join(['%s']*len(identifiers))
                for table in ('discovery_events','discovery_requests'):
                    cursor.execute(f'DELETE FROM {table} WHERE request_id IN ({placeholders})',tuple(identifiers.values()))
                cursor.execute('DELETE FROM discovery_preferences WHERE user_id IN (%s,%s,%s)',tuple(users))
                connection.commit()
            finally: cursor.execute('SET SESSION sql_log_bin=1')
        db.set_control('training',before)
