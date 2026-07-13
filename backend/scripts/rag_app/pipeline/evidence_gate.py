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

# 时效性关键词
FRESHNESS_KEYWORDS = re.compile(
    r"最新|今天|今日|昨天|明天|实时|目前|现在|刚刚|近期|最近|"
    r"新闻|资讯|交易|伤病|复出|签约|裁掉|官宣|自由市场|夏季联赛|"
    r"最新战况|今日比赛|赛况|战报"
)

# 包含明确日期/时间标记的结构化数据，表示数据是新鲜的
FRESH_DATA_MARKERS = re.compile(
    r"2026-07-\d{2}|2026-06-\d{2}"  # 近期日期
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

        # 1. 库外知识问题：即使有文档也不应允许回答
        if question and OUT_OF_SCOPE_PATTERNS.search(question.lower()):
            if not structured_text.strip():
                return EvidenceDecision(False, REFUSAL_MESSAGE, evidence_count=evidence_count)

        # 2. 时效性问题：联网搜索失败/为空时，除非结构化数据明确包含最新日期，否则拒绝
        is_freshness_query = bool(question and FRESHNESS_KEYWORDS.search(question.lower()))
        if is_freshness_query:
            # 如果尝试了联网但失败/为空
            if search_attempted and not search_success:
                # 检查结构化数据是否包含近期日期（表示数据是新鲜的）
                has_fresh_data = bool(structured_text and FRESH_DATA_MARKERS.search(structured_text))
                if not has_fresh_data:
                    return EvidenceDecision(False, REALTIME_REFUSAL_MESSAGE, evidence_count=evidence_count)
            # 如果未尝试联网（被禁用或跳过），但有旧文档，也应拒绝实时问题
            elif not search_attempted and not structured_text.strip():
                if len(documents) > 0:
                    # 有旧文档但不是结构化数据，对实时问题不够可靠
                    return EvidenceDecision(False, REALTIME_REFUSAL_MESSAGE, evidence_count=evidence_count)

        # 3. 通用判断
        if structured_text.strip() or len(documents) >= self.min_documents:
            return EvidenceDecision(True, evidence_count=evidence_count)
        return EvidenceDecision(False, REFUSAL_MESSAGE, evidence_count=evidence_count)
