import copy
import json
from pathlib import Path

import pytest

from discovery_app.domain import Candidate, Document, FEATURE_NAMES, FEATURE_VERSION, features, item_key
from discovery_app.ranking import merge_channels, rank
from discovery_app.samples import TrainingGate, build_samples, temporal_split
from discovery_app.learning import experiment_assignment, ndcg, train

NOW = 1_800_000_000.0


def candidate(index, kind='NEWS', **kwargs):
    return Candidate(Document(f'{kind}:{index}', f'Content {index}', published_at=NOW - index * 60, **kwargs), {'hot'})


def test_identity_does_not_confuse_business_ids_with_external_match_ids():
    assert item_key('MATCH:123') == ('MATCH', 123)
    for value in ('MATCH:0', 'match:1', 'NEWS:1 OR 1=1', 'NEWS:9223372036854775808', 'NEWS:-1'):
        with pytest.raises(ValueError): item_key(value)


def test_content_hash_is_stable_when_only_popularity_changes():
    item = candidate(1).document
    before = item.content_hash()
    item.views = 10_000; item.revision += 1
    assert item.content_hash() == before
    item.text = 'Updated story'
    assert item.content_hash() != before


def test_channel_merge_deduplicates_and_preserves_real_signals():
    a, b = candidate(1), candidate(1)
    a.semantic = .8; b.itemcf = .6
    result = merge_channels({'content': [a], 'itemcf': [b]})
    assert len(result) == 1
    assert result[0].channels == {'content', 'itemcf'}
    assert result[0].semantic == .8 and result[0].itemcf == .6


def test_ranking_hard_filters_apply_to_model_and_exploration():
    hidden, deleted, other, visible = candidate(1), candidate(2, deleted=True), candidate(3, 'POST'), candidate(4)
    hidden.channels.add('explore')
    rows, used = rank([hidden, deleted, other, visible], profile={}, followed=set(), blocked={'NEWS:1'},
                     scene='NEWS_FEED', now=NOW, predict=lambda rows: [100.0] * len(rows))
    assert used and [row['itemKey'] for row in rows] == ['NEWS:4']


def test_invalid_model_scores_fall_back_and_explanations_are_grounded():
    item = candidate(1, entities=['TEAM:1']); item.channels = {'followed'}
    rows, used = rank([item], profile={}, followed={'TEAM:1'}, blocked=set(), scene='NEWS_FEED', now=NOW,
                     predict=lambda rows: [float('nan')])
    assert not used and '关注' in rows[0]['reason']
    rows, _ = rank([candidate(2)], profile={}, followed=set(), blocked=set(), scene='NEWS_FEED', now=NOW)
    assert rows[0]['reason'] == '近期热门内容'


def test_native_model_failure_preserves_the_rule_baseline():
    from lightgbm.basic import LightGBMError
    pool = [candidate(1), candidate(2, featured=True), candidate(3, entities=['TEAM:1'])]
    context = dict(profile={}, followed={'TEAM:1'}, blocked=set(), scene='NEWS_FEED', now=NOW)
    expected, _ = rank(pool, **context)
    def unavailable(values):
        raise LightGBMError('Inference unavailable')
    actual, used = rank(pool, **context, predict=unavailable)
    assert not used and actual == expected


def test_diversity_uses_alternatives_before_relaxing():
    pool = [candidate(i, 'POST', author_id=str(i // 8), team_keys=[f'TEAM:{i // 8}']) for i in range(1, 65)]
    rows, _ = rank(pool, profile={}, followed=set(), blocked=set(), scene='COMMUNITY_FEED', now=NOW, limit=10)
    ids = [int(row['itemKey'].split(':')[1]) for row in rows]
    assert len(ids) == 10 and len({value // 8 for value in ids}) >= 5


def test_home_mixes_content_and_pagination_scores_the_candidates_once():
    pool = [candidate(i) for i in range(1,81)] + [candidate(i, 'POST') for i in range(81,101)]
    batches=[]
    def predict(values):
        batches.append(len(values))
        return list(range(len(values),0,-1))
    rows, used = rank(pool,profile={},followed=set(),blocked=set(),scene='HOME',now=NOW,limit=6,pages=5,predict=predict)
    assert used and batches == [100] and len(rows) == len({row['itemKey'] for row in rows}) == 30
    assert all({row['itemKey'].split(':')[0] for row in rows[start:start+6]} == {'NEWS','POST'} for start in range(0,30,6))


def request(request_id='r1', created_at=NOW - 90000):
    value = features(candidate(1), {}, set(), 'NEWS_FEED', created_at)
    return {'requestId': request_id, 'userId': 1, 'createdAt': created_at, 'scene': 'NEWS_FEED',
            'featureVersion': FEATURE_VERSION, 'context': {'personalized': True},
            'items': [{'itemKey': 'NEWS:1', 'features': value}, {'itemKey': 'NEWS:2', 'features': value}]}


def event(kind, **kwargs):
    return {'eventId': kind, 'requestId': 'r1', 'userId': 1, 'itemKey': 'NEWS:1',
            'eventType': kind, 'occurredAt': NOW - 89000, **kwargs}


def test_only_real_exposure_gets_a_label_and_features_remain_point_in_time():
    req = request()
    original = copy.deepcopy(req)
    result = build_samples([req], [event('IMPRESSION'), event('CLICK'), event('READ', dwellMs=15000),
                                   event('FAVORITE', userId=2), event('FAVORITE', occurredAt=NOW)], now=NOW)
    assert len(result) == 1 and result[0]['label'] == 2
    assert result[0]['itemKey'] == 'NEWS:1'  # Unseen NEWS:2 is not a negative.
    assert result[0]['popularity'] == original['items'][0]['features']['popularity']
    assert req == original


def test_unfinished_windows_disabled_profiles_and_bad_feature_versions_do_not_train():
    unfinished = request(created_at=NOW - 1)
    disabled = request('r2'); disabled['context']['personalized'] = False
    incompatible = request('r3'); incompatible['featureVersion'] = 'future'
    assert build_samples([unfinished, disabled, incompatible], [event('IMPRESSION')], now=NOW) == []


def test_temporal_split_keeps_requests_and_equal_timestamps_together():
    samples = [{'requestId': f'r{i}', 'servedAt': i // 2, 'position': p} for i in range(20) for p in range(3)]
    training, validation, testing = temporal_split(samples)
    assert max(row['servedAt'] for row in training) < min(row['servedAt'] for row in validation)
    assert max(row['servedAt'] for row in validation) < min(row['servedAt'] for row in testing)
    assert set(row['requestId'] for row in training).isdisjoint(row['requestId'] for row in testing)
    assert sum(map(len, (training, validation, testing))) == len(samples)


def test_training_gate_does_not_claim_a_model_when_data_is_missing(tmp_path):
    assert train([], tmp_path)['status'] == 'INSUFFICIENT_DATA'
    assert not list(tmp_path.iterdir())


def test_fixed_user_buckets_are_nested_and_reject_unsupported_traffic():
    five = {user for user in range(1000) if experiment_assignment(user, 5)}
    twenty = {user for user in range(1000) if experiment_assignment(user, 20)}
    fifty = {user for user in range(1000) if experiment_assignment(user, 50)}
    assert five < twenty < fifty
    with pytest.raises(ValueError): experiment_assignment(1, 100)


def test_ndcg_compares_order_within_each_request():
    samples = [{'requestId': 'r', 'itemKey': str(i), 'label': label} for i, label in enumerate([0, 1, 3])]
    assert ndcg(samples, [0, 1, 3]) == 1.0
    assert ndcg(samples, [3, 1, 0]) < 1.0


def test_real_lambdarank_training_parquet_and_verified_online_inference(tmp_path):
    from discovery_app.learning import Model
    import pyarrow.parquet as pq
    # Deliberately synthetic fixture: verifies the pipeline, not a claim about actual user engagement.
    samples = []
    for group in range(60):
        for position in range(10):
            relevant = position % 3 == 0
            values = {name: 0.0 for name in FEATURE_NAMES}
            values.update(quality=float(relevant), popularity=float(not relevant), freshness=float(not relevant))
            samples.append({'requestId': f'r{group}', 'itemKey': f'NEWS:{position + 1}', 'userId': group + 1,
                            'servedAt': NOW - (60 - group) * 86400, 'scene': 'NEWS_FEED', 'position': position,
                            'featureVersion': FEATURE_VERSION, 'label': 3 if relevant else 0, **values})
    result = train(samples, tmp_path, gate=TrainingGate(days=1, impressions=100, positives=10, users=10))
    assert result['status'] == 'CANDIDATE'
    version = tmp_path / result['version']
    assert pq.read_table(version / 'samples.parquet').num_rows == 600
    model = Model(version)
    negative = [samples[1][name] for name in FEATURE_NAMES]
    positive = [samples[0][name] for name in FEATURE_NAMES]
    scores = model.predict([negative, positive])
    assert scores[1] > scores[0]
    with (version / 'model.txt').open('a') as stream: stream.write('\ncorrupt')
    with pytest.raises(ValueError, match='checksum'):
        Model(version)
