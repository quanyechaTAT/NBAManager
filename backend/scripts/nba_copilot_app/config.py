"""Configuration for NBA Copilot"""
import os

DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
DEEPSEEK_API_BASE = os.environ.get("DEEPSEEK_API_BASE", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-v4-flash")
NBA_MANAGER_API = os.environ.get("NBA_MANAGER_API", "http://localhost:8080")
NBA_COPILOT_PORT = int(os.environ.get("NBA_COPILOT_PORT", "8898"))
