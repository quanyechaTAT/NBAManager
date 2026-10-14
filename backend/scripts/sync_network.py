"""Explicit, bounded networking for data providers; never alters process proxy settings."""
import os
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import requests


def build_proxy_url(host, port=''):
    host = (host or '').strip().strip('\"\'')
    port = (port or '').strip().strip('\"\'')
    if not host:
        return ''
    if '://' not in host:
        host = 'http://' + host
    parsed = urllib.parse.urlsplit(host)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname:
        raise ValueError('NBA_PROXY_HOST must be an HTTP(S) proxy host or URL')
    if parsed.port is None and port:
        host = host.rstrip('/') + ':' + str(int(port))
    return host


class SyncNetwork(requests.Session):
    def __init__(self):
        super().__init__()
        # requests otherwise reads Windows registry proxies even after clearing HTTP_PROXY.
        self.trust_env = False
        try:
            self.proxy_url = build_proxy_url(os.getenv('NBA_PROXY_HOST'), os.getenv('NBA_PROXY_PORT'))
        except ValueError:
            print('Invalid NBA proxy configuration; using direct connections', file=sys.stderr)
            self.proxy_url = ''
        if self.proxy_url:
            parsed = urllib.parse.urlsplit(self.proxy_url)
            try:
                sock = socket.create_connection((parsed.hostname, parsed.port or (443 if parsed.scheme == 'https' else 80)), timeout=1.5)
                sock.close()
            except OSError:
                print('NBA proxy unavailable; using direct connections', file=sys.stderr)
                self.proxy_url = ''
        self._direct = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        self._proxy = urllib.request.build_opener(urllib.request.ProxyHandler(self._proxies()))
        self._direct_hosts = set()

    def _proxies(self):
        return {'http': self.proxy_url, 'https': self.proxy_url} if self.proxy_url else {}

    def _routes(self, url):
        host = urllib.parse.urlsplit(url).hostname
        if not self.proxy_url:
            return [False, False]  # one retry for transient EOF/reset, including direct-only setups
        return [False, True] if host in self._direct_hosts else [True, False]

    def _remember(self, url, proxy):
        host = urllib.parse.urlsplit(url).hostname
        if proxy:
            self._direct_hosts.discard(host)
        else:
            self._direct_hosts.add(host)

    def request(self, method, url, **kwargs):
        timeout = kwargs.pop('timeout', None) or 30
        if isinstance(timeout, (int, float)):
            timeout = (min(5, timeout), timeout)
        kwargs.pop('proxies', None)
        routes = self._routes(url)
        for index, use_proxy in enumerate(routes):
            try:
                result = super().request(method, url, timeout=timeout,
                                         proxies=self._proxies() if use_proxy else {}, **kwargs)
                result.raise_for_status()
                self._remember(url, use_proxy)
                return result
            except requests.RequestException as error:
                if error.response is not None:
                    status = error.response.status_code
                    error.response.close()
                    if status < 500 and status not in (407, 429):
                        raise
                if index == len(routes) - 1 or method.upper() not in ('GET', 'HEAD'):
                    raise
                print('NBA data request failed; retrying ' + ('direct' if not routes[index + 1] else 'via proxy'), file=sys.stderr)
                time.sleep(.25)

    def urlopen(self, request, timeout=30):
        url = request.full_url if isinstance(request, urllib.request.Request) else request
        routes = self._routes(url)
        for index, use_proxy in enumerate(routes):
            try:
                result = (self._proxy if use_proxy else self._direct).open(request, timeout=timeout)
                self._remember(url, use_proxy)
                return result
            except (OSError, urllib.error.URLError) as error:
                if isinstance(error, urllib.error.HTTPError):
                    error.close()
                    if error.code < 500 and error.code not in (407, 429):
                        raise
                if index == len(routes) - 1:
                    raise
                time.sleep(.25)
