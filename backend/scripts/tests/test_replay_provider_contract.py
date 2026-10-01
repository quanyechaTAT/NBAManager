import io
import json
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

import nba_data_fetcher


def _event(event_type='jump_shot'):
    return {
        'period': 1,
        'gameClock': '10:00',
        'description': 'Made shot',
        'eventType': event_type,
        'homeScore': 2,
        'awayScore': 0,
    }


class ReplayProviderContractTest(unittest.TestCase):

    def test_success_envelope_is_versioned_and_keeps_legacy_array(self):
        payload = nba_data_fetcher._build_playbyplay_payload(
            [_event()], source='NBA_PLAY_BY_PLAY', default_quality='FULL'
        )

        self.assertEqual(payload['playByPlay'], [_event()])
        self.assertEqual(payload['events'], [_event()])
        self.assertEqual({
            key: value for key, value in payload.items()
            if key not in {'events', 'playByPlay'}
        }, {
            'schemaVersion': 'replay-provider/v1',
            'outcome': 'SUCCESS',
            'quality': 'FULL',
            'source': 'NBA_PLAY_BY_PLAY',
            'eventCount': 1,
            'errorCode': None,
            'errorMessage': None,
            'retryable': False,
        })

    def test_empty_envelope_is_not_reported_as_success(self):
        payload = nba_data_fetcher._build_playbyplay_payload(
            [], source='ESPN_PLAY_BY_PLAY', default_quality='FULL'
        )

        self.assertEqual(payload['playByPlay'], [])
        self.assertEqual(payload['outcome'], 'EMPTY')
        self.assertEqual(payload['quality'], 'UNAVAILABLE')
        self.assertEqual(payload['eventCount'], 0)
        self.assertIsNone(payload['errorCode'])
        self.assertIsNone(payload['errorMessage'])

    def test_exception_envelope_is_error_instead_of_empty(self):
        payload = nba_data_fetcher._run_playbyplay_action(
            lambda: (_ for _ in ()).throw(TimeoutError('upstream timed out')),
            source='NBA_PLAY_BY_PLAY',
            default_quality='FULL',
            error_code='NBA_API_ERROR',
        )

        self.assertEqual(payload['playByPlay'], [])
        self.assertEqual(payload['outcome'], 'ERROR')
        self.assertEqual(payload['quality'], 'UNAVAILABLE')
        self.assertEqual(payload['errorCode'], 'NBA_API_ERROR')
        self.assertIn('upstream timed out', payload['errorMessage'])
        self.assertTrue(payload['retryable'])

    def test_period_score_only_payload_is_summary_only(self):
        payload = nba_data_fetcher._build_playbyplay_payload(
            [_event('period_score')],
            source='ESPN_SCOREBOARD_SUMMARY',
            default_quality='SUMMARY_ONLY',
        )

        self.assertEqual(payload['outcome'], 'SUCCESS')
        self.assertEqual(payload['quality'], 'SUMMARY_ONLY')
        self.assertEqual(payload['source'], 'ESPN_SCOREBOARD_SUMMARY')

    def test_main_wraps_all_playbyplay_actions_with_the_same_contract(self):
        cases = [
            ('playbyplay', '0042500405', 'fetch_playbyplay', 'NBA_PLAY_BY_PLAY', 'FULL'),
            ('espn_playbyplay', '401881722', 'fetch_espn_playbyplay', 'ESPN_PLAY_BY_PLAY', 'FULL'),
            (
                'espn_summer_playbyplay',
                '401881722',
                'fetch_espn_summer_playbyplay',
                'ESPN_SCOREBOARD_SUMMARY',
                'SUMMARY_ONLY',
            ),
        ]

        for action, game_id, function_name, source, quality in cases:
            with self.subTest(action=action), tempfile.NamedTemporaryFile(delete=False) as output:
                output_path = output.name
            try:
                stdout = io.StringIO()
                event = _event('period_score' if quality == 'SUMMARY_ONLY' else 'jump_shot')
                with patch.object(nba_data_fetcher, function_name, return_value=[event]), \
                        patch.object(sys, 'argv', ['nba_data_fetcher.py', action, game_id]), \
                        patch.dict(os.environ, {'OUTPUT_FILE': output_path}), \
                        redirect_stdout(stdout):
                    nba_data_fetcher.main()

                payload = json.loads(stdout.getvalue())
                self.assertEqual(payload['source'], source)
                self.assertEqual(payload['quality'], quality)
                self.assertEqual(payload['outcome'], 'SUCCESS')
                self.assertEqual(payload['eventCount'], 1)
                self.assertEqual(payload['events'], [event])
                self.assertEqual(payload['playByPlay'], [event])
            finally:
                if os.path.exists(output_path):
                    os.unlink(output_path)


if __name__ == '__main__':
    unittest.main()
