from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
import hashlib
import json
import math
import re
from typing import Any

TYPES = frozenset({'PLAYER', 'TEAM', 'MATCH', 'NEWS', 'POST'})
SCENES = frozenset({'SEARCH', 'HOME', 'NEWS_FEED', 'COMMUNITY_FEED', 'NEWS_DETAIL', 'POST_DETAIL'})
FEATURE_VERSION = 'discovery-v1'
FEATURE_NAMES = (
    'entity_interest', 'semantic_similarity', 'followed_entity', 'freshness', 'popularity',
    'quality', 'recent_exposures', 'recent_read', 'itemcf', 'channel_count', 'is_news',
    'is_detail', 'is_home',
)


def item_key(value: str) -> tuple[str, int]:
    match = re.fullmatch(r'(PLAYER|TEAM|MATCH|NEWS|POST):([1-9][0-9]{0,18})', value)
    if not match or int(match[2]) > 9223372036854775807:
        raise ValueError('Invalid item key')
    return match[1], int(match[2])


def timestamp(value: Any) -> float:
    if value is None or value == '':
        return 0.0
    if isinstance(value, (int, float)):
        return float(value)
    date = value if isinstance(value, datetime) else datetime.fromisoformat(str(value).replace('Z', '+00:00'))
    if date.tzinfo is None:
        date = date.replace(tzinfo=timezone.utc)
    return date.timestamp()


def unit(value: float) -> float:
    return max(0.0, min(1.0, value)) if math.isfinite(value) else 0.0


@dataclass
class Document:
    item_key: str
    title: str
    text: str = ''
    title_en: str = ''
    aliases: list[str] = field(default_factory=list)
    entities: list[str] = field(default_factory=list)
    team_keys: list[str] = field(default_factory=list)
    category: str = ''
    season: str = ''
    published_at: float = 0.0
    source: str = ''
    author_id: str = ''
    views: int = 0
    likes: int = 0
    favorites: int = 0
    featured: bool = False
    deleted: bool = False
    revision: int = 1

    def __post_init__(self):
        item_key(self.item_key)

    @property
    def kind(self) -> str:
        return self.item_key.split(':', 1)[0]

    def content_hash(self) -> str:
        # Counters and revisions must never cause redundant embedding work.
        text = json.dumps([self.title, self.title_en, self.text, self.aliases], ensure_ascii=False)
        return hashlib.sha256(text.encode()).hexdigest()

    def as_index(self) -> dict:
        return {
            'item_key': self.item_key, 'kind': self.kind, 'title': self.title,
            'title_en': self.title_en, 'text': self.text, 'aliases': self.aliases,
            'entities': self.entities, 'team_keys': self.team_keys, 'category': self.category,
            'season': self.season, 'published_at': int(self.published_at * 1000),
            'source': self.source, 'author_id': self.author_id, 'views': self.views,
            'likes': self.likes, 'favorites': self.favorites, 'featured': self.featured,
            'deleted': self.deleted, 'revision': self.revision, 'content_hash': self.content_hash(),
        }

    @classmethod
    def from_index(cls, source: dict) -> 'Document':
        fields = cls.__dataclass_fields__
        values = {key: value for key, value in source.items() if key in fields}
        values['published_at'] = float(source.get('published_at') or 0) / 1000
        return cls(**values)


@dataclass
class Candidate:
    document: Document
    channels: set[str] = field(default_factory=set)
    semantic: float = 0.0
    itemcf: float = 0.0


def features(candidate: Candidate, profile: dict, followed: set[str], scene: str, now: float) -> dict[str, float]:
    doc = candidate.document
    interests = profile.get('entities', {})
    interest = max((float(interests.get(key, 0)) for key in doc.entities), default=0.0)
    exposure = float(profile.get('exposures', {}).get(doc.item_key, 0))
    last_read = float(profile.get('reads', {}).get(doc.item_key, 0))
    hours = max(0, now - doc.published_at) / 3600
    return dict(zip(FEATURE_NAMES, (
        unit(interest / 10), unit(candidate.semantic), float(bool(followed.intersection(doc.entities))),
        math.exp(-hours / (72 if doc.kind == 'NEWS' else 168)) if doc.published_at else 0.0,
        unit(math.log1p(max(0, doc.views) + 3 * max(0, doc.likes) + 5 * max(0, doc.favorites)) / math.log(100001)),
        1.0 if doc.featured else 0.5, unit(exposure / 10),
        unit(1 - max(0, now - last_read) / 86400) if last_read else 0.0,
        unit(candidate.itemcf), unit(len(candidate.channels) / 5), float(doc.kind == 'NEWS'),
        float(scene.endswith('_DETAIL')), float(scene == 'HOME'),
    )))


def baseline_score(values: dict[str, float]) -> float:
    return (0.30 * values['entity_interest'] + 0.25 * values['semantic_similarity']
            + 0.15 * values['freshness'] + 0.15 * values['popularity']
            + 0.10 * values['followed_entity'] + 0.05 * values['quality']
            + 0.10 * values['itemcf'] - 0.20 * values['recent_exposures'] - 0.30 * values['recent_read'])


def reason(candidate: Candidate, values: dict[str, float]) -> str:
    if values['followed_entity']:
        return '与你关注的球队或球员有关'
    if values['entity_interest'] > 0:
        return '与你近期阅读的主题有关'
    if 'content' in candidate.channels and values['semantic_similarity'] > 0:
        return '与你读过的内容相似'
    if 'itemcf' in candidate.channels and values['itemcf'] > 0:
        return '有相似阅读兴趣的球迷也喜欢'
    if 'content_entities' in candidate.channels:
        return '与当前内容的球队或球员有关'
    if 'explore' in candidate.channels:
        return '发现新内容'
    return '近期热门内容'
