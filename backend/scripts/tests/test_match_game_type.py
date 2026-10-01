import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))


class MatchGameTypeTest(unittest.TestCase):

    def test_explicit_nba_cup_label_takes_priority(self):
        from nba_data_fetcher import _resolve_game_type

        self.assertEqual(_resolve_game_type({
            'gameId': '0022500123',
            'gameLabel': 'Emirates NBA Cup - Group Play',
        }), 'NBA_CUP')

    def test_official_playoff_id_is_playoffs(self):
        from nba_data_fetcher import _resolve_game_type

        self.assertEqual(_resolve_game_type({'gameId': '0042500401'}), 'PLAYOFFS')

    def test_official_cup_final_id_is_nba_cup(self):
        from nba_data_fetcher import _resolve_game_type

        self.assertEqual(_resolve_game_type({'gameId': '0062500001'}), 'NBA_CUP')

    def test_summer_label_is_summer_league(self):
        from nba_data_fetcher import _resolve_game_type

        self.assertEqual(_resolve_game_type({
            'gameId': '401881722',
            'gameSubtype': 'NBA Summer League',
        }), 'SUMMER_LEAGUE')

    def test_regular_game_is_regular_season(self):
        from nba_data_fetcher import _resolve_game_type

        self.assertEqual(_resolve_game_type({'gameId': '0022500123'}), 'REGULAR_SEASON')


if __name__ == '__main__':
    unittest.main()
