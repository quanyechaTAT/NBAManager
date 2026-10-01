from __future__ import annotations

import json
import os
import sys
import urllib.request
from typing import Iterable

from rag_app.config import RAGSettings
from rag_app.llm.prompts import SYSTEM_PROMPT


class LLMClient:
    def __init__(self, settings: RAGSettings):
        self.settings = settings
        self._opener = self._build_opener()

    @staticmethod
    def _build_opener() -> urllib.request.OpenerDirector:
        proxy_url = os.environ.get("HTTP_PROXY") or os.environ.get("HTTPS_PROXY") or ""
        if not proxy_url:
            host = os.environ.get("NBA_PROXY_HOST", "").strip()
            port = os.environ.get("NBA_PROXY_PORT", "").strip()
            if host and port:
                proxy_url = f"http://{host}:{port}"
        if proxy_url:
            handler = urllib.request.ProxyHandler({
                "http": proxy_url,
                "https": proxy_url,
            })
            return urllib.request.build_opener(handler)
        return urllib.request.build_opener()

    def complete(self, user_prompt: str, max_tokens: int | None = None) -> str:
        if not self.settings.llm_api_key:
            return ""

        payload = {
            "model": self.settings.llm_model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            "max_tokens": self._token_budget(max_tokens),
            "temperature": self.settings.llm_temperature,
            "frequency_penalty": 0.3,
            "presence_penalty": 0.0,
        }
        try:
            result = self._request(payload)
            choices = result.get("choices") or []
            if not choices:
                return ""
            msg = choices[0].get("message") or {}
            # 只取正式回答，reasoning_content 是模型内部思考过程，不输出给用户
            return (msg.get("content") or "").strip()
        except Exception as exc:
            print(f"LLM request failed: {exc}", file=sys.stderr)
            return ""

    def stream(self, user_prompt: str, max_tokens: int | None = None) -> Iterable[str]:
        if not self.settings.llm_api_key:
            return

        payload = {
            "model": self.settings.llm_model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            "max_tokens": self._token_budget(max_tokens),
            "temperature": self.settings.llm_temperature,
            "frequency_penalty": 0.3,
            "presence_penalty": 0.0,
            "stream": True,
        }
        try:
            request = self._build_request(payload)
            response = self._opener.open(request, timeout=self.settings.llm_timeout_seconds)
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
                    delta = choice.get("delta") or {}
                    # 只取正式回答，reasoning_content 是模型内部思考过程，不输出给用户
                    content = delta.get("content")
                    if content:
                        yield content
        except Exception as exc:
            print(f"LLM stream failed: {exc}", file=sys.stderr)

    def _request(self, payload: dict) -> dict:
        request = self._build_request(payload)
        with self._opener.open(request, timeout=self.settings.llm_timeout_seconds) as response:
            return json.loads(response.read().decode("utf-8"))

    def _token_budget(self, requested: int | None) -> int:
        configured = max(1, int(self.settings.llm_max_tokens))
        if requested is None:
            return configured
        return max(1, min(int(requested), configured))

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
