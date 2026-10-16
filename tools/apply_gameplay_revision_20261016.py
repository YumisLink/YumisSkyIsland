"""Synchronise the explicitly confirmed test-period defaults, without runtime migrations."""
from pathlib import Path
import shutil
import tomllib
from config_io import read, write, compact_generated_comments

ROOT = Path(__file__).resolve().parents[1]
BACKUP = ROOT / ".tools" / "gameplay-20261016-before"
OBSOLETE = ("production.toml", "unlocks.toml", "shipping_chest.toml")

def sync(folder):
    if not (folder / "shop/products.toml").exists(): return
    defaults = ROOT / "config/yumis_sky_island"
    if folder != defaults:
        for source in defaults.rglob("*.toml"):
            target = folder / source.relative_to(defaults)
            if not target.exists():
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, target)
    products = read(folder / "shop/products.toml")
    cannon = products["products"]["yumis_sky_island:blueprint_cannon"]
    cannon["baseValueCoins"] = cannon["buyBaseValueCoins"] = 100000
    write(folder / "shop/products.toml", products)
    levels = read(folder / "shop/item_levels.toml")
    levels["itemLevels"]["yumis_sky_island:blueprint_cannon"] = 1
    write(folder / "shop/item_levels.toml", levels)
    rarities = read(folder / "shop/rarities.toml")
    rarities["itemRarities"]["yumis_sky_island:blueprint_cannon"] = "common"
    write(folder / "shop/rarities.toml", rarities)
    lottery = ROOT / "config/yumis_sky_island/gameplay/lottery.toml"
    if folder != ROOT / "config/yumis_sky_island":
        shutil.copy2(lottery, folder / "gameplay/lottery.toml")
    for name in OBSOLETE:
        file = folder / "automation" / name
        if file.exists(): file.unlink()
    for file in folder.rglob("*.toml"):
        old = file.read_text("utf-8-sig")
        compact = compact_generated_comments(old)
        assert tomllib.loads(old) == tomllib.loads(compact), file
        if compact != old: file.write_text(compact, "utf-8", newline="\n")

def reprice_library(library):
    catalog = library / "catalog.toml"
    if not catalog.exists(): return
    original = BACKUP / "prefab-catalog.toml"
    original.parent.mkdir(parents=True, exist_ok=True)
    if not original.exists(): shutil.copy2(catalog, original)
    baseline = tomllib.loads(original.read_text("utf-8"))["templates"]
    current = read(catalog)
    for key, row in current["templates"].items():
        if row.get("category") == "生电" and key in baseline:
            row["blueprintPrice"] = max(1, baseline[key]["blueprintPrice"] // 4)
            row["buildFee"] = max(1, baseline[key]["buildFee"] // 4)
    write(catalog, current)

def main():
    sync(ROOT / "config/yumis_sky_island")
    sync(ROOT / "run/config/yumis_sky_island")
    reprice_library(ROOT / "run/prefabs/yumis_sky_island")
    print("默认与开发TOML、生电图纸和成品费已同步；三份纯资料TOML已删除。")

if __name__ == "__main__": main()
