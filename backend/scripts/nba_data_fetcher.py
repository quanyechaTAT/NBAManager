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

# 导入翻译模块
try:
    from translator import translate_text, translate_news_article
    HAS_TRANSLATOR = True
except ImportError:
    HAS_TRANSLATOR = False
    print("警告: translator模块未找到，将使用本地映射翻译", file=sys.stderr)

# 代理配置
PROXY_HOST = os.environ.get('NBA_PROXY_HOST', '127.0.0.1')
PROXY_PORT = os.environ.get('NBA_PROXY_PORT', '7890')

# 设置全局代理（仅用于NBA API）
proxy_handler = urllib.request.ProxyHandler({
    'https': f'http://{PROXY_HOST}:{PROXY_PORT}',
    'http': f'http://{PROXY_HOST}:{PROXY_PORT}',
})
proxy_opener = urllib.request.build_opener(proxy_handler)
urllib.request.install_opener(proxy_opener)

try:
    from nba_api.stats.endpoints import (
        LeagueStandingsV3,
        LeagueDashPlayerStats,
        ScoreboardV2,
        ScoreboardV3,
        BoxScoreTraditionalV3,
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
    'Andrew Wiggins': 22, 'Anthony Davis': 3, 'Russell Westbrook': 4,
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
    """检查文本是否需要翻译（是否含有大量英文）"""
    import string
    if not text:
        return False
    alpha_chars = [c for c in text if c in string.ascii_letters]
    return len(alpha_chars) > len(text) * 0.3


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
        per = _safe_float(adv.get('E_OFF_RATING'), 110.0)
        ts_pct = _safe_float(adv.get('TS_PCT'), fg_pct)
        usg_pct = _safe_float(adv.get('USG_PCT')) * 100
        if usg_pct < 1.0:
            usg_pct = 20.0

        # 球员名翻译
        en_name = data.get('PLAYER_NAME', '')
        cn_name = PLAYER_CN_MAP.get(en_name, en_name)

        result.append({
            'nbaPlayerId': player_id,
            'name': cn_name,
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
            'jersey': PLAYER_JERSEY_MAP.get(en_name, 0),
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
        return None

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
                'minutes': stat.get('minutes', ''),
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
                'starter': p.get('starter', '') == '1',
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

        # 本地映射翻译
        headline = translate_with_mapping(headline)
        content = translate_with_mapping(content)
        if description:
            description = translate_with_mapping(description)

        # 确定新闻分类
        category = 'general'
        headline_lower = headline.lower()
        if any(word in headline_lower for word in ['trade', 'sign', 'deal', 'contract', '交易', '签约']):
            category = 'trade'
        elif any(word in headline_lower for word in ['injury', 'hurt', 'out', 'doubtful', '伤病', '受伤']):
            category = 'injury'
        elif any(word in headline_lower for word in ['score', 'win', 'loss', 'beat', 'game', 'finals', 'recap', '得分', '胜利', '决赛']):
            category = 'game'
        elif any(word in headline_lower for word in ['draft', 'pick', 'rookie', '选秀', '新秀']):
            category = 'draft'

        parsed = {
            'headline': headline,
            'description': description,
            'content': content,
            'imageUrl': image_url,
            'sourceUrl': source_url,
            'published': published,
            'category': category,
            'homeTeam': home_team,
            'awayTeam': away_team,
            'nbaGameId': nba_game_id,
        }
        parsed_articles.append(parsed)

        # 收集需要API翻译的字段
        if HAS_TRANSLATOR:
            needs_api = False
            if needs_translation(headline):
                needs_api = True
            if needs_translation(content):
                needs_api = True
            if description and needs_translation(description):
                needs_api = True

            if needs_api:
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

    if action == 'news':
        limit = int(sys.argv[2]) if len(sys.argv) > 2 else 20
        result['news'] = fetch_nba_news(limit)

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
