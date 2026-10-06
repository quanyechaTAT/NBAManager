"""End-to-end seams for historical matchup evidence and contextual follow-ups."""
from contextlib import contextmanager
import json
import sqlite3

from test_rag_engine_regressions import _engine, _StructuredStub
from rag_app.pipeline.query_classifier import QueryClassifier, QueryType
from rag_app.structured.repository import StructuredRepository
from rag_app.tools.search_tool import SearchExecution, SearchResult
from rag_app.llm.client import LLMClient
from rag_app.pipeline.follow_ups import build_follow_ups
from test_rag_engine_regressions import _settings
import pytest
from types import SimpleNamespace


class MatchDatabase:
    def __init__(self):
        self.connection = sqlite3.connect(':memory:')
        self.connection.row_factory = sqlite3.Row
        self.connection.create_function('JSON_UNQUOTE', 1, lambda value: value)
        self.connection.create_function('JSON_VALID', 1, lambda value: int(bool(value)))
        self.connection.execute('''CREATE TABLE match_records (
            id INTEGER, nba_game_id TEXT, home_team TEXT, away_team TEXT,
            home_score INTEGER, away_score INTEGER, match_date TEXT, season TEXT,
            status TEXT, game_type TEXT, data_quality TEXT, data_field_states TEXT,
            data_source TEXT, data_source_url TEXT, data_synced_at TEXT)''')
        states = json.dumps({'homeScore': 'OBSERVED', 'awayScore': 'OBSERVED'})
        for row in [
            (1, 'game-a', '尼克斯', '马刺', 117, 114, '2024-12-25', '2024-25'),
            (2, 'game-b', '马刺', '勇士', 100, 99, '2025-02-01', '2024-25'),
            (3, 'game-c', '尼克斯', '湖人', 99, 105, '2025-02-02', '2024-25'),
        ]:
            self.connection.execute('INSERT INTO match_records VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
                (*row, 'FINISHED', 'REGULAR_SEASON', 'COMPLETE', states, 'TEST', '', '2025-02-03'))

    @contextmanager
    def cursor(self):
        connection = self.connection
        class Cursor:
            def execute(self, sql, params=()):
                self.result = connection.execute(sql.replace('%s', '?'), params)
            def fetchall(self):
                return [dict(row) for row in self.result.fetchall()]
            def fetchone(self):
                row = self.result.fetchone()
                return dict(row) if row else None
        yield Cursor()


def test_two_team_analysis_is_a_match_even_when_performance_is_mentioned():
    classified = QueryClassifier().classify('分析马刺和尼克斯比赛中的表现')
    assert classified.query_type == QueryType.MATCH


def test_head_to_head_does_not_return_each_teams_unrelated_latest_game():
    repository = StructuredRepository(MatchDatabase())
    text = repository.match_summary(['马刺', '尼克斯'], 1, '分析马刺和尼克斯的比赛')
    assert '2024-12-25' in text
    assert '勇士' not in text and '湖人' not in text


def test_explicit_historical_date_is_respected():
    repository = StructuredRepository(MatchDatabase())
    assert not repository.match_summary(['马刺', '尼克斯'], 5, '分析2024年12月24日马刺和尼克斯的比赛')


class ArchiveSearch:
    def __init__(self):
        self.calls = []
    def search(self, query, limit=5, context=None):
        self.calls.append((query, context))
        result = SearchResult(title='Spurs vs Knicks', url='https://www.espn.com/nba/game/_/gameId/test',
            snippet='2024-12-25 Spurs 114 - 117 Knicks (Final)', provider='espn_archive',
            facts={'date': '2024-12-25', 'home_team': '尼克斯', 'away_team': '马刺',
                   'home_score': 117, 'away_score': 114, 'season': '2024-25'})
        return [result], SearchExecution()


class Model:
    def complete(self, prompt, max_tokens=None):
        return '2024-12-25 尼克斯117:114马刺，分差3分；缺少回合数据，不能确认胜负的战术原因。'
    def stream(self, prompt, max_tokens=None):
        yield '2024-12-25 尼克斯117:114马刺，'
        yield '分差3分，缺少回合数据。'


def test_missing_local_match_evidence_uses_historical_search_before_refusal():
    search = ArchiveSearch()
    engine = _engine(_StructuredStub(), search_service=search, llm=Model())
    result = engine.ask('分析马刺和尼克斯的比赛')
    assert result.success and '数据不足，无法判断' not in result.answer
    assert search.calls and search.calls[0][1]['historical']


def test_match_stat_question_does_not_fall_back_to_unrelated_league_leaders():
    engine = _engine(_StructuredStub(keyword='联盟三分榜：库里42%'), search_service=ArchiveSearch(), llm=Model())
    prepared = engine.prepare('2024年12月25日马刺和尼克斯的比赛：双方三分命中率是多少？', 5)
    assert prepared['structured_text'] == ''


def test_done_event_contains_specific_evidence_bounded_follow_ups():
    engine = _engine(_StructuredStub(primary='2024-12-25 尼克斯（主）117:114马刺（客），尼克斯获胜。'), llm=Model())
    events = list(engine.stream('分析马刺和尼克斯的比赛', session_id='match-session'))
    questions = events[-1].get('follow_ups', [])
    assert 1 <= len(questions) <= 3
    assert all('马刺' in item and '尼克斯' in item and '2024-12-25' in item for item in questions)
    assert not any('胜率' in item or '高阶' in item or '轮换得分' in item for item in questions)


def test_match_follow_up_recovers_both_teams_and_the_selected_game_date():
    engine = _engine(_StructuredStub())
    engine.memory.add('match-session', '分析马刺和尼克斯的比赛', '查询到2024-12-25尼克斯117:114马刺。')
    resolved = engine._resolve_question('这场比赛的分差说明了什么？', 'match-session')
    assert '马刺' in resolved and '尼克斯' in resolved and '2024-12-25' in resolved


def test_explicit_new_game_date_overrides_previous_match_context():
    engine = _engine(_StructuredStub())
    engine.memory.add('match-session', '分析2026年6月13日马刺和尼克斯的比赛', '2026-06-13尼克斯94:90马刺。')
    question = '2024-12-25马刺和尼克斯的比赛：双方篮板有哪些差异？'
    assert engine._resolve_question(question, 'match-session') == question


def test_follow_ups_move_to_new_supported_topics_after_a_stat_comparison():
    metrics = {'篮板': '45', '进攻篮板': '8', '失误': '15', '投篮命中率%': '51',
               '三分命中率%': '42', '罚球命中-出手': '14-17', '助攻': '33', '禁区得分': '50'}
    result = SearchResult(title='Match', url='https://www.espn.com/nba/game/_/gameId/test', snippet='', provider='espn_archive',
                          facts={'date': '2024-12-25', 'statistics': {'马刺': metrics, '尼克斯': metrics}})
    question = '2024-12-25马刺和尼克斯的比赛：比较双方篮板和失误。'
    prepared = {'allowed': True, 'classification': QueryClassifier().classify(question),
                'resolved_question': question, 'search_results': [result], 'structured_text': ''}
    suggestions = build_follow_ups(prepared, '马刺篮板45，失误15。')
    assert suggestions and not any('篮板' in item or '失误' in item for item in suggestions)
    assert any('罚球' in item for item in suggestions)


def test_llm_stream_accepts_sse_without_space_and_closes_response():
    class Response:
        closed = False
        def __iter__(self):
            return iter([b'data:{"choices":[{"delta":{"content":"first"}}]}\n', b'data:[DONE]\n'])
        def close(self):
            self.closed = True
    response = Response()
    client = LLMClient(_settings())
    client._opener = SimpleNamespace(open=lambda *args, **kwargs: response)
    assert list(client.stream('NBA prompt')) == ['first']
    assert response.closed


def test_llm_stream_does_not_silently_complete_a_truncated_answer():
    class Response:
        def __iter__(self):
            return iter([b'data: {"choices":[{"delta":{"content":"partial"}}]}\n'])
        def close(self):
            pass
    client = LLMClient(_settings())
    client._opener = SimpleNamespace(open=lambda *args, **kwargs: Response())
    stream = client.stream('NBA prompt')
    assert next(stream) == 'partial'
    with pytest.raises(RuntimeError, match='中断'):
        next(stream)


def test_deepseek_request_does_not_spend_the_answer_budget_on_hidden_thinking():
    client = LLMClient(_settings(llm_model='deepseek-v4-flash', llm_thinking=False))
    payloads = []
    client._request = lambda payload: payloads.append(payload) or {'choices': [{'message': {'content': '完整回答'}}]}
    assert client.complete('NBA factual analysis', max_tokens=1536) == '完整回答'
    assert payloads[0]['thinking'] == {'type': 'disabled'}
