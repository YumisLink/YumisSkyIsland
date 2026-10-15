"""Remove only obsolete quest introduction lines, preserving edited quest goals."""
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "config/yumis_sky_island/tasks/quests.toml"
with path.open("r", encoding="utf-8", newline="") as handle:
    lines = handle.readlines()
result = []
for line in lines:
    if line.startswith("description = "):
        if result and result[-1].startswith("# 任务详情说明"):
            result.pop()
        continue
    result.append(line)
if result != lines:
    with path.open("w", encoding="utf-8", newline="") as handle:
        handle.writelines(result)
