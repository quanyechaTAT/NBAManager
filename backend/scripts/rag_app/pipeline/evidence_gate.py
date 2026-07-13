from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence
import re


REFUSAL_MESSAGE = (
    "当前数据不足，无法判断。当前索引和结构化数据中没有找到足够证据支持回答，"
    "请换一个更具体的球队、球员、日期或比赛范围再试。"
)

# 库外知识关键词：这些问题的答案不在 NBA 数据库中
OUT_OF_SCOPE_PATTERNS = re.compile(
    r"薪资帽|工资帽|规则|历史纪录|最伟大|goat|名人堂|hall.of.fame|"
    r"选秀状元|选秀顺位|选秀历史|nba2k|游戏|篮球鞋|球鞋|"
    r"教练|裁判|总裁|老板|市值|收视率|转播合同|"
    r"迈克尔·乔丹|科比·布莱恩特|科比|奥尼尔|邓肯|加内特|诺维茨基|"
    r"韦德|艾弗森|麦迪|卡特|姚明|拉里·伯德|魔术师|张伯伦|贾巴尔"
)


@dataclass(frozen=True)
class EvidenceDecision:
    allowed: bool
    message: str = ""
    evidence_count: int = 0


class EvidenceGate:
    def __init__(self, min_documents: int = 1):
        self.min_documents = min_documents

    def assess(self, structured_text: str = "", documents: Sequence[str] | None = None,
               question: str = "") -> EvidenceDecision:
        documents = documents or []
        evidence_count = (1 if structured_text.strip() else 0) + len([doc for doc in documents if doc and doc.strip()])

        # 库外知识问题：即使有文档也不应允许回答
        if question and OUT_OF_SCOPE_PATTERNS.search(question.lower()):
            if not structured_text.strip():
                return EvidenceDecision(False, REFUSAL_MESSAGE, evidence_count=evidence_count)

        if structured_text.strip() or len(documents) >= self.min_documents:
            return EvidenceDecision(True, evidence_count=evidence_count)
        return EvidenceDecision(False, REFUSAL_MESSAGE, evidence_count=evidence_count)
