"""Minimal public-route regression for nested JPA/JDBC connection starvation."""
from concurrent.futures import ThreadPoolExecutor
import json
import time
from collections import Counter
import httpx
from discovery_stack import JAVA, RUNTIME, settings
from discovery_app.database import Database

people=json.loads((RUNTIME/'test-users.json').read_text()); db=Database(settings())
with httpx.Client(base_url=JAVA,headers={'Authorization':'Bearer '+people[1]['token']},
                  limits=httpx.Limits(max_connections=50,max_keepalive_connections=50),timeout=15) as client:
    def request(number):
        start=time.perf_counter(); response=client.get('/api/discovery/suggest',params={'q':'库里','limit':8})
        return response.status_code,round((time.perf_counter()-start)*1000,1)
    with ThreadPoolExecutor(max_workers=50) as pool:
        tasks=[pool.submit(request,number) for number in range(100)]
        time.sleep(1)
        commands=Counter(row['Command'] for row in db.rows('SHOW PROCESSLIST') if row['db']=='nba_discovery_test')
        print('MySQL connection states during requests:',dict(commands),flush=True)
        rows=[task.result() for task in tasks]
    report={'statuses':dict(Counter(row[0] for row in rows)),'p95Ms':sorted(row[1] for row in rows)[94]}
    print(json.dumps(report),flush=True)
    assert all(status==200 for status,_ in rows),report
    assert report['p95Ms']<3000,report
