"""An atomic, per-collection marker shared by the server and offline index builder."""
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import threading
from uuid import uuid4


class IndexVersion:
    def __init__(self, path, collection):
        self.path = (Path(path) / ('.query-version-' + hashlib.sha256(collection.encode()).hexdigest()[:16] + '.json')) if path else None
        self._lock = threading.RLock()
        self._writing = False

    def active_collection(self, default):
        if self.path is None:
            return default
        try:
            return json.loads(self.path.read_text(encoding='utf-8')).get('collection', default)
        except FileNotFoundError:
            return default

    def token(self):
        if self.path is None:
            return None
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            try:
                with self.path.open('x', encoding='utf-8') as file:
                    json.dump({'version': uuid4().hex, 'status': 'ready'}, file)
            except FileExistsError:
                pass
            value = json.loads(self.path.read_text(encoding='utf-8'))
            return value['version'] if value.get('status') == 'ready' else None
        except (OSError, ValueError, KeyError):
            # An unreadable publication marker must bypass caching, never reuse old evidence.
            return None

    def _publish(self, status, collection=None):
        if self.path is None:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_name(self.path.name + '.' + uuid4().hex + '.tmp')
        try:
            active = collection or self.active_collection(None)
            temporary.write_text(json.dumps({'version': uuid4().hex, 'status': status,
                                              **({'collection': active} if active else {})}), encoding='utf-8')
            os.replace(temporary, self.path)
        finally:
            temporary.unlink(missing_ok=True)

    def publish_snapshot(self, collection):
        self._publish('ready', collection)

    @contextmanager
    def writer(self):
        """OS-owned lock: concurrent processes reject immediately; crashes release it."""
        if not self._lock.acquire(blocking=False):
            raise RuntimeError('index writer busy')
        file = None
        nested = self._writing
        try:
            if not nested and self.path is not None:
                self.path.parent.mkdir(parents=True, exist_ok=True)
                file = self.path.with_suffix('.lock').open('a+b')
                file.seek(0)
                if os.name == 'nt':
                    import msvcrt
                    if os.fstat(file.fileno()).st_size == 0:
                        file.write(b'0'); file.flush()
                    file.seek(0)
                    try: msvcrt.locking(file.fileno(), msvcrt.LK_NBLCK, 1)
                    except OSError as exc: raise RuntimeError('index writer busy') from exc
                else:
                    import fcntl
                    try: fcntl.flock(file, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    except OSError as exc: raise RuntimeError('index writer busy') from exc
            self._writing = True
            yield
        finally:
            if not nested:
                self._writing = False
            if file is not None:
                # Closing the handle releases either platform's lock.
                file.close()
            self._lock.release()

    @contextmanager
    def writing(self):
        with self.writer():
            self._publish('writing')
            try:
                yield
            except BaseException:
                self._publish('failed')
                raise
            else:
                self._publish('ready')
