"""General Agent - handles general queries"""
from tools.llm_tool import call_mimo

SYSTEM_PROMPT = """你是 NBA Copilot，一个友好的 NBA 助手。
你可以回答关于 NBA 的一般问题。使用中文回答。"""

def handle(query: str) -> dict:
    """Handle a general query"""
    answer = call_mimo(SYSTEM_PROMPT, f"用户问题: {query}")
    return {"answer": answer, "citations": [], "agent": "GENERAL"}
