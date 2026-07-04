"""Configuration for NBA Copilot"""
import os

MIMO_API_KEY = os.environ.get("MIMO_API_KEY", "")
MIMO_BASE_URL = os.environ.get("MIMO_BASE_URL", "https://token-plan-cn.xiaomimimo.com/v1")
MIMO_MODEL = os.environ.get("MIMO_MODEL", "mimo-v2.5-pro")
NBA_MANAGER_API = os.environ.get("NBA_MANAGER_API", "http://localhost:8080")
NBA_COPILOT_PORT = int(os.environ.get("NBA_COPILOT_PORT", "8898"))
