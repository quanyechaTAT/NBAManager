from __future__ import annotations

import re
from data_integrity import display_metric
from rag_app.structured.provenance import published_row

from rag_app.pipeline.query_classifier import QueryClassification, QueryType
from rag_app.pipeline.game_scope import game_context
from rag_app.structured.db import Database


def published_sql(alias: str, field: str) -> str:
    """Only module-owned column names enter this SQL; unknown field states never publish."""
    camel = ''.join(word if i == 0 else word.capitalize() for i, word in enumerate(field.split('_')))
    return (f"{alias}.{field} IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT("
            f"CASE WHEN JSON_VALID({alias}.data_field_states) THEN {alias}.data_field_states ELSE '{{}}' END, "
            f"'$.{camel}')) IN ('OBSERVED','DERIVED')")


class StructuredRepository:
    def __init__(self, database: Database):
        self.database = database

    def search(self, classification: QueryClassification, top_k: int, question: str = "") -> str:
        if classification.query_type == QueryType.TEAM and classification.teams:
            team_text = self.team_summary(classification.teams)
            if self._needs_recent_matches(question):
                match_text = self.match_summary(classification.teams, top_k, question)
                return "\n\n".join(part for part in (team_text, match_text) if part)
            return team_text
        if classification.query_type == QueryType.PLAYER and classification.players:
            return self.player_summary(classification.players, question)
        if classification.query_type == QueryType.MATCH and classification.teams:
            return self.match_summary(classification.teams, top_k, question)
        if classification.query_type == QueryType.RANKING:
            requested_limit = self._requested_limit(question, top_k)
            if any(keyword in question.lower() for keyword in ["选秀", "draft", "顺位", "新秀"]):
                year_match = re.search(r"(20\d{2})年", question)
                year = int(year_match.group(1)) if year_match else None
                return self.draft_summary(requested_limit, year=year)
            return self.ranking_summary(requested_limit, question)
        # Keyword-based fallback for common queries
        return ""

    def team_ranking(self, conference: str | None = None, top_k: int = 15) -> str:
        """查询球队排名（按胜率排序）"""
        where_clause = "WHERE t.data_quality IN ('COMPLETE','PARTIAL') AND " + published_sql('t', 'wins') + " AND " + published_sql('t', 'losses') + (" AND t.conference = %s" if conference else "")
        params = [conference] if conference else []
        params.append(top_k)
        sql = f"""
            SELECT t.name, t.city, t.conference, t.wins, t.losses, t.data_quality, t.data_field_states,
                   (t.wins + t.losses) as total,
                   CASE WHEN (t.wins + t.losses) > 0
                        THEN t.wins / (t.wins + t.losses)
                        ELSE NULL END as pct
            FROM teams t
            {where_clause}
            ORDER BY pct DESC, t.wins DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, params)
            rows = [published_row(row) for row in cursor.fetchall()]
        if not rows:
            return ""
        conf_label = conference if conference else "联盟"
        parts = [f"{conf_label}排名（按胜率）："]
        for idx, row in enumerate(rows):
            total = (row.get("wins") or 0) + (row.get("losses") or 0)
            pct = (row.get("wins") or 0) / total if total else None
            parts.append(
                f"  {idx + 1}. {row.get('city')}{row.get('name')}："
                f"{display_metric(row.get('wins'))}胜{display_metric(row.get('losses'))}负，胜率{display_metric(pct, percentage=True)}"
            )
        return "\n".join(parts)

    def search_by_keywords(self, question: str, top_k: int = 5) -> str:
        """补充查询：选秀、伤病、三分命中率、排名等关键词匹配"""
        q = question.lower()
        requested_limit = self._requested_limit(question, top_k)
        parts = []
        if any(kw in q for kw in ["选秀", "draft", "顺位", "新秀"]):
            year_match = re.search(r"(20\d{2})年", question)
            year = int(year_match.group(1)) if year_match else None
            # 提取球员名（从 PLAYER_ALIASES 匹配）
            player_name = None
            from rag_app.pipeline.query_classifier import PLAYER_ALIASES
            for alias, name in PLAYER_ALIASES.items():
                if alias in question and name != "__OUT_OF_SCOPE__":
                    player_name = name
                    break
            parts.append(self.draft_summary(requested_limit, year=year, player_name=player_name))
        if any(kw in q for kw in ["伤病", "受伤", "伤情", "injury"]):
            parts.append(self.injury_summary(requested_limit))
        if any(kw in q for kw in ["三分", "三分球", "three-point", "3分"]):
            parts.append(self.three_point_leaders(requested_limit))
        if any(kw in q for kw in ["排名", "西部排名", "东部排名", "战绩", "胜率"]):
            conf = "西部" if "西部" in q else ("东部" if "东部" in q else None)
            parts.append(self.team_ranking(conf, requested_limit))
        return "\n\n".join(p for p in parts if p)

    def team_summary(self, teams: list[str]) -> str:
        placeholders = ",".join(["%s"] * len(teams))
        sql = f"""
            SELECT id, name, city, conference, wins, losses, data_quality, data_field_states
            FROM teams t
            WHERE name IN ({placeholders}) AND data_quality IN ('COMPLETE','PARTIAL')
              AND {published_sql('t', 'wins')} AND {published_sql('t', 'losses')}
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, teams)
            rows = [published_row(row) for row in cursor.fetchall()]
        parts = []
        for row in rows:
            total = (row.get("wins") or 0) + (row.get("losses") or 0)
            pct = (row.get("wins") or 0) / total if total else None
            parts.append(
                f"{row.get('city') or ''}{row.get('name')}：{row.get('conference')}，"
                f"战绩{display_metric(row.get('wins'))}胜{display_metric(row.get('losses'))}负，胜率{display_metric(pct, percentage=True)}"
            )
        return "\n".join(parts)

    _PLAYER_STATS_COLS = (
        "p.name, t.name AS team_name, p.position, "
        "p.points_per_game, p.rebounds_per_game, p.assists_per_game, "
        "p.steals_per_game, p.blocks_per_game, p.turnovers_per_game, "
        "p.field_goal_pct, p.three_point_pct, p.true_shooting_pct, "
        "p.games_played, p.efficiency, p.per, p.usage_pct, p.data_season, p.data_source, p.data_synced_at, p.data_quality, p.data_field_states"
    )

    def player_summary(self, players: list[str], question: str = "") -> str:
        rows = []
        cols = self._PLAYER_STATS_COLS
        with self.database.cursor() as cursor:
            for player in players:
                # 精确匹配优先
                cursor.execute(
                    f"""
                    SELECT {cols}
                    FROM players p
                    LEFT JOIN teams t ON p.team_id = t.id
                    WHERE p.name = %s AND p.data_quality IN ('COMPLETE','PARTIAL')
                    ORDER BY p.points_per_game DESC
                    LIMIT 1
                    """,
                    (player,),
                )
                row = published_row(cursor.fetchone())
                # 精确匹配失败时才用模糊匹配
                if not row:
                    cursor.execute(
                        f"""
                        SELECT {cols}
                        FROM players p
                        LEFT JOIN teams t ON p.team_id = t.id
                        WHERE p.name LIKE %s AND p.data_quality IN ('COMPLETE','PARTIAL')
                        ORDER BY p.points_per_game DESC
                        LIMIT 1
                        """,
                        (f"%{player}%",),
                    )
                    row = published_row(cursor.fetchone())
                if row:
                    rows.append(row)
        if len(rows) > 1 and re.search(r"对比|比较|谁更|哪个更", question):
            return self._format_player_comparison(rows, question)
        return "\n".join(self._format_player_row(row, question) for row in rows)

    def match_summary(self, teams: list[str], top_k: int, question: str = "") -> str:
        scope = game_context(question, teams)
        requested_limit = self._requested_limit(question, top_k)
        if len(teams) == 2 and not scope['multiple']:
            requested_limit = 1
        placeholders = ",".join(["%s"] * len(teams))
        # With two teams this is a head-to-head query, not the union of their schedules.
        conjunction = 'AND' if len(teams) == 2 else 'OR'
        constraints = []
        params = teams + teams
        if scope['date']:
            constraints.append('match_date = %s')
            params.append(scope['date'])
        if scope['season']:
            constraints.append('season = %s')
            params.append(scope['season'])
        if scope['year']:
            constraints.append('match_date >= %s AND match_date < %s')
            params.extend([f"{scope['year']}-01-01", f"{scope['year'] + 1}-01-01"])
        constraints.append("game_type = 'SUMMER_LEAGUE'" if scope['league'] == 'nba-summer' else "(game_type IS NULL OR game_type <> 'SUMMER_LEAGUE')")
        if scope['playoffs_only']:
            constraints.append("game_type = 'PLAYOFFS'")
        extra_where = ' AND '.join(constraints)
        sql = f"""
            SELECT home_team, away_team, home_score, away_score, match_date, status, data_quality, data_field_states
            FROM match_records m
            WHERE (home_team IN ({placeholders}) {conjunction} away_team IN ({placeholders}))
              AND {extra_where}
              AND status = 'FINISHED' AND data_quality IN ('COMPLETE','PARTIAL') AND home_score IS NOT NULL AND away_score IS NOT NULL
              AND {published_sql('m', 'home_score')} AND {published_sql('m', 'away_score')}
            ORDER BY match_date DESC
            LIMIT %s
        """
        with self.database.cursor() as cursor:
            cursor.execute(sql, params + [requested_limit])
            rows = [published_row(row) for row in cursor.fetchall()]
        if not rows:
            return ""
        label = "、".join(teams)
        parts = [f"{label}直接交锋（已核实，{len(rows)}场）：" if len(teams) == 2 else f"{label}最近{len(rows)}场已结束比赛："]
        if len(teams) == 2 and not any(scope[key] for key in ('date', 'season', 'year', 'multiple')):
            parts.append('未指定日期，以下按数据库最近一次已核实的直接交锋分析；其他场次请指定日期。')
        for index, row in enumerate(rows, start=1):
            if row.get('home_score') is None or row.get('away_score') is None:
                continue
            winner = row["home_team"] if row["home_score"] > row["away_score"] else row["away_team"]
            result = f"{winner}获胜" if row["home_score"] != row["away_score"] else "比分相同，结果未确认"
            parts.append(
                f"{index}. {row.get('match_date')} {row.get('home_team')}（主）"
                f"{row.get('home_score')}:{row.get('away_score')} {row.get('away_team')}（客），"
                f"{result}"
            )
        return "\n".join(parts)

    def _format_player_row(self, row: dict, question: str) -> str:
        name = row.get("name")
        requested = []
        metric_specs = [
            (r"三分", "three_point_pct", "三分命中率", True, "%"),
            (r"投篮命中率|投篮", "field_goal_pct", "投篮命中率", True, "%"),
            (r"真实命中率|真实投篮命中率|TS%", "true_shooting_pct", "真实命中率", True, "%"),
            (r"场均得分|得分", "points_per_game", "场均得分", False, "分"),
            (r"篮板", "rebounds_per_game", "场均篮板", False, "个"),
            (r"助攻", "assists_per_game", "场均助攻", False, "次"),
            (r"抢断", "steals_per_game", "场均抢断", False, "次"),
            (r"盖帽", "blocks_per_game", "场均盖帽", False, "次"),
            (r"失误", "turnovers_per_game", "场均失误", False, "次"),
            (r"出场|打了多少场", "games_played", "出场", False, "场"),
        ]
        for pattern, field, label, percentage, unit in metric_specs:
            if re.search(pattern, question):
                value = row.get(field)
                if value is not None:
                    formatted = f"{float(value) * 100:.1f}" if percentage else self._format_number(value)
                    requested.append(f"{label}为 {formatted}{unit}")

        if requested:
            return f"{name}（{row.get('data_season') or '赛季未知'}）" + "，".join(requested) + "。"

        metrics = [("points_per_game", "场均得分", False), ("rebounds_per_game", "场均篮板", False),
                   ("assists_per_game", "场均助攻", False), ("steals_per_game", "场均抢断", False),
                   ("blocks_per_game", "场均盖帽", False), ("field_goal_pct", "投篮命中率", True),
                   ("three_point_pct", "三分命中率", True), ("true_shooting_pct", "TS%", True),
                   ("usage_pct", "USG%（0–100）", False)]
        values = "，".join(label + display_metric(row.get(field), percentage) for field, label, percentage in metrics)
        season = row.get("data_season") or "未知赛季"
        return f"{name}（{row.get('team_name')}）：{season}，{values}。缺失指标不参与计算。"

    def _format_player_comparison(self, rows: list[dict], question: str) -> str:
        # 对比时始终展示全部核心数据，不限于问题中提到的关键词
        metric_specs = [
            (r"得分|场均得分", "points_per_game", "场均得分", False, "分"),
            (r"篮板", "rebounds_per_game", "场均篮板", False, "个"),
            (r"助攻", "assists_per_game", "场均助攻", False, "次"),
            (r"抢断", "steals_per_game", "场均抢断", False, "次"),
            (r"盖帽", "blocks_per_game", "场均盖帽", False, "次"),
            (r"失误", "turnovers_per_game", "场均失误", False, "次"),
            (r"命中率|投篮", "field_goal_pct", "投篮命中率", True, "%"),
            (r"三分", "three_point_pct", "三分命中率", True, "%"),
            (r"真实命中率|真实投篮命中率|TS%", "true_shooting_pct", "真实命中率", True, "%"),
        ]
        parts = ["对比数据："]
        parts.extend(self._format_player_row(row, question) for row in rows)
        conclusions = ["逐项比较："]
        for pattern, field, label, percentage, unit in metric_specs:
            values = [
                (row, row.get(field))
                for row in rows
                if row.get(field) is not None
            ]
            if len(values) < 2:
                continue
            numeric_values = [float(value) for _row, value in values]
            best = min(numeric_values) if field == "turnovers_per_game" else max(numeric_values)
            leaders = [row.get("name") for row, value in values if float(value) == best]
            rendered = "，".join(
                f"{row.get('name')} {float(value) * 100:.1f}{unit}"
                if percentage
                else f"{row.get('name')} {self._format_number(value)}{unit}"
                for row, value in values
            )
            leader_text = (
                f"{leaders[0]}领先"
                if len(leaders) == 1
                else f"{'、'.join(leaders)}并列领先"
            )
            conclusions.append(f"- {label}：{rendered}；{leader_text}。")
        if len(conclusions) > 1:
            parts.extend(conclusions)
        return "\n".join(parts)

    @staticmethod
    def _needs_recent_matches(question: str) -> bool:
        return bool(re.search(
            r"最近.{0,10}(?:场|比赛|结果|赛果|战绩)|比赛结果|赛果|对阵",
            question,
        ))

    @staticmethod
    def _format_number(value) -> str:
        number = float(value)
        return str(int(number)) if number.is_integer() else f"{number:g}"

    @staticmethod
    def _requested_limit(question: str, default: int) -> int:
        chinese_numbers = {
            "一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5,
            "六": 6, "七": 7, "八": 8, "九": 9, "十": 10,
        }
        patterns = [
            r"(?:最近|近|前)\s*([一二两三四五六七八九十]|\d+)\s*(?:场|名|位|条)?",
            r"([一二两三四五六七八九十]|\d+)\s*(?:场|名|位|条)",
            r"top\s*(\d+)",
        ]
        for pattern in patterns:
            match = re.search(pattern, question, re.IGNORECASE)
            if not match:
                continue
            raw = match.group(1)
            value = chinese_numbers.get(raw, int(raw) if raw.isdigit() else default)
            return max(1, min(value, 20))
        return max(1, min(default, 20))

    def ranking_summary(self, top_k: int, question: str = "") -> str:
        q = question.lower()
        # 球队排名查询
        if any(kw in q for kw in ["球队", "战绩", "西部", "东部", "联盟"]):
            conf = "西部" if "西部" in q else ("东部" if "东部" in q else None)
            return self.team_ranking(conf, top_k)
        # 根据问题中的指标关键词选择排序字段
        metric_map = [
            (["三分", "three-point", "3分", "三分球"], "three_point_pct", "三分命中率", True),
            (["投篮命中率", "fg%", "field goal"], "field_goal_pct", "投篮命中率", True),
            (["罚球", "free throw"], "free_throw_pct", "罚球命中率", True),
            (["篮板", "rebounds"], "rebounds_per_game", "篮板", False),
            (["助攻", "assists"], "assists_per_game", "助攻", False),
            (["抢断", "steals"], "steals_per_game", "抢断", False),
            (["盖帽", "blocks"], "blocks_per_game", "盖帽", False),
            (["per"], "per", "PER", False),
            (["效率值", "efficiency"], "efficiency", "效率值", False),
            (["失误", "turnovers"], "turnovers_per_game", "失误", False),
            (["上场时间", "分钟", "minutes"], "minutes_per_game", "上场时间", False),
            (["使用率", "usage"], "usage_pct", "使用率", False),
        ]
        metric_col = "points_per_game"
        metric_label = "得分"
        is_pct = False
        for keywords, col, label, pct in metric_map:
            if any(kw in q for kw in keywords):
                metric_col = col
                metric_label = label
                is_pct = pct
                break

        with self.database.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT p.name, t.name AS team_name, p.{metric_col} AS metric_val
                FROM players p
                LEFT JOIN teams t ON p.team_id = t.id
                WHERE p.data_quality IN ('COMPLETE','PARTIAL') AND p.{metric_col} IS NOT NULL AND p.games_played >= 10
                  AND {published_sql('p', metric_col)} AND {published_sql('p', 'games_played')}
                ORDER BY p.{metric_col} {'ASC' if metric_col == 'turnovers_per_game' else 'DESC'}
                LIMIT %s
                """,
                (top_k,),
            )
            rows = [published_row(row) for row in cursor.fetchall()]
        if not rows:
            return ""
        parts = [f"{metric_label}排名："]
        for idx, row in enumerate(rows):
            val = row.get("metric_val")
            if val is None: continue
            if is_pct:
                parts.append(f"  {idx + 1}. {row.get('name')}（{row.get('team_name')}）：{val * 100:.1f}%")
            else:
                parts.append(f"  {idx + 1}. {row.get('name')}（{row.get('team_name')}）：{val}")
        return "\n".join(parts)

    def draft_summary(self, top_k: int = 10, year: int | None = None, player_name: str | None = None) -> str:
        with self.database.cursor() as cursor:
            if player_name:
                cursor.execute(
                    """
                    SELECT year, round, pick_number, player_name, team_name, player_position
                    FROM draft_picks
                    WHERE player_name LIKE %s
                    ORDER BY year DESC
                    LIMIT %s
                    """,
                    (f"%{player_name}%", top_k),
                )
            elif year:
                cursor.execute(
                    """
                    SELECT year, round, pick_number, player_name, team_name, player_position
                    FROM draft_picks
                    WHERE year = %s
                    ORDER BY pick_number ASC
                    LIMIT %s
                    """,
                    (year, top_k),
                )
            else:
                cursor.execute(
                    """
                    SELECT year, round, pick_number, player_name, team_name, player_position
                    FROM draft_picks
                    ORDER BY year DESC, pick_number ASC
                    LIMIT %s
                    """,
                    (top_k,),
                )
            rows = [published_row(row) for row in cursor.fetchall()]
        if not rows:
            return ""
        if player_name:
            label = f"{player_name}选秀数据"
        elif year:
            label = f"{year}年选秀数据"
        else:
            label = f"选秀数据（最近{len(rows)}条）"
        parts = [f"{label}："]
        for row in rows:
            parts.append(
                f"  {row.get('year')}年 R{row.get('round')}P{row.get('pick_number')}: "
                f"{row.get('player_name')} -> {row.get('team_name')}，位置{row.get('player_position') or '未知'}"
            )
        return "\n".join(parts)

    def injury_summary(self, top_k: int = 10) -> str:
        with self.database.cursor() as cursor:
            cursor.execute(
                """
                SELECT player_name, team_name, injury_type, status, start_date, expected_return
                FROM injury_reports
                ORDER BY start_date DESC
                LIMIT %s
                """,
                (top_k,),
            )
            rows = [published_row(row) for row in cursor.fetchall()]
        if not rows:
            return "伤病报告：暂无伤病数据。"
        parts = [f"最近伤病报告（{len(rows)}条）："]
        for row in rows:
            parts.append(
                f"  {row.get('player_name')}（{row.get('team_name')}）：{row.get('injury_type') or '未知'}，"
                f"状态{row.get('status') or '未知'}，预计回归{row.get('expected_return') or '未知'}"
            )
        return "\n".join(parts)

    def three_point_leaders(self, top_k: int = 10) -> str:
        with self.database.cursor() as cursor:
            cursor.execute(
                f"""
                SELECT p.name, t.name AS team_name, p.three_point_pct, p.games_played
                FROM players p
                LEFT JOIN teams t ON p.team_id = t.id
                WHERE p.data_quality IN ('COMPLETE','PARTIAL') AND p.three_point_pct IS NOT NULL AND p.games_played >= 10
                  AND {published_sql('p', 'three_point_pct')} AND {published_sql('p', 'games_played')}
                ORDER BY p.three_point_pct DESC
                LIMIT %s
                """,
                (top_k,),
            )
            rows = [published_row(row) for row in cursor.fetchall()]
        if not rows:
            return ""
        parts = ["三分命中率排名："]
        for idx, row in enumerate(rows):
            pct = (row.get("three_point_pct") or 0) * 100
            parts.append(f"  {idx + 1}. {row.get('name')}（{row.get('team_name')}）：{pct:.1f}%")
        return "\n".join(parts)
