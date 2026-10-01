import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))


class NewsProviderAggregationTest(unittest.TestCase):

    def test_normalize_url_removes_tracking_and_fragment(self):
        from nba_data_fetcher import normalize_news_url

        actual = normalize_news_url(
            'HTTPS://Example.COM/story/?utm_source=rss&utm_medium=feed#top'
        )

        self.assertEqual(actual, 'https://example.com/story')

    def test_merge_deduplicates_url_and_prefers_newer_candidate(self):
        from nba_data_fetcher import _merge_news_candidates

        merged = _merge_news_candidates([
            {
                'sourceUrl': 'https://example.com/story?utm_source=old',
                'headline': 'older',
                'published': '2026-07-19T10:00:00Z',
            },
            {
                'sourceUrl': 'https://example.com/story',
                'headline': 'newer',
                'published': '2026-07-20T10:00:00Z',
            },
        ], limit=40)

        self.assertEqual([item['headline'] for item in merged], ['newer'])

    def test_cbs_provider_rejects_non_nba_sections(self):
        from nba_data_fetcher import _is_cbs_nba_url

        self.assertTrue(_is_cbs_nba_url(
            'https://www.cbssports.com/nba/news/lebron-james-update/'
        ))
        self.assertFalse(_is_cbs_nba_url(
            'https://www.cbssports.com/mlb/news/all-star-game/'
        ))
        self.assertFalse(_is_cbs_nba_url(
            'https://www.cbssports.com/wnba/news/caitlin-clark-update/'
        ))
        self.assertFalse(_is_cbs_nba_url(
            'https://www.cbssports.com/golf/news/open-championship/'
        ))

    def test_known_title_excludes_candidate_without_url(self):
        from nba_data_fetcher import _select_translation_candidates

        selected = _select_translation_candidates(
            [
                {'sourceUrl': '', 'headline': 'Already Seen'},
                {'sourceUrl': '', 'headline': 'Brand New'},
            ],
            known_urls=set(),
            known_titles={'already seen'},
        )

        self.assertEqual([item['headline'] for item in selected], ['Brand New'])

    @patch('nba_data_fetcher.HAS_TRANSLATOR', False)
    @patch('nba_data_fetcher.fetch_cbs_rss_news')
    @patch('nba_data_fetcher.fetch_espn_rss_news')
    @patch('nba_data_fetcher.fetch_espn_json_news')
    def test_known_candidates_are_removed_before_limit(
        self, espn_json, espn_rss, cbs_rss
    ):
        espn_json.return_value = ([
            {
                'sourceUrl': 'https://example.com/known',
                'headline': 'Known',
                'published': '2026-07-20T12:00:00Z',
                'provider': 'espn_news',
            },
            {
                'sourceUrl': 'https://example.com/new-1',
                'headline': 'New 1',
                'published': '2026-07-20T11:00:00Z',
                'provider': 'espn_news',
            },
            {
                'sourceUrl': 'https://example.com/new-2',
                'headline': 'New 2',
                'published': '2026-07-20T10:00:00Z',
                'provider': 'espn_news',
            },
        ], {
            'provider': 'espn_news', 'succeeded': True,
            'items': 3, 'error': None,
        })
        espn_rss.return_value = ([], {
            'provider': 'espn_rss', 'succeeded': True,
            'items': 0, 'error': None,
        })
        cbs_rss.return_value = ([], {
            'provider': 'cbs_rss', 'succeeded': True,
            'items': 0, 'error': None,
        })

        news, _providers, status = __import__('nba_data_fetcher').fetch_nba_news(
            limit=2,
            known_urls={'https://example.com/known'},
        )

        self.assertEqual(status, 'ok')
        self.assertEqual(
            [item['sourceUrl'] for item in news],
            ['https://example.com/new-1', 'https://example.com/new-2'],
        )

    @patch('nba_data_fetcher.fetch_cbs_rss_news', side_effect=RuntimeError('cbs down'))
    @patch('nba_data_fetcher.fetch_espn_rss_news', side_effect=RuntimeError('rss down'))
    @patch('nba_data_fetcher.fetch_espn_json_news', side_effect=RuntimeError('json down'))
    def test_all_provider_failures_return_failed(self, *_mocks):
        from nba_data_fetcher import fetch_nba_news

        news, providers, status = fetch_nba_news(limit=40)

        self.assertEqual(status, 'failed')
        self.assertEqual(news, [])
        self.assertEqual(len(providers), 3)
        self.assertTrue(all(not provider['succeeded'] for provider in providers))


if __name__ == '__main__':
    unittest.main()
