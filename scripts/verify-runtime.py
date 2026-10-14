"""Read-only smoke checks for a running Java backend and its public hot cache."""
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
import gzip
import http.client
import json
import os
from pathlib import Path
import re
import socket
import time
from uuid import uuid4
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]


def request(path, headers=None):
    connection = http.client.HTTPConnection('127.0.0.1', 8080, timeout=15)
    started = time.perf_counter()
    try:
        connection.request('GET', path, headers=headers or {})
        response = connection.getresponse()
        return response.status, dict(response.getheaders()), response.read(), round((time.perf_counter() - started) * 1000, 2)
    finally: connection.close()


def redis_read(*command):
    """Minimal RESP client; only AUTH/SCAN/TTL/GET are used, never deleting keys."""
    load_dotenv(ROOT / '.env')
    def parse(file):
        line = file.readline(); kind, value = line[:1], line[1:-2]
        if kind == b'+': return value.decode()
        if kind == b'-': raise RuntimeError('Redis probe rejected')
        if kind == b':': return int(value)
        if kind == b'$':
            length = int(value)
            if length == -1: return None
            data = file.read(length); file.read(2); return data.decode()
        if kind == b'*': return [parse(file) for _ in range(int(value))]
        raise RuntimeError('Invalid Redis response')
    def send(stream, args):
        values = [str(arg).encode() for arg in args]
        stream.write(b'*%d\r\n' % len(values) + b''.join(b'$%d\r\n' % len(value) + value + b'\r\n' for value in values)); stream.flush()
        return parse(stream)
    with socket.create_connection((os.getenv('REDIS_HOST', '127.0.0.1'), int(os.getenv('REDIS_PORT', '6379'))), timeout=3) as sock:
        with sock.makefile('rwb') as stream:
            password = os.getenv('REDIS_PASSWORD')
            if password: send(stream, ['AUTH', password])
            return send(stream, command)


def main():
    status, headers, body, _ = request('/')
    assert status == 200 and headers['Cache-Control'] == 'no-cache'
    cached_status, _, cached_body, _ = request('/', {'If-None-Match': headers['ETag']})
    assert cached_status == 304 and not cached_body
    asset = re.search(r'(?:src|href)="(/assets/[^" ]+\.js)"', body.decode()).group(1)
    status, asset_headers, asset_body, _ = request(asset, {'Accept-Encoding': 'gzip'})
    assert status == 200 and 'immutable' in asset_headers['Cache-Control']
    if asset_headers.get('Content-Encoding') == 'gzip': gzip.decompress(asset_body)
    assert request('/assets/missing-AbCd1234.js')[0] == 404
    assert request('/api/match-records/game/9999999999999999')[0] == 404
    protected_status = request('/api/definitely-missing')[0]
    assert protected_status in (401, 403)
    keyword = 'performanceprobe' + uuid4().hex
    path = '/api/players?page=0&size=20&q=' + keyword
    with ThreadPoolExecutor(max_workers=20) as pool:
        results = list(pool.map(lambda _: request(path), range(20)))
    assert all(result[0] == 200 for result in results)
    assert len({result[2] for result in results}) == 1
    cursor = '0'; keys = []
    while True:
        cursor, found = redis_read('SCAN', cursor, 'MATCH', '*playerList*' + keyword + '*', 'COUNT', 256)
        keys.extend(found)
        if cursor == '0': break
    assert len(keys) == 1, keys
    ttl = redis_read('TTL', keys[0]); assert 0 < ttl <= 60, ttl
    matches = request('/api/match-records')
    assert matches[0] == 200
    match = next((row for row in json.loads(matches[2]) if row.get('nbaGameId') and row.get('status') == 'FINISHED' and row.get('matchDate')), None)
    match_ttl = None
    if match:
        detail = request('/api/match-records/game/' + match['nbaGameId'])
        assert detail[0] == 200
        cursor = '0'; match_keys = []
        while True:
            cursor, found = redis_read('SCAN', cursor, 'MATCH', '*matchRecord::*' + match['nbaGameId'], 'COUNT', 256)
            match_keys.extend(found)
            if cursor == '0': break
        assert len(match_keys) == 1, match_keys
        match_ttl = redis_read('TTL', match_keys[0])
        expected_ttl = 21600 if date.fromisoformat(match['matchDate'][:10]) < date.today() - timedelta(days=2) else 60
        assert 0 < match_ttl <= expected_ttl, (match_ttl, expected_ttl)
    report = {'java_html_304': cached_status, 'java_asset_cache_control': asset_headers['Cache-Control'],
        'missing_asset': 404, 'missing_public_api': 404, 'protected_api_without_auth': protected_status, 'concurrent_public_reads': 20,
        'responses_equal': True, 'errors': 0, 'elapsed_ms': [result[3] for result in results],
        'player_list_redis_ttl_seconds': ttl, 'finished_match_redis_ttl_seconds': match_ttl,
        'shared_match_generation_present': bool(redis_read('GET', 'nba:cache:match-generation:v1'))}
    assert report['shared_match_generation_present']
    folder = ROOT / 'test-evidence/performance'; folder.mkdir(parents=True, exist_ok=True)
    (folder / 'phase3-runtime.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
