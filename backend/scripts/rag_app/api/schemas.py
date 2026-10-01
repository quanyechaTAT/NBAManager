from __future__ import annotations

from pydantic import BaseModel, Field


class ConversationTurn(BaseModel):
    role: str
    content: str


class QueryRequest(BaseModel):
    # 长度上限：question 直送 LLM（token/成本攻击面），超长一律 422 拒绝
    question: str = Field(min_length=2, max_length=2000)
    top_k: int = Field(5, ge=1, le=20)
    session_id: str | None = None
    force_web_search: bool = False
    conversation_history: list[ConversationTurn] = Field(default_factory=list)


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
