from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence


REFUSAL_MESSAGE = (
    "当前数据不足，无法判断。当前索引和结构化数据中没有找到足够证据支持回答，"
    "请换一个更具体的球队、球员、日期或比赛范围再试。"
)


@dataclass(frozen=True)
class EvidenceDecision:
    allowed: bool
    message: str = ""
    evidence_count: int = 0


class EvidenceGate:
    def __init__(self, min_documents: int = 1):
        self.min_documents = min_documents

    def assess(self, structured_text: str = "", documents: Sequence[str] | None = None) -> EvidenceDecision:
        documents = documents or []
        evidence_count = (1 if structured_text.strip() else 0) + len([doc for doc in documents if doc and doc.strip()])
        if structured_text.strip() or len(documents) >= self.min_documents:
            return EvidenceDecision(True, evidence_count=evidence_count)
        return EvidenceDecision(False, REFUSAL_MESSAGE, evidence_count=evidence_count)
