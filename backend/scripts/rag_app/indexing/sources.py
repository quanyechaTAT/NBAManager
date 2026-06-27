from __future__ import annotations

from rag_app.indexing.documents import IndexDocument
from rag_app.structured.db import Database


class MySQLDocumentSource:
    def __init__(self, database: Database):
        self.database = database

    def load_all(self) -> list[IndexDocument]:
        documents: list[IndexDocument] = []
        documents.extend(self.players())
        documents.extend(self.teams())
        documents.extend(self.news())
        documents.extend(self.matches())
        documents.extend(self.historical_players())
        return documents

    def players(self) -> list[IndexDocument]:
        sql = """
            SELECT p.id, p.name, p.name_en, t.name AS team_name, p.position,
                   p.points_per_game, p.rebounds_per_game, p.assists_per_game,
                   p.steals_per_game, p.blocks_per_game, p.games_played
            FROM players p
            LEFT JOIN teams t ON p.team_id = t.id
            WHERE p.points_per_game > 0
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql)
            rows = cursor.fetchall()
        docs = []
        for row in rows:
            text = (
                f"{row.get('name')}是{row.get('team_name')}队的{row.get('position')}，"
                f"本赛季出场{row.get('games_played')}次，场均{row.get('points_per_game')}分、"
                f"{row.get('rebounds_per_game')}篮板、{row.get('assists_per_game')}助攻、"
                f"{row.get('steals_per_game')}抢断、{row.get('blocks_per_game')}盖帽。"
            )
            docs.append(
                IndexDocument(
                    id=f"player:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "player",
                        "id": row.get("id"),
                        "name": row.get("name"),
                        "team": row.get("team_name"),
                    },
                )
            )
        return docs

    def teams(self) -> list[IndexDocument]:
        sql = "SELECT id, name, name_en, city, conference, wins, losses FROM teams"
        with self.database.cursor() as cursor:
            cursor.execute(sql)
            rows = cursor.fetchall()
        docs = []
        for row in rows:
            total = (row.get("wins") or 0) + (row.get("losses") or 0)
            pct = (row.get("wins") or 0) / total if total else 0
            text = (
                f"{row.get('city') or ''}{row.get('name')}是{row.get('conference')}联盟球队，"
                f"本赛季战绩{row.get('wins')}胜{row.get('losses')}负，胜率{pct:.1%}。"
            )
            docs.append(
                IndexDocument(
                    id=f"team:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "team",
                        "id": row.get("id"),
                        "name": row.get("name"),
                        "team": row.get("name"),
                        "conference": row.get("conference"),
                    },
                )
            )
        return docs

    def news(self, limit: int = 500) -> list[IndexDocument]:
        sql = """
            SELECT id, title, summary, category, home_team, away_team
            FROM game_news
            ORDER BY create_time DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, (limit,))
            rows = cursor.fetchall()
        return [
            IndexDocument(
                id=f"news:{row.get('id')}",
                text=f"{row.get('title') or ''}。{row.get('summary') or ''}",
                metadata={
                    "type": "news",
                    "id": row.get("id"),
                    "category": row.get("category"),
                    "team": row.get("home_team") or row.get("away_team") or "",
                },
            )
            for row in rows
        ]

    def matches(self, limit: int = 300) -> list[IndexDocument]:
        sql = """
            SELECT id, home_team, away_team, home_score, away_score,
                   match_date, status, season
            FROM match_records
            WHERE status = 'FINISHED'
            ORDER BY match_date DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, (limit,))
            rows = cursor.fetchall()
        docs = []
        for row in rows:
            winner = row["home_team"] if row["home_score"] > row["away_score"] else row["away_team"]
            text = (
                f"{row.get('match_date')} {row.get('home_team')} {row.get('home_score')}:"
                f"{row.get('away_score')} {row.get('away_team')}，{winner}获胜，赛季{row.get('season')}。"
            )
            docs.append(
                IndexDocument(
                    id=f"match:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "match",
                        "id": row.get("id"),
                        "team": row.get("home_team"),
                        "home_team": row.get("home_team"),
                        "away_team": row.get("away_team"),
                        "match_date": str(row.get("match_date")),
                    },
                )
            )
        return docs

    def historical_players(self, limit: int = 1000) -> list[IndexDocument]:
        sql = """
            SELECT id, season, player_name, team_name, position,
                   points_per_game, rebounds_per_game, assists_per_game
            FROM player_season_stats
            WHERE points_per_game > 0
            ORDER BY season DESC, points_per_game DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, (limit,))
            rows = cursor.fetchall()
        docs = []
        for row in rows:
            text = (
                f"{row.get('season')}赛季 {row.get('player_name')}效力于{row.get('team_name')}，"
                f"位置{row.get('position')}，场均{row.get('points_per_game')}分、"
                f"{row.get('rebounds_per_game')}篮板、{row.get('assists_per_game')}助攻。"
            )
            docs.append(
                IndexDocument(
                    id=f"historical:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "historical",
                        "id": row.get("id"),
                        "season": row.get("season"),
                        "name": row.get("player_name"),
                        "team": row.get("team_name"),
                    },
                )
            )
        return docs
