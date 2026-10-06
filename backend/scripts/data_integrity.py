"""Publication-safe numeric mappings shared by providers and RAG; zero remains an observation."""
import math


def optional_number(value):
    if value is None or isinstance(value, bool) or value == '':
        return None
    try:
        number = float(value)
        return number if math.isfinite(number) else None
    except (TypeError, ValueError):
        return None


def optional_round(value, digits=1):
    number = optional_number(value)
    return None if number is None else round(number, digits)


def per_game(total, games):
    numerator, denominator = optional_number(total), optional_number(games)
    return None if numerator is None or denominator is None or denominator <= 0 else round(numerator / denominator, 1)


def player_metrics(base, advanced):
    mapping = {'ppg': 'PTS', 'rpg': 'REB', 'apg': 'AST', 'spg': 'STL', 'bpg': 'BLK',
               'tpg': 'TOV', 'mpg': 'MIN', 'fgPct': 'FG_PCT', 'threePct': 'FG3_PCT', 'ftPct': 'FT_PCT'}
    result = {key: optional_round(base.get(field), 3 if 'Pct' in key else 1) for key, field in mapping.items()}
    games = optional_number(base.get('GP'))
    result['gp'] = None if games is None else int(games)
    result['tsPct'] = optional_round(advanced.get('TS_PCT'), 3)
    usage = optional_number(advanced.get('USG_PCT'))
    result['usgPct'] = None if usage is None else round(usage * 100, 1)
    # PER and offensive rating are distinct. NBA Advanced does not provide PER.
    for key, field in {'per': 'PER', 'bpm': 'BPM', 'vorp': 'VORP', 'winShares': 'WS',
                       'offensiveRating': 'OFF_RATING', 'defensiveRating': 'DEF_RATING', 'efficiency': 'EFF'}.items():
        result[key] = optional_round(advanced.get(field), 1)
    return result


def display_metric(value, percentage=False):
    number = optional_number(value)
    if number is None:
        return '—（缺失或未核验）'
    return f'{number * 100:.1f}%' if percentage else f'{number:g}'
