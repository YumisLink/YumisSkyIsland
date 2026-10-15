"""Synchronize test-period TOML defaults to the development instance with backup."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
defaults = (root / "config/yumis_sky_island").resolve()
instances = (
    (root / "run/config/yumis_sky_island", root / ".tools/config-backup-professions-2026-10-15"),
    (root / "build/run/clientGameTest/config/yumis_sky_island", root / ".tools/config-backup-client-tests-2026-10-15"),
)
for runtime, backup in instances:
    runtime, backup = runtime.resolve(), backup.resolve()
    for path in (defaults, runtime, backup):
        if not path.is_relative_to(root):
            raise ValueError(f"Path outside workspace: {path}")
    if runtime.exists() and not backup.exists():
        shutil.copytree(runtime, backup)
    for source in defaults.rglob("*.toml"):
        destination = runtime / source.relative_to(defaults)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
    print(f"Synchronized {len(list(defaults.rglob('*.toml')))} files into {runtime}; backup: {backup}")
