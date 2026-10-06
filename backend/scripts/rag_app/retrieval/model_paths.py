"""Resolve only the configured model, including its local Hugging Face revision."""
from pathlib import Path


def resolve_model_path(model_name: str, cache_dir: str) -> str:
    supplied = Path(model_name)
    if supplied.is_dir() or supplied.is_absolute():
        return model_name
    cache = Path(cache_dir)
    name = model_name.replace('/', '--')
    repository = cache / ('models--' + name)
    main = repository / 'refs' / 'main'
    if main.is_file():
        snapshot = repository / 'snapshots' / main.read_text(encoding='utf-8').strip()
        if (snapshot / 'config.json').is_file():
            return str(snapshot)
    snapshots = repository / 'snapshots'
    if snapshots.is_dir():
        for snapshot in sorted(snapshots.iterdir(), reverse=True):
            if (snapshot / 'config.json').is_file():
                return str(snapshot)
    direct = cache / name
    return str(direct) if (direct / 'config.json').is_file() else model_name
