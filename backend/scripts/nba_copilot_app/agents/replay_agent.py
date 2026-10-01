"""Replay Agent - handles game replay analysis"""
import json
from tools.data_tools import get_game_timeline, get_game_momentum, get_game_highlights, get_game_turning_points, get_box_score
from tools.llm_tool import call_deepseek

SYSTEM_PROMPT = """你是 NBA 比赛事实分析助手。

你只能使用 <FACTS> 中给出的事实。
禁止编造任何事件、比分、时间、球员、球队。
如果事实不足，必须回答"当前数据不足以判断"。
所有解释必须引用具体的球员名、比分或时间。
使用中文回答。"""

def handle(query: str, game_id: str | None = None) -> dict:
    """Handle a replay-related query"""
    if not game_id:
        return {"answer": "请先指定一场比赛（gameId），我才能分析比赛回放。", "citations": [], "agent": "REPLAY"}

    # Gather facts
    timeline = get_game_timeline(game_id)[:50]  # Limit for context window
    momentum = get_game_momentum(game_id)
    highlights = get_game_highlights(game_id)
    turning_points = get_game_turning_points(game_id)
    box_score = get_box_score(game_id)

    facts = json.dumps({
        "timeline": timeline[:30],
        "turning_points": turning_points,
        "highlights": highlights[:10],
        "box_score_summary": {
            "homeTeam": box_score.get("homeTeam", "") if box_score else "",
            "awayTeam": box_score.get("awayTeam", "") if box_score else "",
            "homeScore": box_score.get("homeScore", 0) if box_score else 0,
            "awayScore": box_score.get("awayScore", 0) if box_score else 0,
        }
    }, ensure_ascii=False, indent=2)

    user_prompt = f"<FACTS>\n{facts}\n</FACTS>\n\n用户问题: {query}"
    answer = call_deepseek(SYSTEM_PROMPT, user_prompt)

    return {
        "answer": answer,
        "citations": [{"type": "gameId", "value": game_id}],
        "agent": "REPLAY",
        "facts_used": len(timeline)
    }
