from __future__ import annotations

from collections import Counter
import hashlib
import logging
import math
from typing import Callable

from discovery_app.domain import Candidate, FEATURE_NAMES, baseline_score, features, reason

log = logging.getLogger(__name__)


def merge_channels(channels: dict[str, list[Candidate]]) -> list[Candidate]:
    merged: dict[str, Candidate] = {}
    for channel, candidates in channels.items():
        for candidate in candidates[:100]:
            key = candidate.document.item_key
            if key not in merged:
                merged[key] = Candidate(candidate.document, set(), candidate.semantic, candidate.itemcf)
            target = merged[key]
            target.channels.add(channel)
            target.semantic = max(target.semantic, candidate.semantic)
            target.itemcf = max(target.itemcf, candidate.itemcf)
    return list(merged.values())


def rank(candidates: list[Candidate], *, profile: dict, followed: set[str], blocked: set[str],
         scene: str, now: float, limit: int = 20, seed: str = '',
         pages: int = 1,
         predict: Callable[[list[list[float]]], list[float]] | None = None) -> tuple[list[dict], bool]:
    allowed = {'NEWS'} if scene in ('NEWS_FEED', 'NEWS_DETAIL') else {'POST'} if scene in (
        'COMMUNITY_FEED', 'POST_DETAIL') else {'NEWS', 'POST'}
    rows = []
    seen = set()
    for candidate in candidates:
        doc = candidate.document
        if doc.deleted or doc.item_key in blocked or doc.kind not in allowed or doc.item_key in seen:
            continue
        seen.add(doc.item_key)
        values = features(candidate, profile, followed, scene, now)
        rows.append({'candidate': candidate, 'features': values, 'score': baseline_score(values)})
    rows.sort(key=lambda row: (-row['score'], row['candidate'].document.item_key))
    rows = rows[:300]
    refinement = rows[:100]
    used_model = False
    if predict and refinement:
        try:
            scores = list(predict([[row['features'][name] for name in FEATURE_NAMES] for row in refinement]))
            if len(scores) != len(refinement) or not all(math.isfinite(float(value)) for value in scores):
                raise ValueError('Invalid ranking predictions')
            for row, score in zip(refinement, scores):
                row['score'] = float(score)
            used_model = True
        except Exception:
            # Native library errors (including LightGBMError) must not escape to SQL fallback.
            log.warning('Model inference failed; keeping the rule baseline', exc_info=True)
            used_model = False
    refinement.sort(key=lambda row: (-row['score'], row['candidate'].document.item_key))
    limit = max(1, min(20, limit))
    exploration_count = max(1, math.ceil(limit * 0.1))
    exploration = [row for row in rows if 'explore' in row['candidate'].channels
                   and row['features']['recent_exposures'] == 0
                   and row['features']['recent_read'] == 0]
    exploration.sort(key=lambda row: hashlib.sha256(
        (seed + row['candidate'].document.item_key).encode()).hexdigest())
    selected, emitted = [], set()
    for _ in range(max(1, min(5, pages))):
        reserved = [row for row in exploration if row['candidate'].document.item_key not in emitted][:exploration_count]
        reserved_keys = {row['candidate'].document.item_key for row in reserved}
        ordered = [row for row in refinement if row['candidate'].document.item_key not in emitted | reserved_keys]
        pivot = min(max(0, limit - len(reserved)), len(ordered))
        ordered[pivot:pivot] = reserved
        authors: Counter = Counter()
        teams: Counter = Counter()
        page, deferred = [], []
        for row in ordered:
            doc = row['candidate'].document
            main_team = doc.team_keys[0] if doc.team_keys else ''
            if (doc.author_id and authors[doc.author_id] >= 2) or (main_team and teams[main_team] >= 5):
                deferred.append(row)
                continue
            page.append(row)
            if doc.author_id: authors[doc.author_id] += 1
            if main_team: teams[main_team] += 1
            if len(page) >= limit: break
        if len(page) < limit: page.extend(deferred[:limit-len(page)])
        if scene == 'HOME' and limit >= 2:
            for kind in ('NEWS', 'POST'):
                if not any(row['candidate'].document.kind == kind for row in page):
                    alternative = next((row for row in ordered if row['candidate'].document.kind == kind), None)
                    if alternative:
                        if len(page) == limit: page[-1] = alternative
                        else: page.append(alternative)
        if not page: break
        selected.extend(page)
        emitted.update(row['candidate'].document.item_key for row in page)
    return [{
        'itemKey': row['candidate'].document.item_key,
        'reason': reason(row['candidate'], row['features']),
        'features': row['features'],
        'channels': sorted(row['candidate'].channels),
        'score': row['score'],
    } for row in selected], used_model
