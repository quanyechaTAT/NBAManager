"""Reproducible public-API benchmark in the isolated test DB only.

100k synthetic documents, copied fixture vectors, real SQL hydration and request persistence.
No generated impressions or production training labels. Session-local binlog suppression prevents
these temporary load fixtures from entering CDC. A unique index and reserved IDs are restored in finally.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from datetime import datetime, timedelta
import json
import math
import platform
import subprocess
import time

import httpx
from opensearchpy.helpers import bulk
from discovery_stack import settings, RUNTIME, JAVA
from discovery_app.database import Database
from discovery_app.domain import Document
from discovery_app.index import Index

START=10_000_000


def percentile(values, fraction): return sorted(values)[max(0,math.ceil(len(values)*fraction)-1)]


def run(count, concurrency, requests, load_client='httpx', cases=None, report_name='benchmark.json'):
    assert report_name.endswith('.json') and '/' not in report_name and '\\' not in report_name
    cases=cases or ('suggest','lexical','hybrid','recommendations')
    s=settings(); db=Database(s); index=Index(s)
    original=list(index.admin.indices.get_alias(name=index.alias))
    name='nba-discovery-benchmark-'+str(time.time_ns())
    original_query={'term':{'deleted':False}}
    original_count=index.admin.count(index=index.alias,body={'query':original_query})['count']
    assert 0 < original_count < min(count,1000),'Requested corpus must fit the original isolated fixtures'
    original_docs=index.admin.search(index=index.alias,body={'size':1000,'query':original_query})['hits']['hits']
    assert len(original_docs)==original_count
    replica_count=count-original_count
    templates=index.admin.search(index=index.alias,body={'size':150,'query':{'bool':{'filter':[
        {'terms':{'kind':['NEWS','POST']}},{'exists':{'field':'embedding'}},{'term':{'deleted':False}}]}}})['hits']['hits']
    assert len(templates)>10,'Wait for real fixture embeddings before measuring hybrid retrieval'
    people=json.loads((RUNTIME/'test-users.json').read_text())
    report={'generatedAt':datetime.now().isoformat(),'documents':count,'concurrency':concurrency,'requestsPerCase':requests,
            'accountCount':1,'warmupRequestsPerCase':16,
            'boundary':'Public Java API including authentication, hydration, request persistence, Python and OpenSearch',
            'data':'Synthetic copies of fixture text and vectors; no claim about real-world semantic relevance',
            'python':platform.python_version(),'vectorDimensions':s.embedding_dim,'vectorTemplates':len(templates), 'cases':{}}
    report.update(originalDocumentsRetained=original_count,syntheticCopies=replica_count,
                  corpusNote='Original fixture IDs remain searchable so profile seeds and ItemCF references still resolve.')
    report['loadGenerator']='Node native HTTP' if load_client in ('node','both') else 'Python httpx sync threads'
    if load_client=='both': report['comparison']={'loadGenerator':'Python httpx sync threads','cases':{}}
    alias_changed=False
    for table in ('game_news','posts'):
        assert not db.rows(f'SELECT id FROM {table} WHERE id>=%s AND id<%s LIMIT 1',(START,START+count)), 'Reserved IDs already occupied'
    index.create(name)
    index.admin.indices.put_settings(index=name,body={'index':{'refresh_interval':'-1'}})
    try:
        bulk(index.admin,[{'_index':name,'_id':hit['_id'],'_source':hit['_source']} for hit in original_docs],
             chunk_size=200,request_timeout=120)
        # Dedicated connection: this flag never escapes to the application's pooled connections.
        with db.connection() as connection,connection.cursor() as cursor:
            cursor.execute('SET SESSION sql_log_bin=0')
            try:
                now=datetime.now(); prepared=[]; news=[]; posts=[]
                for number in range(replica_count):
                    kind='NEWS' if number%2==0 else 'POST'; identifier=START+number
                    source=dict(templates[number%len(templates)]['_source'])
                    source['item_key']=f'{kind}:{identifier}'; source['kind']=kind; source['revision']=1
                    source['title']='压测样本 '+source['title']; source['views']=number%1000; source['likes']=number%50
                    source['favorites']=number%20; source['author_id']=str(people[number%len(people)]['id']) if kind=='POST' else ''
                    published=now-timedelta(minutes=number%40000); source['published_at']=int(published.timestamp()*1000)
                    doc=Document.from_index(source); source['content_hash']=doc.content_hash(); source['embedding_hash']=source['content_hash']
                    prepared.append({'_index':name,'_id':source['item_key'],'_source':source})
                    if kind=='NEWS':
                        news.append((identifier,source['title'],source['title_en'],'独立压测合成数据',source['text'],now,published,now,now))
                    else:
                        posts.append((identifier,people[number%len(people)]['id'],source['title'],source['text'],published,now))
                    if len(prepared)>=200 or number==replica_count-1:
                        if news:
                            cursor.executemany("INSERT INTO game_news(id,title,title_en,summary,content,home_team,away_team,create_time,published_at,published_at_source,game_start_time,game_end_time,status,category,view_count,favorite_count) "
                                               "VALUES(%s,%s,%s,%s,%s,'','',%s,%s,'INGESTED',%s,%s,'FINISHED','general',0,0)",news)
                        if posts:
                            cursor.executemany("INSERT INTO posts(id,user_id,title,content,category,tags,view_count,like_count,comment_count,favorite_count,is_top,is_featured,has_poll,create_time,update_time) "
                                               "VALUES(%s,%s,%s,%s,'DISCUSSION','压测',0,0,0,0,FALSE,FALSE,FALSE,%s,%s)",posts)
                        connection.commit(); bulk(index.admin,prepared,chunk_size=200,request_timeout=120)
                        prepared=[]; news=[]; posts=[]
                    if (number+1)%5000==0 or number==replica_count-1:
                        print(f'Prepared {number+1+original_count}/{count} documents',flush=True)
            finally: cursor.execute('SET SESSION sql_log_bin=1')
        index.admin.indices.put_settings(index=name,body={'index':{'refresh_interval':'1s'}})
        index.admin.indices.refresh(index=name)
        assert index.admin.count(index=name)['count']==count
        index.admin.indices.update_aliases(body={'actions':[{'remove':{'index':old,'alias':index.alias}} for old in original]+
                                                [{'add':{'index':name,'alias':index.alias}}]})
        alias_changed=True
        # Allow both candidate caches to expire after changing the corpus behind the alias.
        time.sleep(s.query_cache_seconds+1.1)
        queries=['库里','勇士','湖人','詹姆斯','凯尔特人','防守','进攻','三分']
        report['querySet']=queries
        with httpx.Client(base_url=JAVA,headers={'Authorization':'Bearer '+people[1]['token']},
                          limits=httpx.Limits(max_connections=concurrency,max_keepalive_connections=concurrency),timeout=10) as client:
            def call(case,number):
                route='/api/discovery/suggest' if case=='suggest' else '/api/discovery/recommendations' if case=='recommendations' else '/api/discovery/search'
                params={'scene':'HOME'} if case=='recommendations' else {'q':queries[number%len(queries)],'mode':'hybrid' if case=='hybrid' else 'lexical'}
                start=time.perf_counter()
                try:
                    response=client.get(route,params=params); data=response.json()
                    return {'ms':(time.perf_counter()-start)*1000,'status':response.status_code,
                            'degraded':data.get('degraded',True),'strategy':data.get('strategyVersion','error'),'count':len(data.get('items',[]))}
                except Exception as error:
                    return {'ms':(time.perf_counter()-start)*1000,'status':0,'degraded':True,'strategy':type(error).__name__,'count':0}
            for case in cases:
                for generator in (('node','httpx') if load_client=='both' else (load_client,)):
                    if generator=='node':
                        result=subprocess.run(['node','tests/discovery_load.cjs','--runtime',str(RUNTIME),'--case',case,
                            '--requests',str(requests),'--concurrency',str(concurrency)],capture_output=True,text=True,encoding='utf-8',check=True)
                        payload=json.loads(result.stdout); metrics=payload['metrics']; report['nodeRuntime']=payload['client']
                    else:
                        for number in range(16): call(case,number)
                        started=time.perf_counter(); cpu=time.process_time()
                        with ThreadPoolExecutor(max_workers=concurrency) as pool: rows=list(pool.map(lambda i:call(case,i),range(requests)))
                        duration=time.perf_counter()-started; cpu=time.process_time()-cpu; latencies=[row['ms'] for row in rows]
                        metrics={'p50Ms':round(percentile(latencies,.5),1),'p95Ms':round(percentile(latencies,.95),1),
                                 'p99Ms':round(percentile(latencies,.99),1),'requestsPerSecond':round(requests/duration,1),
                                 'clientCpuMs':round(cpu*1000,1),
                                 'errors':sum(row['status']!=200 for row in rows),'empty':sum(row['count']==0 for row in rows),
                                 'degraded':sum(row['degraded'] for row in rows),
                                 'statusCounts':{str(value):sum(row['status']==value for row in rows) for value in {row['status'] for row in rows}},
                                 'strategies':{value:sum(row['strategy']==value for row in rows) for value in {row['strategy'] for row in rows}}}
                    target=report['comparison']['cases'] if load_client=='both' and generator=='httpx' else report['cases']
                    target[case]=metrics; print(json.dumps({'client':generator,case:metrics}),flush=True)
                    (RUNTIME/report_name).write_text(json.dumps(report,indent=2),encoding='utf-8')
    finally:
        if alias_changed:
            index.admin.indices.update_aliases(body={'actions':[{'remove':{'index':name,'alias':index.alias}}]+
                [{'add':{'index':old,'alias':index.alias}} for old in original]})
        with db.connection() as connection,connection.cursor() as cursor:
            cursor.execute('SET SESSION sql_log_bin=0')
            try:
                for table in ('game_news','posts'):
                    cursor.execute(f'DELETE FROM {table} WHERE id>=%s AND id<%s AND title LIKE %s',(START,START+count,'压测样本 %'))
                connection.commit()
            finally: cursor.execute('SET SESSION sql_log_bin=1')
        index.admin.indices.delete(index=name)
        print('Benchmark alias and fixture rows restored.',flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--count',type=int,default=100000)
    parser.add_argument('--concurrency',type=int,default=50); parser.add_argument('--requests',type=int,default=500)
    parser.add_argument('--client',choices=['httpx','node','both'],default='httpx')
    parser.add_argument('--case',dest='cases',action='append',choices=['suggest','lexical','hybrid','recommendations'])
    parser.add_argument('--report-name',default='benchmark.json')
    args=parser.parse_args()
    assert 100<=args.count<=100000 and 1<=args.concurrency<=50 and 10<=args.requests<=5000
    run(args.count,args.concurrency,args.requests,args.client,args.cases,args.report_name)
