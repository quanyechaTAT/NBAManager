from __future__ import annotations

from dataclasses import dataclass, field
import os
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    db_host: str = field(default_factory=lambda: os.getenv('DISCOVERY_DB_HOST', '127.0.0.1'))
    db_port: int = field(default_factory=lambda: int(os.getenv('DISCOVERY_DB_PORT', '3306')))
    db_name: str = field(default_factory=lambda: os.getenv('DISCOVERY_DB_NAME', 'nba_manager'))
    db_user: str = field(default_factory=lambda: os.getenv('DISCOVERY_DB_USER', os.getenv('DB_USERNAME', 'root')))
    db_password: str = field(default_factory=lambda: os.getenv('DISCOVERY_DB_PASSWORD', os.getenv('DB_PASSWORD', '')))
    redis_url: str = field(default_factory=lambda: os.getenv('DISCOVERY_REDIS_URL', 'redis://127.0.0.1:6379/2'))
    search_url: str = field(default_factory=lambda: os.getenv('DISCOVERY_OPENSEARCH_URL', 'http://127.0.0.1:9200'))
    kafka: str = field(default_factory=lambda: os.getenv('DISCOVERY_KAFKA', '127.0.0.1:19092'))
    topic_prefix: str = field(default_factory=lambda: os.getenv('DISCOVERY_TOPIC_PREFIX', 'nba'))
    internal_token: str = field(default_factory=lambda: os.getenv('DISCOVERY_INTERNAL_TOKEN', ''))
    api_threads: int = field(default_factory=lambda: int(os.getenv('DISCOVERY_API_THREADS', '8')))
    query_cache_seconds: float = field(default_factory=lambda: float(os.getenv('DISCOVERY_QUERY_CACHE_SECONDS', '5')))
    index_alias: str = field(default_factory=lambda: os.getenv('DISCOVERY_INDEX_ALIAS', 'nba-discovery'))
    embedding_model: str = field(default_factory=lambda: os.getenv('DISCOVERY_EMBEDDING_MODEL', 'richinfoai/ritrieve_zh_v1'))
    embedding_dim: int = field(default_factory=lambda: int(os.getenv('DISCOVERY_EMBEDDING_DIM', '1792')))
    embedding_enabled: bool = field(default_factory=lambda: os.getenv('DISCOVERY_EMBEDDING_ENABLED', 'true').lower() == 'true')
    model_cache: str = field(default_factory=lambda: os.getenv('DISCOVERY_MODEL_CACHE', os.getenv('RAG_MODEL_CACHE_DIR',
        str(Path(__file__).resolve().parents[1] / 'runtime' / 'rag' / 'models'))))
    runtime: Path = field(default_factory=lambda: Path(os.getenv('DISCOVERY_RUNTIME', str(Path(__file__).resolve().parents[1] / 'runtime' / 'discovery'))))
