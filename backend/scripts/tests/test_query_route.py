"""Behavioral checks for constrained planning, real evidence fusion and optional HyDE."""
from pathlib import Path
from types import SimpleNamespace
import json
import sys
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rag_app.config import RAGSettings
from rag_app.pipeline.query_classifier import QueryClassifier
from rag_app.pipeline.query_planner import QueryPlanner
from rag_app.pipeline.engine import RAGEngine
from rag_app.retrieval.retriever import HybridRetriever
from rag_app.retrieval.vector_store import VectorSearchResult
from rag_app.llm.client import LLMClient
from rag_app.tools.search_tool import SearchExecution, SearchResult


class Tasks:
    def __init__(self, planning=None, hyde=''):
        self.planning = planning or {}
        self.hyde = hyde
        self.calls = []

    def complete_task(self, prompt, *, task, **kwargs):
        self.calls.append((task, prompt, kwargs))
        return json.dumps(self.planning, ensure_ascii=False) if task == 'planning' else self.hyde


def plan(question, model=None, **settings):
    return QueryPlanner(RAGSettings(**settings), model or Tasks()).plan(
        question, question, QueryClassifier().classify(question), 5)


def test_simple_facts_and_historical_game_keep_zero_planning_calls():
    model = Tasks()
    assert plan('库里场均得分是多少', model).intent == 'fact'
    game = plan('分析马刺和尼克斯的比赛', model)
    assert game.game_scope['historical']
    assert game.sub_questions
    assert model.calls == []


def test_complex_plan_retains_original_and_validates_model_rewrites():
    question = '2024-25赛季马刺为什么需要改善弱侧协防'
    model = Tasks({'intent': 'explanation', 'queries': [
        '2024-25赛季马刺弱侧轮转 防守补位', '2025-26赛季马刺弱侧防守',
        '2024-25赛季湖人弱侧防守', '马刺弱侧防守'],
        'sub_questions': ['2024-25赛季马刺如何进行弱侧轮转']})
    result = plan(question, model)
    assert result.retrieval_queries[0] == question
    assert '2024-25赛季马刺弱侧轮转 防守补位' in result.retrieval_queries
    assert all('2025-26' not in q and '湖人' not in q for q in result.retrieval_queries)
    assert all('2024-25' in q for q in result.retrieval_queries)
    assert result.planner_method == 'model+rules'


def test_planning_failure_falls_back_to_rules():
    class Broken(Tasks):
        def complete_task(self, *args, **kwargs):
            raise TimeoutError()
    result = plan('马刺为什么需要改善弱侧协防', Broken())
    assert result.retrieval_queries[0] == result.resolved_question
    assert result.planner_method == 'rules'
    assert result.planning_error == 'TimeoutError'


def test_rewrites_cannot_add_unrequested_entities_numbers_or_relative_time():
    result = plan('马刺为什么需要改善弱侧协防', Tasks({'intent': 'explanation', 'queries': [
        '马刺今天弱侧协防', '马刺文班亚马弱侧协防', '马刺弱侧协防提高20%',
        '马刺弱侧轮转 防守补位']}))
    assert '马刺弱侧轮转 防守补位' in result.retrieval_queries
    assert all('今天' not in q and '文班' not in q and '20%' not in q for q in result.retrieval_queries)


class Store:
    def __init__(self, batches):
        self.batches = batches
        self.calls = []

    def search_many(self, queries, top_k, where=None):
        self.calls.append((queries, top_k, where))
        return [self.batches.get(q, VectorSearchResult([], [], [])) for q in queries]


class Reranker:
    def __init__(self):
        self.calls = []

    def rerank_with_scores(self, query, documents, metadatas, distances, top_k):
        self.calls.append((query, documents))
        return documents[:top_k], metadatas[:top_k], distances[:top_k], [.9] * min(top_k, len(documents))


def retriever(store):
    result = object.__new__(HybridRetriever)
    result.settings = RAGSettings(query_planner_mode='rules')
    result.store = store
    result.reranker = Reranker()
    result.embeddings = SimpleNamespace(_model=True)
    result.reranker._model = True
    return result


def test_multi_query_fuses_duplicates_and_reranks_original_once():
    question = '马刺为什么需要改善弱侧协防'
    p = plan(question, Tasks({'intent': 'explanation', 'queries': ['马刺弱侧轮转 防守补位']}))
    paragraph = '马刺防守中，弱侧轮转需要及时补位。'
    meta = {'type': 'news', 'team': '马刺', 'id': 1}
    r = retriever(Store({q: VectorSearchResult([paragraph], [meta], [.2]) for q in p.retrieval_queries}))
    result = r.retrieve_plan(p)
    assert result.documents == [paragraph]
    assert len(r.reranker.calls) == 1
    assert r.reranker.calls[0][0] == question
    assert len(r.store.calls[0][0]) > 1
    assert result.quality.accepted_count == 1


def test_multi_query_rejects_other_team_and_low_distance_quality():
    p = plan('马刺为什么需要改善弱侧协防', query_planner_mode='rules')
    r = retriever(Store({p.resolved_question: VectorSearchResult(
        ['湖人弱侧补位', '马刺无关记录'], [{'type': 'news', 'team': '湖人'}, {'type': 'news', 'team': '马刺'}], [.1, .99])}))
    assert r.retrieve_plan(p).documents == []
    assert r.reranker.calls == []


class SQL:
    def search(self, *args):
        return ''

    def search_by_keywords(self, *args):
        return ''


def test_hyde_can_recover_real_document_but_never_becomes_evidence():
    question = '马刺为什么需要改善弱侧协防'
    hypothesis = '马刺的弱侧轮转可能涉及协防补位、底角保护和持球防守衔接。'
    document = '实际报道：马刺在防守训练中强调弱侧补位和底角保护。'
    model = Tasks(hyde=hypothesis)
    r = retriever(Store({hypothesis: VectorSearchResult([document], [{'type': 'news', 'team': '马刺'}], [.2])}))
    engine = RAGEngine(RAGSettings(query_planner_mode='rules', search_enabled=False), structured=SQL(), retriever=r, llm=model)
    prepared = engine.prepare(question, 5)
    assert prepared['documents'] == [document]
    assert hypothesis not in engine._build_prompt(prepared, None, 1536)
    assert hypothesis not in str(prepared)
    assert [c[0] for c in model.calls] == ['hyde']
    assert len(r.reranker.calls) == 1


def test_hyde_skips_good_evidence_and_rejects_fabricated_score():
    question = '马刺为什么需要改善弱侧协防'
    model = Tasks(hyde='马刺击败湖人，比分是120比110。')
    r = retriever(Store({}))
    engine = RAGEngine(RAGSettings(query_planner_mode='rules', search_enabled=False), structured=SQL(), retriever=r, llm=model)
    prepared = engine.prepare(question, 5)
    assert not prepared['documents']
    assert len(r.store.calls) == 1
    assert any(t.get('reason') == 'invalid_hypothesis' for t in prepared['tool_trace'])
    r.store.batches[question] = VectorSearchResult(['马刺弱侧协防需要补位。'], [{'type': 'news', 'team': '马刺'}], [.2])
    model.calls.clear()
    assert engine.prepare(question, 5)['documents']
    assert model.calls == []


def test_hyde_skips_facts_current_queries_and_models_warming():
    model = Tasks(hyde='马刺弱侧补位。')
    r = retriever(Store({}))
    engine = RAGEngine(RAGSettings(query_planner_mode='rules', search_enabled=False), structured=SQL(), retriever=r, llm=model)
    engine.prepare('库里场均得分是多少', 5)
    engine.prepare('今天马刺的弱侧防守为什么出问题', 5)
    r.embeddings._model = None
    engine.prepare('马刺为什么需要改善弱侧协防', 5)
    assert model.calls == []


@pytest.mark.parametrize('original,changed', [
    ('今天马刺的防守为什么不好', '昨天马刺的防守为什么不好'),
    ('2024-25赛季马刺的防守为什么不好', '2025年马刺的防守为什么不好'),
    ('2024年12月25日马刺对尼克斯的比赛', '2024年12月26日马刺对尼克斯的比赛'),
    ('马刺季后赛的防守为什么不好', '马刺夏季联赛的防守为什么不好'),
    ('马刺最近三场比赛表现如何', '马刺最近五场比赛表现如何'),
])
def test_rewrite_rejects_changed_temporal_or_league_scope(original, changed):
    planner = QueryPlanner(RAGSettings(query_planner_mode='rules'), Tasks())
    p = planner.plan(original, original, QueryClassifier().classify(original), 5)
    assert not planner.accepts_query(p, changed)


def test_bilingual_player_aliases_preserve_canonical_entity():
    planner = QueryPlanner(RAGSettings(query_planner_mode='rules'), Tasks())
    p = planner.plan('库里为什么擅长无球跑动', '库里为什么擅长无球跑动', QueryClassifier().classify('库里为什么擅长无球跑动'), 5)
    assert planner.accepts_query(p, 'Stephen Curry为什么擅长无球跑动')
    assert not planner.accepts_query(p, 'LeBron James为什么擅长无球跑动')


def test_subquestions_are_retrieval_inputs_and_generation_tasks():
    question = '马刺为什么需要改善弱侧协防'
    sub = '马刺的底角防守与弱侧补位有何关联'
    model = Tasks({'intent': 'explanation', 'queries': [], 'sub_questions': [sub]})
    doc = '马刺报道：弱侧补位与底角保护有关。'
    r = retriever(Store({sub: VectorSearchResult([doc], [{'type': 'news', 'team': '马刺'}], [.2])}))
    engine = RAGEngine(RAGSettings(search_enabled=False), structured=SQL(), retriever=r, llm=model)
    prepared = engine.prepare(question, 5)
    assert prepared['documents'] == [doc]
    assert sub in engine._build_prompt(prepared, None, 1536)
    assert [call[0] for call in model.calls] == ['planning']


def test_multi_query_keeps_rerank_rejections_and_scope_diagnostics():
    p = plan('马刺为什么需要改善弱侧协防', query_planner_mode='rules')
    r = retriever(Store({p.resolved_question: VectorSearchResult(['马刺补位报道'], [{'type': 'news', 'team': '马刺'}], [.2])}))
    r.settings.min_rerank_score = .95
    result = r.retrieve_plan(p)
    assert result.documents == []
    assert result.quality.accepted_count == 0
    assert result.quality.reason == 'no_documents_above_rerank_threshold'


def test_search_rejects_other_player_even_with_matching_news_intent():
    class News:
        def search(self, *args, **kwargs):
            return [SearchResult('LeBron James injury update', 'https://example.invalid/a',
                'LeBron James injury report', 'test')], SearchExecution(attempted=True, status='success')
    engine = RAGEngine(RAGSettings(query_planner_mode='rules'), structured=SQL(), retriever=retriever(Store({})),
                       search_service=News(), llm=Tasks())
    prepared = engine.prepare('库里最新伤病消息', 5)
    assert prepared['search_results'] == []
    assert prepared['search_execution']['status'] == 'empty'


@pytest.mark.parametrize('task', ['planning', 'hyde'])
def test_task_llm_uses_separate_prompt_timeout_and_small_budget(task):
    settings = RAGSettings(llm_api_key='test-key', llm_thinking=True, llm_timeout_seconds=90)
    client = LLMClient(settings)
    calls = []
    class Response:
        def __enter__(self):
            return self
        def __exit__(self, *args):
            pass
        def read(self):
            return b'{"choices":[{"finish_reason":"stop","message":{"content":"{}"}}]}'
    def open_request(request, timeout):
        calls.append((json.loads(request.data), timeout))
        return Response()
    client._opener = SimpleNamespace(open=open_request)
    assert client.complete_task('JSON query', task=task, max_tokens=320, timeout_seconds=4) == '{}'
    payload, timeout = calls[0]
    assert timeout == 4
    assert payload['max_tokens'] == 320
    assert payload['thinking'] == {'type': 'disabled'}
    assert ('response_format' in payload) == (task == 'planning')
    assert settings.llm_thinking and settings.llm_timeout_seconds == 90


def test_batch_vector_search_enforces_contract_and_does_not_write():
    from rag_app.retrieval.vector_store import ChromaVectorStore
    calls = []
    class Collection:
        def query(self, **kwargs):
            calls.append(kwargs)
            return {'documents': [['real a'], ['real b']], 'metadatas': [[{}], [{}]], 'distances': [[.1], [.2]]}
    store = ChromaVectorStore(RAGSettings(), SimpleNamespace(embed_documents=lambda q: [[1], [2]]))
    store._collection = Collection()  # No add/update API is available to the retrieval path.
    results = store.search_many(['original', 'hypothesis'], 5, {'team': '马刺'})
    assert [r.documents for r in results] == [['real a'], ['real b']]
    assert calls[0]['where'] == {'$and': [{'team': '马刺'}, {'data_contract_version': 2}]}


def test_hyde_timeout_preserves_existing_real_evidence():
    class Broken(Tasks):
        def complete_task(self, *args, **kwargs):
            raise TimeoutError()
    question = '马刺为什么需要改善弱侧协防'
    doc = '马刺球队数据：胜场数有已核实记录。'
    r = retriever(Store({question: VectorSearchResult([doc], [{'type': 'team', 'team': '马刺'}], [.2])}))
    engine = RAGEngine(RAGSettings(query_planner_mode='rules', search_enabled=False), structured=SQL(), retriever=r, llm=Broken())
    prepared = engine.prepare(question, 5)
    assert prepared['documents'] == [doc]
    assert any(t.get('tool') == 'hyde' and t.get('reason') == 'TimeoutError' for t in prepared['tool_trace'])


def test_stream_starts_before_any_planning_and_hypothesis_work():
    model = Tasks()
    engine = RAGEngine(RAGSettings(search_enabled=False), structured=SQL(), retriever=retriever(Store({})), llm=model)
    stream = engine.stream('马刺为什么需要改善弱侧协防')
    assert next(stream)['type'] == 'status'
    assert model.calls == []
    stream.close()


def test_semantic_followups_preserve_topic_and_season():
    from rag_app.pipeline.follow_ups import build_follow_ups
    question = '分析2024-25赛季库里的无球跑动'
    p = plan(question, query_planner_mode='rules')
    prepared = {'allowed': True, 'classification': p.classification, 'resolved_question': question,
                'query_plan': p.diagnostics(), 'search_results': []}
    followups = build_follow_ups(prepared, '已核实的部分资料。')
    assert len(followups) == 3
    assert all('2024-25赛季' in q and '斯蒂芬·库里' in q and '无球跑动' in q for q in followups)
    assert all('最近三场' not in q for q in followups)


def test_curry_and_james_aliases_do_not_match_different_full_names():
    classifier = QueryClassifier()
    assert classifier.classify('斯蒂芬·库里和勒布朗·詹姆斯').players == ['斯蒂芬·库里', '勒布朗·詹姆斯']
    assert classifier.classify('赛斯·库里和詹姆斯·怀斯曼').players == []
    p = plan('库里为什么擅长无球跑动', query_planner_mode='rules')
    assert not p.accepts_document('赛斯·库里本赛季场均数据', {'type': 'historical', 'name': '赛斯·库里'})
    assert not QueryPlanner(RAGSettings(), Tasks()).accepts_query(p, '赛斯·库里为什么擅长无球跑动')


def test_low_relevance_news_still_triggers_hyde_and_source_quality_reaches_prompt():
    question = '库里为什么擅长无球跑动'
    hypothesis = '库里的无球跑动可能涉及掩护、接球与空间利用，需要核实相关资料。'
    doc = '库里的续约新闻只提供背景。'
    r = retriever(Store({question: VectorSearchResult([doc], [{'type': 'news', 'quality': 'UNVERIFIED'}], [.2])}))
    original = r.reranker.rerank_with_scores
    def low_score(*args):
        docs, metas, dists, scores = original(*args)
        return docs, metas, dists, [.1] * len(docs)
    r.reranker.rerank_with_scores = low_score
    model = Tasks(hyde=hypothesis)
    engine = RAGEngine(RAGSettings(query_planner_mode='rules', search_enabled=False), structured=SQL(), retriever=r, llm=model)
    prepared = engine.prepare(question, 5)
    assert [call[0] for call in model.calls] == ['hyde']
    assert 'UNVERIFIED' in engine._build_prompt(prepared, None, 768)
    assert hypothesis not in engine._build_prompt(prepared, None, 768)
