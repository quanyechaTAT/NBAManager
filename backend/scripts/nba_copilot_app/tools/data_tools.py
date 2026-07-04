"""Tools for agents to fetch NBA data from the Java backend API"""
import json
import urllib.request
import urllib.error
from config import NBA_MANAGER_API

def _get(path: str, timeout: int = 30) -> dict | list | None:
    """Make GET request to NBA Manager backend"""
    try:
        url = f"{NBA_MANAGER_API}{path}"
        req = urllib.request.Request(url, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except (urllib.error.URLError, json.JSONDecodeError, Exception) as e:
        return None

def get_game_timeline(game_id: str) -> list:
    """Get play-by-play timeline for a game"""
    return _get(f"/api/ai-replay/games/{game_id}/timeline") or []

def get_game_momentum(game_id: str) -> list:
    """Get momentum data for a game"""
    return _get(f"/api/ai-replay/games/{game_id}/momentum") or []

def get_game_highlights(game_id: str) -> list:
    """Get highlights for a game"""
    return _get(f"/api/ai-replay/games/{game_id}/highlights") or []

def get_game_turning_points(game_id: str) -> list:
    """Get turning points for a game"""
    return _get(f"/api/ai-replay/games/{game_id}/turning-points") or []

def get_box_score(game_id: str) -> dict | None:
    """Get box score for a game"""
    return _get(f"/api/match-detail/{game_id}/boxscore")

def get_game_summary(game_id: str) -> dict | None:
    """Get AI summary for a game"""
    return _get(f"/api/ai-replay/games/{game_id}/summary")
