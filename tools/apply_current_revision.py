"""One-time material-only shop migration; preserve edited prices of retained goods."""
import json
import shutil
from pathlib import Path
from revise_shop import wood_block

ROOT = Path(__file__).resolve().parents[1]
REVISION = "materials-tasks-v3"
STONE = set("stone cobblestone granite diorite andesite deepslate cobbled_deepslate tuff calcite blackstone basalt dripstone_block netherrack end_stone obsidian crying_obsidian cinnabar sulfur".split())
facts = json.loads((ROOT / "tools/data/shop-facts-26.2.json").read_text(encoding="utf-8-sig"))
recipes = set(facts["recipeOutputs"])
starters = set(facts["plantStarters"])

def apply(folder):
    path = folder / "shop/products.json"
    if not path.exists():
        return
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    if data.get("shopRevision") == REVISION:
        return
    backup = ROOT / ".tools/revision-v3-backups" / folder.relative_to(ROOT)
    if not backup.exists():
        shutil.copytree(folder, backup)
    changed = 0
    for product in data["products"].values():
        item = product["canonicalStack"]["itemId"].removeprefix("minecraft:")
        raw_wood = item.endswith(("_log", "_stem")) and not item.startswith("stripped_") and item != "mushroom_stem"
        planks = item.endswith("_planks")
        base = raw_wood or planks or item in STONE
        derived = (wood_block(item) or
                   product["canonicalStack"]["itemId"] == "yumis_sky_island:shipping_chest" or
                   item.startswith(("quartz_", "smooth_quartz", "chiseled_quartz")) or
                   item.endswith(("_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_door", "_trapdoor", "_button", "_pressure_plate")) or
                   (product.get("shopCategory") == "minecraft:building_blocks" and item in recipes))
        if base:
            product["tradeMode"] = "buy_sell"
            product["shopRule"] = "保留基础原木、木板、原始石材；可买可卖"
        elif derived:
            product["tradeMode"] = "disabled"
            product["baseValueCoins"] = None
            product["shopRule"] = "加工建筑品删除购买、收购与金币估值"
            changed += 1
        product["firstAcquisitionPremium"] = item in starters and product["tradeMode"] != "disabled"
    data["shopRevision"] = REVISION
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    shipping_path = folder / "automation/shipping_chest.json"
    if shipping_path.exists():
        shipping = json.loads(shipping_path.read_text(encoding="utf-8-sig"))
        shipping["baseValueCoins"] = data["products"]["yumis_sky_island:shipping_chest"]["baseValueCoins"]
        shipping_path.write_text(json.dumps(shipping, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{folder.relative_to(ROOT)}: removed {changed} building products; retained prices preserved")

if __name__ == "__main__":
    for folder in (ROOT / "config/yumis_sky_island", ROOT / "run/config/yumis_sky_island", ROOT / "build/run/clientGameTest/config/yumis_sky_island"):
        apply(folder)
