"""Helpers for the explicitly isolated local acceptance stack, never the user's business database."""
from dataclasses import replace
from datetime import datetime, timedelta
import json
import os
from pathlib import Path
import time

import httpx

from discovery_app.config import Settings
from discovery_app.database import Database

ROOT = Path(__file__).resolve().parents[3]
RUNTIME = ROOT / 'backend' / 'scripts' / 'runtime' / 'discovery'
JAVA = 'http://127.0.0.1:18080'


def settings():
    values = dict(line.split('=', 1) for line in (ROOT/'deploy/discovery/.env.local').read_text().splitlines() if '=' in line)
    if values.get('DISCOVERY_DB_NAME') != 'nba_discovery_test' or values.get('DISCOVERY_DB_PORT') != '13306':
        raise RuntimeError('Acceptance helpers only support nba_discovery_test on port 13306')
    return replace(Settings(), db_host='127.0.0.1', db_port=13306, db_name='nba_discovery_test', db_user='root',
                   db_password=values['DISCOVERY_DB_PASSWORD'], internal_token=values['DISCOVERY_INTERNAL_TOKEN'],
                   search_url='http://127.0.0.1:19200', redis_url='redis://127.0.0.1:16379/2', runtime=RUNTIME)


def eventually(function, seconds=30):
    until=time.monotonic()+seconds
    error=None
    while time.monotonic()<until:
        try:
            value=function()
            if value: return value
        except Exception as caught: error=caught
        time.sleep(.3)
    raise AssertionError(f'Condition did not become true within {seconds}s: {error}')


def user(name='discovery_reader', admin=False):
    password='Local-Discovery-Acceptance-2026!'
    with httpx.Client(base_url=JAVA,timeout=15) as client:
        response=client.post('/api/auth/register',json={'username':name,'password':password})
        if response.status_code not in (200,201,400,409): response.raise_for_status()
        db=Database(settings())
        if admin: db.execute("UPDATE user_accounts SET role='ADMIN' WHERE username=%s",(name,))
        response=client.post('/api/auth/login',json={'username':name,'password':password}); response.raise_for_status()
        token=response.json()['token']
        identifier=db.rows('SELECT id FROM user_accounts WHERE username=%s',(name,))[0]['id']
        return {'id':identifier,'name':name,'token':token}


def seed():
    s=settings(); db=Database(s)
    eventually(lambda: db.rows("SHOW TABLES LIKE 'discovery_requests'"),60)
    people=[user('discovery_reader'),user('discovery_other'),user('discovery_admin',admin=True)]
    teams=[]
    for name,en,abbreviation,city,conference in [('湖人','Los Angeles Lakers','LAL','洛杉矶','西部'),
            ('勇士','Golden State Warriors','GSW','旧金山','西部'),('凯尔特人','Boston Celtics','BOS','波士顿','东部')]:
        found=db.rows('SELECT id FROM teams WHERE name=%s',(name,))
        if not found:
            db.execute('INSERT INTO teams(name,name_en,abbreviation,city,conference) VALUES(%s,%s,%s,%s,%s)',(name,en,abbreviation,city,conference))
            found=db.rows('SELECT id FROM teams WHERE name=%s',(name,))
        teams.append(found[0]['id'])
    for name,en,team in [('勒布朗·詹姆斯','LeBron James',teams[0]),('斯蒂芬·库里','Stephen Curry',teams[1]),('杰森·塔图姆','Jayson Tatum',teams[2])]:
        if not db.rows('SELECT id FROM players WHERE name_en=%s',(en,)):
            db.execute("INSERT INTO players(name,name_en,team_id,position,translation_status) VALUES(%s,%s,%s,'G','MAPPED')",(name,en,team))
    now=datetime.now()
    for number in range(1,61):
        title=f'示例 {number:02d} · '+['库里与勇士的三分配合','詹姆斯与湖人的进攻选择','凯尔特人的团队防守'][number%3]
        if not db.rows('SELECT id FROM game_news WHERE title=%s',(title,)):
            publication=now-timedelta(hours=number)
            db.execute("INSERT INTO game_news(title,title_en,summary,content,home_team,away_team,create_time,published_at,published_at_source,"
                       "game_start_time,game_end_time,status,category,view_count,favorite_count) VALUES(%s,%s,%s,%s,'','',%s,%s,'INGESTED',%s,%s,'FINISHED','general',%s,0)",
                       (title,['Stephen Curry Warriors','LeBron James Lakers','Boston Celtics'][number%3],
                        '从球员选择、空间分配到团队协作，观察篮球比赛中的细节。','本条为独立测试库中的验收样本，不代表真实赛况。'+title,
                        now,publication,publication,publication+timedelta(hours=2),number*3))
        if not db.rows('SELECT id FROM posts WHERE title=%s',(title,)):
            db.execute("INSERT INTO posts(user_id,title,content,category,tags,view_count,like_count,comment_count,favorite_count,is_top,is_featured,has_poll,create_time,update_time) "
                       "VALUES(%s,%s,%s,'DISCUSSION','篮球,战术',%s,%s,0,0,FALSE,FALSE,FALSE,%s,%s)",
                       (people[number%3]['id'],title,'这是独立测试库的球迷讨论样本。'+title,number,number%5,now-timedelta(hours=number),now))
    if not db.rows('SELECT id FROM match_records LIMIT 1'):
        db.execute("INSERT INTO match_records(home_team,away_team,home_team_en,away_team_en,home_score,away_score,match_date,season,status) "
                   "VALUES('湖人','勇士','Los Angeles Lakers','Golden State Warriors',100,101,%s,'2026-27','FINISHED')",(now.date(),))
    db.execute("INSERT IGNORE INTO discovery_entity_aliases(alias,item_key) SELECT '金州勇士',CONCAT('TEAM:',id) FROM teams WHERE abbreviation='GSW'")
    (RUNTIME/'test-users.json').write_text(json.dumps(people),encoding='utf-8')
    print('Seeded isolated acceptance data: three test users, teams/players, 60 news, 60 posts and one match.')


if __name__=='__main__': seed()
