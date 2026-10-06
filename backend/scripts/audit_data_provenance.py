"""Read-only aggregate provenance audit, before or after the additive migration.

Credentials are supplied through DB_USERNAME / DB_PASSWORD (never CLI arguments).
No row values, IDs, credentials or source URLs are included in the report.
"""
from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path

TABLES = ('players', 'player_season_stats', 'player_career_stats', 'player_game_log',
          'teams', 'team_season_stats', 'match_records', 'player_shot_chart')
STAT_FIELDS = ('games_played', 'minutes_per_game', 'points_per_game', 'rebounds_per_game',
               'assists_per_game', 'steals_per_game', 'blocks_per_game', 'turnovers_per_game',
               'field_goal_pct', 'three_point_pct', 'free_throw_pct', 'true_shooting_pct',
               'usage_pct', 'efficiency', 'per', 'bpm', 'offensive_rating', 'defensive_rating',
               'wins', 'losses', 'points', 'rebounds', 'assists', 'steals', 'blocks',
               'turnovers', 'plus_minus', 'home_score', 'away_score')
META_COLUMNS = ('data_source', 'data_source_url', 'data_season', 'data_scope', 'data_as_of',
                'data_synced_at', 'data_quality', 'data_field_states')


def camel_case(field):
    words = field.split('_')
    return words[0] + ''.join(word.capitalize() for word in words[1:])


def publication_sql(field):
    if field not in STAT_FIELDS:
        raise ValueError('Unsupported statistic')
    states = "CASE WHEN JSON_VALID(data_field_states) THEN data_field_states ELSE '{}' END"
    return (f"data_quality IN ('COMPLETE','PARTIAL') AND `{field}` IS NOT NULL AND "
            f"JSON_UNQUOTE(JSON_EXTRACT({states}, '$.{camel_case(field)}')) IN ('OBSERVED','DERIVED')")


def audit(connection):
    report = {'generatedAt': datetime.now(timezone.utc).isoformat(), 'readOnly': True,
              'rules': 'Publish only non-null OBSERVED or DERIVED fields of COMPLETE or PARTIAL rows; numerical values are never used to infer placeholder data.',
              'modules': {}, 'snapshots': []}
    with connection.cursor(dictionary=True) as cursor:
        for table in TABLES:
            cursor.execute('SELECT COLUMN_NAME FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s', (table,))
            columns = {row['COLUMN_NAME'] for row in cursor.fetchall()}
            if not columns:
                report['modules'][table] = {'status': 'TABLE_ABSENT'}
                continue
            cursor.execute(f'SELECT COUNT(*) AS total FROM `{table}`')
            total = cursor.fetchone()['total']
            missing_meta = [column for column in META_COLUMNS if column not in columns]
            module = {'totalRecords': total, 'missingMetadataColumns': missing_meta}
            fields = [field for field in STAT_FIELDS if field in columns]
            expressions = [f"SUM(CASE WHEN `{field}` IS NULL THEN 1 ELSE 0 END) AS m{i}" for i, field in enumerate(fields)]
            migrated = not missing_meta
            if migrated:
                expressions.extend(f"SUM(CASE WHEN {publication_sql(field)} THEN 1 ELSE 0 END) AS p{i}" for i, field in enumerate(fields))
                cursor.execute(f'SELECT data_quality AS quality, data_source AS source, data_season AS season, COUNT(*) AS records, MAX(data_synced_at) AS syncedAt, MAX(data_as_of) AS dataAsOf FROM `{table}` GROUP BY data_quality, data_source, data_season')
                module['coverage'] = cursor.fetchall()
            if expressions:
                cursor.execute(f"SELECT {', '.join(expressions)} FROM `{table}`")
                counts = cursor.fetchone()
                module['fields'] = {field: {'missing': int(counts[f'm{i}'] or 0),
                                          'published': int(counts[f'p{i}'] or 0) if migrated else None,
                                          'withheld': total - int(counts[f'p{i}'] or 0) if migrated else None}
                                    for i, field in enumerate(fields)}
            module['status'] = 'MIGRATED' if migrated else 'LEGACY_SCHEMA'
            report['modules'][table] = module
        cursor.execute("SELECT COUNT(*) AS total FROM information_schema.tables WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='data_verification_snapshots'")
        if cursor.fetchone()['total']:
            cursor.execute('SELECT module, migration_version AS migrationVersion, COUNT(*) AS records FROM data_verification_snapshots GROUP BY module, migration_version')
            report['snapshots'] = cursor.fetchall()
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default=os.getenv('DB_HOST', '127.0.0.1'))
    parser.add_argument('--port', type=int, default=int(os.getenv('DB_PORT', '3306')))
    parser.add_argument('--database', default=os.getenv('DB_NAME'))
    parser.add_argument('--output', type=Path, help='Write the aggregate JSON report to this file')
    args = parser.parse_args()
    if not args.database or not os.getenv('DB_USERNAME'):
        parser.error('Set DB_NAME (or --database) and DB_USERNAME; use DB_PASSWORD for the password.')
    import mysql.connector
    connection = mysql.connector.connect(host=args.host, port=args.port, database=args.database,
                                         user=os.environ['DB_USERNAME'], password=os.getenv('DB_PASSWORD', ''),
                                         connection_timeout=10, charset='utf8mb4')
    try:
        connection.start_transaction(consistent_snapshot=True, readonly=True)
        report = audit(connection)
        content = json.dumps(report, ensure_ascii=False, indent=2, default=str) + '\n'
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(content, encoding='utf8')
        print(content, end='')
    finally:
        connection.rollback()
        connection.close()


if __name__ == '__main__':
    main()
