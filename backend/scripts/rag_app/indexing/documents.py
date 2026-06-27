from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class IndexDocument:
    id: str
    text: str
    metadata: dict
