"""
RAG 搜索系统单元测试（不依赖真实网络）

覆盖：
- ESPN Scoreboard 合并 NBA + 夏联
- 相关性过滤（Scoreboard 跳过关键词过滤，新闻做关键词过滤）
- EvidenceGate 实时问题拒绝
- used_tools / tool_trace 记录
- Provider 路由与降级
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest
from unittest.mock import patch, MagicMock
from datetime import datetime, timezone

from rag_app.tools.search_tool import (
    SearchResult, SearchExecution, ProviderResult,
    EspnScoreboardProvider, EspnNewsProvider, GoogleNewsRssProvider,
    SearchService, SearchQueryRewriter, BaiduSearchTool,
)
from rag_app.pipeline.evidence_gate import EvidenceGate, REFUSAL_MESSAGE, REALTIME_REFUSAL_MESSAGE
from rag_app.pipeline.query_classifier import QueryClassifier


# ══════════════════════════════════════════════════════════════
# ESPN Scoreboard 测试
# ══════════════════════════════════════════════════════════════

class TestEspnScoreboard:
    def test_merge_nba_and_summer(self):
        """NBA + Summer League 数据应合并"""
        provider = EspnScoreboardProvider(timeout=5)
        # _fetch_scoreboard 返回合并后的数据（模拟合并逻辑）
        merged_data = {"events": [
            {"id": "100", "date": "2026-07-13T00:00Z", "competitions": [{"competitors": [
                {"homeAway": "home", "team": {"displayName": "Lakers"}, "score": "100"},
                {"homeAway": "away", "team": {"displayName": "Celtics"}, "score": "95"},
            ], "status": {"type": {"description": "Final"}}}]},
            {"id": "200", "date": "2026-07-13T02:00Z", "competitions": [{"competitors": [
                {"homeAway": "home", "team": {"displayName": "Spurs"}, "score": "80"},
                {"homeAway": "away", "team": {"displayName": "Thunder"}, "score": "75"},
            ], "status": {"type": {"description": "Final"}}}]},
        ]}

        with patch.object(provider, '_fetch_scoreboard', return_value=merged_data):
            results = provider.search("NBA games 2026-07-13", 10)
            assert len(results) == 2
            ids = {r.url.split('/')[-1] for r in results}
            assert "100" in ids
            assert "200" in ids

    def test_no_filter_by_keywords(self):
        """Scoreboard 结果不应被关键词过滤"""
        provider = EspnScoreboardProvider(timeout=5)
        data = {"events": [{"id": "300", "date": "2026-07-13T00:00Z", "competitions": [{"competitors": [
            {"homeAway": "home", "team": {"displayName": "Lakers"}, "score": "100"},
            {"homeAway": "away", "team": {"displayName": "Celtics"}, "score": "95"},
        ], "status": {"type": {"description": "Final"}}}]}]}
        with patch.object(provider, '_fetch_scoreboard', return_value=data):
            results = provider.search("NBA Summer League scores 2026-07-13", 10)
            assert len(results) == 1  # 标题 "Celtics vs Lakers" 不含 "Summer League"，但不应被过滤

    def test_explicit_date_filters_out_stale_events_before_limit(self):
        provider = EspnScoreboardProvider(timeout=5)
        data = {"events": [
            {"id": "old", "date": "2026-06-14T00:30Z", "competitions": [{"competitors": [
                {"homeAway": "home", "team": {"displayName": "Spurs"}, "score": "90"},
                {"homeAway": "away", "team": {"displayName": "Knicks"}, "score": "94"},
            ], "status": {"type": {"description": "Final"}}}]},
            {"id": "current-1", "date": "2026-07-19T20:30Z", "competitions": [{"competitors": [
                {"homeAway": "home", "team": {"displayName": "Thunder"}, "score": "90"},
                {"homeAway": "away", "team": {"displayName": "Nets"}, "score": "108"},
            ], "status": {"type": {"description": "Final"}}}]},
            {"id": "current-2", "date": "2026-07-20T01:00Z", "competitions": [{"competitors": [
                {"homeAway": "home", "team": {"displayName": "Grizzlies"}, "score": "90"},
                {"homeAway": "away", "team": {"displayName": "Warriors"}, "score": "94"},
            ], "status": {"type": {"description": "Final"}}}]},
        ]}

        with patch.object(provider, '_fetch_scoreboard', return_value=data):
            results = provider.search("今天 NBA 比赛 2026-07-20", 2)

        assert [result.url.rsplit('/', 1)[-1] for result in results] == ["current-1", "current-2"]


# ══════════════════════════════════════════════════════════════
# SearchService 测试
# ══════════════════════════════════════════════════════════════

class TestSearchService:
    def _make_settings(self):
        s = MagicMock()
        s.search_timeout_seconds = 5
        s.search_top_k = 5
        s.search_cache_ttl_seconds = 600
        s.search_providers = "espn_scoreboard,espn_news"
        s.baidu_min_interval_seconds = 15
        s.baidu_circuit_breaker_seconds = 600
        s.searxng_base_url = ""
        return s

    def test_scoreboard_not_filtered_by_keywords(self):
        """Scoreboard 结果（比赛标题）不应被关键词过滤移除"""
        service = SearchService(self._make_settings())
        scoreboard_result = SearchResult(
            title="Celtics vs Lakers",
            url="https://espn.com/game/100",
            snippet="Celtics 95 - Lakers 100 (Final)",
            provider="espn_scoreboard",
        )
        # Mock providers
        mock_scoreboard = MagicMock(spec=EspnScoreboardProvider)
        mock_scoreboard.name = "espn_scoreboard"
        mock_scoreboard.is_available.return_value = True
        mock_scoreboard.search.return_value = [scoreboard_result]

        mock_news = MagicMock(spec=EspnNewsProvider)
        mock_news.name = "espn_news"
        mock_news.is_available.return_value = True
        mock_news.search.return_value = []

        service.providers = [mock_scoreboard, mock_news]
        results, execution = service.search("NBA Summer League scores 2026-07-13", 5)
        assert len(results) == 1
        assert results[0].provider == "espn_scoreboard"

    def test_news_filtered_by_keywords(self):
        """新闻结果应被关键词过滤"""
        service = SearchService(self._make_settings())
        news_result = SearchResult(
            title="NFL Draft Results",  # 不含 NBA 相关关键词
            url="https://espn.com/nfl/draft",
            snippet="NFL draft picks",
            provider="espn_news",
        )
        mock_scoreboard = MagicMock(spec=EspnScoreboardProvider)
        mock_scoreboard.name = "espn_scoreboard"
        mock_scoreboard.is_available.return_value = True
        mock_scoreboard.search.return_value = []

        mock_news = MagicMock(spec=EspnNewsProvider)
        mock_news.name = "espn_news"
        mock_news.is_available.return_value = True
        mock_news.search.return_value = [news_result]

        service.providers = [mock_scoreboard, mock_news]
        results, execution = service.search("NBA trade latest", 5)
        # NFL 结果应被过滤（不含 "NBA" 或 "trade" 关键词）
        assert len(results) == 0

    def test_chinese_trade_query_matches_english_espn_article(self):
        service = SearchService(self._make_settings())
        trade_result = SearchResult(
            title="NBA trade latest",
            url="https://espn.com/nba/trade",
            snippet="A trade update from the NBA",
            provider="espn_news",
        )
        news = MagicMock(spec=EspnNewsProvider)
        news.name = "espn_news"
        news.is_available.return_value = True
        news.search.return_value = [trade_result]
        service.providers = [news]

        results, _execution = service.search("最新NBA交易新闻", 5)

        assert results == [trade_result]

    def test_per_provider_tracking(self):
        """每个 Provider 应有独立的状态追踪"""
        service = SearchService(self._make_settings())
        mock_news = MagicMock(spec=EspnNewsProvider)
        mock_news.name = "espn_news"
        mock_news.is_available.return_value = True
        mock_news.search.return_value = [SearchResult("NBA trade news", "http://u1", "NBA trade details", "espn_news")]

        mock_google = MagicMock(spec=GoogleNewsRssProvider)
        mock_google.name = "google_news_rss"
        mock_google.is_available.return_value = True
        mock_google.search.side_effect = Exception("timeout")

        service.providers = [mock_news, mock_google]
        results, execution = service.search("NBA trade latest", 5)
        assert len(execution.provider_results) == 2
        assert execution.provider_results[0].status == "success"
        assert execution.provider_results[1].status == "failed"
        assert "timeout" in execution.provider_results[1].error


# ══════════════════════════════════════════════════════════════
# EvidenceGate 测试
# ══════════════════════════════════════════════════════════════

class TestEvidenceGate:
    def test_freshness_query_search_failed(self):
        """实时问题联网失败时应拒绝"""
        gate = EvidenceGate()
        decision = gate.assess(
            structured_text="",
            documents=["some old document"],
            question="最新NBA交易新闻？",
            search_attempted=True,
            search_success=False,
        )
        assert not decision.allowed
        assert "联网" in decision.message or "最新" in decision.message

    def test_freshness_query_search_success(self):
        """实时问题联网成功时应允许"""
        gate = EvidenceGate()
        decision = gate.assess(
            structured_text="",
            documents=["NBA trade: Kawhi Leonard to Raptors"],
            question="最新NBA交易新闻？",
            search_attempted=True,
            search_success=True,
        )
        assert decision.allowed

    def test_freshness_query_not_attempted_has_old_docs(self):
        """实时问题未联网但有旧文档时应拒绝"""
        gate = EvidenceGate()
        decision = gate.assess(
            structured_text="",
            documents=["some old document from database"],
            question="最新NBA交易新闻？",
            search_attempted=False,
            search_success=False,
        )
        assert not decision.allowed

    def test_non_freshness_query_with_docs(self):
        """非实时问题有文档时应允许"""
        gate = EvidenceGate()
        decision = gate.assess(
            structured_text="",
            documents=["some document"],
            question="雷霆队战绩如何？",
            search_attempted=False,
            search_success=False,
        )
        assert decision.allowed

    def test_out_of_scope_question(self):
        """库外知识问题应拒绝"""
        gate = EvidenceGate()
        decision = gate.assess(
            structured_text="",
            documents=["random document"],
            question="NBA薪资帽是多少？",
        )
        assert not decision.allowed


# ══════════════════════════════════════════════════════════════
# QueryClassifier 测试
# ══════════════════════════════════════════════════════════════

class TestQueryClassifier:
    def test_freshness_keywords_trigger_web_search(self):
        """包含时效性关键词的查询应触发联网"""
        c = QueryClassifier()
        assert c.classify("最新NBA交易新闻").needs_web_search
        assert c.classify("今天有什么比赛").needs_web_search
        assert c.classify("最近有谁受伤了").needs_web_search
        assert c.classify("帮我联网查一下马刺交易").needs_web_search

    def test_non_freshness_no_web_search(self):
        """非实时查询不应触发联网"""
        c = QueryClassifier()
        assert not c.classify("雷霆队战绩").needs_web_search
        assert not c.classify("东契奇场均数据").needs_web_search

    def test_ranking_independent(self):
        """排名查询应独立触发"""
        c = QueryClassifier()
        result = c.classify("谁是本赛季得分王")
        assert result.query_type.value == "ranking"

    def test_score_leaderboard_query_triggers_ranking(self):
        """得分榜/助攻榜这类榜单说法也应触发排名查询"""
        c = QueryClassifier()
        assert c.classify("本赛季得分榜前五是谁？").query_type.value == "ranking"
        assert c.classify("本赛季助攻榜前十是谁？").query_type.value == "ranking"

    def test_full_player_name_wins_over_overlapping_short_alias(self):
        result = QueryClassifier().classify("詹姆斯·哈登本赛季场均得分是多少？")

        assert result.players == ["詹姆斯·哈登"]
        assert result.query_type.value == "player"

    def test_multiple_players_preserve_mention_order(self):
        result = QueryClassifier().classify("对比库里和詹姆斯·哈登的场均得分")

        assert result.players == ["斯蒂芬·库里", "詹姆斯·哈登"]


# ══════════════════════════════════════════════════════════════
# SearchQueryRewriter 测试
# ══════════════════════════════════════════════════════════════

class TestSearchQueryRewriter:
    def test_trade_query_rewrite(self):
        """交易查询应生成英文候选"""
        r = SearchQueryRewriter()
        candidates = r.rewrite("伦纳德交易进展")
        assert len(candidates) >= 2
        assert any("Kawhi" in c or "Leonard" in c for c in candidates)
        assert any("trade" in c.lower() for c in candidates)

    def test_specific_trade_rewrite_precedes_generic_latest_rewrite(self):
        candidates = SearchQueryRewriter().rewrite("伦纳德最新交易进展")

        assert "trade" in candidates[1].lower()
        assert "Kawhi" in candidates[1] or "Leonard" in candidates[1]

    def test_summer_league_rewrite(self):
        """夏联查询应生成英文候选"""
        r = SearchQueryRewriter()
        candidates = r.rewrite("夏季联赛最新战况")
        assert any("Summer League" in c for c in candidates)

    def test_team_rewrite(self):
        """球队查询应生成英文队名"""
        r = SearchQueryRewriter()
        candidates = r.rewrite("马刺最近动态")
        assert any("Spurs" in c for c in candidates)


# ══════════════════════════════════════════════════════════════
# Provider 路由测试
# ══════════════════════════════════════════════════════════════

class TestProviderRouting:
    def _make_settings(self):
        s = MagicMock()
        s.search_timeout_seconds = 5
        s.search_top_k = 5
        s.search_cache_ttl_seconds = 600
        s.search_providers = "espn_scoreboard,espn_news,google_news_rss"
        s.baidu_min_interval_seconds = 15
        s.baidu_circuit_breaker_seconds = 600
        s.searxng_base_url = ""
        return s

    def _mock_scoreboard(self):
        p = MagicMock(spec=EspnScoreboardProvider)
        p.name = "espn_scoreboard"
        p.is_available.return_value = True
        p.search.return_value = [SearchResult("Knicks vs Spurs", "http://espn.com/1", "score", "espn_scoreboard")]
        return p

    def _mock_news(self):
        p = MagicMock(spec=EspnNewsProvider)
        p.name = "espn_news"
        p.is_available.return_value = True
        p.search.return_value = [SearchResult("Kawhi trade news", "http://espn.com/2", "trade details", "espn_news")]
        return p

    def _mock_google(self):
        p = MagicMock(spec=GoogleNewsRssProvider)
        p.name = "google_news_rss"
        p.is_available.return_value = True
        p.search.return_value = [SearchResult("NBA trade latest", "http://news.google.com/1", "trade news", "google_news_rss")]
        return p

    def test_trade_query_excludes_scoreboard(self):
        """交易查询不应调用 espn_scoreboard"""
        service = SearchService(self._make_settings())
        sb = self._mock_scoreboard()
        news = self._mock_news()
        google = self._mock_google()
        service.providers = [sb, news, google]

        results, execution = service.search("NBA trade latest 2026-07-13", 5)

        # Scoreboard 不应被调用
        sb.search.assert_not_called()
        # News 和 Google 应被调用
        news.search.assert_called_once()
        google.search.assert_called_once()
        # 结果不应包含 scoreboard
        assert all(r.provider != "espn_scoreboard" for r in results)

    def test_injury_query_excludes_scoreboard(self):
        """伤病查询不应调用 espn_scoreboard"""
        service = SearchService(self._make_settings())
        sb = self._mock_scoreboard()
        news = self._mock_news()
        service.providers = [sb, news]

        results, execution = service.search("最近有谁受伤了", 5)
        sb.search.assert_not_called()

    def test_free_agent_query_excludes_scoreboard(self):
        """自由市场查询不应调用 espn_scoreboard"""
        service = SearchService(self._make_settings())
        sb = self._mock_scoreboard()
        news = self._mock_news()
        service.providers = [sb, news]

        results, execution = service.search("NBA自由市场最新消息", 5)
        sb.search.assert_not_called()

    def test_game_query_includes_scoreboard(self):
        """比赛查询应调用 espn_scoreboard"""
        service = SearchService(self._make_settings())
        sb = self._mock_scoreboard()
        news = self._mock_news()
        service.providers = [sb, news]

        results, execution = service.search("今天有什么NBA比赛", 5)
        sb.search.assert_called_once()

    def test_summer_league_query_includes_scoreboard(self):
        """夏联查询应调用 espn_scoreboard"""
        service = SearchService(self._make_settings())
        sb = self._mock_scoreboard()
        news = self._mock_news()
        service.providers = [sb, news]

        results, execution = service.search("NBA Summer League scores", 5)
        sb.search.assert_called_once()

    def test_player_query_excludes_scoreboard(self):
        """球员动态查询不应调用 espn_scoreboard"""
        service = SearchService(self._make_settings())
        sb = self._mock_scoreboard()
        news = self._mock_news()
        service.providers = [sb, news]

        results, execution = service.search("文班亚马最近表现", 5)
        sb.search.assert_not_called()

    def test_kawhi_trade_excludes_scoreboard(self):
        """Kawhi Leonard trade 查询不应调用 espn_scoreboard"""
        service = SearchService(self._make_settings())
        sb = self._mock_scoreboard()
        news = self._mock_news()
        google = self._mock_google()
        service.providers = [sb, news, google]

        results, execution = service.search("Kawhi Leonard trade latest NBA", 5)
        sb.search.assert_not_called()
        assert all(r.provider != "espn_scoreboard" for r in results)


if __name__ == "__main__":
    pytest.main([__file__, "-v"])


class TestScoreboardNoGameDay:
    """日期明确但当日无比赛：不得拿其他日期赛事充数，需返回赛程说明"""

    def _provider_with_events(self, events):
        from rag_app.tools.search_tool import EspnScoreboardProvider

        provider = EspnScoreboardProvider.__new__(EspnScoreboardProvider)
        provider.timeout = 5
        provider._opener = None
        import threading as _threading
        provider._cache_lock = _threading.Lock()
        provider._cache_ts = 0.0
        provider._cache_data = None

        def _fake_fetch():
            return {"events": events}

        provider._fetch_scoreboard = _fake_fetch
        return provider

    @staticmethod
    def _event(event_id, date, away, away_score, home, home_score, status):
        return {
            "id": event_id,
            "date": date,
            "competitions": [{
                "competitors": [
                    {"homeAway": "away", "score": str(away_score), "team": {"displayName": away}},
                    {"homeAway": "home", "score": str(home_score), "team": {"displayName": home}},
                ],
                "status": {"type": {"description": status}},
            }],
        }

    def test_explicit_date_no_match_returns_schedule_note(self):
        from datetime import datetime, timezone, timedelta
        future = datetime.now(timezone(timedelta(hours=8))) + timedelta(days=7)
        future_utc = future.replace(hour=23, minute=0, second=0, microsecond=0).astimezone(timezone.utc).isoformat().replace('+00:00', 'Z')
        provider = self._provider_with_events([
            # Old score and a genuinely future event; the test must remain stable as today changes.
            self._event("1", "2026-07-19T02:00:00Z", "Brooklyn Nets", 108, "Oklahoma City Thunder", 90, "Final"),
            self._event("2", future_utc, "Miami Heat", 0, "Toronto Raptors", 0, "Scheduled"),
        ])
        results = provider.search("分析今天所有比赛的胜负关键因素和看点")
        assert len(results) == 1
        assert results[0].title == "NBA 近期赛程说明"
        assert "没有安排 NBA 比赛" in results[0].snippet
        assert "近期赛程" in results[0].snippet
        assert f"{future.month}月{future.day}日" in results[0].snippet

    def test_matching_date_keeps_only_today_games(self):
        provider = self._provider_with_events([
            self._event("1", "2026-07-19T02:00:00Z", "Brooklyn Nets", 108, "Oklahoma City Thunder", 90, "Final"),
            self._event("2", "2026-10-03T23:00:00Z", "Miami Heat", 0, "Toronto Raptors", 0, "Scheduled"),
        ])
        # 构造一场"今天"的比赛（本地时区今天）
        from datetime import datetime, timezone, timedelta
        # 以北京时区"今天中午"换算回 UTC 存储，保证 _event_local_date 判定的北京日期恒等于"今天"
        # （此前用 UTC 当日 12:00 构造，北京时间午夜后运行会错位成"昨天/明天"导致测试随机失败）
        now_bj = datetime.now(timezone(timedelta(hours=8)))
        event_utc = now_bj.replace(hour=12, minute=0, second=0, microsecond=0).astimezone(timezone.utc)
        today_evt = self._event("3", event_utc.strftime("%Y-%m-%dT%H:%M:%SZ"), "Boston Celtics", 112, "LA Lakers", 108, "Final")
        provider2 = self._provider_with_events([self._event("1", "2026-07-19T02:00:00Z", "Brooklyn Nets", 108, "Oklahoma City Thunder", 90, "Final"), today_evt])
        results = provider2.search("今天NBA比赛结果")
        assert len(results) == 1
        assert "Celtics" in results[0].title and "Lakers" in results[0].title
