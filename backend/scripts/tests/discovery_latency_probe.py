"""Fast latency regression against the existing isolated corpus; does not replace the 100k benchmark."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import json
import time
import subprocess

import httpx
from discovery_stack import JAVA, RUNTIME, settings


def run(operation, requests, concurrency, target, client_type):
    settings()  # Reject any environment other than the explicitly isolated test database.
    if client_type == 'node':
        arguments=['node','tests/discovery_load.cjs','--runtime',str(RUNTIME),'--case',operation,
                   '--requests',str(requests),'--concurrency',str(concurrency)]
        if operation=='suggest': arguments+=['--query','库里']
        data=json.loads(subprocess.run(arguments,capture_output=True,text=True,encoding='utf-8',check=True).stdout)
        report={**data,'targetMs':target}
        (RUNTIME/('latency-'+operation+'-native.json')).write_text(json.dumps(report,indent=2),encoding='utf-8')
        print(json.dumps(report),flush=True)
        metrics=data['metrics']
        assert not (metrics['errors'] or metrics['empty'] or metrics['degraded']),report
        assert metrics['p95Ms']<=target,report
        return
    person = json.loads((RUNTIME / 'test-users.json').read_text(encoding='utf-8'))[1]
    params = {'q': '库里', 'limit': 8, 'mode': 'lexical'} if operation == 'suggest' else {'scene': 'HOME'}
    with httpx.Client(base_url=JAVA, headers={'Authorization': 'Bearer ' + person['token']}, timeout=10,
                      limits=httpx.Limits(max_connections=concurrency, max_keepalive_connections=concurrency)) as client:
        def call(_):
            started = time.perf_counter()
            response = client.get('/api/discovery/' + operation, params=params)
            body = response.json()
            return {'ms': (time.perf_counter() - started) * 1000, 'status': response.status_code,
                    'degraded': bool(body.get('degraded')), 'count': len(body.get('items', [])),
                    'timing': response.headers.get('Server-Timing', '')}
        for number in range(16):
            call(number)
        started=time.perf_counter(); cpu=time.process_time()
        with ThreadPoolExecutor(max_workers=concurrency) as pool:
            rows = list(pool.map(call, range(requests)))
        elapsed=time.perf_counter()-started; cpu=time.process_time()-cpu
    latencies = sorted(row['ms'] for row in rows)
    report = {'operation': operation, 'requests': requests, 'concurrency': concurrency, 'client':'httpx sync threads',
              'requestsPerSecond':round(requests/elapsed,1),'clientCpuMs':round(cpu*1000,1),
              'p50Ms': round(latencies[int(requests * .5) - 1], 1),
              'p95Ms': round(latencies[int(requests * .95) - 1], 1),
              'targetMs': target, 'statuses': dict(Counter(row['status'] for row in rows)),
              'degraded': sum(row['degraded'] for row in rows),
              'empty': sum(row['count'] == 0 for row in rows)}
    (RUNTIME / ('latency-' + operation + '.json')).write_text(
        json.dumps({'summary': report, 'requests': rows}, indent=2), encoding='utf-8')
    print(json.dumps(report), flush=True)
    assert all(row['status'] == 200 and row['count'] and not row['degraded'] for row in rows), report
    assert report['p95Ms'] <= target, report


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--operation', choices=['suggest', 'recommendations'], default='suggest')
    parser.add_argument('--requests', type=int, default=500)
    parser.add_argument('--concurrency', type=int, default=50)
    parser.add_argument('--target-ms', type=float, default=150)
    parser.add_argument('--client',choices=['httpx','node'],default='httpx')
    args = parser.parse_args()
    assert 20 <= args.requests <= 2000 and 1 <= args.concurrency <= 50 and args.target_ms > 0
    run(args.operation, args.requests, args.concurrency, args.target_ms, args.client)
