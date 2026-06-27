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

    llm_base_url: str = "https://token-plan-cn.xiaomimimo.com/v1"
    llm_api_key: str = ""
    llm_model: str = "mimo-v2.5-pro"
    llm_temperature: float = 0.2
    llm_max_tokens: int = 900
    llm_timeout_seconds: int = 60

    memory_window: int = 5

    db_host: str = "localhost"
    db_port: int = 3306
    db_user: str = "root"
    db_password: str = ""
    db_name: str = "nba_manager"

    host: str = "127.0.0.1"
    port: int = 8899

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
            llm_base_url=os.environ.get("MIMO_BASE_URL", cls.llm_base_url),
            llm_api_key=os.environ.get("MIMO_API_KEY", ""),
            llm_model=os.environ.get("RAG_LLM_MODEL", cls.llm_model),
            llm_temperature=float(os.environ.get("RAG_LLM_TEMPERATURE", cls.llm_temperature)),
            llm_max_tokens=int(os.environ.get("RAG_LLM_MAX_TOKENS", cls.llm_max_tokens)),
            llm_timeout_seconds=int(os.environ.get("RAG_LLM_TIMEOUT_SECONDS", cls.llm_timeout_seconds)),
            memory_window=int(os.environ.get("RAG_MEMORY_WINDOW", cls.memory_window)),
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
