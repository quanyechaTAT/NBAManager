import sys
from pathlib import Path

import pytest
from pydantic import ValidationError

APP_ROOT = Path(__file__).resolve().parents[1] / "nba_copilot_app"
sys.path.insert(0, str(APP_ROOT))

from nba_copilot_app.api.routes import ChatRequest, MAX_MESSAGE_CHARS


def test_chat_request_rejects_message_over_limit():
    with pytest.raises(ValidationError):
        ChatRequest(message="x" * (MAX_MESSAGE_CHARS + 1))


def test_chat_request_accepts_message_at_limit():
    request = ChatRequest(message="x" * MAX_MESSAGE_CHARS)

    assert len(request.message) == MAX_MESSAGE_CHARS
