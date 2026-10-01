"""Evidence gate：对检索结果 / 搜索结果做最终质量判断。"""
from __future__ import annotations

from dataclasses import dataclass
import re

FRESHNESS_KEYWORDS = re.compile(
    r"最新|今天|今日|昨天|明天|实时|目前|现在|刚刚|近期|最近|"
    r"新闻|资讯|交易|伤病|复出|签约|裁掉|官宣|报道称|报道|"
    r"夏季联赛|自由市场|自由球员|选秀结果"
)

# 明确属于大模型外部知识 / 库外的问题模式
OUT_OF_SCOPE_PATTERNS = re.compile(
    r"薪资帽|工资帽|规则是什么|历史纪录|最伟大|goat|名人堂|"
    r"nba2k|游戏|篮球鞋|球鞋|裁判|总裁|老板|"
    r"市值|收视率|转播合同|中国男篮|足球|天气|"
    r"迈克尔.?乔丹|科比|奥尼尔|邓肯|加内特|诺维茨基|韦德|艾弗森|麦迪|卡特|姚明"
)

REFUSAL_MESSAGE = "当前数据不足，无法判断。当前索引和结构化数据中没有找到足够证据支持回答，请换一个更具体的球队、球员、日期或比赛范围再试。"
REALTIME_REFUSAL_MESSAGE = "当前无法通过联网获取可靠的最新 NBA 数据，请稍后重试，或改问不依赖实时信息的问题。"
STALE_DATA_MESSAGE = "联网未获取到更新，以下为数据库中的最近记录（不保证实时）："
OUT_OF_SCOPE_MESSAGE = "我目前只能回答 NBA 球队、球员、比赛、排名、选秀、伤病和新闻相关问题。"
INVALID_QUESTION_MESSAGE = "请提供一个具体的 NBA 问题。"


@dataclass
class GateDecision:
    allowed: bool
    reason: str
    message: str = ""


class EvidenceGate:
    def __init__(self):
        pass

    def is_out_of_scope(self, question: str) -> bool:
        return bool(OUT_OF_SCOPE_PATTERNS.search(question))

    def assess(self, structured_text: str, documents: list[str], question: str,
               search_attempted: bool = False, search_success: bool = False) -> GateDecision:
        is_freshness = self._is_freshness_query(question)
        has_structured = bool(structured_text and structured_text.strip())
        has_documents = bool(documents)

        if self.is_out_of_scope(question):
            return GateDecision(False, "out_of_scope", OUT_OF_SCOPE_MESSAGE)

        if is_freshness:
            if has_structured and search_attempted and not search_success:
                return GateDecision(True, "structured_stale_fallback", STALE_DATA_MESSAGE)
            if search_attempted and search_success and (has_documents or has_structured):
                return GateDecision(True, "freshness_search_available")
            reason = "freshness_search_failed" if search_attempted else "freshness_search_not_attempted"
            return GateDecision(False, reason, REALTIME_REFUSAL_MESSAGE)

        if has_structured:
            return GateDecision(True, "structured_data_available")

        if has_documents:
            return GateDecision(True, "vector_results_available")

        return GateDecision(False, "no_evidence", REFUSAL_MESSAGE)

    def _is_freshness_query(self, question: str) -> bool:
        """检测问题是否需要实时数据。包含具体年份的查询不是时效性查询。"""
        if not FRESHNESS_KEYWORDS.search(question):
            return False
        # 包含具体年份（如2024、2025）→ 不是时效性查询，是历史查询
        if re.search(r"20\d{2}年", question):
            return False
        return True
