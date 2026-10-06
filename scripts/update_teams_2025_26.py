import json
import sys
import os
import pymysql
from nba_api.stats.endpoints import LeagueStandingsV3

sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'backend', 'scripts'))
from nba_data_fetcher import _init_team_map, TEAM_ID_TO_CN

_init_team_map()
print('Fetching 2025-26 standings from NBA API...')
s = LeagueStandingsV3(season='2025-26', timeout=30)
rs = s.get_dict()['resultSets'][0]
h = rs['headers']
rows = [dict(zip(h, r)) for r in rs['rowSet']]

def format_streak(st):
    if not st:
        return ''
    p = st.strip().split()
    if len(p) == 2:
        return f"{p[1]}连胜" if p[0] == 'W' else f"{p[1]}连败"
    return st

conn = pymysql.connect(
    host=os.getenv('DB_HOST', 'localhost'),
    port=int(os.getenv('DB_PORT', '3306')),
    user=os.getenv('DB_USERNAME', 'root'),
    password=os.environ['DB_PASSWORD'],
    db=os.getenv('DB_NAME', 'nba_manager'),
    charset='utf8mb4',
)
cur = conn.cursor()

# Ensure home_record, away_record, streak columns exist on team_season_stats
cur.execute("SHOW COLUMNS FROM team_season_stats LIKE 'home_record'")
if not cur.fetchone():
    cur.execute("ALTER TABLE team_season_stats ADD COLUMN home_record VARCHAR(16) NULL")
cur.execute("SHOW COLUMNS FROM team_season_stats LIKE 'away_record'")
if not cur.fetchone():
    cur.execute("ALTER TABLE team_season_stats ADD COLUMN away_record VARCHAR(16) NULL")
cur.execute("SHOW COLUMNS FROM team_season_stats LIKE 'streak'")
if not cur.fetchone():
    cur.execute("ALTER TABLE team_season_stats ADD COLUMN streak VARCHAR(16) NULL")

for r in rows:
    name = TEAM_ID_TO_CN.get(r['TeamID'], r['TeamCity'])
    wins = int(r['WINS'])
    losses = int(r['LOSSES'])
    ppg = float(r['PointsPG']) if r.get('PointsPG') is not None else None
    oppg = float(r['OppPointsPG']) if r.get('OppPointsPG') is not None else None
    home = r.get('HOME', '')
    road = r.get('ROAD', '')
    streak = format_streak(r.get('strCurrentStreak', ''))
    field_states = json.dumps({'wins': 'OBSERVED', 'losses': 'OBSERVED'})

    # Update teams table
    cur.execute('''
        UPDATE teams
        SET wins = %s, losses = %s, data_season = '2025-26', data_source = 'NBA_API',
            data_quality = 'COMPLETE', data_field_states = %s, data_synced_at = NOW()
        WHERE name = %s
    ''', (wins, losses, field_states, name))

    # Update team_season_stats table for 2025-26
    tss_states = json.dumps({
        'wins': 'OBSERVED',
        'losses': 'OBSERVED',
        'winPct': 'OBSERVED',
        'pointsPerGame': 'OBSERVED',
        'opponentsPointsPerGame': 'OBSERVED',
        'homeRecord': 'OBSERVED',
        'awayRecord': 'OBSERVED',
        'streak': 'OBSERVED'
    })
    cur.execute('''
        UPDATE team_season_stats
        SET wins = %s, losses = %s, points_per_game = %s, opponents_points_per_game = %s,
            home_record = %s, away_record = %s, streak = %s,
            data_season = '2025-26', data_source = 'NBA_API', data_quality = 'COMPLETE',
            data_field_states = %s, data_synced_at = NOW()
        WHERE team_name = %s AND season = '2025-26'
    ''', (wins, losses, ppg, oppg, home, road, streak, tss_states, name))

conn.commit()
print(f'Successfully updated {len(rows)} teams in MySQL!')
