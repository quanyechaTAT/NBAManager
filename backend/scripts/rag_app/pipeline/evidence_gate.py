from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence
import re


REFUSAL_MESSAGE = (
    "当前数据不足，无法判断。当前索引和结构化数据中没有找到足够证据支持回答，"
    "请换一个更具体的球队、球员、日期或比赛范围再试。"
)

REALTIME_REFUSAL_MESSAGE = (
    "当前无法获取最新联网证据来回答此问题。请检查网络连接后重试，"
    "或换一个不涉及实时信息的问题。"
)

# 库外知识关键词
OUT_OF_SCOPE_PATTERNS = re.compile(
    r"薪资帽|工资帽|规则|历史纪录|最伟大|goat|名人堂|hall.of.fame|"
    r"nba2k|游戏|篮球鞋|球鞋|教练|裁判|总裁|老板|"
    r"市值|收视率|转播合同|中国男篮|足球|天气|"
    r"迈克尔·乔丹|科比·布莱恩特|科比|奥尼尔|邓肯|加内特|诺维茨基|"
    r"韦德|艾弗森|麦迪|卡特|姚明|拉里·伯德|魔术师|张伯伦|贾巴尔"
)

# 时效性关键词：命中时说明用户在问实时/最新信息
FRESHNESS_KEYWORDS = re.compile(
    r"最新|今天|今日|昨天|明天|实时|目前|现在|刚刚|近期|最近|"
    r"新闻|交易|伤病|复出|签约|裁掉|官宣|自由市场|夏季联赛"
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
               question: str = "", search_attempted: bool = False,
               search_success: bool = False) -> EvidenceDecision:
        documents = documents or []
        evidence_count = (1 if structured_text.strip() else 0) + len([doc for doc in documents if doc and doc.strip()])

        # 库外知识问题
        if question and OUT_OF_SCOPE_PATTERNS.search(question.lower()):
            if not structured_text.strip():
                return EvidenceDecision(False, REFUSAL_MESSAGE, evidence_count=evidence_count)

        # 时效性问题：如果尝试了联网搜索但失败/为空，且没有结构化数据，拒绝回答
        is_freshness_query = bool(question and FRESHNESS_KEYWORDS.search(question.lower()))
        if is_freshness_query and search_attempted and not search_success:
            if not structured_text.strip():
                return EvidenceDecision(False, REALTIME_REFUSAL_MESSAGE, evidence_count=evidence_count)

        # 通用判断
        if structured_text.strip() or len(documents) >= self.min_documents:
            return EvidenceDecision(True, evidence_count=evidence_count)
        return EvidenceDecision(False, REFUSAL_MESSAGE, evidence_count=evidence_count)
