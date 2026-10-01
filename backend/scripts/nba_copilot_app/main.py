"""NBA Copilot LangGraph Service - FastAPI entry point"""
import os
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from api.routes import router

app = FastAPI(title="NBA Copilot", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.include_router(router)

@app.get("/health")
def health():
    return {"status": "ok", "service": "nba-copilot"}

if __name__ == "__main__":
    port = int(os.environ.get("NBA_COPILOT_PORT", "18998"))
    # 安全报告 §十二 V2：python 端无鉴权，默认只绑本机回环——0.0.0.0 会让局域网
    # 绕过 java 层的认证/限流直连（实测局域网可达 200）。跨机部署用 COPILOT_HOST
    # 显式放开并配合网关/X-Internal-Token（与 rag_app 的 RAG_HOST 同型）。
    host = os.environ.get("COPILOT_HOST", "127.0.0.1")
    uvicorn.run(app, host=host, port=port)
