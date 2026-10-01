"""Supervisor Agent - routes user queries to specialized agents"""
from tools.llm_tool import call_deepseek
from tools.data_tools import *

INTENT_SYSTEM_PROMPT = """你是 NBA Copilot 的意图分类器。根据用户消息，判断应该路由到哪个 Agent。

可用 Agent:
- REPLAY: 比赛回放、比赛分析、为什么输了/赢了、关键时刻、走势、比赛总结
- STATS: 统计数据、今日比赛、排名、榜单、数据对比
- PLAYER: 球员信息、球员表现、近五场、伤病、合同
- TEAM: 球队信息、阵容、战绩、排名
- NEWS: 新闻、交易、流言
- DRAFT: 选秀、新秀、模拟选秀
- GENERAL: 通用问题、打招呼、无法分类

只返回 Agent 名称，不要返回其他内容。"""

def classify_intent(message: str) -> str:
    """Classify user intent and return agent name"""
    result = call_deepseek(INTENT_SYSTEM_PROMPT, message, temperature=0.1)
    agent = result.strip().upper()
    valid = {"REPLAY", "STATS", "PLAYER", "TEAM", "NEWS", "DRAFT", "GENERAL"}
    return agent if agent in valid else "GENERAL"
