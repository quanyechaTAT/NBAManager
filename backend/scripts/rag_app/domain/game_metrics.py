"""Exact score decomposition, emitted only when it reconciles with the final score."""
import re


def scoring_breakdown(fact: dict) -> dict:
    result = {}
    for side in ('home', 'away'):
        team = fact.get(side + '_team')
        final = fact.get(side + '_score')
        metrics = fact.get('statistics', {}).get(team, {})
        pairs = []
        for label in ('投篮命中-出手', '三分命中-出手', '罚球命中-出手'):
            match = re.fullmatch(r'(\d+)-(\d+)', str(metrics.get(label, '')))
            if not match or int(match[1]) > int(match[2]):
                break
            pairs.append(tuple(map(int, match.groups())))
        if len(pairs) != 3 or not isinstance(final, int) or isinstance(final, bool):
            continue
        (fg, fga), (threes, three_attempts), (ft, _) = pairs
        if threes > fg or three_attempts > fga:
            continue
        parts = {'two_point_points': (fg - threes) * 2, 'three_point_points': threes * 3,
                 'free_throw_points': ft}
        if sum(parts.values()) == final:
            result[team] = parts
    return result
