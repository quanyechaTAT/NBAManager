"""Verify deploy/nginx.conf with a private loopback fixture, never calling an AI model.

Usage: python scripts/verify-proxy.py --nginx /path/to/nginx
Requires frontend/dist. Writes measurements into ignored test-evidence/performance.
"""
import argparse
import gzip
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import http.client
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parents[1]


class Fixture(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_GET(self):
        if self.path.startswith('/api/rag/stream'):
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache, no-transform')
            self.end_headers()
            for event in ['status', 'token', 'done']:
                self.wfile.write(f'event: {event}\ndata: {{"fixture":true}}\n\n'.encode())
                self.wfile.flush()
                if event != 'done': time.sleep(.3)
            return
        if self.path == '/api/private' and self.headers.get('Authorization') == 'Bearer proxy-fixture':
            data = json.dumps({'private': True, 'payload': 'x' * 8192}).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Cache-Control', 'no-store')
        else:
            data = b'{}'
            self.send_response(403 if self.path == '/api/private' else 404)
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers(); self.wfile.write(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--nginx', required=True)
    args = parser.parse_args()
    binary = Path(args.nginx).resolve(strict=True)
    dist = ROOT / 'frontend/dist'
    assert (dist / 'index.html').is_file(), 'Run the frontend production build first'
    evidence = ROOT / 'test-evidence/performance'
    evidence.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0)); port = probe.getsockname()[1]
    with tempfile.TemporaryDirectory(prefix='proxy-probe-', dir=evidence) as directory:
        folder = Path(directory).resolve()
        # Explicit containment check before TemporaryDirectory recursively removes the probe.
        folder.relative_to(evidence.resolve())
        (folder / 'logs').mkdir()
        template = (ROOT / 'deploy/nginx.conf').read_text(encoding='utf-8')
        config = template.replace('127.0.0.1:8080', f'127.0.0.1:{server.server_port}')
        config = config.replace('127.0.0.1:18080', f'127.0.0.1:{port}')
        config = config.replace('root ../frontend/dist;', f'root "{dist.as_posix()}";')
        (folder / 'nginx.conf').write_text(config, encoding='utf-8')
        command = [str(binary), '-p', folder.as_posix() + '/', '-c', 'nginx.conf']
        hidden = {'creationflags': subprocess.CREATE_NO_WINDOW} if os.name == 'nt' else {}
        checked = subprocess.run(command + ['-t'], capture_output=True, text=True, **hidden)
        assert checked.returncode == 0, checked.stderr
        process = subprocess.Popen(command + ['-g', 'daemon off;'], stdout=subprocess.DEVNULL,
                                   stderr=subprocess.DEVNULL, **hidden)
        def fetch(path, headers=None):
            connection = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
            try:
                connection.request('GET', path, headers=headers or {})
                response = connection.getresponse()
                return response.status, dict(response.getheaders()), response.read()
            finally: connection.close()
        try:
            for _ in range(100):
                try: fetch('/'); break
                except OSError: time.sleep(.05)
            else: raise RuntimeError('Nginx fixture did not become ready')
            status, headers, body = fetch('/')
            assert status == 200 and headers['Cache-Control'] == 'no-cache'
            status304, _, cached = fetch('/', {'If-None-Match': headers['ETag']})
            assert status304 == 304 and cached == b''
            asset = re.search(r'(?:src|href)="(/assets/[^" ]+\.js)"', body.decode()).group(1)
            status, asset_headers, asset_body = fetch(asset, {'Accept-Encoding': 'gzip'})
            assert status == 200 and 'immutable' in asset_headers['Cache-Control']
            assert asset_headers['Content-Encoding'] == 'gzip' and len(gzip.decompress(asset_body)) > len(asset_body)
            missing, missing_headers, _ = fetch('/assets/missing-AbCd1234.js')
            assert missing == 404 and 'immutable' not in missing_headers.get('Cache-Control', '')
            assert missing_headers['Cache-Control'] == 'no-store'
            api404, api_headers, _ = fetch('/api/missing')
            assert api404 == 404 and api_headers['Cache-Control'] == 'no-store'
            forbidden, _, _ = fetch('/api/private')
            assert forbidden == 403
            private_status, private_headers, private_body = fetch('/api/private', {
                'Authorization': 'Bearer proxy-fixture', 'Accept-Encoding': 'gzip'})
            assert private_status == 200 and private_headers['Cache-Control'] == 'no-store'
            assert private_headers['Content-Encoding'] == 'gzip'
            assert json.loads(gzip.decompress(private_body))['private'] is True
            connection = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
            started = time.perf_counter()
            connection.request('GET', '/api/rag/stream', headers={'Accept-Encoding': 'gzip'})
            response = connection.getresponse(); stream_headers = dict(response.getheaders())
            events = []
            for line in response:
                if line.startswith(b'event:'):
                    events.append({'event': line.decode().strip(), 'ms': round((time.perf_counter() - started) * 1000, 2)})
            connection.close()
            assert len(events) == 3 and events[-1]['ms'] - events[0]['ms'] > 400, events
            assert events[0]['ms'] < 250 and 'Content-Encoding' not in stream_headers
            assert stream_headers['Cache-Control'] == 'no-cache, no-transform'
            report = {'fixture_only': True, 'nginx': subprocess.check_output([str(binary), '-v'], stderr=subprocess.STDOUT).decode().strip(),
                'html_304': status304, 'asset_cache_control': asset_headers['Cache-Control'],
                'asset_gzip_bytes': len(asset_body), 'asset_raw_bytes': len(gzip.decompress(asset_body)),
                'missing_asset': missing, 'missing_api': api404, 'unauthorized': forbidden,
                'private_cache_control': private_headers['Cache-Control'], 'private_json_gzip': True,
                'sse_events': events, 'sse_cache_control': stream_headers['Cache-Control'],
                'sse_compressed': 'Content-Encoding' in stream_headers}
            (evidence / 'phase3-proxy.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
            print(json.dumps(report, ensure_ascii=False, indent=2))
        finally:
            subprocess.run(command + ['-s', 'quit'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, **hidden)
            try: process.wait(timeout=5)
            except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=5)
            server.shutdown(); server.server_close()


if __name__ == '__main__': main()
