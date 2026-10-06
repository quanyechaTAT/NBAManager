from __future__ import annotations

from collections import OrderedDict, deque
from contextlib import contextmanager
from dataclasses import dataclass
import threading
import time

VALID_ROLES = {"user", "assistant"}
MAX_HISTORY_CONTENT_CHARS = 1000


@dataclass
class _Session:
    messages: deque
    touched_at: float


class ConversationMemory:
    """Bounded, atomic conversation state for one worker process.

    Persisted snapshots hydrate missing state or extend a known conversation;
    they cannot roll back turns already completed in this process. A turn lease
    prevents concurrent generation for one session while allowing other sessions.
    """
    def __init__(self, window=5, *, max_sessions=256, ttl_seconds=3600, clock=time.monotonic):
        self.window = max(1, int(window))
        self.max_sessions = max(1, int(max_sessions))
        self.ttl_seconds = max(1, float(ttl_seconds))
        self._clock = clock
        self._sessions = OrderedDict()
        self._active = set()
        self._lock = threading.RLock()

    def _prune(self):
        now = self._clock()
        for key, session in list(self._sessions.items()):
            if key not in self._active and now - session.touched_at >= self.ttl_seconds:
                del self._sessions[key]

    def _save(self, session_id, messages):
        self._prune()
        if session_id not in self._sessions and len(self._sessions) >= self.max_sessions:
            victim = next((key for key in self._sessions if key not in self._active), None)
            if victim is None:
                return  # All cached sessions are busy; avoid exceeding the bound.
            del self._sessions[victim]
        self._sessions[session_id] = _Session(deque(messages, maxlen=self.window * 2), self._clock())
        self._sessions.move_to_end(session_id)

    def get(self, session_id):
        if not session_id:
            return []
        with self._lock:
            self._prune()
            session = self._sessions.get(session_id)
            if session is None:
                return []
            session.touched_at = self._clock()
            self._sessions.move_to_end(session_id)
            return [dict(message) for message in session.messages]

    def replace(self, session_id, messages):
        normalized = self._normalize(messages or [])
        if session_id and normalized:
            with self._lock:
                self._save(session_id, normalized)

    def hydrate(self, session_id, messages):
        incoming = self._normalize(messages or [])
        if not session_id or not incoming:
            return
        with self._lock:
            current = self.get(session_id)
            if not current:
                self._save(session_id, incoming)
                return
            # An overlapping suffix proves which snapshot extends the live state.
            for length in range(min(len(current), len(incoming)), 1, -1):
                suffix = current[-length:]
                for index in range(len(incoming) - length + 1):
                    if incoming[index:index + length] == suffix:
                        self._save(session_id, current + incoming[index + length:])
                        return
            # A stale or conflicting snapshot must not erase the latest live turns.

    def add(self, session_id, question, answer):
        if not session_id:
            return
        turn = self._normalize([{'role': 'user', 'content': question}, {'role': 'assistant', 'content': answer}])
        with self._lock:
            self._save(session_id, self.get(session_id) + turn)

    @contextmanager
    def turn(self, session_id):
        if not session_id:
            yield True
            return
        with self._lock:
            granted = session_id not in self._active
            if granted:
                self._active.add(session_id)
        try:
            yield granted
        finally:
            if granted:
                with self._lock:
                    self._active.discard(session_id)

    def recent_context(self, session_id, max_messages=4, max_chars=800):
        lines = []
        for item in self.get(session_id)[-max_messages:]:
            role = '用户' if item.get('role') == 'user' else '助手'
            lines.append(f"{role}: {item['content']}")
        return '\n'.join(lines)[-max_chars:]

    def _normalize(self, messages):
        normalized = []
        for item in messages:
            role = str(item.get('role') or '').strip().lower()
            content = str(item.get('content') or '').strip()
            if role in VALID_ROLES and content:
                normalized.append({'role': role, 'content': content[:MAX_HISTORY_CONTENT_CHARS]})
        return normalized[-self.window * 2:]
