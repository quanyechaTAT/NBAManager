"""A bounded, expiring cache; remote calls always happen outside its lock."""
from collections import OrderedDict
import threading
import time


class TTLCache:
    def __init__(self, capacity=256, ttl_seconds=600, clock=time.monotonic):
        self.capacity = max(1, int(capacity))
        self.ttl = max(0, float(ttl_seconds))
        self.clock = clock
        self._values = OrderedDict()
        self._lock = threading.Lock()

    def get(self, key):
        with self._lock:
            item = self._values.get(key)
            if item is None:
                return None
            if self.clock() - item[0] >= self.ttl:
                del self._values[key]
                return None
            self._values.move_to_end(key)
            return item[1]

    def put(self, key, value):
        if self.ttl <= 0:
            return
        with self._lock:
            self._values[key] = (self.clock(), value)
            self._values.move_to_end(key)
            while len(self._values) > self.capacity:
                self._values.popitem(last=False)
