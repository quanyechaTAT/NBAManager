"""Bounded TTL results and shared in-flight loads; no model work under the lock."""
from collections import OrderedDict
from concurrent.futures import Future
from copy import deepcopy
import threading
import time


class LoadingCache:
    def __init__(self, capacity=256, ttl_seconds=60, *, clock=time.monotonic):
        self.capacity = max(0, int(capacity))
        self.ttl = max(0, float(ttl_seconds))
        self.clock = clock
        self._values = OrderedDict()
        self._pending = {}
        self._lock = threading.Lock()
        self._hits = self._misses = self._joined = 0

    def get_or_load(self, key, loader, *, cacheable=lambda value: True):
        return self.get_many([key], lambda keys: [loader()], cacheable=cacheable)[0]

    def get_many(self, keys, loader, *, cacheable=lambda value: True):
        """Load only missing keys in one batch, including duplicate input handling."""
        if not keys:
            return []
        if not self.capacity or not self.ttl:
            return loader(keys)
        ready, waiting, owned, uncached = {}, {}, {}, []
        with self._lock:
            for key in dict.fromkeys(keys):
                item = self._values.get(key)
                if item is not None and self.clock() < item[0]:
                    self._hits += 1
                    self._values.move_to_end(key)
                    ready[key] = item[1]
                    continue
                self._values.pop(key, None)
                if key in self._pending:
                    self._joined += 1
                    waiting[key] = self._pending[key]
                else:
                    self._misses += 1
                    if len(self._pending) >= self.capacity:
                        uncached.append(key)
                    else:
                        owned[key] = self._pending[key] = Future()
        missing = list(owned) + uncached
        if missing:
            try:
                values = loader(missing)
                if len(values) != len(missing):
                    raise ValueError('Loader returned a mismatched batch')
                accepted = [cacheable(value) for value in values]
                with self._lock:
                    for key, value, keep in zip(missing, values, accepted):
                        ready[key] = deepcopy(value)
                        if key in owned and self._pending.get(key) is owned[key]:
                            self._pending.pop(key)
                            if keep:
                                self._values[key] = (self.clock() + self.ttl, deepcopy(value))
                                self._values.move_to_end(key)
                    while len(self._values) > self.capacity:
                        self._values.popitem(last=False)
                for key, future in owned.items():
                    future.set_result(ready[key])
            except BaseException as exc:
                with self._lock:
                    for key, future in owned.items():
                        if self._pending.get(key) is future:
                            self._pending.pop(key)
                for future in owned.values():
                    future.set_exception(exc)
                raise
        for key, future in waiting.items():
            ready[key] = future.result()
        return [deepcopy(ready[key]) for key in keys]

    def clear(self):
        with self._lock:
            self._values.clear()
            # Existing callers can finish, but their loads cannot refill this generation.
            self._pending.clear()

    def stats(self):
        with self._lock:
            return {'hits': self._hits, 'misses': self._misses, 'coalesced': self._joined,
                    'entries': len(self._values), 'inflight': len(self._pending), 'capacity': self.capacity}
