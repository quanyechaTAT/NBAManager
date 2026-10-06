"""Verified historical NBA games, independently of today's global scoreboard."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from collections import OrderedDict
import json
import re
import threading
import time
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from rag_app.tools.search_tool import SearchProvider, SearchResult, _build_proxy_opener


from rag_app.domain.teams import TEAM_IDS
from rag_app.domain.game_metrics import scoring_breakdown
STAT_LABELS = {
    'fieldGoalsMade-fieldGoalsAttempted': '投篮命中-出手', 'fieldGoalPct': '投篮命中率%',
    'threePointFieldGoalsMade-threePointFieldGoalsAttempted': '三分命中-出手', 'threePointFieldGoalPct': '三分命中率%',
    'freeThrowsMade-freeThrowsAttempted': '罚球命中-出手', 'totalRebounds': '篮板',
    'offensiveRebounds': '进攻篮板', 'assists': '助攻', 'totalTurnovers': '失误',
    'turnovers': '个人失误合计', 'pointsInPaint': '禁区得分', 'fastBreakPoints': '快攻得分',
}


class EspnArchiveProvider(SearchProvider):
    name = 'espn_archive'
    def __init__(self, timeout=3):
        self.timeout = timeout
        self._opener = _build_proxy_opener()
        self._cache = OrderedDict()
        self._lock = threading.Lock()

    def is_available(self):
        return True

    def health_check(self):
        return bool(self._fetch_json('nba/teams/24/schedule'))

    def _fetch_json(self, path):
        now = time.monotonic()
        with self._lock:
            cached = self._cache.get(path)
            if cached and now - cached[0] < 900:
                self._cache.move_to_end(path)
                return cached[1]
        url = 'https://site.api.espn.com/apis/site/v2/sports/basketball/' + path
        with self._opener.open(url, timeout=self.timeout) as response:
            data = json.load(response)
        with self._lock:
            self._cache[path] = (now, data)
            while len(self._cache) > 128:
                self._cache.popitem(last=False)
        return data

    @staticmethod
    def _game_date(event):
        raw = event.get('date') or ''
        if not raw:
            return ''
        moment = datetime.fromisoformat(raw.replace('Z', '+00:00'))
        try:
            return moment.astimezone(ZoneInfo('America/New_York')).date().isoformat()
        except ZoneInfoNotFoundError:
            return moment.date().isoformat()

    def _fact(self, event, scope):
        competitions = event.get('competitions') or []
        if not competitions or not re.fullmatch(r'\d+', str(event.get('id') or '')):
            return None
        competition = competitions[0]
        status = (competition.get('status') or event.get('status') or {}).get('type') or {}
        if not status.get('completed'):
            return None
        competitors = competition.get('competitors') or []
        participants = {str(item.get('team', {}).get('id')) for item in competitors}
        requested = {TEAM_IDS[team] for team in scope['teams'] if team in TEAM_IDS}
        if not requested or not requested.issubset(participants) or len(participants) != 2:
            return None
        home = next((item for item in competitors if item.get('homeAway') == 'home'), None)
        away = next((item for item in competitors if item.get('homeAway') == 'away'), None)
        if not home or not away:
            return None
        def score(item):
            value = item.get('score')
            value = value.get('value', value.get('displayValue')) if isinstance(value, dict) else value
            if value is None or not re.fullmatch(r'\d+(?:\.0+)?', str(value)):
                return None
            return int(float(value))
        home_score, away_score = score(home), score(away)
        if home_score is None or away_score is None or home_score == away_score:
            return None
        game_date = self._game_date(event)
        if not game_date or game_date > datetime.now(timezone.utc).date().isoformat():
            return None
        if scope.get('date') and game_date != scope['date']:
            return None
        if scope.get('year') and not game_date.startswith(str(scope['year'])):
            return None
        season = event.get('season') or {}
        season_type = str((event.get('seasonType') or {}).get('type') or season.get('type') or '')
        if scope.get('playoffs_only') and season_type != '3':
            return None
        season_label = season.get('displayName', '')
        if not season_label and str(season.get('year', '')).isdigit():
            ending_year = int(season['year'])
            season_label = f'{ending_year - 1}-{str(ending_year)[-2:]}'
        game_type = {'1': 'PRESEASON', '2': 'REGULAR_SEASON', '3': 'PLAYOFFS'}.get(season_type, 'UNKNOWN')
        if scope.get('league') == 'nba-summer':
            game_type = 'SUMMER_LEAGUE'
        elif (competition.get('type') or {}).get('type') == 'commissioners-cup':
            game_type = 'NBA_CUP'
        names = {value: key for key, value in TEAM_IDS.items()}
        return {
            'game_id': str(event['id']), 'date': game_date,
            'home_team': names.get(str(home['team']['id']), home['team'].get('displayName', '')),
            'away_team': names.get(str(away['team']['id']), away['team'].get('displayName', '')),
            'home_score': home_score, 'away_score': away_score,
            'season': season_label,
            'game_type': game_type,
            'statistics': {}, 'players': [],
        }

    def _enrich(self, fact, league):
        data = self._fetch_json(f"{league}/summary?event={fact['game_id']}")
        header = data.get('header') or {}
        competition = (header.get('competitions') or [{}])[0]
        ids = {str(item.get('team', {}).get('id')) for item in competition.get('competitors', [])}
        expected = {TEAM_IDS[fact['home_team']], TEAM_IDS[fact['away_team']]}
        if str(header.get('id')) != fact['game_id'] or ids != expected:
            return
        for team in (data.get('boxscore') or {}).get('teams', []):
            team_id = str(team.get('team', {}).get('id'))
            name = next((name for name, value in TEAM_IDS.items() if value == team_id), '')
            if team_id not in expected:
                continue
            stats = {}
            for item in team.get('statistics', []):
                label = STAT_LABELS.get(item.get('name'))
                value = str(item.get('displayValue') or '')
                if label and re.fullmatch(r'[\d.%-]+', value):
                    stats[label] = value
            fact['statistics'][name] = stats
        for team in (data.get('boxscore') or {}).get('players', []):
            team_id = str(team.get('team', {}).get('id'))
            if team_id not in expected:
                continue
            team_name = next(name for name, value in TEAM_IDS.items() if value == team_id)
            leaders = []
            for table in team.get('statistics', []):
                labels = table.get('labels', [])
                if 'PTS' not in labels:
                    continue
                for athlete in table.get('athletes', []):
                    stats = dict(zip(labels, athlete.get('stats', [])))
                    if not str(stats.get('PTS', '')).isdigit() or athlete.get('didNotPlay'):
                        continue
                    leaders.append({'name': athlete.get('athlete', {}).get('displayName', ''), 'team': team_name,
                        'points': int(stats['PTS']), 'rebounds': stats.get('REB'), 'assists': stats.get('AST')})
            fact['players'].extend(sorted(leaders, key=lambda player: player['points'], reverse=True)[:3])

    def search(self, query, limit=5, context=None):
        scope = context or {}
        teams = [team for team in scope.get('teams', []) if team in TEAM_IDS]
        if not teams:
            return []
        league = scope.get('league', 'nba')
        if league not in ('nba', 'nba-summer'):
            return []
        errors = []
        events = []
        if scope.get('date'):
            paths = [f"{league}/scoreboard?dates={scope['date'].replace('-', '')}"]
            groups = [paths]
        else:
            now = datetime.now(timezone.utc)
            season = scope.get('season')
            year = scope.get('year')
            years = [int(season[:4]) + 1] if season else ([year + 1, year] if year else [now.year + (now.month >= 10), now.year + (now.month >= 10) - 1])
            types = [3] if scope.get('playoffs_only') else [2, 3]
            groups = [[f"{league}/teams/{TEAM_IDS[teams[0]]}/schedule?season={year}&seasontype={kind}" for kind in types] for year in years]
        facts = []
        for paths in groups:
            with ThreadPoolExecutor(max_workers=2) as executor:
                futures = [executor.submit(self._fetch_json, path) for path in paths]
                for future in futures:
                    try:
                        events.extend(future.result().get('events') or [])
                    except Exception as exc:
                        errors.append(exc)
            facts = [fact for event in events if (fact := self._fact(event, scope))]
            if facts and not scope.get('year'):
                break
        facts = sorted({fact['game_id']: fact for fact in facts}.values(), key=lambda fact: (fact['date'], fact['game_id']), reverse=True)
        facts = facts[:limit if scope.get('multiple') else 1]
        if not facts and errors:
            raise errors[0]
        if facts:
            try:
                self._enrich(facts[0], league)
            except Exception:
                pass  # A summary outage must not discard verified final scores.
        results = []
        for fact in facts:
            score_line = f"{fact['date']}（NBA比赛日） {fact['away_team']}（客）{fact['away_score']}:{fact['home_score']} {fact['home_team']}（主），已结束，赛事类型{fact['game_type']}。"
            details = [score_line]
            for team, stats in fact['statistics'].items():
                details.append(team + '：' + '，'.join(f'{label}{value}' for label, value in stats.items()))
            for team, parts in scoring_breakdown(fact).items():
                details.append(f"{team}得分构成（计算后与最终比分一致）：两分球{parts['two_point_points']}分，"
                               f"三分球{parts['three_point_points']}分，罚球{parts['free_throw_points']}分。")
            if fact['players']:
                details.append('得分较高球员：' + '；'.join(f"{item['team']} {item['name']} {item['points']}分/{item['rebounds']}篮板/{item['assists']}助攻" for item in fact['players']))
            details.append('未提供逐回合战术事实，不可将统计差异断言为因果。')
            details.append('未提供二次进攻得分与真实回合数；不能由进攻篮板推断二次进攻得分，'
                           '也不能用总出手数推断某个篮板或失误是否增加了出手机会。')
            if not any(scope.get(key) for key in ('date', 'season', 'year', 'multiple')):
                details.insert(0, '未指定日期，按最近一次可核实的直接交锋解读；其他场次请指定日期。')
            results.append(SearchResult(title=f"{fact['date']} {fact['away_team']} vs {fact['home_team']}",
                url=f"https://www.espn.com/nba/game/_/gameId/{fact['game_id']}", snippet='\n'.join(details),
                provider=self.name, source_domain='espn.com', published_at=fact['date'],
                fetched_at=datetime.now(timezone.utc).isoformat(), relevance_score=1.0, facts=fact))
        return results
