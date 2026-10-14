"""Read-only request probes during explicitly controlled isolated service outages."""
import argparse
import json
import time
import httpx
from discovery_stack import settings, RUNTIME, JAVA
from discovery_app.database import Database
from discovery_app.index import Index

parser=argparse.ArgumentParser(); parser.add_argument('mode',choices=['redis','opensearch','healthy','cdc-change','cdc-verify'])
args=parser.parse_args(); s=settings(); db=Database(s)
people=json.loads((RUNTIME/'test-users.json').read_text())
if args.mode=='cdc-change':
    row=db.rows('SELECT id,title FROM game_news ORDER BY id DESC LIMIT 1')[0]
    marker='CDC恢复验收 '+str(time.time_ns())
    (RUNTIME/'cdc-restart.json').write_text(json.dumps({**row,'marker':marker,'at':time.time()}),encoding='utf-8')
    db.execute('UPDATE game_news SET title=%s WHERE id=%s',(marker,row['id']))
    print('Committed isolated source edit while CDC transport was stopped.')
elif args.mode=='cdc-verify':
    from discovery_stack import eventually
    row=json.loads((RUNTIME/'cdc-restart.json').read_text(encoding='utf-8')); index=Index(s)
    eventually(lambda:index.get(f"NEWS:{row['id']}")['title']==row['marker'],60)
    db.execute('UPDATE game_news SET title=%s WHERE id=%s',(row['title'],row['id']))
    print('CDC resumed and delivered the committed edit; source title restored.')
else:
    results=[]
    with httpx.Client(base_url=JAVA,headers={'Authorization':'Bearer '+people[0]['token']},timeout=20) as client:
        for route,params in [('/api/discovery/search',{'q':'库里','types':'NEWS','mode':'lexical'}),
                             ('/api/discovery/recommendations',{'scene':'HOME'})]:
            start=time.perf_counter(); response=client.get(route,params=params); response.raise_for_status(); data=response.json()
            assert data['items'],data
            if args.mode=='opensearch': assert data['strategyVersion']=='database-fallback-v1' and data['degraded'],data
            if args.mode=='redis' and route.endswith('recommendations'): assert data['degraded'],data
            if args.mode=='healthy': assert not data['degraded'],data
            results.append({'path':route,'milliseconds':round((time.perf_counter()-start)*1000,1),
                            'strategy':data['strategyVersion'],'degraded':data['degraded'],'items':len(data['items'])})
    (RUNTIME/f'fault-{args.mode}.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    print(json.dumps(results))
