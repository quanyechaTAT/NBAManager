from __future__ import annotations

from collections import defaultdict
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
from typing import Callable
import uuid

from discovery_app.domain import FEATURE_NAMES, FEATURE_VERSION, baseline_score
from discovery_app.samples import TrainingGate, temporal_split, write_parquet


def ndcg(samples: list[dict], scores: list[float], at: int = 10) -> float:
    groups = defaultdict(list)
    for row, score in zip(samples, scores):
        groups[row['requestId']].append((row['label'], float(score), row['itemKey']))
    values = []
    for rows in groups.values():
        ideal = sorted((label for label, _, _ in rows), reverse=True)[:at]
        idcg = sum((2 ** label - 1) / math.log2(index + 2) for index, label in enumerate(ideal))
        if not idcg: continue
        predicted = sorted(rows, key=lambda row: (-row[1], row[2]))[:at]
        dcg = sum((2 ** label - 1) / math.log2(index + 2) for index, (label, _, _) in enumerate(predicted))
        values.append(dcg / idcg)
    return sum(values) / len(values) if values else 0.0


def _matrix(samples: list[dict]):
    import numpy as np
    ordered = sorted(samples, key=lambda row: (row['servedAt'], row['requestId'], row['position']))
    grouped = defaultdict(list)
    for row in ordered: grouped[row['requestId']].append(row)
    ordered = [row for group in grouped.values() for row in group]
    return (ordered, np.asarray([[row[name] for name in FEATURE_NAMES] for row in ordered], dtype='float64'),
            np.asarray([row['label'] for row in ordered]), [len(group) for group in grouped.values()])


def train(samples: list[dict], directory: Path, *, gate: TrainingGate | None = None,
          check_cancel: Callable[[], None] | None = None) -> dict:
    checkpoint=check_cancel or (lambda: None)
    checkpoint()
    decision = (gate or TrainingGate()).check(samples)
    if not decision['eligible']:
        return {'status': 'INSUFFICIENT_DATA', 'gate': decision}
    from lightgbm import LGBMRanker, early_stopping, log_evaluation
    partitions = temporal_split(samples)
    checkpoint()
    train_rows, train_x, train_y, train_groups = _matrix(partitions[0])
    checkpoint()
    validation_rows, validation_x, validation_y, validation_groups = _matrix(partitions[1])
    checkpoint()
    test_rows, test_x, _, _ = _matrix(partitions[2])
    checkpoint()
    informative = lambda rows: len({row['label'] for row in rows}) > 1
    grouped_train = defaultdict(list)
    for row in train_rows: grouped_train[row['requestId']].append(row)
    if not any(informative(rows) for rows in grouped_train.values()):
        return {'status': 'INSUFFICIENT_DATA', 'gate': decision, 'reason': 'No informative within-request ranking pairs'}
    model = LGBMRanker(objective='lambdarank', n_estimators=300, learning_rate=.05,
                      num_leaves=15, max_depth=6, min_child_samples=20, random_state=42, n_jobs=2, verbosity=-1)
    callbacks=[early_stopping(25, verbose=False), log_evaluation(0)]
    if check_cancel is not None:
        def cancel_iteration(_): checkpoint()
        cancel_iteration.before_iteration=True
        cancel_iteration.order=0
        callbacks.append(cancel_iteration)
    model.fit(train_x, train_y, group=train_groups, feature_name=list(FEATURE_NAMES),
              eval_set=[(validation_x, validation_y)], eval_group=[validation_groups], eval_at=[10],
              callbacks=callbacks)
    checkpoint()
    predictions = model.booster_.predict(test_x).tolist()
    validation_predictions = model.booster_.predict(validation_x).tolist()
    metrics = {
        'testNdcg10': ndcg(test_rows, predictions),
        'testBaselineNdcg10': ndcg(test_rows, [baseline_score(row) for row in test_rows]),
        'validationNdcg10': ndcg(validation_rows, validation_predictions),
        'validationBaselineNdcg10': ndcg(validation_rows, [baseline_score(row) for row in validation_rows]),
        'trainRows': len(train_rows), 'validationRows': len(validation_rows), 'testRows': len(test_rows),
    }
    ready = (metrics['testNdcg10'] > metrics['testBaselineNdcg10']
             and metrics['validationNdcg10'] > metrics['validationBaselineNdcg10'])
    checkpoint()
    version = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:8]
    destination = directory / version
    destination.mkdir(parents=True, exist_ok=False)
    artifact = destination / 'model.txt'
    # Python handles Unicode paths on Windows; LightGBM's native file API does not.
    artifact.write_text(model.booster_.model_to_string(), encoding='utf-8')
    metadata = {
        'version': version, 'status': 'CANDIDATE' if ready else 'REJECTED', 'featureVersion': FEATURE_VERSION,
        'featureNames': list(FEATURE_NAMES), 'artifact': 'model.txt',
        'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
        'trainedUntil': max(row['servedAt'] for row in samples), 'metrics': metrics, 'gate': decision,
    }
    (destination / 'manifest.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding='utf-8')
    write_parquet(samples, destination / 'samples.parquet')
    return metadata


class Model:
    """Only verified artifacts with the serving feature contract can enter online inference."""
    def __init__(self, directory: Path):
        from lightgbm import Booster
        self.metadata = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
        if self.metadata.get('featureVersion') != FEATURE_VERSION or self.metadata.get('featureNames') != list(FEATURE_NAMES):
            raise ValueError('Incompatible feature contract')
        artifact = (directory / self.metadata['artifact']).resolve()
        if not artifact.is_relative_to(directory.resolve()):
            raise ValueError('Model artifact must remain inside its version directory')
        if hashlib.sha256(artifact.read_bytes()).hexdigest() != self.metadata['sha256']:
            raise ValueError('Model checksum mismatch')
        self.booster = Booster(model_str=artifact.read_text(encoding='utf-8'))
        if self.booster.feature_name() != list(FEATURE_NAMES):
            raise ValueError('Artifact feature order does not match manifest')
        self.predict([[0.0] * len(FEATURE_NAMES)])  # Warm before atomic publication by the serving process.

    def predict(self, values: list[list[float]]) -> list[float]:
        import numpy as np
        return self.booster.predict(np.asarray(values, dtype='float64'), num_threads=2).tolist()


def experiment_assignment(user_id: int, traffic: int, experiment: str = 'discovery-v1') -> bool:
    if traffic not in (0, 5, 20, 50):
        raise ValueError('Allowed treatment percentages: 0, 5, 20, 50')
    bucket = int(hashlib.sha256(f'{experiment}:{user_id}'.encode()).hexdigest()[:8], 16) % 100
    return bucket < traffic
