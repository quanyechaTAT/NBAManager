"""Observable failure isolation, evidence scope and bounded conversation state."""
from types import SimpleNamespace
import pytest

from test_rag_engine_regressions import _engine, _StructuredStub
from test_ai_assistant_quality import ArchiveSearch, Model
from rag_app.memory.store import ConversationMemory
from rag_app.pipeline.query_classifier import QueryClassifier
from rag_app.retrieval.retriever import HybridRetriever, RetrievalQuality, RetrievalResult
from rag_app.tools.search_tool import EspnScoreboardProvider, SearchService, SearchResult
from test_rag_engine_regressions import _settings


def test_database_outage_does_not_disable_verified_historical_search():
    class OfflineDatabase:
        def search(self, *args):
            raise ConnectionError('database offline')
        def search_by_keywords(self, *args):
            raise ConnectionError('database offline')
    result = _engine(OfflineDatabase(), search_service=ArchiveSearch(), llm=Model()).ask('分析马刺和尼克斯的比赛')
    assert result.success and '117:114' in result.answer
    assert 'database offline' not in result.error


def test_historical_game_does_not_accept_a_team_season_snapshot_as_evidence():
    class WrongScope:
        def retrieve(self, *args):
            return RetrievalResult(['马刺本赛季进攻效率排名第五'], [{'type': 'team', 'team': '马刺'}], [0.1],
                                   [0.9], RetrievalQuality(accepted_count=1))
    class NoArchive:
        def search(self, *args, **kwargs):
            from rag_app.tools.search_tool import SearchExecution
            return [], SearchExecution(attempted=True, status='empty')
    engine = _engine(_StructuredStub(), retriever=WrongScope(), search_service=NoArchive(), llm=Model())
    result = engine.ask('分析2024年12月25日马刺和尼克斯的比赛')
    assert '当前数据不足' in result.answer


def test_low_rerank_results_are_not_restored_when_every_candidate_is_rejected():
    retriever = object.__new__(HybridRetriever)
    retriever.settings = SimpleNamespace(vector_k=8, max_vector_distance=.68, min_rerank_score=.5)
    retriever.store = SimpleNamespace(search=lambda *a, **kw: SimpleNamespace(
        documents=['湖人球队统计'], metadatas=[{'type': 'team', 'team': '湖人'}], distances=[.1]))
    retriever.reranker = SimpleNamespace(rerank_with_scores=lambda q, docs, metas, distances, k:
                                      (docs, metas, distances, [.2]))
    result = retriever.retrieve('分析湖人球队', 5, QueryClassifier().classify('分析湖人球队'))
    assert result.documents == [] and result.quality.accepted_count == 0


def test_scoped_vector_search_never_falls_back_to_an_unrelated_team():
    retriever = object.__new__(HybridRetriever)
    retriever.settings = SimpleNamespace(vector_k=8, max_vector_distance=.68, min_rerank_score=0)
    def search(*args, **kwargs):
        if kwargs.get('where'):
            return SimpleNamespace(documents=[], metadatas=[], distances=[])
        return SimpleNamespace(documents=['勇士球队统计'], metadatas=[{'type': 'team', 'team': '勇士'}], distances=[.1])
    retriever.store = SimpleNamespace(search=search)
    retriever.reranker = SimpleNamespace(rerank_with_scores=lambda q, d, m, distances, k: (d, m, distances, [.9]))
    assert retriever.retrieve('分析湖人球队', 5, QueryClassifier().classify('分析湖人球队')).documents == []


def test_conversation_cache_expires_and_evicts_old_sessions():
    now = [0.0]
    memory = ConversationMemory(window=2, max_sessions=2, ttl_seconds=10, clock=lambda: now[0])
    memory.add('first', 'q1', 'a1')
    memory.add('second', 'q2', 'a2')
    memory.add('third', 'q3', 'a3')
    assert memory.get('first') == []
    assert memory.get('third')[-1]['content'] == 'a3'
    now[0] = 11
    assert memory.get('third') == []


def test_stale_persisted_history_cannot_remove_a_newer_turn():
    engine = _engine(_StructuredStub())
    engine.memory.add('session', 'q1', 'a1')
    engine.memory.add('session', 'q2', 'a2')
    engine._hydrate_memory('session', [{'role': 'user', 'content': 'q1'}, {'role': 'assistant', 'content': 'a1'}])
    assert engine.memory.get('session')[-2]['content'] == 'q2'


def test_historical_match_analysis_enriches_a_local_score_before_generation():
    archive = ArchiveSearch()
    engine = _engine(_StructuredStub(primary='2024-12-25 尼克斯117:114马刺。'), search_service=archive, llm=Model())
    engine.ask('分析2024年12月25日马刺和尼克斯的比赛')
    assert archive.calls, 'Final scores alone must not suppress historical box-score lookup'


def test_same_session_generation_is_rejected_until_the_first_stream_closes():
    engine = _engine(_StructuredStub(), search_service=ArchiveSearch(), llm=Model())
    first = engine.stream('分析马刺和尼克斯的比赛', session_id='shared')
    while next(first)['type'] != 'chunk':
        pass
    second = engine.ask('分析马刺和尼克斯的比赛', session_id='shared')
    assert not second.success and '尚未结束' in second.error
    first.close()
    assert engine.ask('分析马刺和尼克斯的比赛', session_id='shared').success


def test_scoreboard_filters_opponents_before_limiting_results():
    from test_game_archive import event
    provider = EspnScoreboardProvider()
    provider._fetch_scoreboard_cached = lambda: {'events': [event('100', opponent='13'), event('101')]}
    results = provider.search('马刺对尼克斯的比赛', 1, context={'teams': ['马刺', '尼克斯']})
    assert results[0].url.endswith('/101')


def test_search_result_cache_evicts_the_oldest_query():
    service = SearchService(_settings(search_cache_ttl_seconds=600, search_timeout_seconds=3,
                            search_providers='', search_cache_max_entries=2))
    calls = []
    def search(query, *args):
        calls.append(query)
        return [SearchResult(query, 'https://espn.test/' + str(len(calls)), 'NBA news', 'espn_news')]
    service.providers = [SimpleNamespace(name='espn_news', search=search)]
    for query in ['NBA新闻一', 'NBA新闻二', 'NBA新闻三', 'NBA新闻一']:
        service.search(query)
    assert len(calls) == 4


def test_stream_enforces_total_model_deadline_and_closes_the_response():
    import time
    import pytest
    from rag_app.llm.client import LLMClient
    class SlowResponse:
        closed = False
        def __iter__(self):
            yield b'data: {"choices":[{"delta":{"content":"first"}}]}\n'
            time.sleep(.03)
            yield b'data: [DONE]\n'
        def close(self):
            self.closed = True
    response = SlowResponse()
    client = LLMClient(_settings(llm_timeout_seconds=.02))
    client._opener = SimpleNamespace(open=lambda *a, **kw: response)
    with pytest.raises(RuntimeError, match='超时'):
        list(client.stream('NBA question'))
    assert response.closed


@pytest.mark.parametrize('question', ['马刺和尼克斯昨天的比赛', 'Spurs vs Knicks today', 'Spurs vs Knicks live'])
def test_current_game_queries_cannot_select_an_old_game(question):
    from rag_app.pipeline.planning import QueryPlan
    from rag_app.pipeline.evidence_gate import EvidenceGate
    classification = QueryClassifier().classify(question)
    plan = QueryPlan.create(question, question, classification, 5)
    assert classification.needs_web_search and plan.web_first and not plan.historical_game
    assert not plan.can_answer_directly('2024-12-25 尼克斯117:114马刺。')
    assert not EvidenceGate().assess('', ['旧比赛'], question).allowed


def test_requested_season_does_not_discard_games_in_its_second_calendar_year():
    from rag_app.pipeline.planning import QueryPlan
    question = '分析2024-25赛季马刺和尼克斯的比赛'
    plan = QueryPlan.create(question, question, QueryClassifier().classify(question), 5)
    assert plan.accepts_game_fact({'home_team': '马刺', 'away_team': '尼克斯', 'date': '2025-03-19',
                                   'season': '2024-25', 'home_score': 120, 'away_score': 105})


def test_archive_answers_while_vector_models_are_still_warming():
    from fastapi import FastAPI
    from fastapi.testclient import TestClient
    from rag_app.api.routes import create_router
    class Warming:
        ready = False
        def retrieve(self, *args):
            pytest.fail('Historical lookup must not wait for vector model loading')
    engine = _engine(_StructuredStub(), retriever=Warming(), search_service=ArchiveSearch(), llm=Model())
    app = FastAPI()
    app.include_router(create_router(lambda: engine))
    with TestClient(app) as client:
        assert client.get('/health').json() == {'status': 'ok', 'model_loaded': False}
        assert client.get('/ready').status_code == 503
        response = client.post('/query', json={'question': '分析马刺和尼克斯的比赛'})
        assert response.status_code == 200 and response.json()['success']
        assert '117:114' in response.json()['answer']


def test_deeper_follow_ups_explain_observed_differences_without_inventing_causes():
    from rag_app.pipeline.follow_ups import build_follow_ups
    question = '分析2024年12月25日马刺和尼克斯的比赛'
    fact = {'date': '2024-12-25', 'home_team': '尼克斯', 'away_team': '马刺', 'home_score': 117,
        'away_score': 114, 'statistics': {
            '马刺': {'投篮命中-出手': '42-83', '投篮命中率%': '51', '三分命中率%': '42',
                     '三分命中-出手': '16-38', '罚球命中-出手': '14-17', '篮板': '45', '进攻篮板': '8', '失误': '15'},
            '尼克斯': {'投篮命中-出手': '45-100', '投篮命中率%': '45', '三分命中率%': '33',
                       '三分命中-出手': '13-39', '罚球命中-出手': '14-16', '篮板': '47', '进攻篮板': '17', '失误': '5'}}}
    prepared = {'allowed': True, 'resolved_question': question, 'classification': QueryClassifier().classify(question),
        'search_results': [SearchResult('Match', 'https://espn.test/game', '', 'espn_archive', facts=fact)]}
    questions = build_follow_ups(prepared, '马刺114:117尼克斯。')
    assert '51%' in questions[0] and '45%' in questions[0] and '却输球' in questions[0]
    assert '多9个进攻篮板' in questions[1] and '少10次失误' in questions[1]
    assert '因果证据' in questions[1]
    fact['away_score'] = 120
    assert not any('却输球' in item for item in build_follow_ups(prepared, '马刺获胜。'))


def test_follow_up_premises_never_mix_statistics_from_another_date():
    from rag_app.pipeline.follow_ups import build_follow_ups
    question = '分析2024年12月25日马刺和尼克斯的比赛'
    wrong_date = {'date': '2025-01-01', 'statistics': {'马刺': {'投篮命中率%': '51', '三分命中率%': '42'},
                                                  '尼克斯': {'投篮命中率%': '45', '三分命中率%': '33'}}}
    prepared = {'allowed': True, 'resolved_question': question, 'classification': QueryClassifier().classify(question),
        'search_results': [SearchResult('Match', 'https://espn.test/game', '', 'espn_archive', facts=wrong_date)]}
    assert not any('命中率' in item for item in build_follow_ups(prepared, '已取得比分。'))


def test_explanatory_game_follow_ups_receive_an_analysis_budget():
    engine = _engine(_StructuredStub(), settings=_settings(llm_max_tokens=4096))
    question = '2024-12-25马刺对尼克斯的比赛：结合双方投篮出手数，能支持哪些判断，还缺哪些因果证据？'
    assert engine._response_token_budget(QueryClassifier().classify(question), question) == 1536


@pytest.mark.parametrize('model', ['richinfoai/ritrieve_zh_v1', 'BAAI/bge-reranker-v2-m3'])
def test_model_override_never_loads_the_previous_default_model_from_cache(tmp_path, model):
    from rag_app.retrieval.model_paths import resolve_model_path
    previous = tmp_path / ('models--' + model.replace('/', '--')) / 'snapshots' / 'old'
    previous.mkdir(parents=True)
    (previous / 'config.json').write_text('{}')
    assert resolve_model_path('configured/new-model', str(tmp_path)) == 'configured/new-model'


def test_model_cache_honors_the_main_revision(tmp_path):
    from rag_app.retrieval.model_paths import resolve_model_path
    repository = tmp_path / 'models--configured--model'
    for revision in ('aaa-active', 'zzz-stale'):
        snapshot = repository / 'snapshots' / revision
        snapshot.mkdir(parents=True)
        (snapshot / 'config.json').write_text('{}')
    (repository / 'refs').mkdir()
    (repository / 'refs' / 'main').write_text('aaa-active')
    assert resolve_model_path('configured/model', str(tmp_path)).endswith('aaa-active')


def test_derived_scoring_components_reconcile_with_final_score_and_skip_conflicting_stats():
    from rag_app.domain.game_metrics import scoring_breakdown
    fact = {'home_team': '尼克斯', 'away_team': '马刺', 'home_score': 117, 'away_score': 114,
        'statistics': {'尼克斯': {'投篮命中-出手': '45-100', '三分命中-出手': '13-39', '罚球命中-出手': '14-16'},
                       '马刺': {'投篮命中-出手': '42-83', '三分命中-出手': '16-38', '罚球命中-出手': '14-17'}}}
    computed = scoring_breakdown(fact)
    assert computed['尼克斯'] == {'two_point_points': 64, 'three_point_points': 39, 'free_throw_points': 14}
    assert sum(computed['马刺'].values()) == 114
    fact['statistics']['马刺']['罚球命中-出手'] = '99-100'
    assert '马刺' not in scoring_breakdown(fact)


@pytest.mark.parametrize('question,wrong_type', [('马刺和尼克斯的总决赛', 'REGULAR_SEASON'),
    ('马刺和尼克斯的夏季联赛比赛', 'REGULAR_SEASON'), ('马刺和尼克斯的NBA比赛', 'SUMMER_LEAGUE')])
def test_competition_scope_applies_to_vector_documents_and_web_facts(question, wrong_type):
    from rag_app.pipeline.planning import QueryPlan
    plan = QueryPlan.create(question, question, QueryClassifier().classify(question), 5)
    assert not plan.accepts_game_document({'type': 'match', 'match_date': '2024-12-25',
        'home_team': '尼克斯', 'away_team': '马刺', 'game_type': wrong_type})
    assert not plan.accepts_game_fact({'date': '2024-12-25', 'home_team': '尼克斯', 'away_team': '马刺',
                                      'home_score': 117, 'away_score': 114, 'game_type': wrong_type})
