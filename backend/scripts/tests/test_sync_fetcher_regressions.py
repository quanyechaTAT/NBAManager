"""Offline tests of the same provider paths used by the manual sync workers."""
import importlib
import json
import os
import sys
from pathlib import Path
from unittest.mock import Mock

import pytest
import requests

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


@pytest.fixture
def fetcher(monkeypatch):
    monkeypatch.setenv('NBA_PROXY_HOST', '127.0.0.1')
    monkeypatch.setenv('NBA_PROXY_PORT', '7890')
    monkeypatch.setattr('socket.create_connection', lambda *a, **k: Mock())
    import nba_data_fetcher
    f = importlib.reload(nba_data_fetcher)
    monkeypatch.setattr(f, '_get_current_season', lambda: '2025-26')
    monkeypatch.setattr(f.time, 'sleep', lambda _: None)
    return f


def response(data, url):
    r = requests.Response()
    r.status_code = 200
    r._content = json.dumps(data).encode()
    r.url = url
    return r


def test_nba_requests_retry_direct_when_proxy_breaks_after_startup(fetcher, monkeypatch):
    attempts = []
    def send(session, request, **kwargs):
        attempts.append(kwargs.get('proxies'))
        if kwargs.get('proxies'):
            raise requests.exceptions.ProxyError('fixture: proxy tunnel is broken')
        return response({'resultSets': [{'name': 'Standings', 'headers':
            ['TeamID', 'TeamCity', 'TeamName', 'Conference', 'WINS', 'LOSSES'],
            'rowSet': [[1610612738, 'Boston', 'Celtics', 'East', 60, 22]]}]}, request.url)
    monkeypatch.setattr(requests.Session, 'send', send)
    teams = fetcher.fetch_team_standings()
    assert len(teams) == 1, 'manual teams sync lost usable direct data after proxy failure'
    assert attempts[0] and not attempts[-1]


def test_direct_mode_does_not_inherit_windows_or_environment_proxy(fetcher, monkeypatch):
    monkeypatch.setenv('NBA_PROXY_HOST', '')
    monkeypatch.setenv('NBA_PROXY_PORT', '')
    monkeypatch.setenv('HTTPS_PROXY', 'http://unrelated-proxy.invalid:3128')
    importlib.reload(fetcher)
    from nba_api.stats.library.http import NBAStatsHTTP
    assert NBAStatsHTTP.get_session().trust_env is False
    assert os.environ['HTTPS_PROXY'] == 'http://unrelated-proxy.invalid:3128'


def test_player_sync_uses_bulk_biography_and_never_per_player_requests(fetcher, monkeypatch):
    from nba_api.stats import endpoints
    base = {'resultSets': [{'headers': ['PLAYER_ID', 'PLAYER_NAME', 'TEAM_ID', 'GP', 'PTS'],
        'rowSet': [[2544, 'LeBron James', 1610612747, 70, 25.1]]}]}
    monkeypatch.setattr(fetcher, 'LeagueDashPlayerStats', Mock(return_value=Mock(get_dict=lambda: base)))
    index = {'resultSets': [{'headers': ['PERSON_ID', 'POSITION', 'JERSEY_NUMBER', 'HEIGHT', 'WEIGHT', 'COUNTRY'],
        'rowSet': [[2544, 'F', '23', '6-9', '250', 'USA']]}]}
    bulk = Mock(return_value=Mock(get_dict=lambda: index))
    detail = Mock(side_effect=AssertionError('per-player calls exceed worker deadline'))
    monkeypatch.setattr(endpoints, 'PlayerIndex', bulk)
    monkeypatch.setattr(endpoints, 'CommonPlayerInfo', detail)
    players = fetcher.fetch_player_stats()
    assert players[0]['height'] == '6-9'
    assert players[0]['jersey'] == '23'
    bulk.assert_called_once()
    detail.assert_not_called()


def test_recent_games_reports_total_provider_failure(fetcher, monkeypatch):
    monkeypatch.setattr(fetcher, 'ScoreboardV3', Mock(side_effect=RuntimeError('fixture: upstream offline')))
    with pytest.raises(Exception, match='upstream offline'):
        fetcher.fetch_recent_games()


def test_player_fetch_does_not_call_translation_api_for_new_names(fetcher, monkeypatch):
    from nba_api.stats import endpoints
    base = {'resultSets': [{'headers': ['PLAYER_ID', 'PLAYER_NAME', 'TEAM_ID', 'GP'],
        'rowSet': [[999999, 'Fixture Rookie', 1610612747, 1]]}]}
    monkeypatch.setattr(fetcher, 'LeagueDashPlayerStats', Mock(return_value=Mock(get_dict=lambda: base)))
    monkeypatch.setattr(endpoints, 'PlayerIndex', Mock(return_value=Mock(get_dict=lambda: {'resultSets': []})))
    monkeypatch.setattr(endpoints, 'CommonPlayerInfo', Mock(side_effect=RuntimeError('offline')))
    translate = Mock(return_value='Fixture Rookie')
    monkeypatch.setattr(fetcher, 'translate_text', translate)
    players = fetcher.fetch_player_stats()
    assert players[0]['translationStatus'] == 'UNTRANSLATED'
    translate.assert_not_called()


def test_unavailable_proxy_really_switches_to_direct(fetcher, monkeypatch):
    monkeypatch.setattr('socket.create_connection', Mock(side_effect=OSError('proxy offline')))
    importlib.reload(fetcher)
    assert fetcher.PROXY_URL == ''
    assert fetcher.network.trust_env is False


def test_full_proxy_url_does_not_require_separate_port():
    from sync_network import build_proxy_url
    assert build_proxy_url('"http://127.0.0.1:7890"') == 'http://127.0.0.1:7890'
    assert build_proxy_url('127.0.0.1', '7890') == 'http://127.0.0.1:7890'


def test_nba_playoff_batch_pairs_home_and_away_rows(fetcher, monkeypatch):
    from nba_api.stats import endpoints
    data = {'resultSets': [{'headers': ['GAME_ID', 'TEAM_ID', 'MATCHUP', 'PTS', 'WL', 'GAME_DATE'],
        'rowSet': [['0042500401', 1610612759, 'SAS vs. NYK', 100, 'L', '2026-06-04'],
                   ['0042500401', 1610612752, 'NYK @ SAS', 110, 'W', '2026-06-04']]}]}
    endpoint = Mock(return_value=Mock(get_dict=lambda: data))
    monkeypatch.setattr(endpoints, 'LeagueGameFinder', endpoint)
    fetcher._init_team_map()
    games = fetcher.fetch_nba_playoff_games('2025-26')
    assert len(games) == 1
    assert games[0]['homeScore'] == 100 and games[0]['awayScore'] == 110
    assert games[0]['homeTeam'] == '马刺' and games[0]['awayTeam'] == '尼克斯'


def test_playoff_provider_outage_never_publishes_zero_win_bracket(fetcher, monkeypatch):
    monkeypatch.setattr(fetcher, 'fetch_playoff_series', Mock(return_value=[{'seriesId': 'fixture'}]))
    monkeypatch.setattr(fetcher, 'fetch_nba_playoff_games', Mock(side_effect=RuntimeError('NBA offline')))
    monkeypatch.setattr(fetcher, 'fetch_espn_playoff_games', Mock(side_effect=RuntimeError('ESPN offline')))
    result = fetcher.fetch_playoff_bracket('2025-26')
    assert result['sourceStatus'] == 'failed' and result['matchups'] == []


def test_draft_snapshot_is_not_reported_as_a_fresh_sync(fetcher, monkeypatch):
    monkeypatch.setattr(fetcher, '_fetch_draft_from_nba_stats', lambda y: (None, 'offline'))
    monkeypatch.setattr(fetcher, 'HAS_DRAFT_API', False)
    monkeypatch.setattr(fetcher, '_fetch_draft_from_nba_news_order', lambda y: (None, 'offline'))
    monkeypatch.setattr(fetcher, '_fetch_draft_from_nba_com_tracker', lambda y: (None, 'offline'))
    monkeypatch.setattr(fetcher, '_load_draft_snapshot', lambda y: [{'year': y, 'pickNumber': 1}])
    result = fetcher.fetch_draft_history(2026)
    assert result['sourceStatus'] == 'stale'
    assert len(result['draftPicks']) == 1
