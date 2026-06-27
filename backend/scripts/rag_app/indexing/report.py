from __future__ import annotations

import json
from pathlib import Path
from time import time

from rag_app.config import DEFAULT_RUNTIME_DIR


def write_report(report: dict) -> None:
    DEFAULT_RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    report = {**report, "generated_at": int(time())}
    (DEFAULT_RUNTIME_DIR / "index_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
