"""Exposure-derived, point-in-time learning-to-rank data. No synthetic negatives."""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
import math

from discovery_app.domain import FEATURE_NAMES, FEATURE_VERSION, timestamp


@dataclass(frozen=True)
class TrainingGate:
    days: int = 14
    impressions: int = 10_000
    positives: int = 500
    users: int = 100

    def check(self, samples: list[dict]) -> dict:
        users = {row['userId'] for row in samples}
        times = [row['servedAt'] for row in samples]
        span = (max(times) - min(times)) / 86400 if times else 0
        stats = {'impressions': len(samples), 'positives': sum(row['label'] > 0 for row in samples),
                 'users': len(users), 'days': span}
        reasons = [key for key in ('impressions', 'positives', 'users', 'days') if stats[key] < getattr(self, key)]
        return {'eligible': not reasons, 'stats': stats, 'unmet': reasons}


def build_samples(requests: list[dict], events: list[dict], *, now: float) -> list[dict]:
    grouped: dict[tuple, list[dict]] = defaultdict(list)
    seen = set()
    for event in events:
        if event['eventId'] in seen:
            continue
        seen.add(event['eventId'])
        grouped[(event.get('requestId'), event['itemKey'], event['userId'])].append(event)
    result = []
    for request in requests:
        served_at = timestamp(request['createdAt'])
        if served_at + 86400 > now or request.get('featureVersion') != FEATURE_VERSION:
            continue
        if request.get('context', {}).get('personalized') is False:
            continue
        if request['scene'] == 'SEARCH':
            continue  # Search relevance and feed engagement are distinct learning objectives.
        for position, item in enumerate(request['items']):
            values = item.get('features', {})
            if set(values) != set(FEATURE_NAMES):
                continue
            if any(not isinstance(value, (float, int)) or not math.isfinite(value) for value in values.values()):
                continue
            feedback = [event for event in grouped[(request['requestId'], item['itemKey'], request['userId'])]
                        if served_at <= timestamp(event['occurredAt']) <= served_at + 86400]
            if not any(event['eventType'] == 'IMPRESSION' for event in feedback):
                continue
            label = 0
            for event in feedback:
                kind = event['eventType']
                if kind == 'CLICK': label = max(label, 1)
                if kind == 'READ' and event.get('dwellMs', 0) >= 15000: label = max(label, 2)
                if kind in ('LIKE', 'FAVORITE'): label = 3
            result.append({'requestId': request['requestId'], 'itemKey': item['itemKey'], 'userId': request['userId'],
                           'servedAt': served_at, 'scene': request['scene'], 'position': position, 'label': label,
                           'featureVersion': FEATURE_VERSION, **{name: float(values[name]) for name in FEATURE_NAMES}})
    return result


def temporal_split(samples: list[dict]) -> tuple[list[dict], list[dict], list[dict]]:
    groups = defaultdict(list)
    for sample in samples:
        groups[sample['requestId']].append(sample)
    # Same-time requests remain together so no timestamp straddles a chronological split.
    by_time = defaultdict(list)
    for rows in groups.values():
        by_time[min(row['servedAt'] for row in rows)].extend(rows)
    times = sorted(by_time)
    if len(times) < 3:
        raise ValueError('At least three distinct serving times are required for train/validation/test')
    a = max(1, min(len(times) - 2, int(len(times) * .7)))
    b = max(a + 1, min(len(times) - 1, int(len(times) * .85)))
    return tuple([row for time in part for row in by_time[time]] for part in (times[:a], times[a:b], times[b:]))


def write_parquet(samples: list[dict], path: Path) -> None:
    import pyarrow as pa
    import pyarrow.parquet as pq
    path.parent.mkdir(parents=True, exist_ok=True)
    table = pa.Table.from_pylist(samples)
    temporary = path.with_suffix('.parquet.tmp')
    pq.write_table(table, temporary, compression='zstd')
    temporary.replace(path)
