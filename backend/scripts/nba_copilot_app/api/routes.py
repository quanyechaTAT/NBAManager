"""API routes for NBA Copilot"""
import uuid
from fastapi import APIRouter
from pydantic import BaseModel
from agents.supervisor import classify_intent
from agents.replay_agent import handle as replay_handle
from agents.stats_agent import handle as stats_handle
from agents.general_agent import handle as general_handle

router = APIRouter(prefix="/api/nba-copilot")

# In-memory conversation store (replace with DB in production)
conversations: dict[str, dict] = {}

class ChatRequest(BaseModel):
    conversationId: str | None = None
    message: str
    context: dict | None = None

class ChatResponse(BaseModel):
    conversationId: str
    answer: str
    intent: str
    routedAgent: str
    citations: list[dict]

@router.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    # Get or create conversation
    conv_id = req.conversationId or str(uuid.uuid4())
    conv = conversations.get(conv_id, {"history": [], "active_game_id": None})

    # Update context
    if req.context and req.context.get("gameId"):
        conv["active_game_id"] = req.context["gameId"]

    # Classify intent
    intent = classify_intent(req.message)

    # Route to agent
    game_id = conv.get("active_game_id")

    if intent == "REPLAY":
        result = replay_handle(req.message, game_id)
    elif intent == "STATS":
        result = stats_handle(req.message)
    else:
        result = general_handle(req.message)

    # Update conversation
    conv["history"].append({"role": "user", "content": req.message})
    conv["history"].append({"role": "assistant", "content": result["answer"]})
    conversations[conv_id] = conv

    return ChatResponse(
        conversationId=conv_id,
        answer=result["answer"],
        intent=intent,
        routedAgent=result["agent"],
        citations=result.get("citations", [])
    )

@router.get("/conversations")
def list_conversations():
    return [{"id": k, "messageCount": len(v["history"])} for k, v in conversations.items()]

@router.get("/conversations/{conversation_id}")
def get_conversation(conversation_id: str):
    conv = conversations.get(conversation_id)
    if not conv:
        return {"error": "not found"}
    return conv

@router.delete("/conversations/{conversation_id}")
def delete_conversation(conversation_id: str):
    conversations.pop(conversation_id, None)
    return {"deleted": True}
