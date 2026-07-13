from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
import re


class QueryType(str, Enum):
    GENERAL = "general"
    TEAM = "team"
    PLAYER = "player"
    MATCH = "match"
    NEWS = "news"
    RANKING = "ranking"


TEAM_ALIASES = {
    "凯尔特人": "凯尔特人",
    "绿军": "凯尔特人",
    "篮网": "篮网",
    "尼克斯": "尼克斯",
    "76人": "76人",
    "猛龙": "猛龙",
    "公牛": "公牛",
    "骑士": "骑士",
    "活塞": "活塞",
    "步行者": "步行者",
    "雄鹿": "雄鹿",
    "老鹰": "老鹰",
    "黄蜂": "黄蜂",
    "热火": "热火",
    "魔术": "魔术",
    "奇才": "奇才",
    "掘金": "掘金",
    "森林狼": "森林狼",
    "雷霆": "雷霆",
    "开拓者": "开拓者",
    "爵士": "爵士",
    "勇士": "勇士",
    "快船": "快船",
    "湖人": "湖人",
    "太阳": "太阳",
    "国王": "国王",
    "独行侠": "独行侠",
    "灰熊": "灰熊",
    "鹈鹕": "鹈鹕",
    "火箭": "火箭",
    "马刺": "马刺",
}

PLAYER_ALIASES = {
    "詹姆斯": "勒布朗-詹姆斯",
    "勒布朗": "勒布朗-詹姆斯",
    "杜兰特": "凯文-杜兰特",
    "库里": "斯蒂芬-库里",
    "东契奇": "卢卡-东契奇",
    "字母哥": "扬尼斯-阿德托昆博",
    "约基奇": "尼古拉-约基奇",
    "恩比德": "乔尔-恩比德",
    "塔图姆": "杰森-塔图姆",
    "布克": "德文-布克",
    "文班亚马": "维克托-文班亚马",
    # 历史/退役球员（不在当前数据库中）
    "迈克尔·乔丹": "__OUT_OF_SCOPE__",
    "乔丹": "__OUT_OF_SCOPE__",
    "科比": "__OUT_OF_SCOPE__",
    "科比·布莱恩特": "__OUT_OF_SCOPE__",
    "奥尼尔": "__OUT_OF_SCOPE__",
    "邓肯": "__OUT_OF_SCOPE__",
    "加内特": "__OUT_OF_SCOPE__",
    "诺维茨基": "__OUT_OF_SCOPE__",
    "韦德": "__OUT_OF_SCOPE__",
    "艾弗森": "__OUT_OF_SCOPE__",
    "麦迪": "__OUT_OF_SCOPE__",
    "卡特": "__OUT_OF_SCOPE__",
    "姚明": "__OUT_OF_SCOPE__",
}

TEAM_KEYWORDS = re.compile(r"战绩|排名|胜率|胜负|球队|东部|西部")
PLAYER_KEYWORDS = re.compile(r"场均|球员|数据|表现")
MATCH_KEYWORDS = re.compile(r"比赛|比分|赛程|总决赛|季后赛|对阵|主场|客场")
NEWS_KEYWORDS = re.compile(r"新闻|资讯|交易|伤病|报道")
RANKING_KEYWORDS = re.compile(r"最高|排行|排名|前十|top|Top|TOP|第一|最多|得分王|篮板王|助攻王|抢断王|盖帽王|效率王")


@dataclass(frozen=True)
class QueryClassification:
    query_type: QueryType
    teams: list[str] = field(default_factory=list)
    players: list[str] = field(default_factory=list)
    needs_structured_data: bool = False
    needs_web_search: bool = False


class QueryClassifier:
    def classify(self, query: str) -> QueryClassification:
        teams = self._find_teams(query)
        players = self._find_players(query)
        # 过滤掉库外球员
        out_of_scope_players = [p for p in players if p == "__OUT_OF_SCOPE__"]
        valid_players = [p for p in players if p != "__OUT_OF_SCOPE__"]

        if RANKING_KEYWORDS.search(query):
            query_type = QueryType.RANKING
        elif TEAM_KEYWORDS.search(query) and teams:
            query_type = QueryType.TEAM
        elif PLAYER_KEYWORDS.search(query) and valid_players:
            query_type = QueryType.PLAYER
        elif MATCH_KEYWORDS.search(query):
            query_type = QueryType.MATCH
        elif NEWS_KEYWORDS.search(query):
            query_type = QueryType.NEWS
        else:
            query_type = QueryType.GENERAL

        needs_structured = query_type in {
            QueryType.TEAM,
            QueryType.PLAYER,
            QueryType.MATCH,
            QueryType.RANKING,
        }
        # needs_web_search 独立于 query_type：任何包含实时/最新关键词的查询都应触发
        needs_web_search = self._needs_web_search(query)
        return QueryClassification(query_type, teams, valid_players, needs_structured, needs_web_search)

    def _find_teams(self, query: str) -> list[str]:
        found = []
        for alias, team in TEAM_ALIASES.items():
            if alias in query and team not in found:
                found.append(team)
        return found

    def _find_players(self, query: str) -> list[str]:
        found = []
        for alias, player in PLAYER_ALIASES.items():
            if alias in query and player not in found:
                found.append(player)
        return found

    def _needs_web_search(self, query: str) -> bool:
        # 明确要求联网
        if re.search(r"联网|搜索一下|查一下|网上|帮我查|搜一下", query):
            return True
        # 时效性关键词
        return bool(
            re.search(
                r"最新|今天|今日|昨天|明天|实时|目前|现在|刚刚|近期|最近|"
                r"新闻|资讯|交易|伤病|复出|签约|裁掉|官宣|报道称|报道|"
                r"夏季联赛|自由市场|自由球员|选秀结果",
                query,
            )
        )

    def is_out_of_scope(self, query: str) -> bool:
        """检测问题是否超出 NBA 数据库范围"""
        out_of_scope = re.compile(
            r"薪资帽|工资帽|规则|历史纪录|最伟大|goat|名人堂|"
            r"nba2k|游戏|篮球鞋|球鞋|教练|裁判|总裁|老板|"
            r"市值|收视率|转播合同|中国男篮|足球|天气"
        )
        return bool(out_of_scope.search(query.lower()))
