"""Register CDC only after the application schema exists. Credentials are never logged."""
import os
import httpx
from discovery_app.config import Settings


def main():
    s = Settings()
    tables = ['players', 'teams', 'match_records', 'game_news', 'posts', 'discovery_events', 'discovery_entity_aliases']
    config = {
        'connector.class': 'io.debezium.connector.mysql.MySqlConnector', 'tasks.max': '1',
        'database.hostname': os.getenv('DISCOVERY_CDC_DB_HOST', 'mysql'), 'database.port': '3306',
        'database.user': os.getenv('DISCOVERY_CDC_DB_USER', s.db_user),
        'database.password': os.getenv('DISCOVERY_CDC_DB_PASSWORD', s.db_password),
        'database.server.id': '184055', 'topic.prefix': s.topic_prefix,
        'database.include.list': s.db_name, 'table.include.list': ','.join(s.db_name+'.'+table for table in tables),
        'schema.history.internal.kafka.bootstrap.servers': 'kafka:9092',
        'schema.history.internal.kafka.topic': 'discovery_schema_history',
        'include.schema.changes': 'false', 'snapshot.mode': 'initial',
        'decimal.handling.mode': 'double', 'tombstones.on.delete': 'true',
        'heartbeat.interval.ms': '10000', 'database.connectionTimeZone': 'Asia/Shanghai',
        'key.converter': 'org.apache.kafka.connect.json.JsonConverter', 'key.converter.schemas.enable': 'false',
        'value.converter': 'org.apache.kafka.connect.json.JsonConverter', 'value.converter.schemas.enable': 'false',
    }
    url = os.getenv('DISCOVERY_CONNECT_URL', 'http://127.0.0.1:18083')
    response = httpx.put(url+'/connectors/nba-discovery/config', json=config, timeout=30)
    if response.is_error: raise RuntimeError(f'Connector registration failed: HTTP {response.status_code}')
    print('CDC connector registered. Inspect /connectors/nba-discovery/status for snapshot progress.')


if __name__ == '__main__': main()
