from __future__ import annotations

import re
from difflib import SequenceMatcher


_SENTENCE_PATTERN = re.compile(r"[^。！？!?；;\n]+[。！？!?；;]?")


def _similar(a: str, b: str, threshold: float = 0.88) -> bool:
    return SequenceMatcher(None, a, b).ratio() >= threshold


def clean_repetition(text: str) -> str:
    if not text:
        return ""

    sentences = [match.group(0).strip() for match in _SENTENCE_PATTERN.finditer(text)]
    cleaned: list[str] = []

    for sentence in sentences:
        if not sentence:
            continue
        if any(_similar(sentence, existing) for existing in cleaned):
            continue
        cleaned.append(sentence)

    return "\n".join(cleaned).strip()
