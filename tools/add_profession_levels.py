"""Add an independent, editable itemLevel to each existing profession good.

Only missing itemLevel keys are inserted; existing administrator values survive.
"""
from pathlib import Path
import re
import tomllib

root = Path(__file__).resolve().parents[1] / "config" / "yumis_sky_island"
source = root / "products" / "goods.toml"
shop_levels = tomllib.loads((root / "shop" / "item_levels.toml").read_text(encoding="utf-8"))["itemLevels"]
gear_tiers = {"wooden": 0, "stone": 10, "copper": 20, "iron": 30,
              "golden": 40, "diamond": 60, "netherite": 100,
              "leather": 10, "chainmail": 20}
gear_suffixes = ("_pickaxe", "_axe", "_shovel", "_hoe", "_sword", "_helmet",
                 "_chestplate", "_leggings", "_boots", "_spear")

def level(item_id: str) -> int:
    name = item_id.removeprefix("minecraft:")
    if name.endswith(gear_suffixes):
        for material, tier in gear_tiers.items():
            if name.startswith(material + "_"):
                return tier
    if name == "bow":
        return 30
    if name == "crossbow":
        return 40
    if name == "trident":
        return 70
    if name == "mace":
        return 90
    raw = shop_levels.get(item_id, 0)
    if isinstance(raw, dict):
        raw = raw["unlockLevel"]
    return ((int(raw) + 9) // 10) * 10

with source.open("r", encoding="utf-8", newline="") as handle:
    original = handle.read()
contents = original.replace("\r\n", "\n")
newline = "\n"
blocks = re.split(r'(?=^\[items\."[^\"]+"\]$)', contents, flags=re.MULTILINE)
for index, block in enumerate(blocks):
    match = re.match(r'\[items\."([^\"]+)"\]', block)
    if match and not re.search(r'^itemLevel\s*=', block, flags=re.MULTILINE):
        marker = re.search(r'^qualityAllowed[ \t]*=[ \t]*(?:true|false)$', block, flags=re.MULTILINE)
        if marker is None:
            raise ValueError(f"Missing qualityAllowed for {match.group(1)}")
        pos = marker.end()
        blocks[index] = block[:pos] + newline + "# 独立职业物品等级；只影响品质与经验，管理员可单独修改。" + newline + f"itemLevel = {level(match.group(1))}" + block[pos:]
updated = "".join(blocks)
if updated != original:
    with source.open("w", encoding="utf-8", newline="") as handle:
        handle.write(updated)
