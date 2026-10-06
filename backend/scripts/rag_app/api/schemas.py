from __future__ import annotations

from pydantic import BaseModel, Field
from typing import Literal


class ConversationTurn(BaseModel):
    role: Literal['user', 'assistant']
    content: str = Field(min_length=1, max_length=1000)


class QueryRequest(BaseModel):
    # 长度上限：question 直送 LLM（token/成本攻击面），超长一律 422 拒绝
    question: str = Field(min_length=2, max_length=2000)
    top_k: int = Field(5, ge=1, le=20)
    session_id: str | None = Field(default=None, max_length=128)
    force_web_search: bool = False
    conversation_history: list[ConversationTurn] = Field(default_factory=list, max_length=8)


class QueryResponse(BaseModel):
    success: bool
    answer: str = ""
    sources: list[str] = []
    metadatas: list[dict] = []
    distances: list[float] = []
    response_time: int = 0
    model: str = ""
    structured_data: bool = False
    search_results: list[dict] = []
    used_tools: list[str] = []
    tool_trace: list[dict] = []
    search_execution: dict = {}
    retrieval_quality: dict = {}
    error: str = ""
    sourceDetails: list[dict] = Field(default_factory=list)
    follow_ups: list[str] = Field(default_factory=list)
