#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NBA数据获取脚本
从nba_api获取真实的NBA数据（stats.nba.com + cdn.nba.com）
支持使用mimo-v2.5模型进行智能翻译
"""

import json
import os
import sys
import time
import urllib.request
from datetime import datetime, timedelta

# 确保stdout使用UTF-8编码（Windows兼容）
if sys.platform == 'win32':
    import io
    try:
        if hasattr(sys.stdout, 'buffer') and not isinstance(sys.stdout, io.TextIOWrapper):
            sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        if hasattr(sys.stderr, 'buffer') and not isinstance(sys.stderr, io.TextIOWrapper):
            sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    except (AttributeError, ValueError):
        pass

# 加载 .env 文件
env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '.env')
if os.path.exists(env_path):
    with open(env_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                os.environ.setdefault(key.strip(), value.strip())

# 导入翻译模块
try:
    from translator import translate_text, translate_news_article, translate_trade_details
    HAS_TRANSLATOR = True
except ImportError:
    HAS_TRANSLATOR = False
    print("警告: translator模块未找到，将使用本地映射翻译", file=sys.stderr)

# 代理配置（可选，代理不可用时直连）
PROXY_HOST = os.environ.get('NBA_PROXY_HOST', '')
PROXY_PORT = os.environ.get('NBA_PROXY_PORT', '')
PROXY_URL = ''

if PROXY_HOST and PROXY_PORT:
    PROXY_URL = f'http://{PROXY_HOST}:{PROXY_PORT}'
    proxy_handler = urllib.request.ProxyHandler({
        'https': PROXY_URL,
        'http': PROXY_URL,
    })
    proxy_opener = urllib.request.build_opener(proxy_handler)
    urllib.request.install_opener(proxy_opener)
    # 设置requests库的代理环境变量
    os.environ['HTTP_PROXY'] = PROXY_URL
    print(f"已配置代理: {PROXY_HOST}:{PROXY_PORT}", file=sys.stderr)
else:
    # 显式清除所有代理环境变量，防止系统代理干扰
    for key in ['HTTP_PROXY', 'HTTPS_PROXY', 'http_proxy', 'https_proxy', 'ALL_PROXY', 'all_proxy']:
        os.environ.pop(key, None)
    # 安装无代理的opener
    proxy_handler = urllib.request.ProxyHandler({})
    proxy_opener = urllib.request.build_opener(proxy_handler)
    urllib.request.install_opener(proxy_opener)
    print("未配置代理，使用直连（已清除系统代理）", file=sys.stderr)

try:
    from nba_api.stats.endpoints import (
        LeagueStandingsV3,
        LeagueDashPlayerStats,
        ScoreboardV2,
        ScoreboardV3,
        BoxScoreTraditionalV3,
        PlayByPlayV2,
        PlayByPlayV3,
        PlayerCareerStats,
        PlayerGameLog,
    )
    from nba_api.live.nba.endpoints import scoreboard as live_scoreboard
    from nba_api.stats.static import teams as nba_teams
    HAS_NBA_API = True
except ImportError:
    HAS_NBA_API = False

try:
    from nba_api.stats.endpoints import DraftHistory
    HAS_DRAFT_API = True
except ImportError:
    HAS_DRAFT_API = False

# 禁用nba_api的代理（nba_api使用requests库，需要单独配置）
if not PROXY_URL:
    try:
        import requests
        # 设置requests不使用代理
        session = requests.Session()
        session.trust_env = False
        session.proxies = {'http': None, 'https': None}
    except ImportError:
        pass

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
    'G': '控卫', 'F': '大前锋', 'Guard': '控卫', 'Forward': '大前锋', 'Center': '中锋',
    'Point Guard': '控卫', 'Shooting Guard': '分卫', 'Small Forward': '小前锋', 'Power Forward': '大前锋',
}

# 球队简称到中文名映射（用于新闻翻译）
TEAM_SHORT_MAP = {
    'Knicks': '尼克斯', 'Spurs': '马刺', 'Thunder': '雷霆', 'Lakers': '湖人',
    'Celtics': '凯尔特人', 'Warriors': '勇士', 'Nuggets': '掘金', 'Heat': '热火',
    'Bucks': '雄鹿', '76ers': '76人', 'Sixers': '76人', 'Suns': '太阳',
    'Mavericks': '独行侠', 'Mavs': '独行侠', 'Clippers': '快船', 'Kings': '国王',
    'Timberwolves': '森林狼', 'Wolves': '森林狼', 'Pelicans': '鹈鹕', 'Grizzlies': '灰熊',
    'Rockets': '火箭', 'Jazz': '爵士', 'Blazers': '开拓者', 'Trail Blazers': '开拓者',
    'Hawks': '老鹰', 'Cavaliers': '骑士', 'Cavs': '骑士', 'Pacers': '步行者',
    'Magic': '魔术', 'Pistons': '活塞', 'Hornets': '黄蜂', 'Raptors': '猛龙',
    'Bulls': '公牛', 'Wizards': '奇才', 'Nets': '篮网',
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
    'Stephen Curry': '斯蒂芬·库里', 'Curry': '库里',
    'Jimmy Butler': '吉米·巴特勒', 'Butler': '巴特勒',
    'Brandin Podziemski': '布兰丁·波杰姆斯基', 'Jonathan Kuminga': '乔纳森·库明加',
    'De\'Anthony Melton': '德安东尼·梅尔顿', 'Gary Payton II': '加里·佩顿二世',
    'Buddy Hield': '巴迪·希尔德', 'Quinten Post': '昆顿·波斯特',
    'Pat Spencer': '帕特·斯宾塞', 'Trayce Jackson-Davis': '特雷斯·杰克逊-戴维斯',
    'Gui Santos': '吉·桑托斯', 'Kevon Looney': '凯文·卢尼',
    'Moses Moody': '摩西·穆迪',
    # 凯尔特人
    'Jayson Tatum': '杰森·塔图姆', 'Tatum': '塔图姆',
    'Jaylen Brown': '杰伦·布朗', 'Brown': '布朗',
    'Kristaps Porzingis': '克里斯塔普斯·波尔津吉斯', 'Derrick White': '德里克·怀特',
    'Jrue Holiday': '朱·霍勒迪', 'Payton Pritchard': '佩顿·普里查德',
    'Al Horford': '艾尔·霍福德', 'Sam Hauser': '萨姆·豪瑟',
    'Luke Kornet': '卢克·科内特',
    # 雄鹿
    'Giannis Antetokounmpo': '扬尼斯·阿德托昆博', 'Giannis': '字母哥',
    'Damian Lillard': '达米安·利拉德', 'Lillard': '利拉德',
    'Khris Middleton': '克里斯·米德尔顿', 'Brook Lopez': '布鲁克·洛佩兹',
    'Bobby Portis': '博比·波蒂斯',
    # 掘金
    'Nikola Jokic': '尼古拉·约基奇', 'Nikola Jokić': '尼古拉·约基奇', 'Jokic': '约基奇',
    'Jamal Murray': '贾马尔·穆雷', 'Murray': '穆雷',
    'Michael Porter Jr.': '迈克尔·波特', 'Aaron Gordon': '阿隆·戈登',
    'Kentavious Caldwell-Pope': '肯塔维厄斯·考德威尔-波普',
    # 太阳
    'Kevin Durant': '凯文·杜兰特', 'Durant': '杜兰特',
    'Devin Booker': '德文·布克', 'Booker': '布克',
    'Bradley Beal': '布拉德利·比尔', 'Jusuf Nurkic': '尤素夫·努尔基奇',
    # 雷霆
    'Shai Gilgeous-Alexander': '谢伊·吉尔杰斯-亚历山大', 'SGA': 'SGA',
    'Chet Holmgren': '切特·霍姆格伦', 'Jalen Williams': '杰伦·威廉姆斯',
    'Lu Dort': '卢·多尔特', 'Isaiah Joe': '以赛亚·乔',
    # 马刺
    'Victor Wembanyama': '维克托·文班亚马', 'Wembanyama': '文班亚马',
    'Devin Vassell': '德文·瓦塞尔', 'Keldon Johnson': '凯尔登·约翰逊',
    'Jeremy Sochan': '杰里米·索汉', 'Chris Paul': '克里斯·保罗',
    # 火箭
    'Alperen Sengun': '阿尔佩伦·申京', 'Sengun': '申京',
    'Jalen Green': '杰伦·格林', 'Amen Thompson': '阿门·汤普森',
    'Fred VanVleet': '弗雷德·范弗利特', 'Dillon Brooks': '狄龙·布鲁克斯',
    # 尼克斯
    'Jalen Brunson': '杰伦·布伦森', 'Brunson': '布伦森',
    'Julius Randle': '朱利叶斯·兰德尔', 'OG Anunoby': 'OG·阿奴诺比',
    'Donte DiVincenzo': '唐特·迪温琴佐', 'Mikal Bridges': '米卡尔·布里奇斯',
    'Karl-Anthony Towns': '卡尔-安东尼·唐斯', 'Towns': '唐斯',
    # 骑士
    'Donovan Mitchell': '多诺万·米切尔', 'Mitchell': '米切尔',
    'Darius Garland': '达里厄斯·加兰', 'Evan Mobley': '埃文·莫布利',
    'Jarrett Allen': '贾勒特·阿伦',
    # 森林狼
    'Anthony Edwards': '安东尼·爱德华兹', 'Edwards': '爱德华兹',
    'Rudy Gobert': '鲁迪·戈贝尔', 'Jaden McDaniels': '杰登·麦克丹尼尔斯',
    'Naz Reid': '纳兹·里德',
    # 活塞
    'Cade Cunningham': '凯德·坎宁安', 'Cunningham': '坎宁安',
    'Jaden Ivey': '杰登·艾维', 'Jalen Duren': '杰伦·杜伦',
    'Tobias Harris': '托拜亚斯·哈里斯',
    # 独行侠
    'Kyrie Irving': '凯里·欧文', 'Irving': '欧文',
    'P.J. Washington': 'PJ·华盛顿', 'Daniel Gafford': '丹尼尔·加福德',
    'Dereck Lively': '德里克·莱夫利',
    # 76人
    'Joel Embiid': '乔尔·恩比德', 'Embiid': '恩比德',
    'Tyrese Maxey': '泰瑞斯·马克西', 'Paul George': '保罗·乔治',
    'Caleb Martin': '凯莱布·马丁',
    # 老鹰
    'Trae Young': '特雷·杨', 'Dejounte Murray': '德章泰·穆雷',
    'Jalen Johnson': '杰伦·约翰逊', 'Clint Capela': '克林特·卡佩拉',
    # 猛龙
    'Pascal Siakam': '帕斯卡尔·西亚卡姆', 'Scottie Barnes': '斯科蒂·巴恩斯',
    'RJ Barrett': 'RJ·巴雷特', 'Immanuel Quickley': '伊曼纽尔·奎克利',
    # 热火
    'Bam Adebayo': '巴姆·阿德巴约', 'Tyler Herro': '泰勒·希罗',
    'Terry Rozier': '特里·罗齐尔',
    # 快船
    'Kawhi Leonard': '科怀·伦纳德', 'Leonard': '伦纳德',
    'James Harden': '詹姆斯·哈登', 'Harden': '哈登',
    'Norman Powell': '诺曼·鲍威尔', 'Ivica Zubac': '伊维察·祖巴茨',
    # 国王
    'De\'Aaron Fox': '达龙·福克斯', 'Fox': '福克斯',
    'Domantas Sabonis': '多曼塔斯·萨博尼斯', 'Sabonis': '萨博尼斯',
    'DeMar DeRozan': '德玛尔·德罗赞', 'DeRozan': '德罗赞',
    'Keegan Murray': '基根·穆雷',
    # 鹈鹕
    'Zion Williamson': '锡安·威廉姆森', 'Zion': '锡安',
    'Brandon Ingram': '布兰登·英格拉姆', 'CJ McCollum': 'CJ·麦科勒姆',
    'Herbert Jones': '赫伯特·琼斯',
    # 魔术
    'Paolo Banchero': '保罗·班切罗', 'Banchero': '班切罗',
    'Franz Wagner': '弗朗茨·瓦格纳', 'Jalen Suggs': '杰伦·萨格斯',
    'Wendell Carter Jr.': '温德尔·卡特',
    # 步行者
    'Tyrese Haliburton': '泰瑞斯·哈利伯顿', 'Haliburton': '哈利伯顿',
    'Andrew Nembhard': '安德鲁·内姆哈德', 'Myles Turner': '迈尔斯·特纳',
    'Bennedict Mathurin': '本内迪克特·马图林',
    # 公牛
    'Zach LaVine': '扎克·拉文', 'LaVine': '拉文',
    'Nikola Vucevic': '尼古拉·武切维奇', 'Nikola Vučević': '尼古拉·武切维奇',
    'Coby White': '科比·怀特', 'Ayo Dosunmu': '阿约·多苏穆',
    # 灰熊
    'Ja Morant': '贾·莫兰特', 'Morant': '莫兰特',
    'Desmond Bane': '德斯蒙德·贝恩', 'Jaren Jackson Jr.': '小贾伦·杰克逊',
    'Marcus Smart': '马库斯·斯马特',
    # 开拓者
    'Anfernee Simons': '安芬尼·西蒙斯', 'Jerami Grant': '杰拉米·格兰特',
    'Deandre Ayton': '德安德烈·艾顿', 'Shaedon Sharpe': '谢登·夏普',
    # 爵士
    'Lauri Markkanen': '劳里·马尔卡宁', 'Markkanen': '马尔卡宁',
    'Collin Sexton': '科林·塞克斯顿', 'Jordan Clarkson': '乔丹·克拉克森',
    'John Collins': '约翰·柯林斯',
    # 篮网
    'Cameron Thomas': '卡梅隆·托马斯', 'Dennis Schroder': '丹尼斯·施罗德',
    'Nic Claxton': '尼古拉斯·克拉克斯顿',
    # 黄蜂
    'LaMelo Ball': '拉梅洛·鲍尔', 'Miles Bridges': '迈尔斯·布里奇斯',
    'Brandon Miller': '布兰登·米勒', 'Mark Williams': '马克·威廉姆斯',
    # 奇才
    'Kyle Kuzma': '凯尔·库兹马', 'Jordan Poole': '乔丹·普尔',
    'Deni Avdija': '德尼·阿夫迪亚', 'Corey Kispert': '科里·基斯珀特',
    'Alex Sarr': '亚历克斯·萨尔',
    # 其他球员
    'Andrew Wiggins': '安德鲁·威金斯', 'Anthony Davis': '安东尼·戴维斯',
    'Russell Westbrook': '拉塞尔·威斯布鲁克',
    # 马刺补充
    'Julian Champagnie': '朱利安·尚帕尼', 'Dylan Harper': '迪伦·哈珀',
    'Harrison Barnes': '哈里森·巴恩斯', 'Carter Bryant': '卡特·布莱恩特',
    'Stephon Castle': '斯蒂芬·卡斯尔',
    'Bismack Biyombo': '比斯马克·比永博', 'Jordan McLaughlin': '乔丹·麦克劳克林',
    'Kelly Olynyk': '凯利·奥利尼克', 'Mason Plumlee': '梅森·普拉姆利',
    'Lindy Waters III': '林迪·沃特斯三世',
    # 雷霆补充
    'Luguentz Dort': '卢根茨·多尔特', 'Isaiah Hartenstein': '以赛亚·哈滕斯坦因',
    'Cason Wallace': '卡森·华莱士', 'Alex Caruso': '亚历克斯·卡鲁索',
    'Jared McCain': '贾里德·麦凯恩', 'Jaylin Williams': '杰林·威廉姆斯',
    'Kenrich Williams': '肯里奇·威廉姆斯', 'Nikola Topić': '尼古拉·托皮奇',
    'Aaron Wiggins': '阿隆·威金斯',
    # === 补充翻译 ===
    # A
    'A.J. Lawson': 'AJ·劳森', 'AJ Green': 'AJ·格林', 'AJ Johnson': 'AJ·约翰逊',
    'Aaron Holiday': '阿隆·霍勒迪', 'Aaron Nesmith': '阿隆·内史密斯',
    'Ace Bailey': '艾斯·贝利', 'Adama Bal': '阿达马·巴尔',
    'Adem Bona': '阿德姆·博纳', 'Adou Thiero': '阿杜·蒂埃罗',
    'Ajay Mitchell': '阿杰·米切尔', 'Alex Antetokounmpo': '阿莱克斯·阿德托昆博',
    'Alex Morales': '亚历克斯·莫拉莱斯', 'Alijah Martin': '阿利贾·马丁',
    'Alondes Williams': '阿隆德斯·威廉姆斯', 'Amari Williams': '阿马里·威廉姆斯',
    'Amir Coffey': '阿米尔·科菲', 'Andersson Garcia': '安德森·加西亚',
    'Andre Drummond': '安德烈·德拉蒙德', 'Andre Jackson Jr.': '安德烈·杰克逊',
    'Anthony Black': '安东尼·布莱克', 'Anthony Gill': '安东尼·吉尔',
    'Antonio Reeves': '安东尼奥·里夫斯', 'Ariel Hukporti': '阿里尔·胡克波蒂',
    'Asa Newell': '阿萨·纽厄尔', 'Ausar Thompson': '奥萨尔·汤普森',
    # B
    'Baylor Scheierman': '贝勒·谢尔曼', 'Ben Saraf': '本·萨拉夫',
    'Ben Sheppard': '本·谢泼德', 'Bez Mbeng': '贝兹·姆本',
    'Bilal Coulibaly': '比拉尔·库利巴利', 'Blake Hinson': '布莱克·辛森',
    'Blake Wesley': '布莱克·韦斯利', 'Bobi Klintman': '博比·克林特曼',
    'Bogdan Bogdanović': '波格丹·波格丹诺维奇', 'Bones Hyland': '博恩斯·海兰德',
    'Branden Carlson': '布兰登·卡尔森', 'Brandon Clarke': '布兰登·克拉克',
    'Brandon Williams': '布兰登·威廉姆斯', 'Brice Sensabaugh': '布赖斯·森萨鲍',
    'Bronny James': '布朗尼·詹姆斯', 'Brooks Barnhizer': '布鲁克斯·巴恩希泽',
    'Bruce Brown': '布鲁斯·布朗', 'Bryce McGowens': '布赖斯·麦高文斯',
    'Bub Carrington': '巴布·卡林顿', 'Buddy Boeheim': '巴迪·伯海姆',
    # C
    'CJ Huntley': 'CJ·亨特利', 'Caleb Houstan': '凯莱布·豪斯坦',
    'Caleb Love': '凯莱布·洛夫', 'Cam Christie': '卡姆·克里斯蒂',
    'Cam Spencer': '卡姆·斯宾塞', 'Cam Thomas': '卡姆·托马斯',
    'Cam Whitmore': '卡姆·惠特莫尔', 'Cameron Johnson': '卡梅隆·约翰逊',
    'Cameron Payne': '卡梅隆·佩恩', 'Caris LeVert': '卡里斯·勒韦尔',
    'Cedric Coward': '塞德里克·考沃德', 'Chaney Johnson': '切尼·约翰逊',
    'Charles Bassey': '查尔斯·巴西', 'Chaz Lanier': '查兹·拉尼尔',
    'Chris Boucher': '克里斯·鲍彻', 'Chris Livingston': '克里斯·利文斯顿',
    'Chris Youngblood': '克里斯·扬布拉德', 'Christian Braun': '克里斯蒂安·布劳恩',
    'Christian Koloko': '克里斯蒂安·科洛科', 'Chucky Hepburn': '查基·赫本',
    'Cody Martin': '科迪·马丁', 'Cody Williams': '科迪·威廉姆斯',
    'Colby Jones': '科尔比·琼斯', 'Cole Anthony': '科尔·安东尼',
    'Colin Castleton': '科林·卡斯尔顿', 'Collin Gillespie': '科林·吉莱斯皮',
    'Collin Murray-Boyles': '科林·默里-博伊尔斯', 'Cooper Flagg': '库珀·弗拉格',
    'Cormac Ryan': '科马克·瑞安', 'Craig Porter Jr.': '克雷格·波特',
    'Curtis Jones': '柯蒂斯·琼斯',
    # D
    'D\'Angelo Russell': '丹吉洛·拉塞尔', 'DaQuan Jeffries': '达昆·杰弗里斯',
    'DaRon Holmes II': '达龙·霍姆斯二世', 'Daeqwon Plowden': '德昆·普洛登',
    'Dalano Banton': '达拉诺·班顿', 'Dalen Terry': '达伦·特里',
    'Daniss Jenkins': '丹尼斯·詹金斯', 'Danny Wolf': '丹尼·沃尔夫',
    'Dario Saric': '达里奥·萨里奇', 'Dariq Whitehead': '达里克·怀特黑德',
    'Darius Brown': '达里厄斯·布朗', 'David Jones Garcia': '大卫·琼斯·加西亚',
    'David Roddy': '大卫·罗迪', 'Davion Mitchell': '达维昂·米切尔',
    'Day\'Ron Sharpe': '戴伦·夏普', 'De\'Andre Hunter': '德安德烈·亨特',
    'DeAndre Jordan': '德安德烈·乔丹', 'DeJon Jarreau': '德琼·贾罗',
    'Dean Wade': '迪恩·韦德', 'Dennis Schröder': '丹尼斯·施罗德',
    'Dereck Lively II': '德里克·莱夫利二世', 'Derik Queen': '德里克·奎因',
    # E-F
    'Emoni Bates': '埃莫尼·贝茨', 'Eric Gordon': '埃里克·戈登',
    'Evan Fournier': '埃文·富尼耶', 'Franz Wagner': '弗朗茨·瓦格纳',
    'Fred VanVleet': '弗雷德·范弗利特',
    # G-H
    'Gary Trent Jr.': '小加里·特伦特', 'Grant Williams': '格兰特·威廉姆斯',
    'Grayson Allen': '格雷森·阿伦', 'Guerschon Yabusele': '盖尔雄·亚布塞莱',
    'Harrison Barnes': '哈里森·巴恩斯',
    # I-J
    'Immanuel Quickley': '伊曼纽尔·奎克利', 'Isaiah Hartenstein': '以赛亚·哈滕斯坦因',
    'Jaden Hardy': '杰登·哈迪', 'Jalen Smith': '杰伦·史密斯',
    'Jalen Wilson': '杰伦·威尔逊', 'James Bouknight': '詹姆斯·鲍克奈特',
    'Jaime Jaquez Jr.': '海梅·哈克斯', 'Jared Butler': '贾里德·巴特勒',
    'Jaxson Hayes': '杰克森·海耶斯', 'Jaylen Brown': '杰伦·布朗',
    'Jeff Green': '杰夫·格林', 'Jett Howard': '杰特·霍华德',
    'Jimmy Butler': '吉米·巴特勒', 'John Collins': '约翰·柯林斯',
    'Jonas Valanciunas': '约纳斯·瓦兰丘纳斯', 'Jordan Hawkins': '乔丹·霍金斯',
    'Josh Giddey': '吉迪', 'Josh Hart': '乔什·哈特',
    'Josh Okogie': '乔什·奥科吉', 'Julian Champagnie': '朱利安·尚帕尼',
    'Julius Randle': '朱利叶斯·兰德尔',
    # K-L
    'Keita Bates-Diop': '基塔·贝茨-迪奥普', 'Kelly Oubre Jr.': '小凯利·乌布雷',
    'Kendrick Nunn': '肯德里克·纳恩', 'Kenrich Williams': '肯里奇·威廉姆斯',
    'Kessler Edwards': '凯斯勒·爱德华兹', 'Kevin Huerter': '凯文·赫尔特',
    'Kevin Love': '凯文·乐福', 'Kevon Looney': '凯文·卢尼',
    'Khris Middleton': '克里斯·米德尔顿', 'Klay Thompson': '克莱·汤普森',
    'Kris Murray': '克里斯·默里', 'Kyle Anderson': '凯尔·安德森',
    'Kyle Kuzma': '凯尔·库兹马', 'Kyrie Irving': '凯里·欧文',
    # L-M
    'LaMelo Ball': '拉梅洛·鲍尔', 'Landry Shamet': '兰德里·沙梅特',
    'Larry Nance Jr.': '小拉里·南斯', 'Lauri Markkanen': '劳里·马尔卡宁',
    'LeBron James': '勒布朗·詹姆斯', 'Lindy Waters III': '林迪·沃特斯三世',
    'Lonzo Ball': '朗佐·鲍尔', 'Luguentz Dort': '卢根茨·多尔特',
    'Malik Beasley': '马利克·比斯利', 'Malik Monk': '马利克·蒙克',
    'Marcus Morris': '马库斯·莫里斯', 'Marcus Smart': '马库斯·斯马特',
    'Mark Williams': '马克·威廉姆斯', 'Mason Plumlee': '梅森·普拉姆利',
    'Matisse Thybulle': '马蒂斯·赛布尔', 'Max Strus': '马克斯·斯特鲁斯',
    'Michael Porter Jr.': '迈克尔·波特', 'Mikal Bridges': '米卡尔·布里奇斯',
    'Miles Bridges': '迈尔斯·布里奇斯', 'Mitchell Robinson': '米切尔·罗宾逊',
    # N-O
    'Naz Reid': '纳兹·里德', 'Nic Claxton': '尼古拉斯·克拉克斯顿',
    'Nick Richards': '尼克·理查兹', 'Nikola Vucevic': '尼古拉·武切维奇',
    'Norman Powell': '诺曼·鲍威尔', 'OG Anunoby': 'OG·阿奴诺比',
    'Obi Toppin': '奥比·托平', 'Onyeka Okongwu': '奥涅卡·奥孔武',
    # P-R
    'Paolo Banchero': '保罗·班切罗', 'Pascal Siakam': '帕斯卡尔·西亚卡姆',
    'Patrick Williams': '帕特里克·威廉姆斯', 'Paul George': '保罗·乔治',
    'PJ Washington': 'PJ·华盛顿', 'Quentin Grimes': '昆汀·格兰姆斯',
    'RJ Barrett': 'RJ·巴雷特', 'Rui Hachimura': '八村塁',
    'Russell Westbrook': '拉塞尔·威斯布鲁克',
    # S
    'Saddiq Bey': '萨迪克·贝', 'Sam Hauser': '萨姆·豪瑟',
    'Scottie Barnes': '斯科蒂·巴恩斯', 'Seth Curry': '赛斯·库里',
    'Shaedon Sharpe': '谢登·夏普', 'Shai Gilgeous-Alexander': '谢伊·吉尔杰斯-亚历山大',
    'TJ McConnell': 'TJ·麦康奈尔',
    # T
    'Tari Eason': '塔里·伊森', 'Terry Rozier': '特里·罗齐尔',
    'Tyrese Haliburton': '泰瑞斯·哈利伯顿', 'Tyrese Maxey': '泰瑞斯·马克西',
    # V-Z
    'Victor Wembanyama': '维克托·文班亚马', 'Walker Kessler': '沃克·凯斯勒',
    'Wendell Carter Jr.': '温德尔·卡特', 'Zach LaVine': '扎克·拉文',
    'Ziaire Williams': '齐艾尔·威廉姆斯', 'Zion Williamson': '锡安·威廉姆森',
    # === 补充翻译2 ===
    # D
    'Derrick Jones Jr.': '德里克·琼斯', 'Devin Carter': '德文·卡特',
    'Dillon Jones': '狄龙·琼斯', 'Dominick Barlow': '多米尼克·巴洛',
    'Donovan Clingan': '多诺万·克林根', 'Doug McDermott': '道格·麦克德莫特',
    'Drake Powell': '德雷克·鲍威尔', 'Draymond Green': '德雷蒙德·格林',
    'Drew Eubanks': '德鲁·尤班克斯', 'Drew Peterson': '德鲁·彼得森',
    'Drew Timme': '德鲁·蒂姆', 'Dru Smith': '德鲁·史密斯',
    'Duncan Robinson': '邓肯·罗宾逊', 'Duop Reath': '杜奥普·里斯',
    'Dwight Powell': '德怀特·鲍威尔', 'Dylan Cardwell': '迪伦·卡德韦尔',
    'Dyson Daniels': '戴森·丹尼尔斯',
    # E
    'E.J. Liddell': 'EJ·利德尔', 'Egor Dëmin': '埃戈尔·德明',
    'Elijah Harkless': '伊莱贾·哈克勒斯', 'Emanuel Miller': '伊曼纽尔·米勒',
    'Enrique Freeman': '恩里克·弗里曼', 'Ethan Thompson': '伊桑·汤普森',
    # G
    'GG Jackson': 'GG·杰克逊', 'Garrett Temple': '加勒特·坦普尔',
    'Garrison Mathews': '加里森·马修斯', 'Gary Harris': '加里·哈里斯',
    'Goga Bitadze': '戈加·比塔泽', 'Gradey Dick': '格雷迪·迪克',
    'Grant Nelson': '格兰特·纳尔逊',
    # H
    'Harrison Ingram': '哈里森·英格拉姆', 'Hayden Gray': '海登·格雷',
    'Haywood Highsmith': '海伍德·海史密斯', 'Hugo González': '雨果·冈萨雷斯',
    'Hunter Dickinson': '亨特·迪金森', 'Hunter Sallis': '亨特·萨利斯',
    'Hunter Tyson': '亨特·泰森',
    # I
    'Isaac Jones': '艾萨克·琼斯', 'Isaac Okoro': '艾萨克·奥科罗',
    'Isaiah Collier': '以赛亚·科利尔', 'Isaiah Crawford': '以赛亚·克劳福德',
    'Isaiah Jackson': '以赛亚·杰克逊', 'Isaiah Livers': '以赛亚·利弗斯',
    'Isaiah Stevens': '以赛亚·史蒂文斯', 'Isaiah Stewart': '以赛亚·斯图尔特',
    # J
    'JD Davison': 'JD·戴维森', 'Ja\'Kobe Walter': '贾科比·沃尔特',
    'Jabari Smith Jr.': '贾巴里·史密斯', 'Jabari Walker': '贾巴里·沃克',
    'Jacob Toppin': '雅各布·托平', 'Jae\'Sean Tate': '杰肖恩·泰特',
    'Jahmai Mashack': '杰迈·马沙克', 'Jahmir Young': '贾米尔·杨',
    'Jake LaRavia': '杰克·拉拉维亚', 'Jakob Poeltl': '雅各布·珀尔特尔',
    'Jalen Pickett': '杰伦·皮克特', 'Jalen Slawson': '杰伦·斯劳森',
    'Jamal Cain': '贾马尔·凯恩', 'Jamal Shead': '贾马尔·谢德',
    'James Wiseman': '詹姆斯·怀斯曼', 'Jarace Walker': '贾雷斯·沃克',
    'Javonte Green': '贾冯特·格林', 'Jay Huff': '杰伊·赫夫',
    'Jaylen Wells': '杰伦·韦尔斯', 'Jaylon Tyson': '杰伦·泰森',
    'Jeremiah Robinson-Earl': '杰里迈亚·罗宾逊-厄尔', 'Jericho Sims': '杰里科·西姆斯',
    'Jevon Carter': '杰文·卡特', 'Jimmy Butler III': '吉米·巴特勒',
    'Jock Landale': '乔克·兰代尔', 'Joe Ingles': '乔·英格尔斯',
    'John Konchar': '约翰·康查尔', 'Jonas Valančiūnas': '约纳斯·瓦兰丘纳斯',
    'Jonathan Isaac': '乔纳森·艾萨克', 'Jonathan Mogbo': '乔纳森·莫格博',
    'Jordan Goodwin': '乔丹·古德温', 'Jordan Miller': '乔丹·米勒',
    'Jordan Walsh': '乔丹·沃尔什', 'Jose Alvarado': '何塞·阿尔瓦拉多',
    'Josh Green': '乔什·格林', 'Josh Minott': '乔什·米诺特',
    'Julian Phillips': '朱利安·菲利普斯', 'Julian Strawther': '朱利安·斯特劳瑟',
    'Justin Edwards': '贾斯汀·爱德华兹', 'Jusuf Nurkić': '尤素夫·努尔基奇',
    # K
    'KJ Simpson': 'KJ·辛普森', 'Kam Jones': '卡姆·琼斯',
    'Keaton Wallace': '基顿·华莱士', 'Kel\'el Ware': '凯尔·韦尔',
    'Kennedy Chandler': '肯尼迪·钱德勒', 'Keon Ellis': '基恩·埃利斯',
    'Keshad Johnson': '克沙德·约翰逊', 'Kevin Porter Jr.': '凯文·波特',
    'Keyonte George': '基昂特·乔治', 'Killian Hayes': '基利安·海耶斯',
    'Kobe Brown': '科比·布朗', 'Kobe Bufkin': '科比·布夫金',
    'Kon Knueppel': '康·克内佩尔', 'Kris Dunn': '克里斯·邓恩',
    'Kristaps Porziņģis': '克里斯塔普斯·波尔津吉斯', 'Kyle Lowry': '凯尔·洛瑞',
    # L
    'LJ Cryer': 'LJ·克里尔', 'Leaky Black': '莱基·布莱克',
    'Leonard Miller': '伦纳德·米勒', 'Luka Garza': '卢卡·加尔扎',
    'Luke Kennard': '卢克·肯纳德',
    # M
    'Mac McClung': '麦克·麦克朗', 'Malaki Branham': '马拉基·布拉纳姆',
    'Marcus Sasser': '马库斯·萨瑟', 'Mark Sears': '马克·西尔斯',
    'Markelle Fultz': '马克尔·富尔茨', 'Marvin Bagley III': '马文·巴格利',
    'Matas Buzelis': '马塔斯·布泽利斯', 'Max Christie': '马克斯·克里斯蒂',
    'Maxi Kleber': '马克西·克勒贝尔', 'Maxime Raynaud': '马克西姆·雷诺',
    'Mike Conley': '迈克·康利', 'Miles McBride': '迈尔斯·麦克布莱德',
    'Mo Bamba': '穆罕默德·班巴', 'Monte Morris': '蒙特·莫里斯',
    'Moritz Wagner': '莫里茨·瓦格纳',
    # N
    'Naji Marshall': '纳吉·马歇尔', 'Neemias Queta': '内米亚斯·奎塔',
    'Nick Smith Jr.': '尼克·史密斯', 'Nickeil Alexander-Walker': '尼基尔·亚历山大-沃克',
    'Nicolas Batum': '尼古拉斯·巴图姆', 'Nikola Jović': '尼古拉·约维奇',
    'Noah Clowney': '诺亚·克劳尼', 'Nolan Traore': '诺兰·特拉奥雷',
    # O
    'Ochai Agbaji': '奥柴·阿巴吉', 'Ousmane Dieng': '奥斯曼·迪昂',
    # P
    'PJ Hall': 'PJ·霍尔', 'Pat Connaughton': '帕特·康诺顿',
    'Paul Reed': '保罗·里德', 'Peyton Watson': '佩顿·沃特森',
    'Precious Achiuwa': '普雷舍斯·阿丘瓦',
    # R
    'Rasheer Fleming': '拉希尔·弗莱明', 'Rayan Rupert': '拉扬·鲁珀特',
    'Reed Sheppard': '里德·谢泼德', 'Rob Dillingham': '罗布·迪林厄姆',
    'Robert Williams III': '罗伯特·威廉姆斯', 'Royce O\'Neale': '罗伊斯·奥尼尔',
    'Ryan Dunn': '瑞安·邓恩', 'Ryan Rollins': '瑞安·罗林斯',
    # S
    'Sam Merrill': '萨姆·梅里尔', 'Sandro Mamukelashvili': '桑德罗·马穆克拉什维利',
    'Santi Aldama': '桑蒂·阿尔达马', 'Scoot Henderson': '斯库特·亨德森',
    'Scotty Pippen Jr.': '小斯科蒂·皮蓬', 'Simone Fontecchio': '西蒙·丰泰基奥',
    # T
    'T.J. McConnell': 'TJ·麦康奈尔', 'Taj Gibson': '泰·吉布森',
    'Taurean Prince': '陶里安·普林斯', 'Taylor Hendricks': '泰勒·亨德里克斯',
    'Terance Mann': '特伦斯·曼恩', 'Terrence Shannon Jr.': '特伦斯·香农',
    'Thanasis Antetokounmpo': '萨纳西斯·阿德托昆博', 'Thomas Bryant': '托马斯·布莱恩特',
    'Tidjane Salaün': '蒂贾内·萨隆', 'Tim Hardaway Jr.': '小蒂姆·哈达威',
    'Toumani Camara': '图马尼·卡马拉', 'Tre Jones': '特雷·琼斯',
    'Tre Mann': '特雷·曼恩', 'Trendon Watford': '特伦顿·沃特福德',
    'Trey Murphy III': '特雷·墨菲', 'Tristan Vukcevic': '特里斯坦·武克切维奇',
    'Tristan da Silva': '特里斯坦·达席尔瓦', 'Ty Jerome': '泰·杰罗姆',
    'Tyus Jones': '泰厄斯·琼斯',
    # V-W
    'VJ Edgecombe': 'VJ·埃奇科姆', 'Wendell Moore Jr.': '小温德尔·摩尔',
    'Walter Clayton Jr.': '沃尔特·克莱顿',
    # X-Z
    'Xavier Tillman': '泽维尔·蒂尔曼', 'Zach Collins': '扎克·柯林斯',
    'Zach Edey': '扎克·埃迪', 'Zeke Nnaji': '齐克·纳吉',
    # 已翻译但包含英文后缀的
    'Chris Mañon': '克里斯·马尼翁',
    # === 补充翻译3 ===
    'Jahmyl Telfort': '贾米尔·特尔福特', 'Jamaree Bouyea': '贾马里·布耶亚',
    'Jamir Watkins': '贾米尔·沃特金斯', 'Jamison Battle': '贾米森·巴特尔',
    'Jase Richardson': '杰斯·理查森', 'Javon Small': '贾冯·斯莫尔',
    'Javonte Cooke': '贾冯特·库克', 'Jaylen Clark': '杰伦·克拉克',
    'Jayson Kent': '杰森·肯特', 'Jeremiah Fears': '杰里迈亚·费尔斯',
    'Joan Beringer': '琼·贝林格', 'John Poulakidas': '约翰·普拉基达斯',
    'John Tonje': '约翰·通杰', 'Johni Broome': '约翰尼·布鲁姆',
    'Johnny Furphy': '约翰尼·弗菲', 'Johnny Juzang': '约翰尼·朱赞',
    'Josh Oduro': '乔什·奥杜罗', 'Julian Reese': '朱利安·里斯',
    'Justin Champagnie': '贾斯汀·尚帕尼', 'Kadary Richmond': '卡达里·里奇蒙德',
    'Karlo Matković': '卡尔洛·马特科维奇', 'Kasparas Jakučionis': '卡斯帕拉斯·雅库乔尼斯',
    'Keshon Gilbert': '凯申·吉尔伯特', 'Kevin McCullar Jr.': '小凯文·麦卡勒',
    'Khaman Maluach': '哈曼·马拉奇', 'Kobe Sanders': '科比·桑德斯',
    'Koby Brea': '科比·布里亚', 'Kyle Filipowski': '凯尔·菲利波夫斯基',
    'Kyshawn George': '凯肖恩·乔治', 'Lachlan Olbrich': '拉克兰·奥尔布里奇',
    'Lawson Lovering': '劳森·洛夫林', 'Liam McNeeley': '利亚姆·麦克尼利',
    'Lucas Williamson': '卢卡斯·威廉姆森', 'Luke Travers': '卢克·特拉弗斯',
    'Malachi Smith': '马拉基·史密斯', 'Malevy Leons': '马列维·莱昂斯',
    'MarJon Beauchamp': '马琼·博尚', 'Max Shulga': '马克斯·舒尔加',
    'Micah Peavy': '米卡·皮维', 'Micah Potter': '米卡·波特',
    'Miles Kelly': '迈尔斯·凯利', 'Mohamed Diawara': '穆罕默德·迪亚瓦拉',
    'Mouhamadou Gueye': '穆哈马杜·盖耶', 'Mouhamed Gueye': '穆罕默德·盖耶',
    'Moussa Cisse': '穆萨·西塞', 'Moussa Diabaté': '穆萨·迪亚巴特',
    'Myron Gardner': '迈伦·加德纳', 'N\'Faly Dante': '恩法利·丹特',
    'Nae\'Qwan Tomlin': '奈奎恩·汤姆林', 'Nate Williams': '内特·威廉姆斯',
    'Nigel Hayes-Davis': '奈杰尔·海耶斯-戴维斯', 'Nique Clifford': '尼克·克利福德',
    'Noa Essengue': '诺亚·埃森古', 'Noah Penda': '诺亚·彭达',
    'Norchad Omier': '诺查德·奥米尔', 'Olivier Sarr': '奥利维尔·萨尔',
    'Olivier-Maxence Prosper': '奥利维尔-马克桑斯·普罗斯珀', 'Omer Yurtseven': '奥默·尤尔特塞文',
    'Orlando Robinson': '奥兰多·罗宾逊', 'Oscar Tshiebwe': '奥斯卡·希布韦',
    'Oso Ighodaro': '奥索·伊戈达罗', 'Pacôme Dadiet': '帕科姆·达迪特',
    'Patrick Baldwin Jr.': '小帕特里克·鲍德温', 'Payton Sandfort': '佩顿·桑德福特',
    'Pelle Larsson': '佩莱·拉尔森', 'Pete Nance': '皮特·南斯',
    'Quenton Jackson': '昆顿·杰克逊', 'RayJ Dennis': '雷杰·丹尼斯',
    'Riley Minix': '莱利·米尼克斯', 'Rocco Zikarsky': '罗科·齐卡斯基',
    'Ron Harper Jr.': '小罗恩·哈珀', 'Ronald Holland II': '罗纳德·霍兰德二世',
    'Ryan Kalkbrenner': '瑞安·卡尔布伦纳', 'Ryan Nembhard': '瑞安·内姆哈德',
    'Sean Pedulla': '肖恩·佩杜拉', 'Sharife Cooper': '沙里夫·库珀',
    'Sidy Cissoko': '西迪·西索科', 'Sion James': '赛恩·詹姆斯',
    'Skal Labissiere': '斯卡尔·拉比西埃', 'Spencer Jones': '斯宾塞·琼斯',
    'Stanley Umude': '斯坦利·乌穆德', 'Steven Adams': '史蒂文·亚当斯',
    'Svi Mykhailiuk': '斯维·米哈伊柳克', 'Taelon Peter': '泰伦·彼得',
    'Toby Okani': '托比·奥卡尼', 'Tolu Smith': '托卢·史密斯',
    'Tony Bradley': '托尼·布拉德利', 'Tosan Evbuomwan': '托桑·埃布奥姆万',
    'Tre Johnson': '特雷·约翰逊', 'Trentyn Flowers': '特伦廷·弗劳尔斯',
    'Trevon Scott': '特雷冯·斯科特', 'Trevor Keels': '特雷弗·基尔斯',
    'Trey Alexander': '特雷·亚历山大', 'Trey Jemison III': '特雷·杰米森',
    'Tristan Enaruna': '特里斯坦·埃纳鲁纳', 'Tristen Newton': '特里斯顿·牛顿',
    'TyTy Washington Jr.': '小泰泰·华盛顿', 'Tyler Burton': '泰勒·伯顿',
    'Tyler Kolek': '泰勒·科莱克', 'Tyler Smith': '泰勒·史密斯',
    'Tyrese Martin': '泰瑞斯·马丁', 'Tyrese Proctor': '泰瑞斯·普罗克特',
    'Tyson Etienne': '泰森·艾蒂安', 'Vince Williams Jr.': '小文斯·威廉姆斯',
    'Vladislav Goldin': '弗拉迪斯拉夫·戈尔丁', 'Vít Krejčí': '维特·克雷伊奇',
    'Will Richard': '威尔·理查德', 'Will Riley': '威尔·莱利',
    'Yang Hansen': '杨瀚森', 'Yanic Konan Niederhäuser': '亚尼克·科南·尼德豪泽',
    'Yuki Kawamura': '河村勇辉', 'Yves Missi': '伊夫·米西',
    'Zaccharie Risacher': '扎卡里·里萨谢尔', 'Zyon Pullin': '赛恩·普林',
}

# 常见NBA术语英文->中文映射
NBA_TERM_MAP = {
    'triple-double': '三双', 'double-double': '两双',
    'buzzer-beater': '压哨球', 'game-winner': '绝杀',
    'dunk': '扣篮', 'slam dunk': '暴扣',
    'three-pointer': '三分球', '3-pointer': '三分球',
    'free throw': '罚球', 'field goal': '投篮',
    'rebound': '篮板', 'assist': '助攻',
    'steal': '抢断', 'block': '盖帽',
    'turnover': '失误', 'foul': '犯规',
    'overtime': '加时赛', 'OT': '加时',
    'MVP': '最有价值球员', 'Finals': '总决赛',
    'playoffs': '季后赛', 'regular season': '常规赛',
    'draft': '选秀', 'rookie': '新秀',
    'trade': '交易', 'sign': '签约',
    'contract': '合同', 'injury': '伤病',
    'out': '缺阵', 'questionable': '出战存疑',
    'probable': '大概率出战', 'day-to-day': '每日观察',
    'season-high': '赛季新高', 'career-high': '生涯新高',
    'win streak': '连胜', 'losing streak': '连败',
    'home court': '主场优势', 'road game': '客场',
    'recap': '回顾', 'preview': '前瞻',
    'highlights': '精彩集锦', 'stat line': '数据线',
    'points': '分', 'rebounds': '篮板', 'assists': '助攻',
    'leads': '领衔', 'scores': '砍下',
    'defeats': '击败', 'beats': '击败',
    'top scorer': '得分王', 'head coach': '主教练',
}

# 动态翻译映射文件路径
DYNAMIC_MAP_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'player_cn_map_dynamic.json')

# 加载动态翻译映射
_dynamic_cn_map = {}
if os.path.exists(DYNAMIC_MAP_FILE):
    try:
        with open(DYNAMIC_MAP_FILE, 'r', encoding='utf-8') as f:
            _dynamic_cn_map = json.load(f)
    except Exception:
        _dynamic_cn_map = {}

def save_dynamic_map():
    """保存动态翻译映射到文件"""
    try:
        with open(DYNAMIC_MAP_FILE, 'w', encoding='utf-8') as f:
            json.dump(_dynamic_cn_map, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"保存动态映射失败: {e}", file=sys.stderr)

def translate_player_name(en_name):
    """翻译球员名：先查本地映射，再调用MIMO API，最后保存映射"""
    # 1. 先查静态映射
    if en_name in PLAYER_CN_MAP:
        return PLAYER_CN_MAP[en_name], 'MAPPED'

    # 2. 再查动态映射
    if en_name in _dynamic_cn_map:
        return _dynamic_cn_map[en_name], 'MAPPED'

    # 3. 调用MIMO API翻译
    if HAS_TRANSLATOR:
        try:
            prompt = f"请将以下NBA球员名字翻译为中文，只输出中文名字，不要添加其他内容：\n{en_name}"
            cn_name = translate_text(prompt, max_retries=1)
            if cn_name and cn_name != en_name and len(cn_name) < len(en_name) * 3:
                # 翻译成功，保存到动态映射
                _dynamic_cn_map[en_name] = cn_name
                save_dynamic_map()
                return cn_name, 'API_TRANSLATED'
        except Exception as e:
            pass  # 翻译失败时返回英文名

    # 4. 翻译失败，返回英文名
    return en_name, 'UNTRANSLATED'

# 球员英文名 -> 球衣号码 映射（使用字符串以支持"00"等格式）
PLAYER_JERSEY_MAP = {
    # 湖人
    'Luka Doncic': '77', 'LeBron James': '23', 'Austin Reaves': '15',
    'Rui Hachimura': '28', 'Dalton Knecht': '4', 'Christian Wood': '35',
    'Jarred Vanderbilt': '2', 'Jaxson Hayes': '10', 'Gabe Vincent': '7',
    # 勇士
    'Stephen Curry': '30', 'Jimmy Butler': '22', 'Brandin Podziemski': '2',
    'Jonathan Kuminga': '00', 'De\'Anthony Melton': '8', 'Gary Payton II': '0',
    'Buddy Hield': '24', 'Quinten Post': '21', 'Pat Spencer': '61',
    'Trayce Jackson-Davis': '32', 'Gui Santos': '15', 'Kevon Looney': '5',
    'Moses Moody': '4',
    # 凯尔特人
    'Jayson Tatum': '0', 'Jaylen Brown': '7', 'Kristaps Porzingis': '8',
    'Derrick White': '9', 'Jrue Holiday': '4', 'Payton Pritchard': '11',
    'Al Horford': '42', 'Sam Hauser': '30', 'Luke Kornet': '40',
    # 雄鹿
    'Giannis Antetokounmpo': '34', 'Damian Lillard': '0', 'Khris Middleton': '22',
    'Brook Lopez': '11', 'Bobby Portis': '9',
    # 掘金
    'Nikola Jokic': '15', 'Nikola Jokić': '15', 'Jamal Murray': '27', 'Michael Porter Jr.': '1',
    'Aaron Gordon': '50',
    # 太阳
    'Kevin Durant': '35', 'Devin Booker': '1', 'Bradley Beal': '3',
    # 雷霆
    'Shai Gilgeous-Alexander': '2', 'Chet Holmgren': '7', 'Jalen Williams': '8',
    # 马刺
    'Victor Wembanyama': '1', 'Devin Vassell': '24', 'Chris Paul': '3',
    # 火箭
    'Alperen Sengun': '28', 'Jalen Green': '4', 'Fred VanVleet': '5',
    # 尼克斯
    'Jalen Brunson': '11', 'Julius Randle': '30', 'OG Anunoby': '8',
    'Mikal Bridges': '1', 'Karl-Anthony Towns': '32',
    # 骑士
    'Donovan Mitchell': '45', 'Darius Garland': '10', 'Evan Mobley': '4',
    'Jarrett Allen': '31',
    # 森林狼
    'Anthony Edwards': '5', 'Rudy Gobert': '27',
    # 活塞
    'Cade Cunningham': '2', 'Jaden Ivey': '23',
    # 独行侠
    'Kyrie Irving': '11', 'P.J. Washington': '25', 'Dereck Lively': '1',
    # 76人
    'Joel Embiid': '21', 'Tyrese Maxey': '0', 'Paul George': '13',
    # 老鹰
    'Trae Young': '11', 'Dejounte Murray': '5',
    # 猛龙
    'Scottie Barnes': '4', 'RJ Barrett': '9',
    # 热火
    'Bam Adebayo': '13', 'Tyler Herro': '14', 'Terry Rozier': '2',
    # 快船
    'Kawhi Leonard': '2', 'James Harden': '1', 'Norman Powell': '24',
    # 国王
    'De\'Aaron Fox': '5', 'Domantas Sabonis': '10', 'DeMar DeRozan': '10',
    # 鹈鹕
    'Zion Williamson': '1', 'Brandon Ingram': '14', 'CJ McCollum': '3',
    # 魔术
    'Paolo Banchero': '5', 'Franz Wagner': '22',
    # 步行者
    'Tyrese Haliburton': '0', 'Myles Turner': '33',
    # 公牛
    'Zach LaVine': '8', 'Nikola Vucevic': '9',
    # 灰熊
    'Ja Morant': '12', 'Desmond Bane': '22', 'Jaren Jackson Jr.': '13',
    # 开拓者
    'Anfernee Simons': '1', 'Jerami Grant': '9',
    # 爵士
    'Lauri Markkanen': '23', 'Collin Sexton': '2',
    # 篮网
    'Cameron Thomas': '24', 'Nic Claxton': '33',
    # 黄蜂
    'LaMelo Ball': '1', 'Miles Bridges': '0', 'Brandon Miller': '24',
    # 奇才
    'Kyle Kuzma': '33', 'Jordan Poole': '13', 'Alex Sarr': '20',
    # 其他球员
    'Andrew Wiggins': '22', 'Anthony Davis': '3', 'Russell Westbrook': '4',
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


def _format_minutes(minutes_str):
    """将ISO 8601时间格式（PT36M05.00S）转换为可读格式（36:05）"""
    if not minutes_str or not isinstance(minutes_str, str):
        return str(minutes_str) if minutes_str else ''

    # 如果已经是正常格式，直接返回
    if ':' in minutes_str and 'PT' not in minutes_str:
        return minutes_str

    try:
        # 解析 PT#M#S 格式
        import re
        match = re.match(r'PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?', minutes_str)
        if not match:
            return minutes_str

        hours = int(match.group(1) or 0)
        minutes = int(match.group(2) or 0)
        seconds = float(match.group(3) or 0)

        total_minutes = hours * 60 + minutes
        total_seconds = int(seconds)

        if total_seconds > 0:
            return f"{total_minutes}:{total_seconds:02d}"
        else:
            return str(total_minutes)
    except Exception:
        return minutes_str


def _get_current_season():
    """获取当前赛季字符串，如 '2024-25'"""
    now = datetime.now()
    if now.month >= 10:
        return f"{now.year}-{str(now.year + 1)[-2:]}"
    else:
        return f"{now.year - 1}-{str(now.year)[-2:]}"


def translate_text_safe(text):
    """安全翻译文本，失败时返回原文"""
    if not text or not text.strip():
        return text
    if HAS_TRANSLATOR:
        try:
            return translate_text(text)
        except Exception as e:
            print(f"翻译失败: {e}", file=sys.stderr)
            return text
    return text


def translate_with_mapping(text):
    """使用本地映射表翻译文本（球员名 + 球队名 + NBA术语）"""
    import re
    if not text:
        return text

    # 1. 先翻译球员名（更长的先匹配，避免短名误匹配）
    sorted_players = sorted(PLAYER_CN_MAP.items(), key=lambda x: len(x[0]), reverse=True)
    for en_name, cn_name in sorted_players:
        if en_name in text:
            text = text.replace(en_name, cn_name)

    # 2. 再翻译球队名（使用单词边界匹配）
    for en_name, cn_name in TEAM_SHORT_MAP.items():
        pattern = r'\b' + re.escape(en_name) + r'\b'
        text = re.sub(pattern, cn_name, text)

    # 3. 翻译NBA术语（按长度降序匹配）
    sorted_terms = sorted(NBA_TERM_MAP.items(), key=lambda x: len(x[0]), reverse=True)
    for en_term, cn_term in sorted_terms:
        if en_term.lower() in text.lower():
            # 保持大小写不敏感匹配
            pattern = r'\b' + re.escape(en_term) + r'\b'
            text = re.sub(pattern, cn_term, text, flags=re.IGNORECASE)

    return text


def needs_translation(text):
    """检查文本是否需要翻译（是否含有英文单词）"""
    import re
    if not text or not text.strip():
        return False
    # 检查是否包含完整的英文单词（至少3个字母的词）
    return bool(re.search(r'[a-zA-Z]{3,}', text))


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

        # 获取英文名和缩写
        en_name = data.get('TeamName', '')
        abbreviation = data.get('TeamAbbreviation', '')

        result.append({
            'name': cn_name,
            'nameEn': en_name,
            'abbreviation': abbreviation,
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

        # 位置映射 - 尝试多个可能的字段名
        pos_raw = data.get('PLAYER_POSITION', '') or data.get('POSITION', '') or data.get('POS', '')
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
        per = _safe_float(adv.get('E_OFF_RATING'), 110.0)
        ts_pct = _safe_float(adv.get('TS_PCT'), fg_pct)
        usg_pct = _safe_float(adv.get('USG_PCT')) * 100
        if usg_pct < 1.0:
            usg_pct = 20.0

        # 球员名翻译（先查映射，再调用MIMO API）
        en_name = data.get('PLAYER_NAME', '')
        cn_name, translation_status = translate_player_name(en_name)

        result.append({
            'nbaPlayerId': player_id,
            'name': cn_name,
            'nameEn': en_name,
            'translationStatus': translation_status,
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
            'jersey': str(PLAYER_JERSEY_MAP.get(en_name, '')),
            'height': data.get('HEIGHT', '6-6'),
            'weight': _safe_int(data.get('WEIGHT', 210)),
            'country': data.get('COUNTRY', '美国'),
        })

    # 批量获取球员详细信息（包括球衣号码和位置）
    # 从CommonPlayerInfo获取真实数据
    print(f"正在从CommonPlayerInfo获取{len(result)}名球员的详细信息...", file=sys.stderr)
    try:
        from nba_api.stats.endpoints import CommonPlayerInfo
        updated_count = 0
        for player in result:
            try:
                info = CommonPlayerInfo(player_id=player['nbaPlayerId'], timeout=15)
                info_data = info.get_dict()
                if info_data.get('resultSets'):
                    rows = info_data['resultSets'][0].get('rowSet', [])
                    if rows:
                        headers = info_data['resultSets'][0]['headers']
                        row_data = dict(zip(headers, rows[0]))
                        # 更新球衣号码
                        jersey_num = row_data.get('JERSEY', '').strip()
                        if jersey_num:
                            player['jersey'] = jersey_num
                        # 更新位置信息 - 从API获取真实位置
                        pos_raw = row_data.get('POSITION', '')
                        if pos_raw and pos_raw.strip():
                            player['position'] = POSITION_MAP.get(pos_raw.strip(), player['position'])
                        # 更新身高体重
                        if row_data.get('HEIGHT'):
                            player['height'] = row_data['HEIGHT']
                        if row_data.get('WEIGHT'):
                            player['weight'] = _safe_int(row_data['WEIGHT'])
                        if row_data.get('COUNTRY'):
                            player['country'] = row_data['COUNTRY']
                        updated_count += 1
                time.sleep(0.3)
            except Exception as e:
                pass  # 忽略单个球员的错误
        print(f"成功更新{updated_count}名球员的详细信息", file=sys.stderr)
    except Exception as e:
        print(f"获取球员详细信息失败: {e}", file=sys.stderr)

    # 注意：不再将空球衣号设置为默认值，保持为空字符串

    # 去重：同一球员只保留出场次数最多的记录（处理转会情况）
    seen = {}
    for player in result:
        nid = player['nbaPlayerId']
        if nid not in seen or player['gp'] > seen[nid]['gp']:
            seen[nid] = player
    result = list(seen.values())
    print(f"去重后球员数: {len(result)}", file=sys.stderr)

    # 按得分排序，取所有球员
    result.sort(key=lambda x: x['ppg'], reverse=True)

    print(f"获取到 {len(result)} 名球员数据", file=sys.stderr)
    return result


def fetch_today_games():
    """从nba_api获取今日比赛（实时数据）"""
    _init_team_map()
    print("正在获取今日比赛数据...", file=sys.stderr)

    result = []

    # 方式1: 尝试live endpoint（有实时比分和时钟）
    try:
        from nba_api.live.nba.endpoints import scoreboard as live_sb
        sb = live_sb.ScoreBoard(timeout=15)
        data = sb.get_dict()
        time.sleep(0.5)
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
        if result:
            print(f"Live API获取到 {len(result)} 场今日比赛", file=sys.stderr)
            return result
    except Exception as e:
        print(f"Live scoreboard失败，切换到ScoreboardV3: {e}", file=sys.stderr)

    # 方式2: 使用ScoreboardV3（兼容性更好，支持代理）
    try:
        today_str = datetime.now().strftime('%m/%d/%Y')
        sb = ScoreboardV3(game_date=today_str, timeout=15)
        data = sb.get_dict()
        time.sleep(0.5)
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
    except Exception as e:
        print(f"ScoreboardV3也失败: {e}", file=sys.stderr)

    print(f"获取到 {len(result)} 场今日比赛", file=sys.stderr)
    return result


def fetch_recent_games():
    """从nba_api获取最近比赛记录（最近30天）"""
    _init_team_map()
    print("正在获取最近比赛记录...", file=sys.stderr)

    games = []
    today = datetime.now()

    for days_ago in range(30):
        date = today - timedelta(days=days_ago)
        date_str = date.strftime('%m/%d/%Y')

        try:
            sb = ScoreboardV3(game_date=date_str, timeout=30)
            data = sb.get_dict()
            time.sleep(1.0)
        except Exception as e:
            print(f"获取{date_str}比赛数据失败: {e}", file=sys.stderr)
            continue

        # 解析比赛数据 (ScoreboardV3 格式)
        scoreboard = data.get('scoreboard', {})
        games_list = scoreboard.get('games', [])

        for game in games_list:
            game_id = game.get('gameId', '')
            home_team = game.get('homeTeam', {})
            away_team = game.get('awayTeam', {})

            home_id = _safe_int(home_team.get('teamId'))
            away_id = _safe_int(away_team.get('teamId'))
            home_score = _safe_int(home_team.get('score'))
            away_score = _safe_int(away_team.get('score'))

            home_cn = TEAM_ID_TO_CN.get(home_id, home_team.get('teamTricode', ''))
            away_cn = TEAM_ID_TO_CN.get(away_id, away_team.get('teamTricode', ''))

            game_status = game.get('gameStatus', 0)
            status = 'FINISHED' if game_status == 3 else 'LIVE' if game_status == 2 else 'SCHEDULED'

            games.append({
                'homeTeam': home_cn,
                'awayTeam': away_cn,
                'homeScore': home_score,
                'awayScore': away_score,
                'date': date.strftime('%Y-%m-%d'),
                'status': status,
                'gameId': game_id,
            })

    print(f"获取到 {len(games)} 场比赛记录", file=sys.stderr)
    return games


def fetch_playoff_bracket(season=None):
    """从NBA API和ESPN API获取季后赛对阵图（真实数据）"""
    _init_team_map()

    if season is None:
        current_year = datetime.now().year
        season = f"{current_year-1}-{str(current_year)[-2:]}"

    print(f"正在获取 {season} 赛季季后赛数据...", file=sys.stderr)

    # 第一步：从 NBA API 获取季后赛系列赛结构（包含轮次信息）
    series_data = fetch_playoff_series(season)
    print(f"从NBA API获取到 {len(series_data)} 组系列赛结构", file=sys.stderr)

    # 第二步：从 ESPN API 获取比赛比分
    start_year = int(season.split('-')[0])
    playoff_start = f"{start_year+1}0415"
    playoff_end = f"{start_year+1}0630"
    games_data = fetch_espn_playoff_games(playoff_start, playoff_end)
    print(f"从ESPN API获取到 {len(games_data)} 场比赛", file=sys.stderr)

    # 第三步：结合系列赛结构和比赛数据构建对阵图
    matchups = build_bracket(series_data, games_data)

    print(f"最终构建 {len(matchups)} 组系列赛", file=sys.stderr)
    return {'matchups': matchups, 'games': games_data}


def build_bracket_from_games(games_data):
    """仅从ESPN比赛数据构建季后赛对阵图"""
    # 按球队组合分组比赛
    series_map = {}
    for game in games_data:
        teams = tuple(sorted([game['homeTeam'], game['awayTeam']]))
        if teams not in series_map:
            series_map[teams] = {
                'team1Name': teams[0],
                'team2Name': teams[1],
                'team1Wins': 0,
                'team2Wins': 0,
                'games': [],
                'status': 'SCHEDULED',
                'conference': 'Unknown',
                'round': 1,
            }

        matchup = series_map[teams]
        matchup['games'].append(game)

        # 使用结构化的胜负数据
        home_wins = game.get('homeSeriesWins', 0)
        away_wins = game.get('awaySeriesWins', 0)

        # 更新胜负数（取最大值，因为每场比赛都会报告当前系列赛比分）
        if home_wins > 0 or away_wins > 0:
            if teams[0] == game['homeTeam']:
                matchup['team1Wins'] = max(matchup['team1Wins'], home_wins)
                matchup['team2Wins'] = max(matchup['team2Wins'], away_wins)
            else:
                matchup['team1Wins'] = max(matchup['team1Wins'], away_wins)
                matchup['team2Wins'] = max(matchup['team2Wins'], home_wins)

        # 判断状态
        if game.get('seriesCompleted'):
            matchup['status'] = 'COMPLETED'
            matchup['winner'] = matchup['team1Name'] if matchup['team1Wins'] > matchup['team2Wins'] else matchup['team2Name']
        elif matchup['team1Wins'] >= 4 or matchup['team2Wins'] >= 4:
            matchup['status'] = 'COMPLETED'
            matchup['winner'] = matchup['team1Name'] if matchup['team1Wins'] > matchup['team2Wins'] else matchup['team2Name']
        elif game['status'] == 'LIVE':
            matchup['status'] = 'IN_PROGRESS'
        elif matchup['games']:
            matchup['status'] = 'IN_PROGRESS'

    # 判断联盟
    eastern_teams = {'活塞', '老鹰', '凯尔特人', '76人', '魔术', '猛龙', '骑士', '尼克斯',
                     '热火', '黄蜂', '奇才', '步行者', '篮网', '公牛', '雄鹿'}

    for key, matchup in series_map.items():
        team1 = matchup['team1Name']
        matchup['conference'] = 'East' if team1 in eastern_teams else 'West'

    # 根据总胜场数排序和判断轮次
    sorted_matchups = sorted(series_map.values(), key=lambda x: x['team1Wins'] + x['team2Wins'])

    # 分配轮次（根据胜场数范围）
    for matchup in sorted_matchups:
        total_wins = matchup['team1Wins'] + matchup['team2Wins']
        if total_wins <= 4:
            matchup['round'] = 1
        elif total_wins <= 8:
            matchup['round'] = 2
        elif total_wins <= 12:
            matchup['round'] = 3
        else:
            matchup['round'] = 4

    return sorted_matchups


def fetch_playoff_series(season):
    """从NBA API获取季后赛对阵结构"""
    try:
        from nba_api.stats.endpoints import CommonPlayoffSeries
        # 明确指定不使用代理
        series = CommonPlayoffSeries(season=season, timeout=60, proxy='')
        data = series.get_dict()
        time.sleep(1.0)

        result = []
        seen_series = set()
        for rs in data.get('resultSets', []):
            if rs.get('name') != 'PlayoffSeries':
                continue
            headers = rs.get('headers', [])
            for row in rs.get('rowSet', []):
                d = dict(zip(headers, row))
                series_id = d.get('SERIES_ID', '')

                # 只处理每个系列赛一次（每场比赛都有一行）
                if series_id in seen_series:
                    continue
                seen_series.add(series_id)

                # 解析轮次: 0042500RN, R=轮次 (1=首轮, 2=半决赛, 3=分区决赛, 4=总决赛)
                round_num = 1
                if len(series_id) >= 8:
                    round_char = series_id[7]
                    round_num = int(round_char) if round_char.isdigit() else 1

                home_id = _safe_int(d.get('HOME_TEAM_ID'))
                away_id = _safe_int(d.get('VISITOR_TEAM_ID'))
                home_cn = TEAM_ID_TO_CN.get(home_id, str(home_id))
                away_cn = TEAM_ID_TO_CN.get(away_id, str(away_id))

                result.append({
                    'seriesId': series_id,
                    'homeTeamId': home_id,
                    'awayTeamId': away_id,
                    'homeTeam': home_cn,
                    'awayTeam': away_cn,
                    'round': round_num,
                })
        return result
    except Exception as e:
        print(f"获取季后赛对阵失败: {e}", file=sys.stderr)
        return []


def fetch_espn_playoff_games(start_date, end_date):
    """从ESPN API获取季后赛比赛（分段请求避免超时）"""
    all_games = []

    # 将日期范围分成更小的段（每30天一段）
    from datetime import datetime, timedelta
    start = datetime.strptime(start_date, '%Y%m%d')
    end = datetime.strptime(end_date, '%Y%m%d')

    current = start
    while current < end:
        chunk_end = min(current + timedelta(days=30), end)
        chunk_start_str = current.strftime('%Y%m%d')
        chunk_end_str = chunk_end.strftime('%Y%m%d')

        url = f"https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?seasontype=3&dates={chunk_start_str}-{chunk_end_str}&limit=100"
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Accept': 'application/json'})
            no_proxy_opener = urllib.request.build_opener()
            response = no_proxy_opener.open(req, timeout=30)
            data = json.loads(response.read().decode('utf-8'))

            events = data.get('events', [])
            for event in events:
                comp = event.get('competitions', [{}])[0]
                competitors = comp.get('competitors', [])
                if len(competitors) < 2:
                    continue

                home = next((c for c in competitors if c.get('homeAway') == 'home'), competitors[0])
                away = next((c for c in competitors if c.get('homeAway') == 'away'), competitors[1])

                home_team = home.get('team', {})
                away_team = away.get('team', {})

                status_type = comp.get('status', {}).get('type', {}).get('name', '')
                status = 'FINISHED' if status_type == 'STATUS_FINAL' else 'LIVE' if status_type == 'STATUS_IN_PROGRESS' else 'SCHEDULED'

                # 系列赛信息
                series = comp.get('series', {})
                series_summary = series.get('summary', '')
                series_completed = series.get('completed', False)

                # 从series.competitors获取胜负数
                series_competitors = series.get('competitors', [])
                home_series_wins = 0
                away_series_wins = 0
                for sc in series_competitors:
                    sc_id = sc.get('id', '')
                    sc_wins = sc.get('wins', 0)
                    if sc_id == home_team.get('id', ''):
                        home_series_wins = sc_wins
                    elif sc_id == away_team.get('id', ''):
                        away_series_wins = sc_wins

                all_games.append({
                    'homeTeam': TEAM_NAME_MAP.get(home_team.get('displayName', ''), home_team.get('displayName', '')),
                    'homeTeamEn': home_team.get('displayName', ''),
                    'homeTeamId': home_team.get('id', ''),
                    'awayTeam': TEAM_NAME_MAP.get(away_team.get('displayName', ''), away_team.get('displayName', '')),
                    'awayTeamEn': away_team.get('displayName', ''),
                    'awayTeamId': away_team.get('id', ''),
                    'homeScore': _safe_int(home.get('score', 0)),
                    'awayScore': _safe_int(away.get('score', 0)),
                    'date': event.get('date', '')[:10],
                    'status': status,
                    'gameId': event.get('id', ''),
                    'seriesSummary': series_summary,
                    'seriesCompleted': series_completed,
                    'homeSeriesWins': home_series_wins,
                    'awaySeriesWins': away_series_wins,
                })

            time.sleep(0.5)  # 避免请求过快
        except Exception as e:
            print(f"获取{chunk_start_str}-{chunk_end_str}数据失败: {e}", file=sys.stderr)

        current = chunk_end

    print(f"获取到 {len(all_games)} 场季后赛比赛", file=sys.stderr)
    return all_games


def build_bracket(series_data, games_data):
    """根据NBA API对阵结构和ESPN比分构建对阵图"""
    # 按球队组合分组比赛，并提取系列赛信息
    games_by_teams = {}
    series_info_by_teams = {}  # 存储系列赛的最新状态
    for game in games_data:
        teams = tuple(sorted([game['homeTeam'], game['awayTeam']]))
        if teams not in games_by_teams:
            games_by_teams[teams] = []
            series_info_by_teams[teams] = {
                'homeSeriesWins': 0,
                'awaySeriesWins': 0,
                'seriesCompleted': False,
                'seriesSummary': ''
            }
        games_by_teams[teams].append(game)

        # 更新系列赛信息（使用最新的数据）
        if game.get('homeSeriesWins', 0) > 0 or game.get('awaySeriesWins', 0) > 0:
            series_info_by_teams[teams]['homeSeriesWins'] = game['homeSeriesWins']
            series_info_by_teams[teams]['awaySeriesWins'] = game['awaySeriesWins']
        if game.get('seriesCompleted'):
            series_info_by_teams[teams]['seriesCompleted'] = True
        if game.get('seriesSummary'):
            series_info_by_teams[teams]['seriesSummary'] = game['seriesSummary']

    # 东部球队ID列表
    eastern_ids = {1610612738, 1610612752, 1610612749, 1610612739, 1610612754, 1610612755,
                   1610612748, 1610612753, 1610612741, 1610612751, 1610612737, 1610612761,
                   1610612765, 1610612766, 1610612764}

    # 按 seriesId 构建系列赛
    series_map = {}
    for s in series_data:
        sid = s['seriesId']
        if sid in series_map:
            continue

        conference = 'East' if s['homeTeamId'] in eastern_ids else 'West'
        # 总决赛
        if s['round'] == 4:
            conference = 'Finals'

        series_map[sid] = {
            'seriesId': sid,
            'team1Name': s['homeTeam'],
            'team2Name': s['awayTeam'],
            'team1Id': s['homeTeamId'],
            'team2Id': s['awayTeamId'],
            'round': s['round'],
            'conference': conference,
            'team1Wins': 0,
            'team2Wins': 0,
            'status': 'SCHEDULED',
            'games': [],
        }

        # 查找对应的比赛
        matchup = series_map[sid]
        teams_tuple = tuple(sorted([s['homeTeam'], s['awayTeam']]))

        # 优先使用ESPN API的系列赛数据（更准确）
        if teams_tuple in series_info_by_teams:
            info = series_info_by_teams[teams_tuple]
            # 确定哪个是team1（主场球队）
            if s['homeTeam'] == teams_tuple[0]:
                matchup['team1Wins'] = info['homeSeriesWins']
                matchup['team2Wins'] = info['awaySeriesWins']
            else:
                matchup['team1Wins'] = info['awaySeriesWins']
                matchup['team2Wins'] = info['homeSeriesWins']

            if info['seriesCompleted']:
                matchup['status'] = 'COMPLETED'
                matchup['winner'] = matchup['team1Name'] if matchup['team1Wins'] > matchup['team2Wins'] else matchup['team2Name']
            elif matchup['team1Wins'] >= 4:
                matchup['status'] = 'COMPLETED'
                matchup['winner'] = matchup['team1Name']
            elif matchup['team2Wins'] >= 4:
                matchup['status'] = 'COMPLETED'
                matchup['winner'] = matchup['team2Name']
            elif info['homeSeriesWins'] > 0 or info['awaySeriesWins'] > 0:
                matchup['status'] = 'IN_PROGRESS'

        # 添加比赛记录
        if teams_tuple in games_by_teams:
            matchup['games'] = games_by_teams[teams_tuple]

            # 如果没有系列赛数据，从比赛结果计算
            if matchup['team1Wins'] == 0 and matchup['team2Wins'] == 0:
                for game in games_by_teams[teams_tuple]:
                    if game['status'] == 'FINISHED':
                        if game['homeScore'] > game['awayScore']:
                            if game['homeTeam'] == matchup['team1Name']:
                                matchup['team1Wins'] += 1
                            else:
                                matchup['team2Wins'] += 1
                        else:
                            if game['awayTeam'] == matchup['team1Name']:
                                matchup['team1Wins'] += 1
                            else:
                                matchup['team2Wins'] += 1

                # 更新状态
                if matchup['team1Wins'] >= 4:
                    matchup['status'] = 'COMPLETED'
                    matchup['winner'] = matchup['team1Name']
                elif matchup['team2Wins'] >= 4:
                    matchup['status'] = 'COMPLETED'
                    matchup['winner'] = matchup['team2Name']
                elif matchup['games']:
                    matchup['status'] = 'IN_PROGRESS'

    matchups = []
    for sid, m in series_map.items():
        matchup = {
            'team1Name': m['team1Name'],
            'team2Name': m['team2Name'],
            'team1Wins': m['team1Wins'],
            'team2Wins': m['team2Wins'],
            'round': m['round'],
            'conference': m['conference'],
            'status': m['status'],
            'winner': m.get('winner', ''),
        }
        matchups.append(matchup)

    return matchups


def fetch_boxscore(game_id):
    """获取指定比赛的Box Score（使用V3 API）"""
    _init_team_map()
    print(f"正在获取比赛 {game_id} 的Box Score...", file=sys.stderr)

    try:
        box = BoxScoreTraditionalV3(game_id=game_id, timeout=30)
        data = box.get_dict()
        time.sleep(0.5)
    except Exception as e:
        print(f"获取Box Score失败: {e}", file=sys.stderr)
        # 返回空结构而不是None，避免JSON解析错误
        return {
            'gameId': game_id,
            'homeTeam': '',
            'awayTeam': '',
            'homePlayers': [],
            'awayPlayers': [],
            'quarterScores': []
        }

    player_stats = []
    team_stats = []
    unmapped_names = []  # 收集未映射的球员名

    # V3 格式：boxScoreTraditional.homeTeam / awayTeam
    bs = data.get('boxScoreTraditional', {})

    for side in ['homeTeam', 'awayTeam']:
        team_data = bs.get(side, {})
        team_id = _safe_int(team_data.get('teamId'))
        team_tricode = team_data.get('teamTricode', '')
        team_cn = TEAM_ID_TO_CN.get(team_id, team_tricode)
        is_home = side == 'homeTeam'

        # 球队统计
        team_stat = team_data.get('statistics', {})
        if team_stat:
            team_stats.append({
                'teamId': team_id,
                'teamName': team_cn,
                'points': _safe_int(team_stat.get('points')),
                'rebounds': _safe_int(team_stat.get('reboundsTotal')),
                'assists': _safe_int(team_stat.get('assists')),
            })

        # 球员统计
        players = team_data.get('players', [])
        for p in players:
            en_name = f"{p.get('firstName', '')} {p.get('familyName', '')}".strip()
            cn_name = PLAYER_CN_MAP.get(en_name, '')
            if not cn_name:
                cn_name = en_name
                if en_name:
                    unmapped_names.append(en_name)
            stat = p.get('statistics', {})
            player_stats.append({
                'playerId': _safe_int(p.get('personId')),
                'playerName': cn_name,
                'teamId': team_id,
                'teamName': team_cn,
                'isHome': is_home,
                'minutes': _format_minutes(stat.get('minutes', '')),
                'points': _safe_int(stat.get('points')),
                'rebounds': _safe_int(stat.get('reboundsTotal')),
                'assists': _safe_int(stat.get('assists')),
                'steals': _safe_int(stat.get('steals')),
                'blocks': _safe_int(stat.get('blocks')),
                'turnovers': _safe_int(stat.get('turnovers')),
                'fgMade': _safe_int(stat.get('fieldGoalsMade')),
                'fgAttempted': _safe_int(stat.get('fieldGoalsAttempted')),
                'fgPct': _safe_float(stat.get('fieldGoalsPercentage')),
                'threeMade': _safe_int(stat.get('threePointersMade')),
                'threeAttempted': _safe_int(stat.get('threePointersAttempted')),
                'threePct': _safe_float(stat.get('threePointersPercentage')),
                'ftMade': _safe_int(stat.get('freeThrowsMade')),
                'ftAttempted': _safe_int(stat.get('freeThrowsAttempted')),
                'ftPct': _safe_float(stat.get('freeThrowsPercentage')),
                'plusMinus': _safe_int(stat.get('plusMinusPoints')),
                'starter': str(stat.get('starter', p.get('starter', ''))) in ('1', 'True', 'true'),
            })

    # 批量翻译未映射的球员名
    if unmapped_names and HAS_TRANSLATOR:
        try:
            from translator import translate_batch
            translated = translate_batch(unmapped_names)
            name_map = dict(zip(unmapped_names, translated))
            for ps in player_stats:
                if ps['playerName'] in name_map:
                    ps['playerName'] = name_map[ps['playerName']]
            print(f"翻译了 {len(unmapped_names)} 个球员名", file=sys.stderr)
        except Exception as e:
            print(f"球员名翻译失败: {e}", file=sys.stderr)

    print(f"获取到 {len(player_stats)} 名球员的Box Score", file=sys.stderr)
    return {'players': player_stats, 'teams': team_stats}


def fetch_playbyplay(game_id, start_period=0, end_period=10):
    """获取指定比赛的Play-by-Play（使用V3 API）"""
    print(f"正在获取比赛 {game_id} 的Play-by-Play...", file=sys.stderr)

    try:
        pbp = PlayByPlayV3(game_id=game_id, start_period=start_period, end_period=end_period, timeout=30)
        data = pbp.get_dict()
        time.sleep(0.5)
    except Exception as e:
        print(f"获取Play-by-Play失败: {e}", file=sys.stderr)
        return []

    # V3格式: resultSets → rowSet + headers
    result_sets = data.get('resultSet', {})
    headers = result_sets.get('headers', [])
    rows = result_sets.get('rowSet', [])
    events = []

    for row in rows:
        d = dict(zip(headers, row))
        desc = d.get('homeDescription') or d.get('visitorDescription') or d.get('neutralDescription', '')
        if not desc:
            continue
        en_name = d.get('playerNameI', '') or d.get('personId', '')
        events.append({
            'period': _safe_int(d.get('period')),
            'gameClock': _format_minutes(d.get('gameClock', '')),
            'description': desc,
            'homeScore': _safe_int(d.get('scoreHome')),
            'awayScore': _safe_int(d.get('scoreAway')),
            'eventType': d.get('eventType', ''),
            'playerId': _safe_int(d.get('personId')),
            'playerName': str(d.get('playerName', '')),
        })

    print(f"获取到 {len(events)} 条Play-by-Play事件", file=sys.stderr)
    return events


def fetch_quarter_scores_from_scoreboard(game_id):
    """从ScoreboardV3获取逐节比分（更可靠的备选方案）"""
    print(f"正在从ScoreboardV3获取比赛 {game_id} 的逐节比分...", file=sys.stderr)

    try:
        # 从gameId提取日期 (格式: 00YYMMDDNNN → 20YY-MM-DD)
        season_year = int(game_id[1:3])
        full_year = 2000 + season_year
        # 用BoxScoreV3的gameId查找对应日期
        # 先尝试直接用ScoreboardV3获取最近日期的比赛
        from datetime import datetime, timedelta
        # 逐日搜索最近30天
        for days_ago in range(0, 30):
            search_date = (datetime.now() - timedelta(days=days_ago)).strftime('%Y-%m-%d')
            sb = ScoreboardV3(game_date=search_date, timeout=30)
            sb_data = sb.get_dict()
            games = sb_data.get('scoreboard', {}).get('games', [])
            for g in games:
                if g.get('gameId') == game_id:
                    home_periods = g.get('homeTeam', {}).get('periods', [])
                    away_periods = g.get('awayTeam', {}).get('periods', [])
                    if home_periods and away_periods:
                        result = []
                        for hp, ap in zip(home_periods, away_periods):
                            result.append({
                                'period': hp.get('period'),
                                'homeScore': hp.get('score'),
                                'awayScore': ap.get('score'),
                            })
                        print(f"获取到 {len(result)} 节比分", file=sys.stderr)
                        return result
            time.sleep(0.5)
    except Exception as e:
        print(f"从ScoreboardV3获取逐节比分失败: {e}", file=sys.stderr)

    return []


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
            # 解析 MATCHUP 字段（如 "LAL vs. BOS" 或 "LAL @ BOS"）
            matchup = d.get('MATCHUP', '')
            is_home = 'vs.' in matchup
            opponent_cn = translate_opponent(matchup, is_home)
            result.append({
                'gameId': d.get('Game_ID', ''),
                'matchDate': d.get('GAME_DATE', ''),
                'opponent': opponent_cn,
                'isHome': is_home,
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

    # 如果当前赛季没有数据，尝试获取上个赛季
    if not result and season == _get_current_season():
        prev_season = str(int(season.split('-')[0]) - 1) + '-' + str(int(season.split('-')[0]))[-2:]
        print(f"当前赛季无数据，尝试获取上个赛季 {prev_season}...", file=sys.stderr)
        return fetch_player_gamelog(player_id, prev_season)

    print(f"获取到 {len(result)} 场比赛日志", file=sys.stderr)
    return result


def translate_opponent(matchup, is_home):
    """翻译对阵信息，如 'LAL vs. BOS' → '凯尔特人'，'LAL @ BOS' → '凯尔特人'"""
    if not matchup:
        return matchup
    # 提取对手球队缩写
    if is_home:
        # "LAL vs. BOS" → 对手是 BOS
        parts = matchup.split(' vs. ')
    else:
        # "LAL @ BOS" → 对手是 BOS
        parts = matchup.split(' @ ')

    if len(parts) == 2:
        opponent_abbr = parts[1].strip()
        # 翻译对手球队缩写
        for abbr, cn in TEAM_ABBR_MAP.items():
            if abbr == opponent_abbr:
                return cn
        return opponent_abbr
    return matchup


def fetch_nba_news(limit=20):
    """从ESPN API获取NBA新闻，先用本地映射翻译，再用批量API翻译剩余英文"""
    print(f"正在从ESPN获取NBA新闻 (limit={limit})...", file=sys.stderr)

    url = f"https://site.api.espn.com/apis/site/v2/sports/basketball/nba/news?limit={limit}"

    try:
        req = urllib.request.Request(url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Accept': 'application/json',
        })

        no_proxy_opener = urllib.request.build_opener()
        response = no_proxy_opener.open(req, timeout=15)
        data = json.loads(response.read().decode('utf-8'))
    except Exception as e:
        print(f"获取ESPN新闻失败: {e}", file=sys.stderr)
        return []

    articles = data.get('articles', [])
    if not articles:
        return []

    # ====== 第一步：解析所有文章，提取信息，用本地映射翻译 ======
    parsed_articles = []
    headlines_to_translate = []
    descriptions_to_translate = []
    contents_to_translate = []
    translate_indices = []  # 需要API翻译的文章索引

    for idx, article in enumerate(articles):
        headline = article.get('headline', '')
        description = article.get('description', '')
        published = article.get('published', '')
        source_url = article.get('links', {}).get('web', {}).get('href', '')

        # 获取图片
        images = article.get('images', [])
        image_url = images[0].get('url', '') if images else ''

        # 尝试从URL中提取gameId
        nba_game_id = ''
        if 'gameId=' in source_url:
            import re
            game_match = re.search(r'gameId=(\d+)', source_url)
            if game_match:
                nba_game_id = game_match.group(1)
        elif '/events/' in source_url:
            import re
            event_match = re.search(r'/events/(\d+)', source_url)
            if event_match:
                nba_game_id = event_match.group(1)

        # 从标题和描述中提取球队名
        home_team = ''
        away_team = ''
        content = description or headline

        for en_name, cn_name in TEAM_NAME_MAP.items():
            if en_name.lower() in headline.lower() or en_name.lower() in description.lower():
                if not home_team:
                    home_team = cn_name
                elif not away_team and cn_name != home_team:
                    away_team = cn_name

        if not home_team:
            for short_name, cn_name in TEAM_SHORT_MAP.items():
                if short_name.lower() in headline.lower():
                    home_team = cn_name
                    break

        # 保存原始英文标题用于分类判断
        original_headline = headline

        # 确定新闻分类（基于原始英文标题）
        category = 'general'
        original_lower = original_headline.lower()
        if any(word in original_lower for word in ['trade', 'sign', 'deal', 'contract']):
            category = 'trade'
        elif any(word in original_lower for word in ['injury', 'hurt', 'out', 'doubtful']):
            category = 'injury'
        elif any(word in original_lower for word in ['draft', 'pick', 'rookie']):
            category = 'draft'
        elif any(word in original_lower for word in ['score', 'win', 'loss', 'beat', 'game', 'finals', 'recap']):
            # 只有当有实际比赛数据时才分类为game
            # 检查是否有nba_game_id或者标题中包含具体比分
            import re
            has_score = bool(re.search(r'\d+\s*[-:]\s*\d+', headline))
            if nba_game_id or has_score:
                category = 'game'
            else:
                category = 'general'

        parsed = {
            'headline': headline,
            'headlineEn': headline,  # 保存英文原版
            'description': description,
            'content': content,
            'contentEn': content,  # 保存英文原版
            'imageUrl': image_url,
            'sourceUrl': source_url,
            'published': published,
            'category': category,
            'homeTeam': home_team,
            'awayTeam': away_team,
            'nbaGameId': nba_game_id,
        }
        parsed_articles.append(parsed)

        # 收集需要API翻译的字段（新闻内容始终发送API翻译，确保完整翻译）
        if HAS_TRANSLATOR:
            translate_indices.append(idx)
            headlines_to_translate.append(headline)
            contents_to_translate.append(content)
            descriptions_to_translate.append(description)

    # ====== 第二步：批量API翻译 ======
    if HAS_TRANSLATOR and translate_indices:
        print(f"需要API翻译的文章: {len(translate_indices)} 条", file=sys.stderr)

        try:
            from translator import translate_batch

            # 批量翻译标题
            if headlines_to_translate:
                translated_headlines = translate_batch(headlines_to_translate)
                for i, idx in enumerate(translate_indices):
                    parsed_articles[idx]['headline'] = translated_headlines[i]

            # 批量翻译内容
            if contents_to_translate:
                translated_contents = translate_batch(contents_to_translate)
                for i, idx in enumerate(translate_indices):
                    parsed_articles[idx]['content'] = translated_contents[i]

            # 批量翻译描述
            if descriptions_to_translate:
                # 过滤空描述
                valid_descs = [(i, d) for i, d in enumerate(descriptions_to_translate) if d and d.strip()]
                if valid_descs:
                    desc_indices = [vd[0] for vd in valid_descs]
                    desc_texts = [vd[1] for vd in valid_descs]
                    translated_descs = translate_batch(desc_texts)
                    for i, desc_idx in enumerate(desc_indices):
                        orig_idx = translate_indices[desc_idx]
                        parsed_articles[orig_idx]['description'] = translated_descs[i]

            print(f"批量翻译完成", file=sys.stderr)

        except Exception as e:
            print(f"批量翻译失败，回退到逐条翻译: {e}", file=sys.stderr)
            # 回退到逐条翻译
            for idx in translate_indices:
                parsed = parsed_articles[idx]
                if needs_translation(parsed['headline']):
                    parsed['headline'] = translate_text_safe(parsed['headline'])
                if needs_translation(parsed['content']):
                    parsed['content'] = translate_text_safe(parsed['content'])
                if parsed['description'] and needs_translation(parsed['description']):
                    parsed['description'] = translate_text_safe(parsed['description'])

    print(f"获取到 {len(parsed_articles)} 条新闻", file=sys.stderr)
    return parsed_articles


def fetch_draft_history(year=None):
    """从nba_api获取选秀历史数据（最近5年）"""
    if not HAS_DRAFT_API:
        print("DraftHistory API不可用，请安装nba_api", file=sys.stderr)
        return []

    # 如果指定了年份，只获取该年份；否则获取最近5年
    if year is not None:
        years_to_fetch = [year]
    else:
        current_year = datetime.now().year
        years_to_fetch = [current_year - i for i in range(5)]  # 最近5年

    all_result = []
    for yr in years_to_fetch:
        print(f"正在获取{yr}年选秀历史数据...", file=sys.stderr)

        try:
            # 使用正确的参数名 season_year_nullable
            draft = DraftHistory(season_year_nullable=yr, timeout=60)
            data = draft.get_dict()
            time.sleep(1.5)  # 避免请求过快
        except Exception as e:
            print(f"获取{yr}年选秀历史失败: {e}", file=sys.stderr)
            continue

        # 解析数据
        for rs in data.get('resultSets', []):
            headers = rs.get('headers', [])
            for row in rs.get('rowSet', []):
                d = dict(zip(headers, row))

                player_name = d.get('PLAYER_NAME', '')
                team_abbr = d.get('TEAM_ABBREVIATION', '')
                team_name = d.get('TEAM_NAME', '')

                # 翻译球员名
                cn_player = PLAYER_CN_MAP.get(player_name, player_name)
                # 翻译球队名
                cn_team = TEAM_ABBR_MAP.get(team_abbr, TEAM_NAME_MAP.get(
                    f"{d.get('TEAM_CITY', '')} {team_name}".strip(), team_name))

                round_num = _safe_int(d.get('ROUND_NUMBER'))
                round_pick = _safe_int(d.get('ROUND_PICK'))
                overall_pick = _safe_int(d.get('OVERALL_PICK'))

                # 确定轮次（Round 3+ 通常算作第二轮）
                if round_num > 2:
                    effective_round = 2
                else:
                    effective_round = round_num if round_num > 0 else 1

                all_result.append({
                    'year': _safe_int(d.get('SEASON', yr)),
                    'round': effective_round,
                    'pickNumber': overall_pick if overall_pick > 0 else round_pick,
                    'teamName': cn_team,
                    'teamNameEn': f"{d.get('TEAM_CITY', '')} {team_name}".strip(),
                    'playerName': cn_player,
                    'playerNameEn': player_name,
                    'playerPosition': '',
                    'fromTeamName': '',
                    'notes': f"选秀顺位第{overall_pick}位",
                })

    print(f"共获取到 {len(all_result)} 条选秀记录", file=sys.stderr)
    return all_result


def fetch_transactions():
    """从ESPN获取NBA交易/球员变动数据（支持分页）"""
    import re
    print("正在从ESPN获取NBA交易数据...", file=sys.stderr)

    all_transactions = []
    page = 1
    max_pages = 5  # 最多获取5页（125条记录）

    while page <= max_pages:
        url = f"https://site.api.espn.com/apis/site/v2/sports/basketball/nba/transactions?page={page}"

        try:
            req = urllib.request.Request(url, headers={
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                'Accept': 'application/json',
            })

            no_proxy_opener = urllib.request.build_opener()
            response = no_proxy_opener.open(req, timeout=15)
            data = json.loads(response.read().decode('utf-8'))
        except Exception as e:
            print(f"获取ESPN交易数据失败 (page {page}): {e}", file=sys.stderr)
            break

        transactions = data.get('transactions', [])
        if not transactions:
            break

        all_transactions.extend(transactions)

        # 检查是否有更多页
        page_count = data.get('pageCount', 1)
        if page >= page_count:
            break
        page += 1
        time.sleep(0.5)  # 避免请求过快

    if not all_transactions:
        print("未获取到交易数据", file=sys.stderr)
        return []

    print(f"获取到 {len(all_transactions)} 条交易原始记录", file=sys.stderr)

    result = []
    for txn in all_transactions:
        date_str = txn.get('date', '')
        description = txn.get('description', '')
        team_info = txn.get('team', {})

        # 解析日期
        trade_date = ''
        if date_str:
            try:
                trade_date = date_str.replace('Z', '').split('T')[0]
            except Exception:
                trade_date = date_str

        # 获取发起交易的球队
        team_name = team_info.get('displayName', '')
        cn_team1 = TEAM_NAME_MAP.get(team_name, team_name) if team_name else ''

        # 确定交易类型
        desc_lower = description.lower()
        trade_type = 'TRADE'
        if 'waive' in desc_lower or 'release' in desc_lower:
            trade_type = 'WAIVE'
        elif 'sign' in desc_lower:
            trade_type = 'SIGN'
        elif 'recall' in desc_lower:
            trade_type = 'RECALL'
        elif 'hire' in desc_lower or 'appoint' in desc_lower:
            trade_type = 'HIRE'

        # 只保留真正的交易（球队间的球员交换），跳过签约、裁员、任命等
        if trade_type != 'TRADE':
            continue

        # 从description中提取涉及的球队和球员
        cn_teams = []
        players_involved = []

        # 提取所有提到的球队
        for en_name, cn_name in TEAM_NAME_MAP.items():
            if en_name in description:
                if cn_name not in cn_teams:
                    cn_teams.append(cn_name)

        # 如果只找到一个球队，添加发起球队
        if cn_team1 and cn_team1 not in cn_teams:
            cn_teams.insert(0, cn_team1)

        # 确保至少有一个球队
        if not cn_teams:
            cn_teams.append(cn_team1 if cn_team1 else '其他球队')

        # 提取球员名字（支持两部分、三部分名字，带撇号和连字符）
        # 提取球员名字
        player_patterns = [
            r'(?:Acquired|Signed|Waived|Claimed|Traded)\s+(?:G|F|C|PG|SG|SF|PF)\s+([A-Za-z\'\-]+(?:\s+[A-Za-z\'\-]+)+)',
            r'(?:Acquired|Signed|Waived|Claimed|Traded)\s+([A-Za-z\'\-]+(?:\s+[A-Za-z\'\-]+)+)',
            r'([A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)\s+(?:from|to|with)',
        ]
        for pattern in player_patterns:
            matches = re.findall(pattern, description)
            for match in matches:
                # 排除常见的非球员名词汇
                if match.lower() in ('the', 'a', 'an', 'for', 'from', 'and', 'with', 'to', 'in', 'of', 'exchange', 'consideration'):
                    continue
                # 使用翻译函数（先查映射，再调用MIMO API）
                cn_player, _ = translate_player_name(match)
                if cn_player not in players_involved:
                    players_involved.append(cn_player)

        # 提取选秀权信息
        draft_picks = []
        # 匹配选秀权模式：如 "2026 second-round pick", "future first-round pick"
        pick_patterns = [
            (r'(\d{4})\s+(first|second|third|1st|2nd|3rd)\s*-\s*round\s+picks?', lambda m: f'{m.group(1)}年{"第一轮" if "first" in m.group(2).lower() or "1st" in m.group(2).lower() else "第二轮" if "second" in m.group(2).lower() or "2nd" in m.group(2).lower() else "第三轮"}选秀权'),
            (r'(first|second|third|1st|2nd|3rd)\s*-\s*round\s+picks?', lambda m: f'{"第一轮" if "first" in m.group(1).lower() or "1st" in m.group(1).lower() else "第二轮" if "second" in m.group(1).lower() or "2nd" in m.group(1).lower() else "第三轮"}选秀权'),
            (r'future\s+(first|second|third|1st|2nd|3rd)\s*-\s*round\s+picks?', lambda m: f'未来{"第一轮" if "first" in m.group(1).lower() or "1st" in m.group(1).lower() else "第二轮" if "second" in m.group(1).lower() or "2nd" in m.group(1).lower() else "第三轮"}选秀权'),
            (r'(\d{4})\s+(first|second|third)\s+round\s+picks?', lambda m: f'{m.group(1)}年{"第一轮" if "first" in m.group(2).lower() else "第二轮" if "second" in m.group(2).lower() else "第三轮"}选秀权'),
        ]
        for pattern, formatter in pick_patterns:
            matches = re.finditer(pattern, description, re.IGNORECASE)
            for match in matches:
                pick_str = formatter(match)
                if pick_str and pick_str not in draft_picks:
                    draft_picks.append(pick_str)

        # 使用本地映射翻译描述（确保快速响应）
        cn_description = description
        # 先翻译球队名
        for en_name, cn_name in TEAM_NAME_MAP.items():
            cn_description = cn_description.replace(en_name, cn_name)
        # 合并静态映射和动态映射
        all_player_map = {**PLAYER_CN_MAP, **_dynamic_cn_map}
        # 再翻译球员名（按名字长度降序排序，避免短名字误匹配长名字）
        sorted_players = sorted(all_player_map.items(), key=lambda x: len(x[0]), reverse=True)
        for en_name, cn_name in sorted_players:
            cn_description = cn_description.replace(en_name, cn_name)

        # 格式化交易信息
        players_str = '、'.join(players_involved) if players_involved else ''
        draft_picks_str = '、'.join(draft_picks) if draft_picks else ''

        # 交易：显示所有涉及的球队（可能多于2支）
        teams_display = cn_teams if cn_teams else ['未知球队']
        if len(teams_display) == 1:
            details = f"{teams_display[0]}交易" + (f"，涉及{players_str}" if players_str else '')
        elif len(teams_display) == 2:
            details = f"{teams_display[0]}与{teams_display[1]}交易" + (f"，涉及{players_str}" if players_str else '')
        else:
            # 多方交易
            teams_part = '、'.join(teams_display)
            details = f"多方交易（{teams_part}）" + (f"，涉及{players_str}" if players_str else '')

        # 如果MIMO翻译更详细，使用MIMO翻译
        if cn_description != description and len(cn_description) > len(details):
            details = cn_description

        result.append({
            'date': trade_date,
            'teams': teams_display,
            'playersInvolved': players_str,
            'draftPicksInvolved': draft_picks_str,
            'details': details,
            'type': trade_type,
        })

    print(f"解析到 {len(result)} 条交易记录", file=sys.stderr)
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

    if action in ('all', 'trades'):
        result['trades'] = fetch_transactions()

    if action in ('all', 'draft'):
        result['draftPicks'] = fetch_draft_history()

    if action in ('all', 'playoff'):
        season = sys.argv[2] if len(sys.argv) > 2 else None
        result['playoff'] = fetch_playoff_bracket(season)

    if action == 'today':
        result['todayGames'] = fetch_today_games()

    if action == 'news':
        limit = int(sys.argv[2]) if len(sys.argv) > 2 else 20
        result['news'] = fetch_nba_news(limit)

    if action == 'boxscore' and len(sys.argv) > 2:
        result['boxscore'] = fetch_boxscore(sys.argv[2])

    if action == 'playbyplay' and len(sys.argv) > 2:
        result['playByPlay'] = fetch_playbyplay(sys.argv[2])

    if action == 'quarters' and len(sys.argv) > 2:
        result['quarterScores'] = fetch_quarter_scores_from_scoreboard(sys.argv[2])

    if action == 'player_career' and len(sys.argv) > 2:
        result['career'] = fetch_player_career(int(sys.argv[2]))

    if action == 'player_gamelog' and len(sys.argv) > 2:
        season = sys.argv[3] if len(sys.argv) > 3 else None
        result['gameLog'] = fetch_player_gamelog(int(sys.argv[2]), season)

    if action == 'draft' and len(sys.argv) > 2:
        year = int(sys.argv[2])
        result['draftPicks'] = fetch_draft_history(year)

    if action == 'historical_players' and len(sys.argv) > 2:
        season = sys.argv[2]
        result['players'] = fetch_historical_players(season)

    if action == 'historical_teams' and len(sys.argv) > 2:
        season = sys.argv[2]
        result['teams'] = fetch_historical_teams(season)

    if action == 'games_by_date' and len(sys.argv) > 2:
        date_str = sys.argv[2]
        result['games'] = fetch_games_by_date(date_str)

    if action == 'shot_chart' and len(sys.argv) > 2:
        player_id = int(sys.argv[2])
        season = sys.argv[3] if len(sys.argv) > 3 else _get_current_season()
        result['shots'] = fetch_shot_chart(player_id, season)

    # 输出JSON到文件（支持自定义输出文件名，避免竞争条件）
    output_filename = os.environ.get('OUTPUT_FILE', 'output.json')
    output_file = os.path.join(os.path.dirname(__file__), output_filename)
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    # 输出到stdout（使用utf-8编码避免Windows GBK错误）
    try:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except UnicodeEncodeError:
        # Windows GBK编码兼容：使用ensure_ascii=True
        print(json.dumps(result, ensure_ascii=True, indent=2))


def fetch_historical_players(season):
    """获取指定赛季的球员常规赛数据"""
    _init_team_map()
    print(f"正在获取 {season} 赛季球员常规赛数据...", file=sys.stderr)

    try:
        from nba_api.stats.endpoints import LeagueDashPlayerStats

        stats = LeagueDashPlayerStats(
            season=season,
            per_mode_detailed='PerGame',
            measure_type_detailed_defense='Base',
            season_type_all_star='Regular Season',
            timeout=90
        )
        data = stats.get_dict()
        time.sleep(1.0)

        result = []
        for row in data['resultSets'][0]['rowSet']:
            headers = data['resultSets'][0]['headers']
            d = dict(zip(headers, row))

            player_id = d.get('PLAYER_ID', 0)
            player_name = d.get('PLAYER_NAME', '')

            # 翻译球员名
            cn_name = PLAYER_CN_MAP.get(player_name, player_name)

            # 获取球队名
            team_id = d.get('TEAM_ID', 0)
            team_name = TEAM_ID_TO_CN.get(team_id, d.get('TEAM_ABBREVIATION', ''))

            result.append({
                'nbaPlayerId': player_id,
                'name': cn_name,
                'nameEn': player_name,
                'teamName': team_name,
                'teamNameEn': d.get('TEAM_ABBREVIATION', ''),
                'position': POSITION_MAP.get(d.get('POSITION', ''), ''),
                'jersey': str(PLAYER_JERSEY_MAP.get(player_name, '')),
                'gp': d.get('GP', 0),
                'gs': d.get('GS', 0),
                'mpg': round(d.get('MIN', 0), 1),
                'ppg': round(d.get('PTS', 0), 1),
                'rpg': round(d.get('REB', 0), 1),
                'apg': round(d.get('AST', 0), 1),
                'spg': round(d.get('STL', 0), 1),
                'bpg': round(d.get('BLK', 0), 1),
                'fgPct': round(d.get('FG_PCT', 0), 3),
                'threePct': round(d.get('FG3_PCT', 0), 3),
                'ftPct': round(d.get('FT_PCT', 0), 3),
                'topg': round(d.get('TOV', 0), 1),
            })

        print(f"获取到 {len(result)} 名球员的 {season} 赛季常规赛数据", file=sys.stderr)
        return result

    except Exception as e:
        print(f"获取 {season} 赛季球员数据失败: {e}", file=sys.stderr)
        return []


def fetch_historical_teams(season):
    """获取指定赛季的球队常规赛数据"""
    _init_team_map()
    print(f"正在获取 {season} 赛季球队常规赛数据...", file=sys.stderr)

    try:
        from nba_api.stats.endpoints import LeagueStandingsV3

        standings = LeagueStandingsV3(
            season=season,
            season_type='Regular Season',
            timeout=90
        )
        data = standings.get_dict()
        time.sleep(1.0)

        result = []
        for team in data['resultSets'][0]['rowSet']:
            headers = data['resultSets'][0]['headers']
            d = dict(zip(headers, team))

            team_id = d.get('TeamID', 0)
            team_name = TEAM_ID_TO_CN.get(team_id, d.get('TeamCity', ''))

            result.append({
                'teamId': team_id,
                'teamName': team_name,
                'teamNameEn': d.get('TeamCity', '') + ' ' + d.get('TeamName', ''),
                'conference': d.get('Conference', ''),
                'division': d.get('Division', ''),
                'wins': d.get('WINS', 0),
                'losses': d.get('LOSSES', 0),
                'winPct': round(d.get('WinPCT', 0), 3),
                'conferenceRank': d.get('ConferenceRank', 0),
                'divisionRank': d.get('DivisionRank', 0),
                'leagueRank': d.get('LeagueRank', 0),
                'ppg': round(d.get('PTS', 0), 1),
                'oppg': round(d.get('OPP_PTS', 0), 1),
                'rpg': round(d.get('REB', 0), 1),
                'apg': round(d.get('AST', 0), 1),
            })

        print(f"获取到 {len(result)} 支球队的 {season} 赛季常规赛数据", file=sys.stderr)
        return result

    except Exception as e:
        print(f"获取 {season} 赛季球队数据失败: {e}", file=sys.stderr)
        return []


def fetch_shot_chart(player_id, season=None):
    """获取球员投篮数据"""
    _init_team_map()
    if season is None:
        season = _get_current_season()

    print(f"正在获取球员 {player_id} 的投篮数据 (赛季: {season})...", file=sys.stderr)

    try:
        from nba_api.stats.endpoints import ShotChartDetail

        shots = ShotChartDetail(
            player_id=player_id,
            team_id=0,
            season_nullable=season,
            context_measure_simple='FGA',
            timeout=60
        )
        data = shots.get_dict()
        time.sleep(1.0)

        result = []
        for row in data.get('resultSets', []):
            if row.get('name') == 'Shot_Chart_Detail':
                headers = row.get('headers', [])
                for shot_row in row.get('rowSet', []):
                    d = dict(zip(headers, shot_row))

                    # 转换坐标（NBA API的坐标系统）
                    loc_x = d.get('LOC_X', 0)
                    loc_y = d.get('LOC_Y', 0)

                    # 转换为SVG坐标系 (x: 0-50, y: 0-47)
                    # NBA API: x = -250 to 250, y = -50 to 470
                    svg_x = (loc_x + 250) / 10  # 转换为 0-50
                    svg_y = (loc_y + 50) / 11    # 转换为 0-47

                    # 判断区域
                    zone = determine_zone(svg_x, svg_y)

                    # 判断命中
                    made = d.get('SHOT_MADE_FLAG', 0) == 1

                    result.append({
                        'x': round(svg_x, 2),
                        'y': round(svg_y, 2),
                        'made': made,
                        'zone': zone,
                        'shotType': d.get('SHOT_TYPE', ''),
                        'gameId': d.get('GAME_ID', ''),
                        'gameDate': d.get('GAME_DATE', ''),
                        'period': d.get('PERIOD', 0),
                    })

        print(f"获取到 {len(result)} 次投篮记录", file=sys.stderr)
        return result

    except Exception as e:
        print(f"获取球员 {player_id} 投篮数据失败: {e}", file=sys.stderr)
        return []


def determine_zone(x, y):
    """根据坐标判断投篮区域"""
    cx = 25  # 中心x
    basket_y = 5.25  # 篮筐y

    # 计算到篮筐的距离
    dist = ((x - cx) ** 2 + (y - basket_y) ** 2) ** 0.5

    # 篮下区域 (距离篮筐很近)
    if dist < 4:
        return 'restricted'

    # 油漆区
    if 17 <= x <= 33 and y <= 19:
        return 'paint'

    # 底角三分
    if (x < 5 or x > 45) and y < 14:
        return 'corner_three'

    # 三分线外
    if dist > 22:
        return 'three_point'

    # 中距离
    return 'mid_range'


def fetch_games_by_date(date_str):
    """获取指定日期的比赛数据"""
    _init_team_map()
    print(f"正在获取 {date_str} 的比赛数据...", file=sys.stderr)

    try:
        from datetime import datetime
        from nba_api.stats.endpoints import ScoreboardV3

        # 解析日期
        date = datetime.strptime(date_str, '%Y%m%d')

        # 使用ScoreboardV3获取指定日期的比赛
        scoreboard = ScoreboardV3(game_date=date.strftime('%Y-%m-%d'), timeout=30)
        data = scoreboard.get_dict()
        time.sleep(0.5)

        result = []
        for game in data.get('scoreboard', {}).get('games', []):
            home = game.get('homeTeam', {})
            away = game.get('awayTeam', {})

            home_id = _safe_int(home.get('teamId'))
            away_id = _safe_int(away.get('teamId'))
            home_score = _safe_int(home.get('score'))
            away_score = _safe_int(away.get('score'))

            home_cn = TEAM_ID_TO_CN.get(home_id, home.get('teamTricode', ''))
            away_cn = TEAM_ID_TO_CN.get(away_id, away.get('teamTricode', ''))

            game_status = game.get('gameStatus', 0)
            status = 'FINISHED' if game_status == 3 else 'LIVE' if game_status == 2 else 'SCHEDULED'

            result.append({
                'gameId': game.get('gameId', ''),
                'homeTeam': home_cn,
                'awayTeam': away_cn,
                'homeScore': home_score,
                'awayScore': away_score,
                'status': status,
                'date': date.strftime('%Y-%m-%d'),
            })

        print(f"获取到 {len(result)} 场比赛", file=sys.stderr)
        return result

    except Exception as e:
        print(f"获取 {date_str} 比赛数据失败: {e}", file=sys.stderr)
        return []


if __name__ == '__main__':
    main()
