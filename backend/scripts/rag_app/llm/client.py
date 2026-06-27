from __future__ import annotations

import json
import sys
import urllib.request
from typing import Iterable

from rag_app.config import RAGSettings
from rag_app.llm.prompts import SYSTEM_PROMPT


class LLMClient:
    def __init__(self, settings: RAGSettings):
        self.settings = settings

    def complete(self, user_prompt: str) -> str:
        if not self.settings.llm_api_key:
            return ""

        payload = {
            "model": self.settings.llm_model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            "max_tokens": self.settings.llm_max_tokens,
            "temperature": self.settings.llm_temperature,
            "frequency_penalty": 0.5,
            "presence_penalty": 0.0,
        }
        try:
            result = self._request(payload)
            choices = result.get("choices") or []
            if not choices:
                return ""
            return (choices[0].get("message") or {}).get("content", "").strip()
        except Exception as exc:
            print(f"LLM request failed: {exc}", file=sys.stderr)
            return ""

    def stream(self, user_prompt: str) -> Iterable[str]:
        if not self.settings.llm_api_key:
            return

        payload = {
            "model": self.settings.llm_model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            "max_tokens": self.settings.llm_max_tokens,
            "temperature": self.settings.llm_temperature,
            "frequency_penalty": 0.5,
            "presence_penalty": 0.0,
            "stream": True,
        }
        try:
            request = self._build_request(payload)
            response = urllib.request.urlopen(request, timeout=self.settings.llm_timeout_seconds)
            for raw_line in response:
                line = raw_line.decode("utf-8").strip()
                if not line.startswith("data: "):
                    continue
                data = line[6:]
                if data == "[DONE]":
                    break
                try:
                    chunk = json.loads(data)
                except json.JSONDecodeError:
                    continue
                for choice in chunk.get("choices") or []:
                    content = (choice.get("delta") or {}).get("content")
                    if content:
                        yield content
        except Exception as exc:
            print(f"LLM stream failed: {exc}", file=sys.stderr)

    def _request(self, payload: dict) -> dict:
        request = self._build_request(payload)
        with urllib.request.urlopen(request, timeout=self.settings.llm_timeout_seconds) as response:
            return json.loads(response.read().decode("utf-8"))

    def _build_request(self, payload: dict):
        url = f"{self.settings.llm_base_url.rstrip('/')}/chat/completions"
        return urllib.request.Request(
            url,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.settings.llm_api_key}",
                "User-Agent": "NBAManager/1.0",
            },
        )
