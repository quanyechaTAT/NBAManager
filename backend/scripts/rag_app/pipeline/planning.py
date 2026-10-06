"""A single interpretation of query scope for every evidence adapter."""
from dataclasses import dataclass
import re

from rag_app.pipeline.game_scope import game_context
from rag_app.pipeline.query_classifier import QueryClassification, QueryType, QueryClassifier
from rag_app.pipeline.query_classifier import PLAYER_ALIASES
from rag_app.domain.players import PLAYER_EN

EXPLANATION_INTENT = re.compile(r'分析|复盘|为什么|原因|影响|展望|预测|如何评价|详细|全面|解释|能支持|因果|贡献|局限')
ANALYSIS_INTENT = re.compile(EXPLANATION_INTENT.pattern + r'|看点|关键|因素|评价|解读|解析|为何|优势|劣势|对比|比较|战术|体系|协防|挡拆|怎么|如何防|如何进攻')
GAME_METRICS = re.compile(r'篮板|助攻|失误|命中率|三分|罚球|技术统计|技统|首发|轮换|得分较高|得分最高|禁区|来源|依据')


@dataclass(frozen=True)
class QueryPlan:
    question: str
    resolved_question: str
    classification: QueryClassification
    top_k: int
    force_web: bool = False
    game_scope: dict | None = None
    intent: str = 'fact'
    retrieval_queries: tuple[str, ...] = ()
    sub_questions: tuple[str, ...] = ()
    planner_method: str = 'rules'
    planning_ms: int = 0
    planning_error: str = ''

    @classmethod
    def create(cls, question, resolved_question, classification, top_k, force_web=False):
        is_game = classification.query_type == QueryType.MATCH or bool(
            classification.teams and re.search(r'比赛|对阵|交锋|交手|比分|近[一二两三四五六七八九十\d]+场', resolved_question))
        scope = game_context(resolved_question, classification.teams) if is_game and classification.teams else None
        return cls(question, resolved_question, classification, max(1, min(20, top_k)), force_web, scope)

    @property
    def historical_game(self):
        return bool(self.game_scope and self.game_scope['historical'])

    @property
    def web_first(self):
        return self.force_web or self.classification.needs_web_search or self.historical_game

    @property
    def wants_game_details(self):
        return bool(ANALYSIS_INTENT.search(self.resolved_question) or GAME_METRICS.search(self.resolved_question))

    @property
    def semantic(self):
        return self.intent in {'analysis', 'explanation', 'comparison'}

    def diagnostics(self):
        return {'intent': self.intent, 'query_type': self.classification.query_type.value,
                'teams': self.classification.teams, 'players': self.classification.players,
                'game_scope': self.game_scope, 'queries': list(self.retrieval_queries),
                'sub_questions': list(self.sub_questions), 'method': self.planner_method,
                'elapsed_ms': self.planning_ms, 'error': self.planning_error}

    def accepts_document(self, document, metadata):
        if self.game_scope:
            return self.accepts_game_document(metadata)
        classification = self.classification
        mentioned = QueryClassifier().classify(document)
        if classification.players and metadata.get('type') in {'player', 'historical'}:
            name = metadata.get('name') or metadata.get('player_name')
            if name:
                english_names = {english.lower(): PLAYER_ALIASES[alias] for alias, english in PLAYER_EN.items()}
                canonical = PLAYER_ALIASES.get(name, english_names.get(str(name).lower(), name))
                if canonical not in classification.players:
                    return False
        participants = {metadata.get('team'), metadata.get('home_team'), metadata.get('away_team')}
        if not set(classification.teams).issubset(participants | set(mentioned.teams)):
            return False
        if classification.players and not set(classification.players).issubset(
                {metadata.get('name'), metadata.get('player_name')} | set(mentioned.players)):
            return False
        scope = game_context(self.resolved_question, classification.teams)
        try:
            doc_scope = game_context(document, [])
        except ValueError:
            return False
        recorded_date = str(metadata.get('match_date') or metadata.get('published_at') or '')[:10]
        if scope['date'] and scope['date'] not in {recorded_date, doc_scope['date']}:
            return False
        if scope['season'] and scope['season'] not in {metadata.get('season'), doc_scope['season']}:
            return False
        if scope['year'] and not (recorded_date.startswith(str(scope['year'])) or doc_scope['year'] == scope['year']):
            return False
        return True

    def can_answer_directly(self, structured_text):
        if not structured_text or self.force_web or self.semantic:
            return False
        if self.classification.needs_web_search and not self.historical_game:
            return False
        question = self.resolved_question
        if ANALYSIS_INTENT.search(question) or re.search(r'来源|依据|局限|说明|哪些判断|能支持|建议|谁更好|谁更强', question):
            return False
        if self.classification.query_type in {QueryType.TEAM, QueryType.PLAYER, QueryType.RANKING}:
            return True
        if self.classification.query_type == QueryType.MATCH and self.classification.teams:
            return not GAME_METRICS.search(question) and not re.search(r'今天|今日|明天|实时|正在|未开始', question)
        return not self.classification.needs_web_search and self.classification.query_type != QueryType.NEWS

    def accepts_game_document(self, metadata):
        """A team snapshot cannot answer a dated game, even at high similarity."""
        if not self.game_scope:
            return True
        if metadata.get('type') != 'match':
            return False
        participants = {metadata.get('home_team'), metadata.get('away_team')}
        if not set(self.classification.teams).issubset(participants):
            return False
        scope = self.game_scope
        game_type = metadata.get('game_type')
        if scope['playoffs_only'] and game_type != 'PLAYOFFS':
            return False
        if scope['league'] == 'nba-summer' and game_type != 'SUMMER_LEAGUE':
            return False
        if scope['league'] == 'nba' and game_type == 'SUMMER_LEAGUE':
            return False
        match_date = str(metadata.get('match_date') or '')[:10]
        if scope['date'] and match_date != scope['date']:
            return False
        if scope['year'] and not match_date.startswith(str(scope['year'])):
            return False
        if scope['season'] and metadata.get('season') != scope['season']:
            return False
        return bool(match_date)

    def accepts_game_fact(self, fact):
        metadata = {'type': 'match', 'home_team': fact.get('home_team'), 'away_team': fact.get('away_team'),
                    'match_date': fact.get('date'), 'season': fact.get('season'), 'game_type': fact.get('game_type')}
        if not self.accepts_game_document(metadata):
            return False
        return all(isinstance(fact.get(key), int) and not isinstance(fact[key], bool) for key in ('home_score', 'away_score'))
