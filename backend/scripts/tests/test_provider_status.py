"""Provider 状态契约测试：验证 fetch_today_games 和 fetch_nba_news 返回的 sourceStatus 正确性。"""
import json
import sys
import os
import unittest
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))


class TestTodayGamesProviderStatus(unittest.TestCase):
    """今日比赛 Provider 状态测试"""

    @patch('nba_data_fetcher.fetch_espn_summer_today_games')
    @patch('nba_data_fetcher.ScoreboardV3')
    @patch('nba_api.live.nba.endpoints.scoreboard.ScoreBoard')
    def test_all_providers_failed_returns_failed(self, mock_sb, mock_v3, mock_espn):
        """NBA Live、V3、ESPN 全失败 → sourceStatus=failed"""
        from nba_data_fetcher import fetch_today_games

        # NBA Live 失败
        mock_sb.side_effect = Exception("API timeout")
        # V3 两天都失败
        mock_v3.side_effect = Exception("V3 error")
        # ESPN 失败
        mock_espn.return_value = ([], False, "ESPN error")

        games, providers, status = fetch_today_games()

        self.assertEqual(status, 'failed')
        self.assertEqual(len(games), 0)
        self.assertTrue(all(not p['succeeded'] for p in providers))

    @patch('nba_data_fetcher.fetch_espn_summer_today_games')
    @patch('nba_data_fetcher.ScoreboardV3')
    @patch('nba_api.live.nba.endpoints.scoreboard.ScoreBoard')
    def test_espn_empty_legitimate_returns_empty(self, mock_sb, mock_v3, mock_espn):
        """ESPN 合法空赛程（请求成功但无比赛）→ sourceStatus=empty"""
        from nba_data_fetcher import fetch_today_games

        # NBA Live 失败
        mock_sb.side_effect = Exception("API timeout")
        # V3 失败
        mock_v3.side_effect = Exception("V3 error")
        # ESPN 成功但无比赛
        mock_espn.return_value = ([], True, None)

        games, providers, status = fetch_today_games()

        self.assertEqual(status, 'empty')
        self.assertEqual(len(games), 0)

    @patch('nba_data_fetcher.fetch_espn_summer_today_games')
    @patch('nba_data_fetcher.ScoreboardV3')
    @patch('nba_api.live.nba.endpoints.scoreboard.ScoreBoard')
    def test_any_provider_returns_games_returns_ok(self, mock_sb, mock_v3, mock_espn):
        """任一 Provider 返回比赛 → sourceStatus=ok"""
        from nba_data_fetcher import fetch_today_games

        # NBA Live 返回比赛
        mock_sb_instance = MagicMock()
        mock_sb_instance.get_dict.return_value = {
            'scoreboard': {'games': [{'gameId': '001', 'homeTeam': {'teamTricode': 'LAL', 'teamName': 'Lakers'},
                                       'awayTeam': {'teamTricode': 'BOS', 'teamName': 'Celtics'},
                                       'gameStatus': 3, 'score': {'home': 100, 'away': 98}}]}
        }
        mock_sb.return_value = mock_sb_instance
        # V3 失败
        mock_v3.side_effect = Exception("V3 error")
        # ESPN 失败
        mock_espn.return_value = ([], False, "ESPN error")

        games, providers, status = fetch_today_games()

        self.assertEqual(status, 'ok')
        self.assertGreater(len(games), 0)

    @patch('nba_data_fetcher.fetch_espn_summer_today_games')
    @patch('nba_data_fetcher.ScoreboardV3')
    @patch('nba_api.live.nba.endpoints.scoreboard.ScoreBoard')
    def test_v3_one_day_success_one_day_fail(self, mock_sb, mock_v3, mock_espn):
        """V3 一天成功一天失败 → Provider 成功"""
        from nba_data_fetcher import fetch_today_games

        # NBA Live 失败
        mock_sb.side_effect = Exception("API timeout")
        # V3 第一次成功，第二次失败
        mock_v3_instance = MagicMock()
        mock_v3_instance.get_dict.return_value = {'scoreboard': {'games': []}}
        mock_v3.side_effect = [mock_v3_instance, Exception("V3 day2 error")]
        # ESPN 失败
        mock_espn.return_value = ([], False, "ESPN error")

        games, providers, status = fetch_today_games()

        v3_result = next(p for p in providers if p['provider'] == 'scoreboard_v3')
        self.assertTrue(v3_result['succeeded'])
        self.assertIn('V3 day2 error', v3_result['error'] or '')


class TestNewsProviderStatus(unittest.TestCase):
    """新闻 Provider 状态测试"""

    def test_merge_candidates_deduplicates_normalized_urls_and_prioritizes_recent(self):
        from nba_data_fetcher import _merge_news_candidates

        candidates = [
            {
                'sourceUrl': 'https://example.com/story/?utm_source=feed',
                'headline': 'old duplicate',
                'published': '2026-07-19T12:00:00Z',
            },
            {
                'sourceUrl': 'https://example.com/story',
                'headline': 'new duplicate',
                'published': '2026-07-20T12:00:00Z',
            },
            {
                'sourceUrl': 'https://example.com/new',
                'headline': 'new story',
                'published': '2026-07-20T10:00:00Z',
            },
        ]

        merged = _merge_news_candidates(candidates, limit=2)

        self.assertEqual(len(merged), 2)
        self.assertEqual(merged[0]['headline'], 'new duplicate')
        self.assertEqual(merged[1]['headline'], 'new story')

    def test_known_urls_are_excluded_from_translation_candidates(self):
        from nba_data_fetcher import _select_translation_candidates

        candidates = [
            {'sourceUrl': 'https://example.com/existing', 'headline': 'existing'},
            {'sourceUrl': 'https://example.com/new', 'headline': 'new'},
        ]

        selected = _select_translation_candidates(
            candidates, {'https://example.com/existing'}
        )

        self.assertEqual([item['headline'] for item in selected], ['new'])

    @patch('nba_data_fetcher.fetch_espn_json_news')
    @patch('nba_data_fetcher.fetch_espn_rss_news')
    @patch('nba_data_fetcher.fetch_cbs_rss_news')
    @patch('nba_data_fetcher.HAS_TRANSLATOR', False)
    def test_partial_provider_failure_keeps_successful_candidates(
        self, mock_cbs, mock_espn_rss, mock_espn_json
    ):
        from nba_data_fetcher import fetch_nba_news

        mock_espn_json.return_value = (
            [{'sourceUrl': 'https://espn.test/1', 'headline': 'ESPN'}],
            {'provider': 'espn_news', 'succeeded': True, 'items': 1, 'error': None},
        )
        mock_espn_rss.side_effect = RuntimeError('RSS down')
        mock_cbs.return_value = (
            [{'sourceUrl': 'https://cbs.test/1', 'headline': 'CBS'}],
            {'provider': 'cbs_rss', 'succeeded': True, 'items': 1, 'error': None},
        )

        news, providers, status = fetch_nba_news(limit=40)

        self.assertEqual(status, 'partial')
        self.assertEqual({item['headline'] for item in news}, {'ESPN', 'CBS'})
        self.assertTrue(any(not item['succeeded'] for item in providers))

    @patch('nba_data_fetcher.direct_opener.open')
    @patch('nba_data_fetcher.proxy_opener.open')
    @patch('nba_data_fetcher.PROXY_URL', 'http://127.0.0.1:7890')
    def test_news_request_falls_back_to_direct_when_proxy_fails(
        self, mock_proxy_open, mock_direct_open
    ):
        from nba_data_fetcher import _open_news_url

        mock_proxy_open.side_effect = OSError('proxy reset')
        response = MagicMock()
        mock_direct_open.return_value = response

        actual = _open_news_url('https://example.com/news', timeout=15)

        self.assertIs(actual, response)
        mock_direct_open.assert_called_once()

    @patch('nba_data_fetcher.direct_opener.open')
    @patch('nba_data_fetcher.proxy_opener.open')
    @patch('nba_data_fetcher.PROXY_URL', 'http://127.0.0.1:7890')
    def test_news_request_reports_proxy_and_direct_failures(
        self, mock_proxy_open, mock_direct_open
    ):
        from nba_data_fetcher import _open_news_url

        mock_proxy_open.side_effect = OSError('proxy reset')
        mock_direct_open.side_effect = OSError('direct timeout')

        with self.assertRaisesRegex(RuntimeError, 'proxy reset.*direct timeout'):
            _open_news_url('https://example.com/news', timeout=15)

    @patch('nba_data_fetcher.fetch_cbs_rss_news', side_effect=RuntimeError('Network error'))
    @patch('nba_data_fetcher.fetch_espn_rss_news', side_effect=RuntimeError('Network error'))
    @patch('nba_data_fetcher.fetch_espn_json_news', side_effect=RuntimeError('Network error'))
    def test_espn_news_failed_returns_failed(self, *_mocks):
        """ESPN News 请求异常 → sourceStatus=failed"""
        from nba_data_fetcher import fetch_nba_news

        news, providers, status = fetch_nba_news()

        self.assertEqual(status, 'failed')
        self.assertEqual(len(news), 0)
        self.assertFalse(providers[0]['succeeded'])

    @patch('nba_data_fetcher.fetch_cbs_rss_news')
    @patch('nba_data_fetcher.fetch_espn_rss_news')
    @patch('nba_data_fetcher.fetch_espn_json_news')
    def test_espn_news_empty_returns_empty(self, mock_json, mock_rss, mock_cbs):
        """ESPN News 正常返回空列表 → sourceStatus=empty"""
        from nba_data_fetcher import fetch_nba_news

        for provider, name in (
            (mock_json, 'espn_news'),
            (mock_rss, 'espn_rss'),
            (mock_cbs, 'cbs_rss'),
        ):
            provider.return_value = ([], {
                'provider': name, 'succeeded': True, 'items': 0, 'error': None,
            })

        news, providers, status = fetch_nba_news()

        self.assertEqual(status, 'empty')
        self.assertEqual(len(news), 0)
        self.assertTrue(providers[0]['succeeded'])

    @patch('nba_data_fetcher.HAS_TRANSLATOR', False)
    @patch('nba_data_fetcher.fetch_cbs_rss_news')
    @patch('nba_data_fetcher.fetch_espn_rss_news')
    @patch('nba_data_fetcher.fetch_espn_json_news')
    def test_espn_news_with_data_returns_ok(self, mock_json, mock_rss, mock_cbs):
        """ESPN News 返回新闻 → sourceStatus=ok"""
        from nba_data_fetcher import fetch_nba_news

        mock_json.return_value = ([{
            'headline': 'Test News',
            'description': 'Test desc',
            'published': '2025-01-01T00:00:00Z',
            'sourceUrl': 'http://test.com',
            'provider': 'espn_news',
        }], {
            'provider': 'espn_news', 'succeeded': True, 'items': 1, 'error': None,
        })
        mock_rss.return_value = ([], {
            'provider': 'espn_rss', 'succeeded': True, 'items': 0, 'error': None,
        })
        mock_cbs.return_value = ([], {
            'provider': 'cbs_rss', 'succeeded': True, 'items': 0, 'error': None,
        })

        news, providers, status = fetch_nba_news(limit=1)

        self.assertEqual(status, 'ok')
        self.assertGreater(len(news), 0)
        self.assertTrue(providers[0]['succeeded'])


class TestProxyConfiguration(unittest.TestCase):

    def test_proxy_is_applied_to_http_and_https_requests(self):
        from nba_data_fetcher import _apply_proxy_environment

        proxy_url = 'http://127.0.0.1:7890'
        with patch.dict(os.environ, {}, clear=True):
            _apply_proxy_environment(proxy_url)

            self.assertEqual(os.environ['HTTP_PROXY'], proxy_url)
            self.assertEqual(os.environ['HTTPS_PROXY'], proxy_url)
            self.assertEqual(os.environ['http_proxy'], proxy_url)
            self.assertEqual(os.environ['https_proxy'], proxy_url)


if __name__ == '__main__':
    unittest.main()
