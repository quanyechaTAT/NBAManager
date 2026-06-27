from __future__ import annotations

from pydantic import BaseModel, Field


class QueryRequest(BaseModel):
    question: str
    top_k: int = Field(5, ge=1, le=20)
    session_id: str | None = None


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
    error: str = ""
