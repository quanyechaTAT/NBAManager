from __future__ import annotations

from collections import defaultdict, deque


class ConversationMemory:
    def __init__(self, window: int = 5):
        self.window = window
        self._messages = defaultdict(lambda: deque(maxlen=self.window * 2))

    def get(self, session_id: str | None) -> list[dict]:
        if not session_id:
            return []
        return list(self._messages[session_id])

    def add(self, session_id: str | None, question: str, answer: str) -> None:
        if not session_id:
            return
        self._messages[session_id].append({"role": "user", "content": question})
        self._messages[session_id].append({"role": "assistant", "content": answer})
