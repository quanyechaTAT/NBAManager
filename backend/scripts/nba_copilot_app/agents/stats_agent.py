"""Stats Agent - handles statistics queries"""
from tools.llm_tool import call_deepseek

SYSTEM_PROMPT = """你是 NBA 数据分析助手。根据提供的数据回答问题。
如果数据不足，说明缺少什么数据。使用中文回答。"""

def handle(query: str) -> dict:
    """Handle a stats-related query"""
    # For now, use DeepSeek general knowledge for stats queries
    # In production, this would query the database via tools
    answer = call_deepseek(SYSTEM_PROMPT, f"用户问题: {query}\n\n请根据你的知识回答关于NBA数据的问题。")
    return {"answer": answer, "citations": [], "agent": "STATS"}
