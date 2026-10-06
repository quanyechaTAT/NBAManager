"""Suggest concrete, answerable next questions without another LLM request."""
import re
from rag_app.pipeline.game_scope import explicit_game_date, game_context


def build_follow_ups(prepared: dict, answer: str) -> list[str]:
    if not prepared.get('allowed') or not answer.strip():
        return []
    classification = prepared['classification']
    facts = [item.facts for item in prepared.get('search_results', []) if getattr(item, 'facts', None)]
    if len(classification.teams) >= 2:
        evidence = prepared.get('structured_text', '') + '\n' + '\n'.join(item.snippet for item in prepared.get('search_results', []))
        selected_date = explicit_game_date(prepared['resolved_question'])
        if not selected_date and facts:
            selected_date = facts[0].get('date', '')
        if not selected_date:
            match = re.search(r'\b(?:19|20)\d{2}-\d{2}-\d{2}\b', evidence)
            selected_date = match.group(0) if match else ''
        # An ambiguous multi-game answer must not suggest a question about an invented game.
        if not selected_date:
            return []
        topic = selected_date + ' ' + '对'.join(classification.teams[:2]) + '的比赛'
        facts = [fact for fact in facts if fact.get('date') == selected_date]
        stats = [metrics for fact in facts for metrics in fact.get('statistics', {}).values()]
        deeper = _comparisons(facts[0], classification.teams[:2]) if facts else {}
        asked = prepared['resolved_question'] + '\n' + '\n'.join(
            question for question in prepared.get('recent_questions', [])
            if explicit_game_date(question) == selected_date
            and all(team in question for team in classification.teams[:2])
        )
        questions = []

        def suggest(question, topic_pattern):
            if not re.search(topic_pattern, asked):
                questions.append(topic + '：' + question)

        if len(stats) >= 2 and all('投篮命中率%' in metrics and '三分命中率%' in metrics for metrics in stats):
            suggest(deeper.get('shooting', '双方投篮和三分命中率有哪些差异？'), r'投篮|三分|命中率')
        if len(stats) >= 2 and all('篮板' in metrics and '失误' in metrics for metrics in stats):
            metrics = '篮板、进攻篮板和失误' if all('进攻篮板' in metrics for metrics in stats) else '篮板和失误'
            suggest(deeper.get('possessions', f'比较双方{metrics}，哪些差异最明显？'), r'篮板|失误')
        if any(fact.get('players') for fact in facts):
            suggest(deeper.get('players', '双方得分较高球员的得分、篮板和助攻分别是多少？'), r'球员|得分较高|得分最高')
        if len(stats) >= 2 and all('罚球命中-出手' in metrics for metrics in stats):
            suggest('比较双方罚球命中数与次数，和最终分差分别是多少？', r'罚球')
        if len(stats) >= 2 and all('助攻' in metrics and '禁区得分' in metrics for metrics in stats):
            suggest('双方助攻和禁区得分有哪些差异，能支持什么判断？', r'助攻|禁区得分')
        if not stats:
            suggest('分差能支持哪些判断，哪些战术原因还缺少证据？', r'原因|判断|局限|因果')
            suggest('请补查技术统计，核实双方篮板和失误的差异。', r'技术统计|篮板|失误')
        suggest('请列出数据来源，并区分已核实事实与推测。', r'来源|依据|推测')
        return questions[:3]
    if prepared.get('query_plan', {}).get('intent') in {'analysis', 'explanation', 'comparison'}:
        entities = classification.teams + classification.players
        if not entities:
            return []
        question = prepared['resolved_question']
        scope = game_context(question, classification.teams)
        period = scope['date'] or (scope['season'] + '赛季' if scope['season'] else '') or (str(scope['year']) + '年' if scope['year'] else '')
        relative = re.search(r'本赛季|上赛季|上个赛季|最近|近期', question)
        subject = (period or (relative.group(0) if relative else '')) + '、'.join(entities)
        theme = re.search(r'弱侧协防|弱侧轮转|挡拆|无球跑动|防守体系|外线投射|进攻空间|防守|进攻', question)
        topic = theme.group(0) if theme else '上述分析'
        return [f'{subject}：现有资料能否验证关于{topic}的前提，哪些是直接证据，哪些只是推测？',
                f'{subject}：关于{topic}，比较不同解释的依据和反例，还缺哪些关键证据？',
                f'{subject}：关于{topic}，资料的时间、样本和来源有哪些局限，结论适用到什么范围？']
    if classification.players:
        player = classification.players[0]
        return [f'{player}的场均得分、篮板和助攻来自哪个赛季？',
                f'{player}的投篮和三分命中率分别是多少？',
                f'分析{player}现有数据可以支持的结论及其局限。']
    if classification.teams:
        team = classification.teams[0]
        return [f'比较{team}最近三场已结束比赛的比分和分差。',
                f'{team}目前的胜负记录有什么数据来源？',
                f'分析{team}已有比赛记录能支持哪些判断。']
    return []


def _comparisons(fact, teams):
    """Every question premise is computed from one game's validated facts."""
    stats = fact.get('statistics', {})
    if not all(team in stats for team in teams):
        return {}
    left, right = teams
    scores = {fact.get('home_team'): fact.get('home_score'), fact.get('away_team'): fact.get('away_score')}
    questions = {}

    def number(team, label):
        value = str(stats[team].get(label, '')).rstrip('%')
        return float(value) if re.fullmatch(r'\d+(?:\.\d+)?', value) else None

    def shots(team):
        value = str(stats[team].get('投篮命中-出手', ''))
        match = re.fullmatch(r'(\d+)-(\d+)', value)
        return bool(match and int(match[1]) <= int(match[2]))

    shot_details = all(shots(team) and all(label in stats[team] for label in
        ('三分命中-出手', '罚球命中-出手')) for team in teams)
    if shot_details:
        questions['shooting'] = '结合投篮出手次数、三分和罚球，比较双方得分构成；哪些差异与最终分差对应？'
        rates = {team: number(team, '投篮命中率%') for team in teams}
        if all(isinstance(scores.get(team), int) and rates[team] is not None and rates[team] <= 100 for team in teams):
            higher = max(teams, key=rates.get)
            other = right if higher == left else left
            if rates[higher] > rates[other] and scores[higher] < scores[other]:
                questions['shooting'] = f'{higher}投篮命中率{rates[higher]:g}%高于{other}的{rates[other]:g}%却输球，结合出手次数、三分和罚球，如何解释得分构成？'

    if shot_details and all(number(team, key) is not None for team in teams for key in ('进攻篮板', '失误')):
        rebounds = {team: number(team, '进攻篮板') for team in teams}
        turnovers = {team: number(team, '失误') for team in teams}
        rebound_team = max(teams, key=rebounds.get)
        turnover_team = min(teams, key=turnovers.get)
        rebound_difference = abs(rebounds[left] - rebounds[right])
        turnover_difference = abs(turnovers[left] - turnovers[right])
        rebound_premise = f'{rebound_team}多{rebound_difference:g}个进攻篮板' if rebound_difference else '双方进攻篮板持平'
        turnover_premise = f'{turnover_team}少{turnover_difference:g}次失误' if turnover_difference else '双方失误次数相同'
        questions['possessions'] = f'{rebound_premise}、{turnover_premise}：结合双方投篮出手数，能支持哪些判断，还缺哪些因果证据？'

    leaders = []
    for team in teams:
        players = [player for player in fact.get('players', []) if player.get('team') == team
                   and player.get('name') and isinstance(player.get('points'), int)]
        if players and isinstance(scores.get(team), int) and scores[team] > 0:
            leaders.append(max(players, key=lambda player: player['points']))
    if len(leaders) == 2:
        names = '和'.join(player['name'] for player in leaders)
        questions['players'] = f'比较已列出的球员{names}的得分占全队比例，并结合篮板和助攻说明贡献与判断局限。'
    return questions
