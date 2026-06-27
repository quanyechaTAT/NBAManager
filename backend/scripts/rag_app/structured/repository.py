from __future__ import annotations

import sys

from rag_app.pipeline.query_classifier import QueryClassification, QueryType
from rag_app.structured.db import Database


class StructuredRepository:
    def __init__(self, database: Database):
        self.database = database

    def search(self, classification: QueryClassification, top_k: int) -> str:
        try:
            if classification.query_type == QueryType.TEAM and classification.teams:
                return self.team_summary(classification.teams)
            if classification.query_type == QueryType.PLAYER and classification.players:
                return self.player_summary(classification.players)
            if classification.query_type == QueryType.MATCH and classification.teams:
                return self.match_summary(classification.teams, top_k)
            if classification.query_type == QueryType.RANKING:
                return self.ranking_summary(top_k)
        except Exception as exc:
            print(f"Structured query failed: {exc}", file=sys.stderr)
        return ""

    def team_summary(self, teams: list[str]) -> str:
        placeholders = ",".join(["%s"] * len(teams))
        sql = f"""
            SELECT id, name, city, conference, wins, losses
            FROM teams
            WHERE name IN ({placeholders})
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, teams)
            rows = cursor.fetchall()
        parts = []
        for row in rows:
            total = (row.get("wins") or 0) + (row.get("losses") or 0)
            pct = (row.get("wins") or 0) / total if total else 0
            parts.append(
                f"{row.get('city') or ''}{row.get('name')}：{row.get('conference')}，"
                f"战绩{row.get('wins')}胜{row.get('losses')}负，胜率{pct:.1%}"
            )
        return "\n".join(parts)

    def player_summary(self, players: list[str]) -> str:
        parts = []
        with self.database.cursor() as cursor:
            for player in players:
                cursor.execute(
                    """
                    SELECT p.name, t.name AS team_name, p.position,
                           p.points_per_game, p.rebounds_per_game, p.assists_per_game,
                           p.field_goal_pct, p.three_point_pct, p.games_played
                    FROM players p
                    LEFT JOIN teams t ON p.team_id = t.id
                    WHERE p.name = %s OR p.name LIKE %s
                    ORDER BY p.points_per_game DESC
                    LIMIT 1
                    """,
                    (player, f"%{player}%"),
                )
                row = cursor.fetchone()
                if row:
                    fg = row.get("field_goal_pct") or 0
                    three = row.get("three_point_pct") or 0
                    parts.append(
                        f"{row.get('name')}（{row.get('team_name')}，{row.get('position')}）："
                        f"出场{row.get('games_played')}场，场均{row.get('points_per_game')}分、"
                        f"{row.get('rebounds_per_game')}篮板、{row.get('assists_per_game')}助攻，"
                        f"投篮命中率{fg * 100:.1f}%，三分命中率{three * 100:.1f}%"
                    )
        return "\n".join(parts)

    def match_summary(self, teams: list[str], top_k: int) -> str:
        placeholders = ",".join(["%s"] * len(teams))
        sql = f"""
            SELECT home_team, away_team, home_score, away_score, match_date, status
            FROM match_records
            WHERE (home_team IN ({placeholders}) OR away_team IN ({placeholders}))
              AND status = 'FINISHED'
            ORDER BY match_date DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, teams + teams + [top_k])
            rows = cursor.fetchall()
        parts = []
        for row in rows:
            winner = row["home_team"] if row["home_score"] > row["away_score"] else row["away_team"]
            parts.append(
                f"{row.get('match_date')} {row.get('home_team')} {row.get('home_score')}:"
                f"{row.get('away_score')} {row.get('away_team')}，{winner}获胜"
            )
        return "\n".join(parts)

    def ranking_summary(self, top_k: int) -> str:
        with self.database.cursor() as cursor:
            cursor.execute(
                """
                SELECT p.name, t.name AS team_name, p.position,
                       p.points_per_game, p.games_played
                FROM players p
                LEFT JOIN teams t ON p.team_id = t.id
                WHERE p.points_per_game > 0 AND p.games_played >= 10
                ORDER BY p.points_per_game DESC
                LIMIT %s
                """,
                (top_k,),
            )
            rows = cursor.fetchall()
        return "\n".join(
            f"{idx + 1}. {row.get('name')}（{row.get('team_name')}）：场均{row.get('points_per_game')}分"
            for idx, row in enumerate(rows)
        )
