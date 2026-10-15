from __future__ import annotations

import json
import math
import time
from datetime import timezone
from typing import Callable

import redis
from discovery_app.database import Database
from discovery_app.domain import timestamp


class Profiles:
    def __init__(self, db: Database, cache: redis.Redis, index):
        self.db, self.cache, self.index = db, cache, index

    def get(self, user: int, revision: int) -> tuple[dict, bool]:
        key = f'discovery:profile:{user}:{revision}'
        try:
            value = self.cache.get(key)
            if value: return json.loads(value), False
            rows = self.db.rows('SELECT profile_json FROM discovery_profiles WHERE user_id=%s', (user,))
            profile = json.loads(rows[0]['profile_json']) if rows else {}
            self.cache.setex(key, 30, json.dumps(profile))
            return profile, False
        except redis.RedisError:
            return {}, True

    def consume(self, event: dict):
        user = event['user_id']
        docs = self.index.documents([event['item_key']])
        entities = docs[0].entities if docs else []
        with self.db.connection() as connection, connection.cursor() as cursor:
            cursor.execute('INSERT IGNORE INTO discovery_preferences(user_id,personalized,updated_at,revision) '
                           'VALUES(%s,TRUE,UTC_TIMESTAMP(6),0)', (user,))
            cursor.execute('SELECT * FROM discovery_preferences WHERE user_id=%s FOR UPDATE', (user,))
            preferences = cursor.fetchone()
            inserted = cursor.execute('INSERT IGNORE INTO discovery_processed_events(event_id,processed_at) '
                                      'VALUES(%s,UTC_TIMESTAMP(6))', (event['event_id'],))
            if not inserted:
                connection.commit(); return
            cleared = preferences['cleared_at']
            if not preferences['personalized'] or (cleared and event['received_at'] <= cleared):
                connection.commit(); return
            cursor.execute('SELECT profile_json FROM discovery_profiles WHERE user_id=%s FOR UPDATE', (user,))
            row = cursor.fetchone()
            profile = json.loads(row['profile_json']) if row else {}
            apply_event(profile, event, entities)
            cursor.execute('INSERT INTO discovery_profiles(user_id,profile_json,revision,updated_at) '
                           'VALUES(%s,%s,1,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE profile_json=VALUES(profile_json),'
                           'revision=revision+1,updated_at=VALUES(updated_at)', (user, json.dumps(profile)))
            connection.commit()
        try: self.cache.delete(f"discovery:profile:{user}:{preferences['revision']}")
        except redis.RedisError: pass  # The 30-second cache TTL bounds staleness after a failed invalidation.


def apply_event(profile: dict, event: dict, entities: list[str]):
    now = timestamp(event['received_at'])
    elapsed = max(0, now - float(profile.get('updated', now)))
    interests = {key: value * math.exp(-elapsed / (7 * 86400))
                 for key, value in profile.get('entities', {}).items() if value > .01}
    kind, key = event['event_type'], event['item_key']
    weight = {'CLICK': .5, 'READ': 1.0 if event.get('dwell_ms', 0) >= 15000 else 0,
              'LIKE': 2.0, 'FAVORITE': 3.0, 'UNLIKE': -2.0, 'UNFAVORITE': -3.0, 'DISMISS': -2.0}.get(kind, 0)
    for entity in entities: interests[entity] = max(0, min(20, interests.get(entity, 0) + weight))
    profile['entities'] = dict(sorted(interests.items(), key=lambda pair: -pair[1])[:100])
    reads = profile.setdefault('reads', {})
    exposures = profile.setdefault('exposures', {})
    exposure_times = profile.setdefault('exposureTimes', {})
    if kind == 'IMPRESSION':
        exposures[key] = min(10, exposures.get(key, 0) + 1); exposure_times[key] = now
    if kind == 'READ' and weight > 0: reads[key] = now
    profile['reads'] = dict(sorted(((key, value) for key, value in reads.items() if now-value < 7*86400),
                                  key=lambda pair: -pair[1])[:200])
    profile['exposureTimes'] = dict(sorted(((key, value) for key, value in exposure_times.items() if now-value < 86400),
                                         key=lambda pair: -pair[1])[:500])
    profile['exposures'] = {key: exposures[key] for key in profile['exposureTimes']}
    seeds = profile.setdefault('seeds', [])
    if weight > 0 and kind != 'CLICK': profile['seeds'] = [key] + [seed for seed in seeds if seed != key][:19]
    elif kind in ('UNLIKE', 'UNFAVORITE', 'DISMISS'): profile['seeds'] = [seed for seed in seeds if seed != key]
    profile['updated'] = max(now, float(profile.get('updated', 0)))


def build_itemcf(db: Database, cache: redis.Redis, *, check_cancel: Callable[[], None] | None = None):
    """Binary positive co-engagement, capped per user and time window; no invented impressions."""
    from collections import Counter, defaultdict
    checkpoint=check_cancel or (lambda: None)
    checkpoint()
    rows = db.rows("SELECT e.user_id,e.item_key FROM discovery_events e LEFT JOIN discovery_preferences p "
                   "ON p.user_id=e.user_id WHERE e.received_at>=UTC_TIMESTAMP()-INTERVAL 30 DAY "
                   "AND (p.personalized IS NULL OR p.personalized=TRUE) AND (p.cleared_at IS NULL OR e.received_at>p.cleared_at) "
                   "AND (e.event_type IN ('FAVORITE','LIKE') OR (e.event_type='READ' AND e.dwell_ms>=15000)) "
                   "ORDER BY e.received_at DESC LIMIT 200000")
    checkpoint()
    users, counts, pairs = defaultdict(set), Counter(), defaultdict(Counter)
    for number,row in enumerate(rows):
        if number%1000==0: checkpoint()
        if len(users[row['user_id']]) < 50: users[row['user_id']].add(row['item_key'])
    for number,items in enumerate(users.values()):
        if number%50==0: checkpoint()
        counts.update(items)
        weight = 1 / math.log2(2+len(items))
        for left in items:
            for right in items:
                if left != right: pairs[left][right] += weight
    generation = str(time.time_ns())
    pipeline = cache.pipeline(transaction=False)
    for number,(left, neighbors) in enumerate(pairs.items()):
        if number%1000==0: checkpoint()
        scores = sorted(((right, value / math.sqrt(counts[left]*counts[right])) for right, value in neighbors.items()),
                        key=lambda pair: (-pair[1], pair[0]))[:100]
        pipeline.setex(f'discovery:itemcf:{generation}:{left}', 2*86400, json.dumps(scores))
    checkpoint()
    pipeline.execute()
    # Uploaded generations remain inactive until this last cooperative cancellation point.
    checkpoint()
    cache.setex('discovery:itemcf:active', 2*86400, generation)
    return {'items': len(pairs), 'users': len(users), 'generation': generation}
