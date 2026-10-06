"""Index publication checks use stored evidence only, without network or a live DB."""
from contextlib import contextmanager
from pathlib import Path
import json
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from rag_app.indexing.sources import MySQLDocumentSource


class FixtureDatabase:
    def __init__(self, rows):
        self.rows = rows

    @contextmanager
    def cursor(self):
        class Cursor:
            def execute(self, *_):
                pass

            def fetchall(inner_self):
                return self.rows
        yield Cursor()


def test_team_index_never_derives_win_percentage_from_unverified_loss():
    rows = [{'id': 1, 'name': '球队', 'conference': 'EAST', 'wins': 20, 'losses': 0,
             'data_season': '2020-21', 'data_quality': 'PARTIAL',
             'data_field_states': json.dumps({'wins': 'OBSERVED', 'losses': 'UNVERIFIED'})}]
    result = MySQLDocumentSource(FixtureDatabase(rows)).teams()[0]
    assert '2020-21赛季' in result.text
    assert '胜率—' in result.text
    assert '100.0%' not in result.text


def test_match_index_preserves_zero_without_inventing_winner():
    rows = [{'id': 7, 'home_team': '主队', 'away_team': '客队', 'home_score': 0, 'away_score': 0,
             'season': '2025-26', 'data_quality': 'PARTIAL',
             'data_field_states': '{"homeScore":"OBSERVED","awayScore":"OBSERVED"}'}]
    result = MySQLDocumentSource(FixtureDatabase(rows)).matches()[0]
    assert '0:0' in result.text
    assert '结果尚未核验' in result.text
    assert '获胜' not in result.text


def test_news_index_retains_real_link_and_does_not_claim_publication_time():
    rows = [{'id': 3, 'title': '原始资讯', 'summary': '原始摘要', 'source_url': 'https://www.nba.com/news/example', 'create_time': '2026-10-04 12:00:00'}]
    result = MySQLDocumentSource(FixtureDatabase(rows)).news()[0]
    assert result.text == '原始资讯。原始摘要'
    assert result.metadata['source_url'] == rows[0]['source_url']
    assert 'synced_at' not in result.metadata
