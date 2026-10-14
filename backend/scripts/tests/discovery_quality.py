"""Fixed small-fixture relevance and real CDC/feature-delay measurements; not a production relevance study."""
import json
import time
from datetime import datetime
import httpx
from discovery_stack import settings, RUNTIME, JAVA, eventually
from discovery_app.database import Database
from discovery_app.index import Index

s=settings(); db=Database(s); index=Index(s)
people=json.loads((RUNTIME/'test-users.json').read_text())
players={row['name_en']:f"PLAYER:{row['id']}" for row in db.rows('SELECT id,name_en FROM players')}
teams={row['abbreviation']:f"TEAM:{row['id']}" for row in db.rows('SELECT id,abbreviation FROM teams')}
golden=[(query,players[name]) for name,queries in {
    'Stephen Curry':['库里','Stephen Curry','Curry','萌神'],
    'LeBron James':['詹姆斯','LeBron James','LBJ','老詹'],
    'Jayson Tatum':['塔图姆','Jayson Tatum']}.items() for query in queries]
golden += [(query,teams[name]) for name,queries in {'LAL':['湖人','Lakers','LAL'],
    'GSW':['勇士','Golden State Warriors','GSW','金州勇士'],'BOS':['凯尔特人','Celtics']}.items() for query in queries]
report={'at':datetime.now().astimezone().isoformat(),'corpus':'Isolated synthetic fixtures; 19 fixed entity/name/alias queries', 'modes':{}}
with httpx.Client(base_url=JAVA,headers={'Authorization':'Bearer '+people[1]['token']},timeout=10) as client:
    # Readiness is separate from quality: BM25 fallback cannot prove hybrid relevance.
    def hybrid_ready():
        response=client.get('/api/discovery/search',params={'q':'库里','types':'PLAYER','mode':'hybrid','limit':10})
        response.raise_for_status(); data=response.json()
        return data['strategyVersion']=='hybrid-rrf-v1' and not data['degraded']
    eventually(hybrid_ready,120)
    for mode in ('lexical','hybrid'):
        rows=[]
        for query,key in golden:
            response=client.get('/api/discovery/search',params={'q':query,'types':key.split(':')[0],'mode':mode,'limit':10})
            response.raise_for_status(); data=response.json(); keys=[item['itemKey'] for item in data['items']]
            rows.append({'query':query,'expected':key,'rank':keys.index(key)+1 if key in keys else None,
                         'strategy':data['strategyVersion'],'degraded':data['degraded']})
        report['modes'][mode]={'queries':rows,'mrrAt10':round(sum(1/row['rank'] if row['rank'] else 0 for row in rows)/len(rows),4),
                              'recallAt10':sum(row['rank'] is not None for row in rows)/len(rows),
                              'degraded':sum(row['degraded'] for row in rows)}
    news=db.rows('SELECT id,title FROM game_news ORDER BY id LIMIT 1')[0]; key=f"NEWS:{news['id']}"
    text_delays=[]
    try:
        for number in range(20):
            marker='latency'+str(time.time_ns()); start=time.perf_counter()
            db.execute('UPDATE game_news SET title=%s WHERE id=%s',(marker,news['id']))
            eventually(lambda:any(hit['_id']==key for hit in index.lexical({'q':marker,'types':['NEWS']})),30)
            text_delays.append(round((time.perf_counter()-start)*1000,1))
    finally: db.execute('UPDATE game_news SET title=%s WHERE id=%s',(news['title'],news['id']))
    delays=[]
    for number in range(10):
        start=time.perf_counter()
        response=client.post('/api/favorites',json={'targetType':'NEWS','targetId':news['id']}); response.raise_for_status()
        event=db.rows('SELECT event_id FROM discovery_events WHERE user_id=%s AND item_key=%s AND event_type IN (%s,%s) ORDER BY received_at DESC LIMIT 1',
                      (people[1]['id'],key,'FAVORITE','UNFAVORITE'))[0]['event_id']
        eventually(lambda:db.rows('SELECT event_id FROM discovery_processed_events WHERE event_id=%s',(event,)),30)
        delays.append(round((time.perf_counter()-start)*1000,1))
    report['textLag']={'observations':20,'p95Ms':sorted(text_delays)[18],'valuesMs':text_delays}
    report['featureLag']={'observations':10,'p95Ms':max(delays),'valuesMs':delays}
    (RUNTIME/'quality.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({mode:{key:value for key,value in result.items() if key!='queries'} for mode,result in report['modes'].items()}))
    print(json.dumps({key:{k:v for k,v in report[key].items() if k!='valuesMs'} for key in ('textLag','featureLag')}))
    assert all(result['recallAt10']==1 for result in report['modes'].values()),'Fixed entity recall regression'
    assert all(not result['degraded'] for result in report['modes'].values()),'Quality probe unexpectedly used a degraded path'
    assert all(row['strategy']=='hybrid-rrf-v1' for row in report['modes']['hybrid']['queries']),'Hybrid relevance requires semantic retrieval'
    assert max(text_delays+delays)<30000,'CDC/feature arrival exceeded 30 seconds'
