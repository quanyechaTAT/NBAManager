"""Shared interpretation of a game's opponents, date, season and league."""
from datetime import date
import re

FRESHNESS_KEYWORDS = re.compile(
    r'最新|今天|今日|昨天|明天|实时|目前|现在|刚刚|近期|最近|'
    r'新闻|资讯|交易|伤病|复出|签约|裁掉|官宣|报道称|报道|'
    r'夏季联赛|自由市场|自由球员|选秀结果|'
    r'\b(?:today|tomorrow|yesterday|tonight|live|current|latest|recent|news|injury|trade)\b', re.I)
CURRENT_GAME_KEYWORDS = re.compile(
    r'今天|今日|昨天|明天|正在|实时|今晚|目前|现在|'
    r'\b(?:today|tomorrow|yesterday|tonight|live|current)\b', re.I)


def needs_fresh_evidence(question: str) -> bool:
    # An explicit historical period takes precedence over "latest" in a retrospective question.
    return bool(FRESHNESS_KEYWORDS.search(question)) and not bool(re.search(r'\b(?:19|20)\d{2}\b|(?:19|20)\d{2}年', question))


def explicit_game_date(question: str) -> str:
    match = re.search(r'((?:19|20)\d{2})[年/-](\d{1,2})[月/-](\d{1,2})日?', question)
    if not match:
        return ''
    return date(*map(int, match.groups())).isoformat()


def game_context(question: str, teams: list[str]) -> dict:
    specified_date = explicit_game_date(question)
    season_match = re.search(r'((?:19|20)\d{2})[-/](\d{2,4})\s*(?:赛季|season)', question, re.I)
    season = ''
    if season_match:
        start, end = season_match.groups()
        season = f'{start}-{end[-2:]}'
    year_match = re.search(r'((?:19|20)\d{2})(?:年|\b)', question)
    year = int(year_match.group(1)) if year_match and not specified_date and not season else None
    historical = bool(teams) and (bool(specified_date or season or year) or not CURRENT_GAME_KEYWORDS.search(question))
    multiple = bool(re.search(r'历史交锋|历次|交手记录|最近\s*\d+场|近[一二两三四五六七八九十\d]+场|这几场|这些比赛', question))
    return {
        'teams': teams, 'date': specified_date, 'season': season, 'year': year,
        'historical': historical, 'multiple': multiple,
        'league': 'nba-summer' if re.search(r'夏季联赛|夏联|summer', question, re.I) else 'nba',
        'playoffs_only': bool(re.search(r'季后赛|总决赛|playoffs|finals', question, re.I)),
    }
