from __future__ import annotations

import json
import os
import sys
import time
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

        payload = self._payload(user_prompt, max_tokens)
        try:
            result = self._request(payload)
            choices = result.get("choices") or []
            if not choices:
                return ""
            if choices[0].get('finish_reason') == 'length':
                return ''  # Return verified evidence instead of an incomplete answer.
            msg = choices[0].get("message") or {}
            # 只取正式回答，reasoning_content 是模型内部思考过程，不输出给用户
            return (msg.get("content") or "").strip()
        except Exception as exc:
            print(f"LLM request failed: {type(exc).__name__}", file=sys.stderr)
            return ""

    def stream(self, user_prompt: str, max_tokens: int | None = None) -> Iterable[str]:
        if not self.settings.llm_api_key:
            return

        payload = self._payload(user_prompt, max_tokens, stream=True)
        response = None
        finished = False
        deadline = time.monotonic() + self.settings.llm_timeout_seconds
        try:
            request = self._build_request(payload)
            response = self._opener.open(request, timeout=self.settings.llm_timeout_seconds)
            for raw_line in response:
                if time.monotonic() >= deadline:
                    raise RuntimeError('模型回答超时，请稍后重试或缩小问题范围')
                line = raw_line.decode("utf-8").strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    finished = True
                    break
                try:
                    chunk = json.loads(data)
                except json.JSONDecodeError as exc:
                    raise RuntimeError('模型流式事件格式错误') from exc
                if chunk.get('error'):
                    raise RuntimeError('模型返回错误，请稍后重试')
                for choice in chunk.get("choices") or []:
                    delta = choice.get("delta") or {}
                    # 只取正式回答，reasoning_content 是模型内部思考过程，不输出给用户
                    content = delta.get("content")
                    if content:
                        yield content
                    if choice.get('finish_reason') == 'length':
                        raise RuntimeError('回答达到模型输出长度限制，请缩小问题范围')
                    if choice.get('finish_reason') in ('stop', 'tool_calls'):
                        finished = True
            if not finished:
                raise RuntimeError('模型流式输出中断，请重试')
        except Exception as exc:
            print(f"LLM stream failed: {type(exc).__name__}", file=sys.stderr)
            raise RuntimeError(str(exc) if isinstance(exc, RuntimeError) else '模型流式输出中断，请重试') from exc
        finally:
            if response is not None and hasattr(response, 'close'):
                response.close()

    def _request(self, payload: dict) -> dict:
        request = self._build_request(payload)
        with self._opener.open(request, timeout=self.settings.llm_timeout_seconds) as response:
            return json.loads(response.read().decode("utf-8"))

    def complete_task(self, user_prompt: str, *, task: str, max_tokens: int = 512,
                      timeout_seconds: int = 5) -> str:
        """Small, bounded retrieval tasks with a separate purpose and token budget."""
        if task not in {'planning', 'hyde'}:
            raise ValueError('unsupported_task')
        if not self.settings.llm_api_key:
            return ''
        payload = self._payload(user_prompt, max_tokens)
        payload['messages'][0]['content'] = ('你是检索查询规划器。只输出合法 JSON，不回答问题。' if task == 'planning'
                                            else '你生成假设性检索文档。只输出一段假设文字，不声称其为事实。')
        payload['temperature'] = 0.0
        payload['max_tokens'] = min(max(1, max_tokens), 768, max(1, self.settings.llm_max_tokens))
        payload.pop('reasoning_effort', None)
        if self.settings.llm_model.startswith('deepseek-'):
            payload['thinking'] = {'type': 'disabled'}
        if task == 'planning':
            payload['response_format'] = {'type': 'json_object'}
        with self._opener.open(self._build_request(payload), timeout=max(1, min(10, timeout_seconds))) as response:
            result = json.loads(response.read().decode('utf-8'))
        choices = result.get('choices') or []
        if not choices or choices[0].get('finish_reason') == 'length':
            return ''
        return ((choices[0].get('message') or {}).get('content') or '').strip()

    def _payload(self, user_prompt: str, max_tokens: int | None, stream: bool = False) -> dict:
        payload = {
            'model': self.settings.llm_model,
            'messages': [{'role': 'system', 'content': SYSTEM_PROMPT},
                         {'role': 'user', 'content': user_prompt}],
            'max_tokens': self._token_budget(max_tokens),
            'temperature': self.settings.llm_temperature,
            'frequency_penalty': 0.3,
            'presence_penalty': 0.0,
        }
        if stream:
            payload['stream'] = True
        if self.settings.llm_model.startswith('deepseek-'):
            payload['thinking'] = {'type': 'enabled' if self.settings.llm_thinking else 'disabled'}
            if self.settings.llm_thinking:
                effort = self.settings.llm_reasoning_effort
                payload['reasoning_effort'] = effort if effort in ('low', 'high', 'max') else 'low'
        return payload

    def _token_budget(self, requested: int | None) -> int:
        configured = max(1, int(self.settings.llm_max_tokens))
        if requested is None:
            return configured
        reserve = 4096 if self.settings.llm_model.startswith('deepseek-') and self.settings.llm_thinking else 0
        return max(1, min(int(requested) + reserve, configured))

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
