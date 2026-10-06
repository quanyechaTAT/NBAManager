from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from data_integrity import display_metric, optional_number, per_game, player_metrics
from rag_app.structured.provenance import source_details, published_row


def test_observed_zero_and_legitimate_usage_are_preserved():
    values = player_metrics({'PTS': 0, 'FG_PCT': .5}, {'USG_PCT': .2, 'E_OFF_RATING': 110})
    assert values['ppg'] == 0
    assert values['usgPct'] == 20
    assert values['tsPct'] is None
    assert values['per'] is None
    assert values['gp'] is None


def test_missing_and_invalid_values_never_become_zero():
    for value in (None, '', True, float('nan'), float('inf'), 'unknown'):
        assert optional_number(value) is None
    assert optional_number(0) == 0
    assert per_game(10, None) is None
    assert per_game(10, 0) is None
    assert per_game(0, 10) == 0


def test_rag_representation_matches_frontend_units_and_missing_states():
    assert display_metric(0) == '0'
    assert display_metric(None).startswith('—')
    assert display_metric(.5, percentage=True) == '50.0%'
    assert display_metric(20) == '20'


def test_source_links_are_real_safe_and_unknown_provenance_stays_unknown():
    result = source_details([{'title': 'NBA', 'source_url': 'https://www.nba.com/stats', 'source': 'NBA_API', 'season': '2025-26'}, {'title': '未知', 'url': 'javascript:alert(1)'}])
    assert result[0]['url'] == 'https://www.nba.com/stats'
    assert result[1]['url'] is None
    assert result[1]['source'] == 'UNKNOWN'
    assert result[1]['quality'] == 'UNVERIFIED'


def test_field_publication_is_identical_in_rag_and_domain():
    row = {'data_quality': 'PARTIAL', 'data_field_states': '{"usagePct":"OBSERVED","pointsPerGame":"OBSERVED","per":"UNVERIFIED"}', 'usage_pct': 20, 'points_per_game': 0, 'per': 110}
    assert published_row(row)['usage_pct'] == 20
    assert published_row(row)['points_per_game'] == 0
    assert published_row(row)['per'] is None
    assert published_row({**row, 'data_quality': 'UNVERIFIED'})['usage_pct'] is None
    assert published_row({**row, 'data_field_states': 'invalid'})['points_per_game'] is None
