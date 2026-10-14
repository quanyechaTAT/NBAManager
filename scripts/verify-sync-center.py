"""Verify the running local sync center. --full/--module explicitly refresh real data.

Credentials come from ADMIN_USER/ADMIN_PASS or .env and are never printed/saved.
Default is a read-only overview after login. Evidence contains task results only.
"""
import argparse
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
ACTIVE = {'QUEUED', 'RUNNING', 'CANCELLING'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--full', action='store_true')
    mode.add_argument('--module')
    mode.add_argument('--job', type=int)
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--timeout', type=int, default=900)
    args = parser.parse_args()
    load_dotenv(ROOT / '.env')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    token = None

    def request(path, body=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(args.base_url + '/api' + path,
                                     json.dumps(body).encode() if body is not None else None, headers)
        with opener.open(req, timeout=20) as response:
            return json.load(response)

    token = request('/auth/login', {'username': os.environ['ADMIN_USER'], 'password': os.environ['ADMIN_PASS']})['token']
    if not (args.full or args.module or args.job):
        data = request('/admin/overview')
        print(json.dumps({'season': data['currentSeason'], 'activeFullJob': data['activeFullJob'],
                          'modules': [{k: row[k] for k in ('key', 'count', 'activeJob', 'latestJob')}
                                      for row in data['modules']]}, ensure_ascii=True), flush=True)
        return

    started = time.monotonic()
    if args.job:
        job_id = args.job
    else:
        for attempt in range(30):
            try:
                job = request('/admin/sync/' + ('full' if args.full else args.module), {})
                job_id = job['jobId']
                break
            except urllib.error.HTTPError as error:
                if error.code != 409 or attempt == 29:
                    raise
                time.sleep(2)
        print('Submitted job ' + str(job_id), flush=True)

    previous = None
    while time.monotonic() - started < args.timeout:
        job = request('/admin/jobs/' + str(job_id))
        summary = {'jobId': job_id, 'status': job['status'], 'stage': job.get('stage'),
                   'progress': job['progress'], 'steps': [{k: s[k] for k in ('module', 'status', 'fetchedCount')}
                                                          for s in job.get('steps', [])]}
        if summary != previous:
            print(json.dumps(summary, ensure_ascii=True), flush=True)
            previous = summary
        if job['status'] not in ACTIVE:
            folder = ROOT / 'test-evidence/sync-center'
            folder.mkdir(parents=True, exist_ok=True)
            (folder / f'validated-job-{job_id}.json').write_text(json.dumps(job, ensure_ascii=False, indent=2), encoding='utf-8')
            print(json.dumps({'seconds': round(time.monotonic() - started, 1), 'result': job}, ensure_ascii=True), flush=True)
            assert job['status'] == 'SUCCESS', 'Sync did not fully succeed; inspect saved task evidence'
            if job['module'] == 'FULL_SYNC':
                assert len(job['steps']) == 9 and all(step['status'] == 'SUCCESS' for step in job['steps'])
            return
        time.sleep(3)
    raise TimeoutError(f'Job {job_id} is still active; it was not cancelled')


if __name__ == '__main__':
    main()
