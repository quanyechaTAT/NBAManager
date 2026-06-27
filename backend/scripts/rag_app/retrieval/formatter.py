def format_documents(documents: list[str], limit: int = 6) -> str:
    return "\n".join(f"- {doc}" for doc in documents[:limit] if doc)
