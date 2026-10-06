from urllib.parse import urlsplit
import json

_STAT_FIELDS = {'points_per_game', 'rebounds_per_game', 'assists_per_game', 'steals_per_game', 'blocks_per_game',
                'turnovers_per_game', 'minutes_per_game', 'games_played', 'field_goal_pct', 'three_point_pct',
                'free_throw_pct', 'true_shooting_pct', 'usage_pct', 'efficiency', 'per', 'bpm', 'vorp', 'win_shares',
                'offensive_rating', 'defensive_rating', 'wins', 'losses', 'home_score', 'away_score'}


def published_row(row):
    if row is None:
        return None
    # Non-statistical queries (news, drafts) are outside this publication contract.
    if 'data_field_states' not in row:
        return dict(row)
    try:
        states = json.loads(row.get('data_field_states') or '{}')
        if not isinstance(states, dict):
            states = {}
    except (ValueError, TypeError):
        states = {}
    result = dict(row)
    for key in _STAT_FIELDS & result.keys():
        field = ''.join(word if i == 0 else word.capitalize() for i, word in enumerate(key.split('_')))
        if row.get('data_quality') not in ('COMPLETE', 'PARTIAL') or states.get(field) not in ('OBSERVED', 'DERIVED'):
            result[key] = None
    return result


def source_metadata(row):
    return {'source': row.get('data_source') or 'UNKNOWN', 'source_url': row.get('data_source_url') or '',
            'season': row.get('data_season') or row.get('season') or '', 'synced_at': str(row.get('data_synced_at') or ''),
            'quality': row.get('data_quality') or 'UNVERIFIED'}


def source_details(metadatas):
    details = []
    for meta in metadatas or []:
        url = meta.get('source_url') or meta.get('url')
        if url and urlsplit(str(url)).scheme not in ('http', 'https'):
            url = None
        details.append({'title': meta.get('name') or meta.get('title') or meta.get('type') or '数据来源',
                        'url': url, 'source': meta.get('source') or 'UNKNOWN', 'season': meta.get('season'),
                        'syncedAt': meta.get('synced_at'), 'quality': meta.get('quality') or 'UNVERIFIED'})
    return details
