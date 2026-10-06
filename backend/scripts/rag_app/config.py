from dataclasses import dataclass
import os
from pathlib import Path
import sys


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
DEFAULT_RUNTIME_DIR = SCRIPTS_DIR / "runtime" / "rag"


def _bool_env(name: str, default: bool) -> bool:
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _detect_device() -> str:
    try:
        import torch

        if torch.cuda.is_available():
            print(f"RAG device: cuda ({torch.cuda.get_device_name(0)})", file=sys.stderr)
            return "cuda"
    except Exception:
        pass
    print("RAG device: cpu", file=sys.stderr)
    return "cpu"


def _default_chroma_path() -> str:
    legacy_path = SCRIPTS_DIR / "chroma_db"
    if legacy_path.exists():
        return str(legacy_path)
    return str(DEFAULT_RUNTIME_DIR / "chroma_db")


def _default_model_cache() -> str:
    legacy_path = SCRIPTS_DIR / "models"
    if legacy_path.exists():
        return str(legacy_path)
    return str(DEFAULT_RUNTIME_DIR / "models")


@dataclass
class RAGSettings:
    embedding_model: str = "richinfoai/ritrieve_zh_v1"
    embedding_device: str = ""
    embedding_fp16: bool = True
    model_cache_dir: str = ""

    chroma_path: str = ""
    collection_name: str = "nba_data"
    vector_k: int = 8
    rerank_top_k: int = 5
    distance_threshold: float = 0.68

    reranker_model: str = "BAAI/bge-reranker-v2-m3"
    reranker_device: str = ""
    reranker_enabled: bool = True

    llm_base_url: str = "https://api.deepseek.com"
    llm_api_key: str = ""
    llm_model: str = "deepseek-v4-flash"
    llm_temperature: float = 0.3
    llm_max_tokens: int = 10240
    llm_timeout_seconds: int = 90
    llm_thinking: bool = False
    llm_reasoning_effort: str = "low"

    memory_window: int = 5
    memory_max_sessions: int = 256
    memory_ttl_seconds: int = 3600
    db_connect_timeout_seconds: int = 3

    db_host: str = "localhost"
    db_port: int = 3306
    db_user: str = "root"
    db_password: str = ""
    db_name: str = "nba_manager"

    host: str = "127.0.0.1"
    port: int = 8899

    # ── 联网搜索配置 ──
    search_enabled: bool = True
    search_timeout_seconds: int = 3
    search_top_k: int = 5
    search_cache_ttl_seconds: int = 600
    search_cache_max_entries: int = 256
    search_providers: str = "espn_scoreboard,espn_news"
    baidu_min_interval_seconds: int = 15
    baidu_circuit_breaker_seconds: int = 600
    searxng_base_url: str = ""
    allow_general_web_search: bool = False
    max_vector_distance: float = 0.68
    min_rerank_score: float = 0.0
    force_web_search: bool = False
    query_planner_mode: str = 'auto'
    query_planner_timeout_seconds: int = 5
    multi_query_limit: int = 3
    hyde_enabled: bool = True
    hyde_timeout_seconds: int = 5
    auto_build_index: bool = True

    # 向后兼容
    web_search_enabled: bool = True
    web_search_timeout_seconds: int = 8
    web_search_top_k: int = 5

    @classmethod
    def from_env(cls) -> "RAGSettings":
        settings = cls(
            embedding_model=os.environ.get("RAG_EMBEDDING_MODEL", cls.embedding_model),
            embedding_device=os.environ.get("RAG_EMBEDDING_DEVICE", ""),
            embedding_fp16=_bool_env("RAG_EMBEDDING_FP16", True),
            model_cache_dir=os.environ.get("RAG_MODEL_CACHE_DIR", _default_model_cache()),
            chroma_path=os.environ.get("RAG_CHROMA_PATH", _default_chroma_path()),
            collection_name=os.environ.get("RAG_COLLECTION_NAME", cls.collection_name),
            vector_k=int(os.environ.get("RAG_VECTOR_K", cls.vector_k)),
            rerank_top_k=int(os.environ.get("RAG_RERANK_TOP_K", cls.rerank_top_k)),
            distance_threshold=float(os.environ.get("RAG_DISTANCE_THRESHOLD", cls.distance_threshold)),
            reranker_model=os.environ.get("RAG_RERANKER_MODEL", cls.reranker_model),
            reranker_device=os.environ.get("RAG_RERANKER_DEVICE", ""),
            reranker_enabled=_bool_env("RAG_RERANKER_ENABLED", True),
            llm_base_url=os.environ.get("DEEPSEEK_API_BASE", cls.llm_base_url),
            llm_api_key=os.environ.get("DEEPSEEK_API_KEY", ""),
            llm_model=os.environ.get("RAG_LLM_MODEL", cls.llm_model),
            llm_temperature=float(os.environ.get("RAG_LLM_TEMPERATURE", cls.llm_temperature)),
            llm_max_tokens=int(os.environ.get("RAG_LLM_MAX_TOKENS", cls.llm_max_tokens)),
            llm_timeout_seconds=int(os.environ.get("RAG_LLM_TIMEOUT_SECONDS", cls.llm_timeout_seconds)),
            llm_thinking=_bool_env("RAG_LLM_THINKING", False),
            llm_reasoning_effort=os.environ.get("RAG_LLM_REASONING_EFFORT", "low"),
            memory_window=int(os.environ.get("RAG_MEMORY_WINDOW", cls.memory_window)),
            memory_max_sessions=int(os.environ.get('RAG_MEMORY_MAX_SESSIONS', cls.memory_max_sessions)),
            memory_ttl_seconds=int(os.environ.get('RAG_MEMORY_TTL_SECONDS', cls.memory_ttl_seconds)),
            db_connect_timeout_seconds=int(os.environ.get('RAG_DB_CONNECT_TIMEOUT_SECONDS', cls.db_connect_timeout_seconds)),
            db_host=os.environ.get("DB_HOST", "localhost"),
            db_port=int(os.environ.get("DB_PORT", "3306")),
            db_user=os.environ.get("DB_USERNAME", "root"),
            db_password=os.environ.get("DB_PASSWORD", ""),
            db_name=os.environ.get("DB_NAME", "nba_manager"),
            host=os.environ.get("RAG_HOST", "127.0.0.1"),
            port=int(os.environ.get("RAG_PORT", "8899")),
            web_search_enabled=_bool_env("RAG_WEB_SEARCH_ENABLED", True),
            web_search_timeout_seconds=int(os.environ.get("RAG_WEB_SEARCH_TIMEOUT_SECONDS", "8")),
            web_search_top_k=int(os.environ.get("RAG_WEB_SEARCH_TOP_K", "5")),
            search_enabled=_bool_env("RAG_SEARCH_ENABLED", True),
            search_timeout_seconds=int(os.environ.get("RAG_SEARCH_TIMEOUT_SECONDS", "3")),
            search_top_k=int(os.environ.get("RAG_SEARCH_TOP_K", "5")),
            search_cache_ttl_seconds=int(os.environ.get("RAG_SEARCH_CACHE_TTL_SECONDS", "600")),
            search_cache_max_entries=int(os.environ.get('RAG_SEARCH_CACHE_MAX_ENTRIES', cls.search_cache_max_entries)),
            search_providers=os.environ.get("RAG_SEARCH_PROVIDERS", "espn_scoreboard,espn_news,google_news_rss,baidusearch"),
            baidu_min_interval_seconds=int(os.environ.get("RAG_BAIDU_MIN_INTERVAL_SECONDS", "15")),
            baidu_circuit_breaker_seconds=int(os.environ.get("RAG_BAIDU_CIRCUIT_BREAKER_SECONDS", "600")),
            searxng_base_url=os.environ.get("RAG_SEARXNG_BASE_URL", ""),
            allow_general_web_search=_bool_env("RAG_ALLOW_GENERAL_WEB_SEARCH", False),
            max_vector_distance=float(os.environ.get("RAG_MAX_VECTOR_DISTANCE", "0.68")),
            min_rerank_score=float(os.environ.get("RAG_MIN_RERANK_SCORE", "0")),
            force_web_search=_bool_env("RAG_FORCE_WEB_SEARCH", False),
            query_planner_mode=os.environ.get('RAG_QUERY_PLANNER_MODE', cls.query_planner_mode),
            query_planner_timeout_seconds=int(os.environ.get('RAG_QUERY_PLANNER_TIMEOUT_SECONDS', cls.query_planner_timeout_seconds)),
            multi_query_limit=int(os.environ.get('RAG_MULTI_QUERY_LIMIT', cls.multi_query_limit)),
            hyde_enabled=_bool_env('RAG_HYDE_ENABLED', cls.hyde_enabled),
            hyde_timeout_seconds=int(os.environ.get('RAG_HYDE_TIMEOUT_SECONDS', cls.hyde_timeout_seconds)),
            auto_build_index=_bool_env('RAG_AUTO_BUILD_INDEX', cls.auto_build_index),
        )
        settings.resolve_devices()
        return settings

    def resolve_devices(self) -> None:
        if not self.embedding_device:
            self.embedding_device = _detect_device() if _bool_env("RAG_AUTO_DETECT_DEVICE", False) else "cpu"
        if not self.reranker_device:
            self.reranker_device = self.embedding_device

    def ensure_runtime_dirs(self) -> None:
        Path(self.chroma_path).mkdir(parents=True, exist_ok=True)
        Path(self.model_cache_dir).mkdir(parents=True, exist_ok=True)
        (DEFAULT_RUNTIME_DIR / "logs").mkdir(parents=True, exist_ok=True)
