from __future__ import annotations

from data_integrity import display_metric
from rag_app.structured.provenance import published_row, source_metadata
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
        documents.extend(self.draft_picks())
        documents.extend(self.injury_reports())
        return documents

    def players(self) -> list[IndexDocument]:
        sql = """
            SELECT p.id, p.name, p.name_en, t.name AS team_name, p.position,
                   p.points_per_game, p.rebounds_per_game, p.assists_per_game,
                   p.steals_per_game, p.blocks_per_game, p.games_played,
                   p.field_goal_pct, p.three_point_pct, p.free_throw_pct,
                   p.true_shooting_pct, p.efficiency, p.minutes_per_game, p.data_source, p.data_source_url, p.data_season, p.data_synced_at, p.data_quality, p.data_field_states
            FROM players p
            LEFT JOIN teams t ON p.team_id = t.id
            WHERE p.data_quality IN ('COMPLETE','PARTIAL')
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql)
            rows = [published_row(row) for row in cursor.fetchall()]
        docs = []
        for row in rows:
            metrics = [("points_per_game", "场均得分", False), ("rebounds_per_game", "场均篮板", False),
                       ("assists_per_game", "场均助攻", False), ("steals_per_game", "场均抢断", False),
                       ("blocks_per_game", "场均盖帽", False), ("field_goal_pct", "投篮命中率", True),
                       ("three_point_pct", "三分命中率", True), ("free_throw_pct", "罚球命中率", True),
                       ("true_shooting_pct", "TS%", True), ("efficiency", "效率值", False)]
            text = f"{row.get('name')}，{row.get('team_name')}，赛季{row.get('data_season') or '未知'}。" + "，".join(label + display_metric(row.get(field), percentage) for field, label, percentage in metrics)
            docs.append(
                IndexDocument(
                    id=f"player:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "player",
                        "source": row.get("data_source") or "UNKNOWN",
                        "source_url": row.get("data_source_url") or "",
                        "season": row.get("data_season") or "",
                        "synced_at": str(row.get("data_synced_at") or ""),
                        "quality": row.get("data_quality") or "UNVERIFIED",
                        "id": row.get("id"),
                        "name": row.get("name"),
                        "team": row.get("team_name"),
                        "ppg": row.get("points_per_game"),
                        "three_pct": row.get("three_point_pct"),
                    },
                )
            )
        return docs

    def teams(self) -> list[IndexDocument]:
        sql = "SELECT id, name, name_en, city, conference, wins, losses, data_source, data_source_url, data_season, data_synced_at, data_quality, data_field_states FROM teams WHERE data_quality IN ('COMPLETE','PARTIAL') AND wins IS NOT NULL AND losses IS NOT NULL"
        with self.database.cursor() as cursor:
            cursor.execute(sql)
            rows = [published_row(row) for row in cursor.fetchall()]
        docs = []
        for row in rows:
            wins, losses = row.get("wins"), row.get("losses")
            total = wins + losses if wins is not None and losses is not None else None
            pct = wins / total if total else None
            text = (
                f"{row.get('city') or ''}{row.get('name')}是{row.get('conference')}联盟球队，"
                f"{row.get('data_season') or '未知'}赛季战绩{display_metric(wins)}胜{display_metric(losses)}负，胜率{display_metric(pct, percentage=True)}。"
            )
            docs.append(
                IndexDocument(
                    id=f"team:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "team", **source_metadata(row),
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
            SELECT id, title, summary, category, home_team, away_team, source_url, create_time
            FROM game_news
            ORDER BY create_time DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, (limit,))
            rows = [published_row(row) for row in cursor.fetchall()]
        return [
            IndexDocument(
                id=f"news:{row.get('id')}",
                text=f"{row.get('title') or ''}。{row.get('summary') or ''}",
                metadata={
                    "type": "news",
                    "title": row.get("title"),
                    "source": "EXTERNAL_NEWS" if row.get("source_url") else "UNKNOWN",
                    "source_url": row.get("source_url") or "",
                    "recorded_at": str(row.get("create_time") or ""),
                    "quality": "UNVERIFIED",
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
                   match_date, status, season, data_source, data_source_url, data_season, data_synced_at, data_quality, data_field_states
            FROM match_records
            WHERE status = 'FINISHED' AND data_quality IN ('COMPLETE','PARTIAL') AND home_score IS NOT NULL AND away_score IS NOT NULL
            ORDER BY match_date DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, (limit,))
            rows = [published_row(row) for row in cursor.fetchall()]
        docs = []
        for row in rows:
            if row.get("home_score") is None or row.get("away_score") is None: continue
            result = "结果尚未核验" if row["home_score"] == row["away_score"] else f"{row['home_team'] if row['home_score'] > row['away_score'] else row['away_team']}获胜"
            text = (
                f"{row.get('match_date')} {row.get('home_team')} {row.get('home_score')}:"
                f"{row.get('away_score')} {row.get('away_team')}，{result}，赛季{row.get('season') or '未知'}。"
            )
            docs.append(
                IndexDocument(
                    id=f"match:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "match", **source_metadata(row),
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
                   points_per_game, rebounds_per_game, assists_per_game, data_source, data_source_url, data_season, data_synced_at, data_quality, data_field_states
            FROM player_season_stats
            WHERE data_quality IN ('COMPLETE','PARTIAL')
            ORDER BY season DESC, points_per_game DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, (limit,))
            rows = [published_row(row) for row in cursor.fetchall()]
        docs = []
        for row in rows:
            text = (
                f"{row.get('season')}赛季 {row.get('player_name')}效力于{row.get('team_name')}，"
                f"位置{row.get('position')}，场均{display_metric(row.get('points_per_game'))}分、"
                f"{display_metric(row.get('rebounds_per_game'))}篮板、{display_metric(row.get('assists_per_game'))}助攻。"
            )
            docs.append(
                IndexDocument(
                    id=f"historical:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "historical", **source_metadata(row),
                        "id": row.get("id"),
                        "season": row.get("season"),
                        "name": row.get("player_name"),
                        "team": row.get("team_name"),
                    },
                )
            )
        return docs

    def draft_picks(self, limit: int = 500) -> list[IndexDocument]:
        sql = """
            SELECT id, year, round, pick_number, player_name, team_name, player_position, notes
            FROM draft_picks
            ORDER BY year DESC, pick_number ASC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, (limit,))
            rows = [published_row(row) for row in cursor.fetchall()]
        docs = []
        for row in rows:
            text = (
                f"{row.get('year')}年NBA选秀第{row.get('round')}轮第{row.get('pick_number')}顺位："
                f"{row.get('player_name')}被{row.get('team_name')}选中，位置{row.get('player_position') or '未知'}。"
                f"{row.get('notes') or ''}"
            )
            docs.append(
                IndexDocument(
                    id=f"draft:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "draft",
                        "id": row.get("id"),
                        "year": row.get("year"),
                        "name": row.get("player_name"),
                        "team": row.get("team_name"),
                    },
                )
            )
        return docs

    def injury_reports(self, limit: int = 200) -> list[IndexDocument]:
        sql = """
            SELECT id, player_name, team_name, injury_type, description, status, start_date, expected_return
            FROM injury_reports
            ORDER BY start_date DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, (limit,))
            rows = [published_row(row) for row in cursor.fetchall()]
        docs = []
        for row in rows:
            text = (
                f"伤病报告：{row.get('player_name')}（{row.get('team_name')}），"
                f"伤病类型：{row.get('injury_type') or '未知'}，"
                f"状态：{row.get('status') or '未知'}，"
                f"开始日期：{row.get('start_date') or '未知'}，"
                f"预计回归：{row.get('expected_return') or '未知'}。"
                f"{row.get('description') or ''}"
            )
            docs.append(
                IndexDocument(
                    id=f"injury:{row.get('id')}",
                    text=text,
                    metadata={
                        "type": "injury",
                        "id": row.get("id"),
                        "name": row.get("player_name"),
                        "team": row.get("team_name"),
                    },
                )
            )
        return docs
