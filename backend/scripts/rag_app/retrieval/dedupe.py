from __future__ import annotations

import hashlib
from typing import Any, Iterable, List, Sequence, Tuple


def normalize_text(text: str) -> str:
    return " ".join((text or "").strip().split())


def text_hash(text: str) -> str:
    return hashlib.md5(normalize_text(text).encode("utf-8")).hexdigest()


def document_text(document: Any) -> str:
    if isinstance(document, str):
        return document
    return getattr(document, "page_content", str(document))


def document_metadata(document: Any) -> dict:
    metadata = getattr(document, "metadata", None)
    return metadata if isinstance(metadata, dict) else {}


def dedupe_documents(documents: Iterable[Any]) -> List[Any]:
    seen = set()
    unique = []
    for document in documents:
        digest = text_hash(document_text(document))
        if digest in seen:
            continue
        seen.add(digest)
        unique.append(document)
    return unique


def dedupe_texts(
    documents: Sequence[str],
    metadatas: Sequence[dict] | None = None,
    distances: Sequence[float] | None = None,
) -> Tuple[List[str], List[dict], List[float]]:
    metadatas = metadatas or [{} for _ in documents]
    distances = distances or [0.0 for _ in documents]
    seen = set()
    unique_docs: List[str] = []
    unique_metas: List[dict] = []
    unique_distances: List[float] = []

    for document, metadata, distance in zip(documents, metadatas, distances):
        digest = text_hash(document)
        if digest in seen:
            continue
        seen.add(digest)
        unique_docs.append(document)
        unique_metas.append(metadata or {})
        unique_distances.append(float(distance or 0.0))

    return unique_docs, unique_metas, unique_distances
