from __future__ import annotations

from collections import defaultdict, deque


VALID_ROLES = {"user", "assistant"}
MAX_HISTORY_CONTENT_CHARS = 1000


class ConversationMemory:
    def __init__(self, window: int = 5):
        self.window = window
        self._messages = defaultdict(lambda: deque(maxlen=self.window * 2))

    def get(self, session_id: str | None) -> list[dict]:
        if not session_id:
            return []
        return list(self._messages[session_id])

    def replace(self, session_id: str | None, messages: list[dict] | None) -> None:
        if not session_id or not messages:
            return
        normalized = self._normalize(messages)
        if not normalized:
            return
        bucket = self._messages[session_id]
        bucket.clear()
        bucket.extend(normalized)

    def add(self, session_id: str | None, question: str, answer: str) -> None:
        if not session_id:
            return
        self._messages[session_id].append({"role": "user", "content": question})
        self._messages[session_id].append({"role": "assistant", "content": answer})

    def recent_context(self, session_id: str | None, max_messages: int = 4,
                       max_chars: int = 800) -> str:
        messages = self.get(session_id)[-max_messages:]
        lines = []
        for item in messages:
            role = "用户" if item.get("role") == "user" else "助手"
            content = str(item.get("content") or "").strip()
            if content:
                lines.append(f"{role}: {content}")
        context = "\n".join(lines)
        return context[-max_chars:]

    def _normalize(self, messages: list[dict]) -> list[dict]:
        normalized = []
        for item in messages:
            role = str(item.get("role") or "").strip().lower()
            content = str(item.get("content") or "").strip()
            if role not in VALID_ROLES or not content:
                continue
            normalized.append({
                "role": role,
                "content": content[:MAX_HISTORY_CONTENT_CHARS],
            })
        return normalized[-self.window * 2:]
