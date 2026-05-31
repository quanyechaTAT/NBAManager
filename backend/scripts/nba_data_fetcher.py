#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NBA数据获取脚本
从nba_api获取真实的NBA数据（stats.nba.com + cdn.nba.com）
"""

import json
import sys
import time
from datetime import datetime, timedelta

# 确保stdout使用UTF-8编码（Windows兼容）
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

try:
    from nba_api.stats.endpoints import (
        LeagueStandingsV3,
        LeagueDashPlayerStats,
        ScoreboardV2,
        BoxScoreTraditionalV2,
        PlayByPlayV2,
        PlayerCareerStats,
        PlayerGameLog,
    )
    from nba_api.live.nba.endpoints import scoreboard as live_scoreboard
    from nba_api.stats.static import teams as nba_teams
    HAS_NBA_API = True
except ImportError:
    HAS_NBA_API = False

# 球队英文名/缩写到中文名映射
TEAM_NAME_MAP = {
    'Detroit Pistons': '活塞', 'Atlanta Hawks': '老鹰', 'Boston Celtics': '凯尔特人',
    'Philadelphia 76ers': '76人', 'Orlando Magic': '魔术', 'Toronto Raptors': '猛龙',
    'Cleveland Cavaliers': '骑士', 'New York Knicks': '尼克斯', 'Miami Heat': '热火',
    'Charlotte Hornets': '黄蜂', 'Washington Wizards': '奇才', 'Indiana Pacers': '步行者',
    'Brooklyn Nets': '篮网', 'Chicago Bulls': '公牛', 'Milwaukee Bucks': '雄鹿',
    'Los Angeles Lakers': '湖人', 'San Antonio Spurs': '马刺', 'Portland Trail Blazers': '开拓者',
    'Phoenix Suns': '太阳', 'Minnesota Timberwolves': '森林狼', 'Houston Rockets': '火箭',
    'Denver Nuggets': '掘金', 'Golden State Warriors': '勇士', 'LA Clippers': '快船',
    'Sacramento Kings': '国王', 'Utah Jazz': '爵士', 'Memphis Grizzlies': '灰熊',
    'New Orleans Pelicans': '鹈鹕', 'Dallas Mavericks': '独行侠', 'Oklahoma City Thunder': '雷霆',
}

TEAM_ABBR_MAP = {
    'DET': '活塞', 'ATL': '老鹰', 'BOS': '凯尔特人', 'PHI': '76人',
    'ORL': '魔术', 'TOR': '猛龙', 'CLE': '骑士', 'NYK': '尼克斯',
    'MIA': '热火', 'CHA': '黄蜂', 'WSH': '奇才', 'IND': '步行者',
    'BKN': '篮网', 'CHI': '公牛', 'MIL': '雄鹿',
    'LAL': '湖人', 'SAS': '马刺', 'POR': '开拓者', 'PHX': '太阳',
    'MIN': '森林狼', 'HOU': '火箭', 'DEN': '掘金', 'GSW': '勇士',
    'LAC': '快船', 'SAC': '国王', 'UTA': '爵士', 'MEM': '灰熊',
    'NOP': '鹈鹕', 'DAL': '独行侠', 'OKC': '雷霆',
}

TEAM_CITY_MAP = {
    '活塞': '底特律', '老鹰': '亚特兰大', '凯尔特人': '波士顿', '76人': '费城',
    '魔术': '奥兰多', '猛龙': '多伦多', '骑士': '克利夫兰', '尼克斯': '纽约',
    '热火': '迈阿密', '黄蜂': '夏洛特', '奇才': '华盛顿', '步行者': '印第安纳',
    '篮网': '布鲁克林', '公牛': '芝加哥', '雄鹿': '密尔沃基',
    '湖人': '洛杉矶', '马刺': '圣安东尼奥', '开拓者': '波特兰', '太阳': '菲尼克斯',
    '森林狼': '明尼苏达', '火箭': '休斯顿', '掘金': '丹佛', '勇士': '金州',
    '快船': '洛杉矶', '国王': '萨克拉门托', '爵士': '犹他', '灰熊': '孟菲斯',
    '鹈鹕': '新奥尔良', '独行侠': '达拉斯', '雷霆': '俄克拉荷马城',
}

# 位置映射
POSITION_MAP = {
    'PG': '控卫', 'SG': '分卫', 'SF': '小前锋', 'PF': '大前锋', 'C': '中锋',
    'G': '控卫', 'F': '大前锋',
}

# 球员英文名 -> 中文名 映射（覆盖当前NBA主要球员）
PLAYER_CN_MAP = {
    # 湖人
    'Luka Doncic': '卢卡·东契奇', 'Luka Dončić': '卢卡·东契奇',
    'LeBron James': '勒布朗·詹姆斯', 'Austin Reaves': '奥斯汀·里夫斯',
    'Rui Hachimura': '八村塁', 'Dalton Knecht': '道尔顿·克内克特',
    'Christian Wood': '克里斯蒂安·伍德', 'Jarred Vanderbilt': '贾里德·范德比尔特',
    'Jaxson Hayes': '杰克森·海耶斯', 'Gabe Vincent': '加布·文森特',
    'Dorian Finney-Smith': '多里安·芬尼-史密斯', 'Shake Milton': '谢克·米尔顿',
    # 勇士
    'Stephen Curry': '斯蒂芬·库里', 'Jimmy Butler': '吉米·巴特勒',
    'Brandin Podziemski': '布兰丁·波杰姆斯基', 'Jonathan Kuminga': '乔纳森·库明加',
    'De\'Anthony Melton': '德安东尼·梅尔顿', 'Gary Payton II': '加里·佩顿二世',
    'Buddy Hield': '巴迪·希尔德', 'Quinten Post': '昆顿·波斯特',
    'Pat Spencer': '帕特·斯宾塞', 'Trayce Jackson-Davis': '特雷斯·杰克逊-戴维斯',
    'Gui Santos': '吉·桑托斯', 'Kevon Looney': '凯文·卢尼',
    'Moses Moody': '摩西·穆迪',
    # 凯尔特人
    'Jayson Tatum': '杰森·塔图姆', 'Jaylen Brown': '杰伦·布朗',
    'Kristaps Porzingis': '克里斯塔普斯·波尔津吉斯', 'Derrick White': '德里克·怀特',
    'Jrue Holiday': '朱·霍勒迪', 'Payton Pritchard': '佩顿·普里查德',
    'Al Horford': '艾尔·霍福德', 'Sam Hauser': '萨姆·豪瑟',
    'Luke Kornet': '卢克·科内特',
    # 雄鹿
    'Giannis Antetokounmpo': '扬尼斯·阿德托昆博', 'Damian Lillard': '达米安·利拉德',
    'Khris Middleton': '克里斯·米德尔顿', 'Brook Lopez': '布鲁克·洛佩兹',
    'Bobby Portis': '博比·波蒂斯',
    # 掘金
    'Nikola Jokic': '尼古拉·约基奇', 'Nikola Jokić': '尼古拉·约基奇', 'Jamal Murray': '贾马尔·穆雷',
    'Michael Porter Jr.': '迈克尔·波特', 'Aaron Gordon': '阿隆·戈登',
    'Kentavious Caldwell-Pope': '肯塔维厄斯·考德威尔-波普',
    # 太阳
    'Kevin Durant': '凯文·杜兰特', 'Devin Booker': '德文·布克',
    'Bradley Beal': '布拉德利·比尔', 'Jusuf Nurkic': '尤素夫·努尔基奇',
    # 雷霆
    'Shai Gilgeous-Alexander': '谢伊·吉尔杰斯-亚历山大',
    'Chet Holmgren': '切特·霍姆格伦', 'Jalen Williams': '杰伦·威廉姆斯',
    'Lu Dort': '卢·多尔特', 'Isaiah Joe': '以赛亚·乔',
    # 马刺
    'Victor Wembanyama': '维克托·文班亚马', 'Devin Vassell': '德文·瓦塞尔',
    'Keldon Johnson': '凯尔登·约翰逊', 'Jeremy Sochan': '杰里米·索汉',
    'Chris Paul': '克里斯·保罗',
    # 火箭
    'Alperen Sengun': '阿尔佩伦·申京', 'Jalen Green': '杰伦·格林',
    'Amen Thompson': '阿门·汤普森', 'Fred VanVleet': '弗雷德·范弗利特',
    'Dillon Brooks': '狄龙·布鲁克斯',
    # 尼克斯
    'Jalen Brunson': '杰伦·布伦森', 'Julius Randle': '朱利叶斯·兰德尔',
    'OG Anunoby': 'OG·阿奴诺比', 'Donte DiVincenzo': '唐特·迪温琴佐',
    'Mikal Bridges': '米卡尔·布里奇斯', 'Karl-Anthony Towns': '卡尔-安东尼·唐斯',
    # 骑士
    'Donovan Mitchell': '多诺万·米切尔', 'Darius Garland': '达里厄斯·加兰',
    'Evan Mobley': '埃文·莫布利', 'Jarrett Allen': '贾勒特·阿伦',
    # 森林狼
    'Anthony Edwards': '安东尼·爱德华兹', 'Rudy Gobert': '鲁迪·戈贝尔',
    'Jaden McDaniels': '杰登·麦克丹尼尔斯', 'Naz Reid': '纳兹·里德',
    # 活塞
    'Cade Cunningham': '凯德·坎宁安', 'Jaden Ivey': '杰登·艾维',
    'Jalen Duren': '杰伦·杜伦', 'Tobias Harris': '托拜亚斯·哈里斯',
    # 独行侠
    'Kyrie Irving': '凯里·欧文', 'P.J. Washington': 'PJ·华盛顿',
    'Daniel Gafford': '丹尼尔·加福德', 'Dereck Lively': '德里克·莱夫利',
    # 76人
    'Joel Embiid': '乔尔·恩比德', 'Tyrese Maxey': '泰瑞斯·马克西',
    'Paul George': '保罗·乔治', 'Caleb Martin': '凯莱布·马丁',
    # 老鹰
    'Trae Young': '特雷·杨', 'Dejounte Murray': '德章泰·穆雷',
    'Jalen Johnson': '杰伦·约翰逊', 'Clint Capela': '克林特·卡佩拉',
    # 猛龙
    'Pascal Siakam': '帕斯卡尔·西亚卡姆', 'Scottie Barnes': '斯科蒂·巴恩斯',
    'RJ Barrett': 'RJ·巴雷特', 'Immanuel Quickley': '伊曼纽尔·奎克利',
    # 热火
    'Bam Adebayo': '巴姆·阿德巴约', 'Jimmy Butler': '吉米·巴特勒',
    'Tyler Herro': '泰勒·希罗', 'Terry Rozier': '特里·罗齐尔',
    # 快船
    'Kawhi Leonard': '科怀·伦纳德', 'James Harden': '詹姆斯·哈登',
    'Norman Powell': '诺曼·鲍威尔', 'Ivica Zubac': '伊维察·祖巴茨',
    # 国王
    'De\'Aaron Fox': '达龙·福克斯', 'Domantas Sabonis': '多曼塔斯·萨博尼斯',
    'DeMar DeRozan': '德玛尔·德罗赞', 'Keegan Murray': '基根·穆雷',
    # 鹈鹕
    'Zion Williamson': '锡安·威廉姆森', 'Brandon Ingram': '布兰登·英格拉姆',
    'CJ McCollum': 'CJ·麦科勒姆', 'Herbert Jones': '赫伯特·琼斯',
    # 魔术
    'Paolo Banchero': '保罗·班切罗', 'Franz Wagner': '弗朗茨·瓦格纳',
    'Jalen Suggs': '杰伦·萨格斯', 'Wendell Carter Jr.': '温德尔·卡特',
    # 步行者
    'Tyrese Haliburton': '泰瑞斯·哈利伯顿', 'Andrew Nembhard': '安德鲁·内姆哈德',
    'Myles Turner': '迈尔斯·特纳', 'Bennedict Mathurin': '本内迪克特·马图林',
    # 公牛
    'Zach LaVine': '扎克·拉文', 'Nikola Vucevic': '尼古拉·武切维奇',
    'Coby White': '科比·怀特', 'Ayo Dosunmu': '阿约·多苏穆',
    # 灰熊
    'Ja Morant': '贾·莫兰特', 'Desmond Bane': '德斯蒙德·贝恩',
    'Jaren Jackson Jr.': '小贾伦·杰克逊', 'Marcus Smart': '马库斯·斯马特',
    # 开拓者
    'Anfernee Simons': '安芬尼·西蒙斯', 'Jerami Grant': '杰拉米·格兰特',
    'Deandre Ayton': '德安德烈·艾顿', 'Shaedon Sharpe': '谢登·夏普',
    # 爵士
    'Lauri Markkanen': '劳里·马尔卡宁', 'Collin Sexton': '科林·塞克斯顿',
    'Jordan Clarkson': '乔丹·克拉克森', 'John Collins': '约翰·柯林斯',
    # 篮网
    'Cameron Thomas': '卡梅隆·托马斯', 'Dennis Schroder': '丹尼斯·施罗德',
    'Nic Claxton': '尼古拉斯·克拉克斯顿', 'Dorian Finney-Smith': '多里安·芬尼-史密斯',
    # 黄蜂
    'LaMelo Ball': '拉梅洛·鲍尔', 'Miles Bridges': '迈尔斯·布里奇斯',
    'Brandon Miller': '布兰登·米勒', 'Mark Williams': '马克·威廉姆斯',
    # 奇才
    'Kyle Kuzma': '凯尔·库兹马', 'Jordan Poole': '乔丹·普尔',
    'Deni Avdija': '德尼·阿夫迪亚', 'Corey Kispert': '科里·基斯珀特',
    'Alex Sarr': '亚历克斯·萨尔',
    # 其他球员
    'Nikola Vučević': '尼古拉·武切维奇', 'Nikola Vucevic': '尼古拉·武切维奇',
    'Andrew Wiggins': '安德鲁·威金斯', 'Anthony Black': '安东尼·布莱克',
    'Anthony Davis': '安东尼·戴维斯', 'Cooper Flagg': '库珀·弗拉格',
    'Dariq Whitehead': '达里克·怀特黑德', 'Grayson Allen': '格雷森·阿伦',
    'Jabari Smith Jr.': '贾巴里·史密斯', 'Jaime Jaquez Jr.': '海梅·哈克斯',
    'Jimmy Butler III': '吉米·巴特勒', 'Josh Giddey': '约什·吉迪',
    'Kevin Porter Jr.': '小凯文·波特', 'Keyonte George': '基扬特·乔治',
    'Kon Knueppel': '康·克内佩尔', 'Matas Buzelis': '马塔斯·布泽利斯',
    'Naji Marshall': '纳吉·马歇尔', 'Nickeil Alexander-Walker': '尼基尔·亚历山大-沃克',
    'Onyeka Okongwu': '奥涅卡·奥孔武', 'Russell Westbrook': '拉塞尔·威斯布鲁克',
    'Ryan Rollins': '瑞安·罗林斯', 'Saddiq Bey': '萨迪克·贝',
    'Stephon Castle': '斯蒂芬·卡斯尔', 'Trey Murphy III': '特雷·墨菲',
    'Ty Jerome': '泰·杰罗姆', 'VJ Edgecombe': 'VJ·埃奇科姆',
}

# 球员英文名 -> 球衣号码 映射
PLAYER_JERSEY_MAP = {
    # 湖人
    'Luka Doncic': 77, 'LeBron James': 23, 'Austin Reaves': 15,
    'Rui Hachimura': 28, 'Dalton Knecht': 4, 'Christian Wood': 35,
    'Jarred Vanderbilt': 2, 'Jaxson Hayes': 10, 'Gabe Vincent': 7,
    # 勇士
    'Stephen Curry': 30, 'Jimmy Butler': 22, 'Brandin Podziemski': 2,
    'Jonathan Kuminga': 00, 'De\'Anthony Melton': 8, 'Gary Payton II': 0,
    'Buddy Hield': 24, 'Quinten Post': 21, 'Pat Spencer': 61,
    'Trayce Jackson-Davis': 32, 'Gui Santos': 15, 'Kevon Looney': 5,
    'Moses Moody': 4,
    # 凯尔特人
    'Jayson Tatum': 0, 'Jaylen Brown': 7, 'Kristaps Porzingis': 8,
    'Derrick White': 9, 'Jrue Holiday': 4, 'Payton Pritchard': 11,
    'Al Horford': 42, 'Sam Hauser': 30, 'Luke Kornet': 40,
    # 雄鹿
    'Giannis Antetokounmpo': 34, 'Damian Lillard': 0, 'Khris Middleton': 22,
    'Brook Lopez': 11, 'Bobby Portis': 9,
    # 掘金
    'Nikola Jokic': 15, 'Nikola Jokić': 15, 'Jamal Murray': 27, 'Michael Porter Jr.': 1,
    'Aaron Gordon': 50,
    # 太阳
    'Kevin Durant': 35, 'Devin Booker': 1, 'Bradley Beal': 3,
    # 雷霆
    'Shai Gilgeous-Alexander': 2, 'Chet Holmgren': 7, 'Jalen Williams': 8,
    # 马刺
    'Victor Wembanyama': 1, 'Devin Vassell': 24, 'Chris Paul': 3,
    # 火箭
    'Alperen Sengun': 28, 'Jalen Green': 4, 'Fred VanVleet': 5,
    # 尼克斯
    'Jalen Brunson': 11, 'Julius Randle': 30, 'OG Anunoby': 8,
    'Mikal Bridges': 1, 'Karl-Anthony Towns': 32,
    # 骑士
    'Donovan Mitchell': 45, 'Darius Garland': 10, 'Evan Mobley': 4,
    'Jarrett Allen': 31,
    # 森林狼
    'Anthony Edwards': 5, 'Rudy Gobert': 27,
    # 活塞
    'Cade Cunningham': 2, 'Jaden Ivey': 23,
    # 独行侠
    'Kyrie Irving': 11, 'P.J. Washington': 25, 'Dereck Lively': 1,
    # 76人
    'Joel Embiid': 21, 'Tyrese Maxey': 0, 'Paul George': 13,
    # 老鹰
    'Trae Young': 11, 'Dejounte Murray': 5,
    # 猛龙
    'Scottie Barnes': 4, 'RJ Barrett': 9,
    # 热火
    'Bam Adebayo': 13, 'Tyler Herro': 14, 'Terry Rozier': 2,
    # 快船
    'Kawhi Leonard': 2, 'James Harden': 1, 'Norman Powell': 24,
    # 国王
    'De\'Aaron Fox': 5, 'Domantas Sabonis': 10, 'DeMar DeRozan': 10,
    # 鹈鹕
    'Zion Williamson': 1, 'Brandon Ingram': 14, 'CJ McCollum': 3,
    # 魔术
    'Paolo Banchero': 5, 'Franz Wagner': 22,
    # 步行者
    'Tyrese Haliburton': 0, 'Myles Turner': 33,
    # 公牛
    'Zach LaVine': 8, 'Nikola Vucevic': 9,
    # 灰熊
    'Ja Morant': 12, 'Desmond Bane': 22, 'Jaren Jackson Jr.': 13,
    # 开拓者
    'Anfernee Simons': 1, 'Jerami Grant': 9,
    # 爵士
    'Lauri Markkanen': 23, 'Collin Sexton': 2,
    # 篮网
    'Cameron Thomas': 24, 'Nic Claxton': 33,
    # 黄蜂
    'LaMelo Ball': 1, 'Miles Bridges': 0, 'Brandon Miller': 24,
    # 奇才
    'Kyle Kuzma': 33, 'Jordan Poole': 13, 'Alex Sarr': 20,
    # 其他球员
    'Andrew Wiggins': 22, 'Anthony Black': 0, 'Anthony Davis': 3,
    'Cooper Flagg': 0, 'Grayson Allen': 8, 'Jabari Smith Jr.': 10,
    'Jaime Jaquez Jr.': 11, 'Josh Giddey': 3, 'Kevin Porter Jr.': 3,
    'Keyonte George': 3, 'Naji Marshall': 0, 'Nickeil Alexander-Walker': 6,
    'Onyeka Okongwu': 17, 'Russell Westbrook': 4, 'Saddiq Bey': 41,
    'Stephon Castle': 0, 'Trey Murphy III': 25, 'Ty Jerome': 2,
}

# nba_api team id -> 中文名
TEAM_ID_TO_CN = {}


def _init_team_map():
    """初始化球队ID映射"""
    global TEAM_ID_TO_CN
    if TEAM_ID_TO_CN:
        return
    if HAS_NBA_API:
        for t in nba_teams.get_teams():
            cn = TEAM_NAME_MAP.get(t['full_name'], TEAM_ABBR_MAP.get(t['abbreviation'], t['full_name']))
            TEAM_ID_TO_CN[t['id']] = cn


def _safe_float(val, default=0.0):
    """安全转换为float"""
    try:
        if val is None:
            return default
        return float(val)
    except (ValueError, TypeError):
        return default


def _safe_int(val, default=0):
    """安全转换为int"""
    try:
        if val is None:
            return default
        return int(val)
    except (ValueError, TypeError):
        return default


def _get_current_season():
    """获取当前赛季字符串，如 '2024-25'"""
    now = datetime.now()
    if now.month >= 10:
        return f"{now.year}-{str(now.year + 1)[-2:]}"
    else:
        return f"{now.year - 1}-{str(now.year)[-2:]}"


def fetch_team_standings():
    """从nba_api获取球队战绩"""
    _init_team_map()
    print("正在从nba_api获取球队战绩数据...", file=sys.stderr)

    try:
        standings = LeagueStandingsV3(season=_get_current_season(), timeout=30)
        df = standings.get_dict()
        time.sleep(1.0)
    except Exception as e:
        print(f"获取球队数据失败: {e}", file=sys.stderr)
        return []

    result = []
    for row in df.get('resultSets', [{}])[0].get('rowSet', []):
        headers = df['resultSets'][0]['headers']
        data = dict(zip(headers, row))

        team_id = _safe_int(data.get('TeamID'))
        cn_name = TEAM_ID_TO_CN.get(team_id, data.get('TeamCity', ''))
        city = TEAM_CITY_MAP.get(cn_name, data.get('TeamCity', ''))
        conf = data.get('Conference', '')
        conference = '东部' if conf == 'East' else '西部'

        result.append({
            'name': cn_name,
            'city': city,
            'conference': conference,
            'wins': _safe_int(data.get('WINS')),
            'losses': _safe_int(data.get('LOSSES')),
        })

    print(f"获取到 {len(result)} 支球队数据", file=sys.stderr)
    return result


def fetch_player_stats():
    """从nba_api获取球员统计数据（基础+高级）"""
    _init_team_map()
    print("正在从nba_api获取球员统计数据...", file=sys.stderr)
    season = _get_current_season()

    # 获取基础数据
    try:
        base = LeagueDashPlayerStats(
            season=season,
            measure_type_detailed_defense='Base',
            per_mode_detailed='PerGame',
            timeout=60
        )
        base_df = base.get_dict()
        time.sleep(1.0)
    except Exception as e:
        print(f"获取基础球员数据失败: {e}", file=sys.stderr)
        return []

    # 获取高级数据
    try:
        advanced = LeagueDashPlayerStats(
            season=season,
            measure_type_detailed_defense='Advanced',
            per_mode_detailed='PerGame',
            timeout=60
        )
        adv_df = advanced.get_dict()
        time.sleep(1.0)
    except Exception as e:
        print(f"获取高级球员数据失败: {e}", file=sys.stderr)
        adv_df = None

    # 解析基础数据
    base_headers = base_df['resultSets'][0]['headers']
    base_rows = base_df['resultSets'][0]['rowSet']

    # 构建高级数据索引 (player_id -> data)
    adv_map = {}
    if adv_df:
        adv_headers = adv_df['resultSets'][0]['headers']
        for row in adv_df['resultSets'][0]['rowSet']:
            data = dict(zip(adv_headers, row))
            adv_map[_safe_int(data.get('PLAYER_ID'))] = data

    result = []
    for row in base_rows:
        data = dict(zip(base_headers, row))

        player_id = _safe_int(data.get('PLAYER_ID'))
        team_id = _safe_int(data.get('TEAM_ID'))
        cn_team = TEAM_ID_TO_CN.get(team_id, data.get('TEAM_ABBREVIATION', ''))

        # 位置映射
        pos_raw = data.get('PLAYER_POSITION', '')
        position = POSITION_MAP.get(pos_raw, '控卫')

        # 基础数据
        gp = _safe_int(data.get('GP'))
        ppg = _safe_float(data.get('PTS'))
        rpg = _safe_float(data.get('REB'))
        apg = _safe_float(data.get('AST'))
        spg = _safe_float(data.get('STL'))
        bpg = _safe_float(data.get('BLK'))
        tpg = _safe_float(data.get('TOV'))
        mpg = _safe_float(data.get('MIN'))
        fg_pct = _safe_float(data.get('FG_PCT'))
        three_pct = _safe_float(data.get('FG3_PCT'))
        ft_pct = _safe_float(data.get('FT_PCT'))

        # 高级数据
        adv = adv_map.get(player_id, {})
        per = _safe_float(adv.get('E_OFF_RATING'), 110.0)  # 进攻效率 (每100回合得分)
        ts_pct = _safe_float(adv.get('TS_PCT'), fg_pct)
        usg_pct = _safe_float(adv.get('USG_PCT')) * 100  # 转为百分比
        if usg_pct < 1.0:
            usg_pct = 20.0  # 默认值

        result.append({
            'name': PLAYER_CN_MAP.get(data.get('PLAYER_NAME', ''), data.get('PLAYER_NAME', '')),
            'team': cn_team,
            'position': position,
            'ppg': round(ppg, 1),
            'rpg': round(rpg, 1),
            'apg': round(apg, 1),
            'spg': round(spg, 1),
            'gp': gp,
            'mpg': round(mpg, 1),
            'fgPct': round(fg_pct, 3),
            'threePct': round(three_pct, 3),
            'ftPct': round(ft_pct, 3),
            'bpg': round(bpg, 1),
            'tpg': round(tpg, 1),
            'per': round(per, 1),
            'tsPct': round(ts_pct, 3),
            'usgPct': round(usg_pct, 1),
            'jersey': PLAYER_JERSEY_MAP.get(data.get('PLAYER_NAME', ''), 0),
        })

    # 按得分排序，取前100名
    result.sort(key=lambda x: x['ppg'], reverse=True)
    result = result[:100]

    print(f"获取到 {len(result)} 名球员数据", file=sys.stderr)
    return result


def fetch_today_games():
    """从nba_api获取今日比赛（实时数据）"""
    _init_team_map()
    print("正在获取今日比赛数据...", file=sys.stderr)

    try:
        sb = live_scoreboard.Scoreboard(timeout=15)
        data = sb.get_dict()
        time.sleep(0.5)
    except Exception as e:
        print(f"获取今日比赛失败: {e}", file=sys.stderr)
        return []

    result = []
    for game in data.get('scoreboard', {}).get('games', []):
        home = game.get('homeTeam', {})
        away = game.get('awayTeam', {})

        home_cn = TEAM_ABBR_MAP.get(home.get('teamTricode', ''), home.get('teamName', ''))
        away_cn = TEAM_ABBR_MAP.get(away.get('teamTricode', ''), away.get('teamName', ''))

        game_status = game.get('gameStatus', 0)
        if game_status == 1:
            status = 'SCHEDULED'
        elif game_status == 2:
            status = 'LIVE'
        else:
            status = 'FINISHED'

        result.append({
            'gameId': game.get('gameId', ''),
            'homeTeam': home_cn,
            'awayTeam': away_cn,
            'homeScore': _safe_int(home.get('score')),
            'awayScore': _safe_int(away.get('score')),
            'status': status,
            'startTime': game.get('gameEt', ''),
            'period': game.get('period', 0),
            'gameClock': game.get('gameClock', ''),
        })

    print(f"获取到 {len(result)} 场今日比赛", file=sys.stderr)
    return result


def fetch_recent_games():
    """从nba_api获取最近比赛记录"""
    _init_team_map()
    print("正在获取最近比赛记录...", file=sys.stderr)

    games = []
    today = datetime.now()

    for days_ago in range(7):
        date = today - timedelta(days=days_ago)
        date_str = date.strftime('%m/%d/%Y')

        try:
            sb = ScoreboardV2(game_date=date_str, timeout=30)
            data = sb.get_dict()
            time.sleep(1.0)
        except Exception as e:
            print(f"获取{date_str}比赛数据失败: {e}", file=sys.stderr)
            continue

        # 解析比赛数据
        result_sets = data.get('resultSets', [])
        game_header = None
        line_score = None

        for rs in result_sets:
            if rs.get('name') == 'GameHeader':
                game_header = rs
            elif rs.get('name') == 'LineScore':
                line_score = rs

        if not game_header or not line_score:
            continue

        # 构建球队得分索引
        score_map = {}
        ls_headers = line_score['headers']
        for row in line_score['rowSet']:
            sd = dict(zip(ls_headers, row))
            game_id = sd.get('GAME_ID')
            team_id = _safe_int(sd.get('TEAM_ID'))
            score = _safe_int(sd.get('PTS'))
            score_map[(game_id, team_id)] = {
                'team': TEAM_ID_TO_CN.get(team_id, sd.get('TEAM_ABBREVIATION', '')),
                'score': score,
            }

        gh_headers = game_header['headers']
        for row in game_header['rowSet']:
            gd = dict(zip(gh_headers, row))
            game_id = gd.get('GAME_ID')
            home_id = _safe_int(gd.get('HOME_TEAM_ID'))
            away_id = _safe_int(gd.get('VISITOR_TEAM_ID'))

            home_info = score_map.get((game_id, home_id), {})
            away_info = score_map.get((game_id, away_id), {})

            if home_info and away_info:
                games.append({
                    'homeTeam': home_info.get('team', ''),
                    'awayTeam': away_info.get('team', ''),
                    'homeScore': home_info.get('score', 0),
                    'awayScore': away_info.get('score', 0),
                    'date': date.strftime('%Y-%m-%d'),
                    'status': 'FINISHED',
                    'gameId': game_id,
                })

    print(f"获取到 {len(games)} 场比赛记录", file=sys.stderr)
    return games


def fetch_boxscore(game_id):
    """获取指定比赛的Box Score"""
    _init_team_map()
    print(f"正在获取比赛 {game_id} 的Box Score...", file=sys.stderr)

    try:
        box = BoxScoreTraditionalV2(game_id=game_id, timeout=30)
        data = box.get_dict()
        time.sleep(0.5)
    except Exception as e:
        print(f"获取Box Score失败: {e}", file=sys.stderr)
        return None

    result_sets = data.get('resultSets', [])
    player_stats = []
    team_stats = []

    for rs in result_sets:
        headers = rs['headers']
        for row in rs.get('rowSet', []):
            d = dict(zip(headers, row))

            if rs['name'] == 'PlayerStats':
                team_id = _safe_int(d.get('TEAM_ID'))
                en_name = d.get('PLAYER_NAME', '')
                player_stats.append({
                    'playerId': _safe_int(d.get('PLAYER_ID')),
                    'playerName': PLAYER_CN_MAP.get(en_name, en_name),
                    'teamId': team_id,
                    'teamName': TEAM_ID_TO_CN.get(team_id, d.get('TEAM_ABBREVIATION', '')),
                    'minutes': d.get('MIN', ''),
                    'points': _safe_int(d.get('PTS')),
                    'rebounds': _safe_int(d.get('REB')),
                    'assists': _safe_int(d.get('AST')),
                    'steals': _safe_int(d.get('STL')),
                    'blocks': _safe_int(d.get('BLK')),
                    'turnovers': _safe_int(d.get('TO')),
                    'fgMade': _safe_int(d.get('FGM')),
                    'fgAttempted': _safe_int(d.get('FGA')),
                    'fgPct': _safe_float(d.get('FG_PCT')),
                    'threeMade': _safe_int(d.get('FG3M')),
                    'threeAttempted': _safe_int(d.get('FG3A')),
                    'threePct': _safe_float(d.get('FG3_PCT')),
                    'ftMade': _safe_int(d.get('FTM')),
                    'ftAttempted': _safe_int(d.get('FTA')),
                    'ftPct': _safe_float(d.get('FT_PCT')),
                    'plusMinus': _safe_int(d.get('PLUS_MINUS')),
                    'starter': d.get('START_POSITION', '') != '',
                })
            elif rs['name'] == 'TeamStats':
                team_id = _safe_int(d.get('TEAM_ID'))
                team_stats.append({
                    'teamId': team_id,
                    'teamName': TEAM_ID_TO_CN.get(team_id, ''),
                    'points': _safe_int(d.get('PTS')),
                    'rebounds': _safe_int(d.get('REB')),
                    'assists': _safe_int(d.get('AST')),
                })

    print(f"获取到 {len(player_stats)} 名球员的Box Score", file=sys.stderr)
    return {'players': player_stats, 'teams': team_stats}


def fetch_playbyplay(game_id, start_period=0, end_period=10):
    """获取指定比赛的Play-by-Play"""
    print(f"正在获取比赛 {game_id} 的Play-by-Play...", file=sys.stderr)

    try:
        pbp = PlayByPlayV2(game_id=game_id, start_period=start_period, end_period=end_period, timeout=30)
        data = pbp.get_dict()
        time.sleep(0.5)
    except Exception as e:
        print(f"获取Play-by-Play失败: {e}", file=sys.stderr)
        return []

    result_sets = data.get('resultSets', [])
    events = []

    for rs in result_sets:
        if rs['name'] != 'PlayByPlay':
            continue
        headers = rs['headers']
        for row in rs.get('rowSet', []):
            d = dict(zip(headers, row))
            desc = d.get('HOMEDESCRIPTION') or d.get('VISITORDESCRIPTION') or d.get('NEUTRALDESCRIPTION', '')
            if not desc:
                continue
            en_name = d.get('PLAYER1_NAME', '')
            # 替换描述中的英文名为中文
            if en_name and en_name in PLAYER_CN_MAP:
                desc = desc.replace(en_name, PLAYER_CN_MAP[en_name])
            events.append({
                'period': _safe_int(d.get('PERIOD')),
                'gameClock': d.get('PCTIMESTRING', ''),
                'description': desc,
                'homeScore': _safe_int(d.get('SCOREHOME')),
                'awayScore': _safe_int(d.get('SCOREAWAY')),
                'eventType': d.get('EVENTMSGTYPE', ''),
                'playerId': _safe_int(d.get('PLAYER1_ID')),
                'playerName': PLAYER_CN_MAP.get(en_name, en_name),
            })

    print(f"获取到 {len(events)} 条Play-by-Play事件", file=sys.stderr)
    return events


def fetch_player_career(player_id):
    """获取球员生涯数据"""
    print(f"正在获取球员 {player_id} 的生涯数据...", file=sys.stderr)

    try:
        career = PlayerCareerStats(player_id=player_id, timeout=30)
        data = career.get_dict()
        time.sleep(1.0)
    except Exception as e:
        print(f"获取生涯数据失败: {e}", file=sys.stderr)
        return []

    result = []
    for rs in data.get('resultSets', []):
        if rs['name'] != 'SeasonTotalsRegularSeason':
            continue
        headers = rs['headers']
        for row in rs.get('rowSet', []):
            d = dict(zip(headers, row))
            team_id = _safe_int(d.get('TEAM_ID'))
            result.append({
                'season': d.get('SEASON_ID', ''),
                'teamName': TEAM_ID_TO_CN.get(team_id, d.get('TEAM_ABBREVIATION', '')),
                'gamesPlayed': _safe_int(d.get('GP')),
                'minutesPerGame': round(_safe_float(d.get('MIN')) / max(_safe_int(d.get('GP')), 1), 1),
                'pointsPerGame': round(_safe_float(d.get('PTS')) / max(_safe_int(d.get('GP')), 1), 1),
                'reboundsPerGame': round(_safe_float(d.get('REB')) / max(_safe_int(d.get('GP')), 1), 1),
                'assistsPerGame': round(_safe_float(d.get('AST')) / max(_safe_int(d.get('GP')), 1), 1),
                'stealsPerGame': round(_safe_float(d.get('STL')) / max(_safe_int(d.get('GP')), 1), 1),
                'blocksPerGame': round(_safe_float(d.get('BLK')) / max(_safe_int(d.get('GP')), 1), 1),
                'fgPct': _safe_float(d.get('FG_PCT')),
                'threePct': _safe_float(d.get('FG3_PCT')),
                'ftPct': _safe_float(d.get('FT_PCT')),
            })

    print(f"获取到 {len(result)} 个赛季的生涯数据", file=sys.stderr)
    return result


def fetch_player_gamelog(player_id, season=None):
    """获取球员比赛日志"""
    if season is None:
        season = _get_current_season()
    print(f"正在获取球员 {player_id} 的比赛日志 ({season})...", file=sys.stderr)

    try:
        log = PlayerGameLog(player_id=player_id, season=season, timeout=30)
        data = log.get_dict()
        time.sleep(1.0)
    except Exception as e:
        print(f"获取比赛日志失败: {e}", file=sys.stderr)
        return []

    result = []
    for rs in data.get('resultSets', []):
        if rs['name'] != 'PlayerGameLog':
            continue
        headers = rs['headers']
        for row in rs.get('rowSet', []):
            d = dict(zip(headers, row))
            result.append({
                'gameId': d.get('Game_ID', ''),
                'matchDate': d.get('GAME_DATE', ''),
                'opponent': d.get('MATCHUP', ''),
                'minutes': d.get('MIN', ''),
                'points': _safe_int(d.get('PTS')),
                'rebounds': _safe_int(d.get('REB')),
                'assists': _safe_int(d.get('AST')),
                'steals': _safe_int(d.get('STL')),
                'blocks': _safe_int(d.get('BLK')),
                'turnovers': _safe_int(d.get('TOV')),
                'fgPct': _safe_float(d.get('FG_PCT')),
                'threePct': _safe_float(d.get('FG3_PCT')),
                'ftPct': _safe_float(d.get('FT_PCT')),
                'plusMinus': _safe_int(d.get('PLUS_MINUS')),
                'result': d.get('WL', ''),
            })

    print(f"获取到 {len(result)} 场比赛日志", file=sys.stderr)
    return result


def main():
    """主函数"""
    if not HAS_NBA_API:
        print(json.dumps({'error': 'nba_api not installed. Run: pip install nba_api'}, ensure_ascii=False))
        sys.exit(1)

    action = sys.argv[1] if len(sys.argv) > 1 else 'all'

    result = {
        'timestamp': datetime.now().isoformat(),
        'source': 'nba_api (stats.nba.com + cdn.nba.com)',
        'season': _get_current_season(),
    }

    if action in ('all', 'teams'):
        result['teams'] = fetch_team_standings()

    if action in ('all', 'players'):
        result['players'] = fetch_player_stats()

    if action in ('all', 'games'):
        result['games'] = fetch_recent_games()

    if action == 'today':
        result['todayGames'] = fetch_today_games()

    if action == 'boxscore' and len(sys.argv) > 2:
        result['boxscore'] = fetch_boxscore(sys.argv[2])

    if action == 'playbyplay' and len(sys.argv) > 2:
        result['playByPlay'] = fetch_playbyplay(sys.argv[2])

    if action == 'player_career' and len(sys.argv) > 2:
        result['career'] = fetch_player_career(int(sys.argv[2]))

    if action == 'player_gamelog' and len(sys.argv) > 2:
        season = sys.argv[3] if len(sys.argv) > 3 else None
        result['gameLog'] = fetch_player_gamelog(int(sys.argv[2]), season)

    # 输出JSON到文件（避免Windows编码问题）
    output_file = os.path.join(os.path.dirname(__file__), 'output.json')
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    # 同时输出到stdout
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
