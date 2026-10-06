"""Regression tests for correctness, latency, memory, and response budgets."""
from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path
from types import SimpleNamespace
import sys

from fastapi import FastAPI
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rag_app.llm.client import LLMClient
from rag_app.llm.prompts import SYSTEM_PROMPT
from rag_app.memory.store import ConversationMemory
from rag_app.api.routes import create_router
from rag_app.config import RAGSettings
from rag_app.pipeline.engine import RAGEngine
from rag_app.pipeline.evidence_gate import EvidenceGate, INVALID_QUESTION_MESSAGE
from rag_app.pipeline.query_classifier import QueryClassifier
from rag_app.retrieval.retriever import RetrievalQuality, RetrievalResult
from rag_app.structured.repository import StructuredRepository
from rag_app.tools.search_tool import SearchExecution, SearchResult, SearchService, format_search_results


class _FailIfCalled:
    def __init__(self, name: str):
        self.name = name

    def __getattr__(self, method: str):
        def fail(*_args, **_kwargs):
            raise AssertionError(f"{self.name}.{method} must not be called")

        return fail


class _EmptyRetriever:
    def __init__(self):
        self.calls: list[str] = []

    def retrieve(self, question, _top_k, _classification):
        self.calls.append(question)
        quality = RetrievalQuality(confidence_level="none", reason="test")
        return RetrievalResult([], [], [], [], quality)


class _StructuredStub:
    def __init__(self, primary: str = "", keyword: str = ""):
        self.primary = primary
        self.keyword = keyword
        self.search_calls = []
        self.keyword_calls = []

    def search(self, classification, top_k, question):
        self.search_calls.append((classification, top_k, question))
        return self.primary

    def search_by_keywords(self, question, top_k):
        self.keyword_calls.append((question, top_k))
        return self.keyword


def _settings(**overrides):
    values = {
        "embedding_model": "embedding-test",
        "llm_model": "llm-test",
        "llm_api_key": "test-key",
        "llm_max_tokens": 1024,
        "llm_temperature": 0.2,
        "llm_timeout_seconds": 5,
        "llm_base_url": "https://example.invalid/v1",
        "force_web_search": False,
        "search_enabled": True,
        "search_top_k": 5,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


def _engine(structured, *, retriever=None, search_service=None, llm=None, query_rewriter=None, settings=None):
    return RAGEngine(settings or _settings(), structured=structured, retriever=retriever or _EmptyRetriever(),
        search_service=search_service or _FailIfCalled('search_service'), llm=llm or _FailIfCalled('llm'),
        query_rewriter=query_rewriter or SimpleNamespace(rewrite=lambda question: [question]), memory=ConversationMemory(window=3))


def test_structured_fact_returns_directly_without_rag_search_or_llm():
    structured = _StructuredStub(primary="斯蒂芬·库里本赛季场均得分为 26.6 分。")
    engine = _engine(
        structured,
        retriever=_FailIfCalled("retriever"),
        search_service=_FailIfCalled("search_service"),
        llm=_FailIfCalled("llm"),
    )

    result = engine.ask("斯蒂芬·库里本赛季场均得分是多少？", session_id="direct")

    assert result.success
    assert result.answer == "斯蒂芬·库里本赛季场均得分为 26.6 分。"
    assert result.used_tools == ["sql"]


def test_primary_structured_query_does_not_append_keyword_fallback():
    structured = _StructuredStub(primary="得分排名：\n1. 卢卡·东契奇：33.5")
    engine = _engine(structured, retriever=_FailIfCalled("retriever"))

    result = engine.ask("本赛季得分排名前五是谁？")

    assert result.success
    assert structured.keyword_calls == []


def test_out_of_scope_question_short_circuits_every_expensive_backend():
    engine = _engine(
        _FailIfCalled("structured"),
        retriever=_FailIfCalled("retriever"),
        search_service=_FailIfCalled("search_service"),
        llm=_FailIfCalled("llm"),
    )

    result = engine.ask("北京今天的天气怎么样？")

    assert result.success
    assert "NBA" in result.answer
    assert result.used_tools == []
    assert result.response_time < 1000


def test_gibberish_ascii_query_short_circuits_every_expensive_backend():
    engine = _engine(
        _FailIfCalled("structured"),
        retriever=_FailIfCalled("retriever"),
        search_service=_FailIfCalled("search_service"),
        llm=_FailIfCalled("llm"),
    )

    result = engine.ask("xyzqwerty")

    assert result.success
    assert result.answer == INVALID_QUESTION_MESSAGE
    assert result.used_tools == []


def test_follow_up_pronoun_resolves_to_recent_player_before_querying():
    structured = _StructuredStub(primary="")

    def search(classification, top_k, question):
        structured.search_calls.append((classification, top_k, question))
        if classification.players == ["斯蒂芬·库里"]:
            return "斯蒂芬·库里本赛季三分命中率为 39.3%。"
        return ""

    structured.search = search
    engine = _engine(structured, retriever=_FailIfCalled("retriever"))
    engine.memory.add("follow-up", "请告诉我斯蒂芬·库里本赛季的场均得分。", "26.6 分。")

    result = engine.ask("他本赛季的三分命中率是多少？", session_id="follow-up")

    assert result.success
    assert result.answer == "斯蒂芬·库里本赛季三分命中率为 39.3%。"
    assert structured.search_calls[-1][2].startswith("斯蒂芬·库里")


def test_restored_conversation_history_resolves_follow_up_pronoun():
    structured = _StructuredStub(primary="")

    def search(classification, top_k, question):
        structured.search_calls.append((classification, top_k, question))
        if classification.players == ["斯蒂芬·库里"]:
            return "斯蒂芬·库里本赛季三分命中率为 39.3%。"
        return ""

    structured.search = search
    engine = _engine(structured, retriever=_FailIfCalled("retriever"))

    result = engine.ask(
        "他的三分命中率是多少？",
        session_id="restored",
        conversation_history=[
            {"role": "user", "content": "请告诉我斯蒂芬·库里本赛季的场均得分。"},
            {"role": "assistant", "content": "斯蒂芬·库里本赛季场均得分为 26.6 分。"},
        ],
    )

    assert result.success
    assert result.answer == "斯蒂芬·库里本赛季三分命中率为 39.3%。"
    assert structured.search_calls[-1][2].startswith("斯蒂芬·库里")


def test_llm_client_uses_per_request_token_budget_for_complete_and_stream():
    client = LLMClient(_settings(llm_max_tokens=1024))
    payloads = []
    client._request = lambda payload: payloads.append(payload) or {
        "choices": [{"message": {"content": "ok"}}]
    }

    assert client.complete("prompt", max_tokens=256) == "ok"
    assert payloads[0]["max_tokens"] == 256

    class _Response:
        def __iter__(self):
            return iter([b'data: {"choices":[{"delta":{"content":"ok"}}]}\n', b"data: [DONE]\n"])

    client._build_request = lambda payload: payloads.append(payload) or object()

    class _Opener:
        def open(self, _request, timeout=None):
            return _Response()

    # stream() 走实例的 _opener.open（而非模块级 urlopen），需替换 _opener 本体
    client._opener = _Opener()
    assert list(client.stream("prompt", max_tokens=512)) == ["ok"]
    assert payloads[-1]["max_tokens"] == 512


def test_analysis_question_uses_structured_evidence_with_bounded_generation():
    structured = _StructuredStub(primary="洛杉矶湖人：西部，战绩53胜29负，胜率64.6%")

    class _LLM:
        def __init__(self):
            self.calls = []

        def complete(self, prompt, max_tokens=None):
            self.calls.append((prompt, max_tokens))
            return "湖人胜率为64.6%。进一步原因需要更多比赛证据。"

    llm = _LLM()
    engine = _engine(structured, retriever=_EmptyRetriever(), llm=llm)

    result = engine.ask("请分析湖人本赛季战绩，并说明表现出色的原因。")

    assert result.success
    assert len(llm.calls) == 1
    assert llm.calls[0][1] == 768
    assert "53胜29负" in llm.calls[0][0]


def test_system_prompt_forbids_causal_claims_from_descriptive_stats():
    assert "相关性" in SYSTEM_PROMPT
    assert "因果" in SYSTEM_PROMPT


def test_health_reports_actual_model_readiness():
    fake_engine = SimpleNamespace(
        retriever=SimpleNamespace(ready=True),
        settings=SimpleNamespace(reranker_enabled=True),
    )
    app = FastAPI()
    app.include_router(create_router(lambda: fake_engine))

    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "model_loaded": True}


def test_query_post_forwards_conversation_history_to_engine():
    captured = {}

    class _Engine:
        def ask(self, question, top_k, session_id, force_web_search, conversation_history=None):
            captured["question"] = question
            captured["history"] = conversation_history
            return SimpleNamespace(
                success=True,
                answer="ok",
                sources=[],
                metadatas=[],
                distances=[],
                response_time=1,
                model="test",
                structured_data=False,
                search_results=[],
                used_tools=[],
                tool_trace=[],
                search_execution={},
                retrieval_quality={},
                error="",
            )

    app = FastAPI()
    app.include_router(create_router(lambda: _Engine()))

    response = TestClient(app).post("/query", json={
        "question": "他的三分命中率是多少？",
        "top_k": 5,
        "session_id": "restored",
        "conversation_history": [
            {"role": "user", "content": "请告诉我斯蒂芬·库里本赛季的场均得分。"},
        ],
    })

    assert response.status_code == 200
    assert captured["question"] == "他的三分命中率是多少？"
    assert captured["history"] == [
        {"role": "user", "content": "请告诉我斯蒂芬·库里本赛季的场均得分。"},
    ]


def test_game_search_stops_after_authoritative_scoreboard_results():
    settings = _settings(
        search_timeout_seconds=5,
        search_cache_ttl_seconds=0,
        search_providers="",
        baidu_min_interval_seconds=15,
        baidu_circuit_breaker_seconds=600,
        searxng_base_url="",
    )
    service = SearchService(settings)

    class _Provider:
        def __init__(self, name, results):
            self.name = name
            self.results = results
            self.calls = 0

        def search(self, *_args, **_kwargs):
            self.calls += 1
            return self.results

    scoreboard = _Provider("espn_scoreboard", [
        SearchResult("Bulls vs Lakers", "https://espn.test/1", "82 - 105 (Final)", "espn_scoreboard")
    ])
    news = _Provider("espn_news", [
        SearchResult("NBA daily recap", "https://espn.test/2", "recap", "espn_news")
    ])
    service.providers = [scoreboard, news]

    results, execution = service.search("今天有哪些 NBA 比赛？", 5)

    assert len(results) == 1
    assert scoreboard.calls == 1
    assert news.calls == 0
    assert execution.attempted_providers == ["espn_scoreboard"]


def test_game_search_does_not_use_news_when_scoreboard_is_empty():
    settings = _settings(
        search_timeout_seconds=5,
        search_cache_ttl_seconds=0,
        search_providers="",
        baidu_min_interval_seconds=15,
        baidu_circuit_breaker_seconds=600,
        searxng_base_url="",
    )
    service = SearchService(settings)

    class _Provider:
        def __init__(self, name, results):
            self.name = name
            self.results = results
            self.calls = 0

        def search(self, *_args, **_kwargs):
            self.calls += 1
            return self.results

    scoreboard = _Provider("espn_scoreboard", [])
    news = _Provider("espn_news", [
        SearchResult("NBA offseason buzz", "https://espn.test/news", "free agency notes", "espn_news")
    ])
    service.providers = [scoreboard, news]

    results, execution = service.search("今天 NBA 有哪些比赛？", 5)

    assert results == []
    assert scoreboard.calls == 1
    assert news.calls == 0
    assert execution.attempted_providers == ["espn_scoreboard"]


def test_freshness_query_skips_vector_and_falls_back_to_web_results():
    result = SearchResult(
        "Warriors vs Grizzlies",
        "https://espn.test/game/current",
        "Warriors 94 - 90 Grizzlies (Final)",
        "espn_scoreboard",
        published_at="2026-07-20T01:00Z",
    )

    class _Search:
        def search(self, *_args, **_kwargs):
            execution = SearchExecution(
                attempted=True,
                status="success",
                attempted_providers=["espn_scoreboard"],
                successful_provider="espn_scoreboard",
                results=[result],
            )
            return [result], execution

    class _EmptyLLM:
        def complete(self, *_args, **_kwargs):
            return ""

    engine = _engine(
        _StructuredStub(),
        retriever=_FailIfCalled("retriever"),
        search_service=_Search(),
        llm=_EmptyLLM(),
    )

    answer = engine.ask("今天 NBA 有哪些比赛？")

    assert answer.success
    assert "Warriors 94:90 Grizzlies（已结束）" in answer.answer
    assert "web_search" in answer.used_tools
    assert "rag" not in answer.used_tools
    assert answer.sources == []


def test_freshness_search_failure_returns_structured_data_with_staleness_warning():
    structured = _StructuredStub(keyword="最近伤病报告（1条）：\n  测试球员（测试队）：脚踝，状态缺阵")

    class _EmptySearch:
        def search(self, *_args, **_kwargs):
            return [], SearchExecution(
                attempted=True,
                status="empty",
                attempted_providers=["espn_news"],
            )

    engine = _engine(
        structured,
        retriever=_FailIfCalled("retriever"),
        search_service=_EmptySearch(),
        llm=_FailIfCalled("llm"),
    )

    answer = engine.ask("最近有哪些伤病信息？")

    assert answer.success
    assert "联网未获取到更新" in answer.answer
    assert "不保证实时" in answer.answer
    assert "测试球员" in answer.answer
    assert answer.used_tools == ["sql", "web_search"]


def test_freshness_query_rejects_rewrite_with_unrequested_old_date():
    calls = []

    class _EmptySearch:
        def search(self, query, *_args, **_kwargs):
            calls.append(query)
            return [], SearchExecution(attempted=True, status="empty")

    engine = _engine(
        _StructuredStub(),
        retriever=_FailIfCalled("retriever"),
        search_service=_EmptySearch(),
        llm=_FailIfCalled("llm"),
    )
    engine.query_planner.rewriter = SimpleNamespace(
        rewrite=lambda _question: ["最近有哪些伤病信息？", "NBA injury report 2026-07-21"]
    )

    answer = engine.ask("最近有哪些伤病信息？")

    assert answer.success
    assert calls == ["最近有哪些伤病信息？"]


def test_default_search_timeout_remains_short(monkeypatch):
    monkeypatch.delenv("RAG_SEARCH_TIMEOUT_SECONDS", raising=False)

    assert RAGSettings.from_env().search_timeout_seconds == 3


def test_scoreboard_results_return_deterministic_chinese_answer_without_llm():
    results = [
        SearchResult(
            "Brooklyn Nets vs Oklahoma City Thunder",
            "https://espn.test/game/1",
            "Brooklyn Nets 108 - 90 Oklahoma City Thunder (Final)",
            "espn_scoreboard",
        ),
        SearchResult(
            "Golden State Warriors vs Memphis Grizzlies",
            "https://espn.test/game/2",
            "Golden State Warriors 94 - 90 Memphis Grizzlies (Final)",
            "espn_scoreboard",
        ),
    ]

    class _Search:
        def search(self, *_args, **_kwargs):
            return results, SearchExecution(
                attempted=True,
                status="success",
                attempted_providers=["espn_scoreboard"],
                successful_provider="espn_scoreboard",
                results=results,
            )

    engine = _engine(
        _StructuredStub(),
        retriever=_FailIfCalled("retriever"),
        search_service=_Search(),
        llm=_FailIfCalled("llm"),
    )

    answer = engine.ask("今天 NBA 有哪些比赛？")

    assert answer.success
    assert "篮网 108:90 雷霆（已结束）" in answer.answer
    assert "勇士 94:90 灰熊（已结束）" in answer.answer
    assert answer.used_tools == ["web_search"]


def test_match_prompt_keeps_all_bounded_search_results():
    engine = _engine(_StructuredStub())
    classification = QueryClassifier().classify("今天 NBA 有哪些比赛？")
    search_results = [
        SearchResult(
            f"Game {index}",
            f"https://espn.test/{index}",
            f"score {index}",
            "espn_scoreboard",
        )
        for index in range(1, 5)
    ]
    prepared = {
        "classification": classification,
        "resolved_question": "今天 NBA 有哪些比赛？",
        "structured_text": "",
        "documents": [],
        "search_results": search_results,
    }

    prompt = engine._build_prompt(prepared, None, 256)

    assert "Game 1" in prompt
    assert "Game 4" in prompt


def test_search_results_are_truncated_before_prompt_injection():
    long_result = SearchResult(
        "T" * 500,
        "https://example.test/" + ("u" * 500),
        "S" * 3000,
        "example",
    )

    formatted = format_search_results([long_result], limit=5)

    assert len(formatted) <= 700
    assert "S" * 1000 not in formatted
    assert "u" * 300 not in formatted


def test_match_list_uses_medium_token_budget_to_avoid_truncation():
    engine = _engine(_StructuredStub())
    classification = QueryClassifier().classify("今天 NBA 有哪些比赛？")

    assert engine._response_token_budget(classification, "今天 NBA 有哪些比赛？") == 512


def test_requested_list_size_scales_budget_without_exceeding_hard_cap():
    engine = _engine(_StructuredStub())
    classification = QueryClassifier().classify("本赛季得分榜前五是谁？")

    five_budget = engine._response_token_budget(classification, "本赛季得分榜前五是谁？")
    ten_budget = engine._response_token_budget(classification, "本赛季得分榜前十是谁？")

    assert five_budget == 512
    assert ten_budget > five_budget
    assert ten_budget <= 1024


class _Cursor:
    def __init__(self, rows):
        self.rows = rows
        self.params = None

    def execute(self, _sql, params):
        self.params = params

    def fetchone(self):
        return self.rows[0] if self.rows else None

    def fetchall(self):
        return self.rows


class _Database:
    def __init__(self, rows):
        self.cursor_instance = _Cursor(rows)

    @contextmanager
    def cursor(self):
        yield self.cursor_instance


class _RoutingCursor:
    def __init__(self, team_rows, match_rows, player_rows):
        self.team_rows = team_rows
        self.match_rows = match_rows
        self.player_rows = player_rows
        self.rows = []
        self.params = None

    def execute(self, sql, params):
        self.params = params
        if "FROM teams" in sql and "match_records" not in sql:
            self.rows = self.team_rows
        elif "FROM match_records" in sql:
            self.rows = self.match_rows
        elif "FROM players" in sql:
            player = params[0].replace("%", "")
            self.rows = [self.player_rows[player]] if player in self.player_rows else []
        else:
            self.rows = []

    def fetchone(self):
        return self.rows[0] if self.rows else None

    def fetchall(self):
        return self.rows


class _RoutingDatabase:
    def __init__(self, team_rows, match_rows, player_rows):
        self.cursor_instance = _RoutingCursor(team_rows, match_rows, player_rows)

    @contextmanager
    def cursor(self):
        yield self.cursor_instance


def test_player_summary_only_returns_the_requested_metric():
    database = _Database([{
        "name": "斯蒂芬·库里",
        "team_name": "勇士",
        "position": "后卫",
        "points_per_game": 26.6,
        "rebounds_per_game": 3.6,
        "assists_per_game": 4.7,
        "field_goal_pct": 0.468,
        "three_point_pct": 0.393,
        "games_played": 43,
    }])
    repository = StructuredRepository(database)

    answer = repository.player_summary(["斯蒂芬·库里"], "他的三分命中率是多少？")

    assert answer == "斯蒂芬·库里（赛季未知）三分命中率为 39.3%。"
    assert "篮板" not in answer
    assert "助攻" not in answer


def test_team_composite_question_returns_record_and_recent_matches():
    database = _RoutingDatabase(
        team_rows=[{
            "name": "湖人",
            "city": "洛杉矶",
            "conference": "西部",
            "wins": 53,
            "losses": 29,
        }],
        match_rows=[
            {
                "match_date": "2026-07-17",
                "home_team": "公牛",
                "away_team": "湖人",
                "home_score": 82,
                "away_score": 105,
                "status": "FINISHED",
            },
            {
                "match_date": "2026-07-15",
                "home_team": "湖人",
                "away_team": "快船",
                "home_score": 99,
                "away_score": 85,
                "status": "FINISHED",
            },
        ],
        player_rows={},
    )
    repository = StructuredRepository(database)
    question = "湖人本赛季战绩如何？最近两场比赛结果是什么？"
    classification = QueryClassifier().classify(question)

    answer = repository.search(classification, 5, question)

    assert "战绩53胜29负" in answer
    assert "最近2场已结束比赛" in answer
    assert "公牛（主）82:105 湖人（客）" in answer


def test_player_comparison_names_leader_for_each_requested_metric():
    database = _RoutingDatabase(
        team_rows=[],
        match_rows=[],
        player_rows={
            "斯蒂芬·库里": {
                "name": "斯蒂芬·库里",
                "team_name": "勇士",
                "position": "后卫",
                "points_per_game": 26.6,
                "rebounds_per_game": 3.6,
                "assists_per_game": 4.7,
                "field_goal_pct": 0.468,
                "three_point_pct": 0.393,
                "games_played": 43,
            },
            "卢卡·东契奇": {
                "name": "卢卡·东契奇",
                "team_name": "湖人",
                "position": "后卫",
                "points_per_game": 33.5,
                "rebounds_per_game": 7.7,
                "assists_per_game": 8.3,
                "field_goal_pct": 0.48,
                "three_point_pct": 0.366,
                "games_played": 50,
            },
        },
    )
    repository = StructuredRepository(database)
    question = "对比斯蒂芬·库里和卢卡·东契奇本赛季的场均得分、篮板、助攻和三分命中率。"
    classification = QueryClassifier().classify(question)

    answer = repository.search(classification, 5, question)

    assert "场均得分：" in answer
    assert "卢卡·东契奇领先" in answer
    assert "场均篮板：" in answer
    assert "场均助攻：" in answer
    assert "三分命中率：" in answer
    assert "斯蒂芬·库里领先" in answer


def test_recent_matches_respect_requested_count_and_home_away_roles():
    database = _Database([
        {
            "match_date": "2026-07-17",
            "home_team": "湖人",
            "away_team": "公牛",
            "home_score": 105,
            "away_score": 82,
            "status": "FINISHED",
        },
        {
            "match_date": "2026-07-15",
            "home_team": "快船",
            "away_team": "湖人",
            "home_score": 85,
            "away_score": 99,
            "status": "FINISHED",
        },
    ])
    repository = StructuredRepository(database)

    answer = repository.match_summary(["湖人"], 5, "湖人最近两场比赛结果")

    assert database.cursor_instance.params[-1] == 2
    assert "湖人（主）105:82 公牛（客）" in answer
    assert "快船（主）85:99 湖人（客）" in answer
