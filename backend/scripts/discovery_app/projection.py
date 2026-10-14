"""Canonical SQL projection; CDC payloads are invalidations, never authoritative replacements."""
from __future__ import annotations

from datetime import date, datetime
import re
import time
from zoneinfo import ZoneInfo

from discovery_app.database import Database
from discovery_app.domain import Document, item_key

TABLES = {'PLAYER': 'players', 'TEAM': 'teams', 'MATCH': 'match_records', 'NEWS': 'game_news', 'POST': 'posts'}
KINDS = {table: kind for kind, table in TABLES.items()}
COMMON_ALIASES = {
    'LeBron James': ['詹姆斯', '勒布朗', '老詹', 'LBJ'], 'Stephen Curry': ['库里', '斯蒂芬库里', '萌神'],
    'Kevin Durant': ['杜兰特', 'KD'], 'Luka Doncic': ['东契奇'], 'Nikola Jokic': ['约基奇', '约老师'],
    'Giannis Antetokounmpo': ['字母哥', '扬尼斯'], 'Victor Wembanyama': ['文班亚马', '文班'],
}


def source_time(value) -> float:
    if not value: return 0.0
    if isinstance(value, date) and not isinstance(value, datetime): value = datetime.combine(value, datetime.min.time())
    if not isinstance(value, datetime): value = datetime.fromisoformat(str(value))
    if value.tzinfo is None: value = value.replace(tzinfo=ZoneInfo('Asia/Shanghai'))
    return value.timestamp()


def plain(value, limit=10000) -> str:
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]*>', ' ', str(value or '')))[:limit].strip()


class Projection:
    def __init__(self, database: Database):
        self.db = database
        self.loaded = 0.0
        self.aliases: dict[str, list[str]] = {}
        self.teams: dict[str, str] = {}

    def refresh_entities(self, force=False):
        if not force and time.monotonic() - self.loaded < 30: return
        aliases, teams = {}, {}
        for row in self.db.rows('SELECT id,name,name_en,abbreviation FROM teams'):
            key = f"TEAM:{row['id']}"
            aliases[key] = [str(row[name]) for name in ('name', 'name_en', 'abbreviation') if row.get(name)]
            for alias in aliases[key]: teams[alias.casefold()] = key
        for row in self.db.rows('SELECT id,name,name_en FROM players'):
            key = f"PLAYER:{row['id']}"
            aliases[key] = [str(row[name]) for name in ('name', 'name_en') if row.get(name)]
            aliases[key] += COMMON_ALIASES.get(row.get('name_en'), [])
        for row in self.db.rows('SELECT item_key,alias FROM discovery_entity_aliases'):
            aliases.setdefault(row['item_key'], []).append(row['alias'])
        self.aliases, self.teams, self.loaded = aliases, teams, time.monotonic()

    def project(self, key: str, cursor=None) -> Document | None:
        kind, identifier = item_key(key)
        if cursor is None:
            rows = self.db.rows(f'SELECT * FROM {TABLES[kind]} WHERE id=%s', (identifier,))
        else:
            cursor.execute(f'SELECT * FROM {TABLES[kind]} WHERE id=%s', (identifier,)); rows = cursor.fetchall()
        if not rows: return None
        self.refresh_entities()
        row = rows[0]
        doc = Document(key, '', revision=1)
        doc.aliases = list(dict.fromkeys(self.aliases.get(key, [])))
        doc.category = str(row.get('category') or '')
        doc.season = str(row.get('season') or '')
        doc.views = row.get('view_count') or 0
        doc.likes = row.get('like_count') or 0
        doc.favorites = row.get('favorite_count') or 0
        doc.featured = bool(row.get('is_featured'))
        doc.published_at = source_time(row.get('published_at') or row.get('match_date') or row.get('create_time'))
        if kind in ('PLAYER', 'TEAM'):
            doc.title, doc.title_en = row['name'], row.get('name_en') or ''
            doc.entities = [key]
            if kind == 'PLAYER': doc.team_keys = [f"TEAM:{row['team_id']}"]
            else: doc.team_keys = [key]
            doc.entities = list(dict.fromkeys(doc.entities + doc.team_keys))
            doc.text = ' '.join(str(row.get(name) or '') for name in ('city', 'conference', 'position'))
        elif kind == 'MATCH':
            doc.title = f"{row['home_team']} vs {row['away_team']}"
            doc.title_en = f"{row.get('home_team_en') or ''} vs {row.get('away_team_en') or ''}"
            doc.text = f"{row['match_date']} {doc.season} {row.get('status') or ''}"
            doc.team_keys = list(dict.fromkeys(self.teams[value.casefold()] for name in ('home_team', 'away_team', 'home_team_en', 'away_team_en')
                                              if (value := row.get(name)) and value.casefold() in self.teams))
            doc.entities = doc.team_keys.copy()
        else:
            doc.title, doc.title_en = row['title'], row.get('title_en') or ''
            doc.text = plain(row.get('content'))
            doc.source = row.get('source_url') or ''
            doc.author_id = str(row['user_id']) if kind == 'POST' else ''
            text = (doc.title + ' ' + doc.title_en + ' ' + doc.text).casefold()
            for entity, aliases in self.aliases.items():
                if any(len(alias) > 1 and (re.search(r'(?<![a-z])' + re.escape(alias.casefold()) + r'(?![a-z])', text)
                                          if alias.isascii() else alias.casefold() in text) for alias in aliases):
                    doc.entities.append(entity)
            doc.entities = doc.entities[:64]
            doc.team_keys = [entity for entity in doc.entities if entity.startswith('TEAM:')]
            doc.aliases = list(dict.fromkeys(alias for entity in doc.entities for alias in self.aliases.get(entity, [])))[:100]
        return doc

    def keys(self, kind: str, after: int = 0, size: int = 500) -> list[str]:
        if kind not in TABLES: raise ValueError('Invalid kind')
        return [f"{kind}:{row['id']}" for row in self.db.rows(
            f'SELECT id FROM {TABLES[kind]} WHERE id>%s ORDER BY id LIMIT %s', (after, min(size, 500)))]
