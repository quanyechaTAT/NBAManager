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
    port = int(os.environ.get("NBA_COPILOT_PORT", "8898"))
    uvicorn.run(app, host="0.0.0.0", port=port)
