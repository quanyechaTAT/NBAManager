"""Archive queries must match both opponents and the requested NBA game day."""
import json
import threading
from rag_app.tools.game_archive import EspnArchiveProvider
from rag_app.pipeline.game_scope import game_context
from rag_app.api.sse import stream_with_heartbeats


def event(game_id='101', opponent='24', game_date='2024-12-25T18:00Z', completed=True, score='114'):
    return {'id': game_id, 'date': game_date, 'competitions': [{
        'status': {'type': {'completed': completed}},
        'competitors': [
            {'team': {'id': '18'}, 'homeAway': 'home', 'score': '117'},
            {'team': {'id': opponent}, 'homeAway': 'away', 'score': score},
        ],
    }]}


def test_archive_filters_other_opponents_future_games_missing_scores_and_other_dates():
    provider = EspnArchiveProvider()
    payload = {'events': [event(), event('102', opponent='13'), event('103', completed=False),
                          event('104', score=None), event('105', game_date='2024-12-24T18:00Z')]}
    provider._fetch_json = lambda path: payload if 'scoreboard' in path else {}
    scope = game_context('分析2024年12月25日马刺和尼克斯的比赛', ['马刺', '尼克斯'])
    results = provider.search('historical NBA game', context=scope)
    assert len(results) == 1 and results[0].facts['game_id'] == '101'
    assert results[0].facts['away_score'] == 114


def test_explicit_date_does_not_substitute_a_neighbouring_game():
    provider = EspnArchiveProvider()
    provider._fetch_json = lambda path: {'events': [event()]}
    scope = game_context('分析2024年12月24日马刺和尼克斯的比赛', ['马刺', '尼克斯'])
    assert provider.search('historical NBA game', context=scope) == []


def test_scoreboard_season_metadata_preserves_playoffs_and_season():
    provider = EspnArchiveProvider()
    game = event()
    game['season'] = {'year': 2025, 'type': 3, 'slug': 'post-season'}
    scope = game_context('分析2024年12月25日马刺和尼克斯的比赛', ['马刺', '尼克斯'])
    fact = provider._fact(game, scope)
    assert fact['game_type'] == 'PLAYOFFS'
    assert fact['season'] == '2024-25'


def test_heartbeat_is_emitted_while_generation_waits_and_cancellation_closes_the_iterator():
    release = threading.Event()
    closed = threading.Event()
    def events():
        try:
            yield {'type': 'chunk', 'content': 'first'}
            release.wait(1)
            yield {'type': 'done'}
        finally:
            closed.set()
    stream = stream_with_heartbeats(events(), interval=0.01)
    first = next(stream)
    assert json.loads(first.removeprefix('data: ').strip())['content'] == 'first'
    assert next(stream) == ': heartbeat\n\n'
    stream.close()
    release.set()
    assert closed.wait(1)
