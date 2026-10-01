"""DeepSeek LLM tool for agents"""
import json
import urllib.request
from config import DEEPSEEK_API_KEY, DEEPSEEK_API_BASE, DEEPSEEK_MODEL

def call_deepseek(system_prompt: str, user_prompt: str, temperature: float = 0.3) -> str:
    """Call DeepSeek API and return the response content"""
    if not DEEPSEEK_API_KEY:
        return "AI 服务未配置（缺少 DEEPSEEK_API_KEY）"

    try:
        body = json.dumps({
            "model": DEEPSEEK_MODEL,
            "temperature": temperature,
            "max_tokens": 2000,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ]
        }).encode("utf-8")

        req = urllib.request.Request(
            f"{DEEPSEEK_API_BASE}/chat/completions",
            data=body,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {DEEPSEEK_API_KEY}"
            }
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        return f"AI 调用失败: {str(e)}"
