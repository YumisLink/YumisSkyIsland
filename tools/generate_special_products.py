"""Append the 26.2 biome-ball and flight-ticket defaults without changing existing products."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "config/yumis_sky_island/shop"
ASSETS = ROOT / "src/main/resources/assets/yumis_sky_island"
BIOMES = {
    "plains": "平原", "sunflower_plains": "向日葵平原", "snowy_plains": "雪原", "ice_spikes": "冰刺之地",
    "desert": "沙漠", "swamp": "沼泽", "mangrove_swamp": "红树林沼泽", "forest": "森林",
    "flower_forest": "繁花森林", "birch_forest": "桦木森林", "dark_forest": "黑森林", "pale_garden": "苍白之园",
    "old_growth_birch_forest": "原始桦木森林", "old_growth_pine_taiga": "原始松木针叶林",
    "old_growth_spruce_taiga": "原始云杉针叶林", "taiga": "针叶林", "snowy_taiga": "积雪针叶林",
    "savanna": "热带草原", "savanna_plateau": "热带高原", "windswept_hills": "风袭丘陵",
    "windswept_gravelly_hills": "风袭沙砾丘陵", "windswept_forest": "风袭森林",
    "windswept_savanna": "风袭热带草原", "jungle": "丛林", "sparse_jungle": "稀疏丛林",
    "bamboo_jungle": "竹林", "badlands": "恶地", "eroded_badlands": "风蚀恶地",
    "wooded_badlands": "疏林恶地", "meadow": "草甸", "cherry_grove": "樱花树林",
    "grove": "雪林", "snowy_slopes": "积雪山坡", "frozen_peaks": "冰封山峰",
    "jagged_peaks": "尖峭山峰", "stony_peaks": "裸岩山峰", "river": "河流",
    "frozen_river": "冻河", "beach": "沙滩", "snowy_beach": "积雪沙滩",
    "stony_shore": "石岸", "warm_ocean": "暖水海洋", "lukewarm_ocean": "温水海洋",
    "deep_lukewarm_ocean": "温水深海", "ocean": "海洋", "deep_ocean": "深海",
    "cold_ocean": "冷水海洋", "deep_cold_ocean": "冷水深海", "frozen_ocean": "冻洋",
    "deep_frozen_ocean": "冰冻深海", "mushroom_fields": "蘑菇岛", "dripstone_caves": "溶洞",
    "lush_caves": "繁茂洞穴", "deep_dark": "深暗之域", "sulfur_caves": "硫磺洞穴",
    "nether_wastes": "下界荒地", "warped_forest": "诡异森林", "crimson_forest": "绯红森林",
    "soul_sand_valley": "灵魂沙峡谷", "basalt_deltas": "玄武岩三角洲",
    "the_end": "末地", "end_highlands": "末地高地", "end_midlands": "末地内陆",
    "small_end_islands": "末地小型岛屿", "end_barrens": "末地荒地",
}
LEGENDARY = {"mushroom_fields", "deep_dark", "pale_garden", "the_end"}
EPIC = {"ice_spikes", "mangrove_swamp", "old_growth_birch_forest", "old_growth_pine_taiga",
        "old_growth_spruce_taiga", "bamboo_jungle", "badlands", "eroded_badlands",
        "wooded_badlands", "cherry_grove", "frozen_peaks", "jagged_peaks",
        "deep_frozen_ocean", "sulfur_caves", "warped_forest", "crimson_forest", "end_highlands"}
assert len(BIOMES) == 65 and len(LEGENDARY) == 4 and len(EPIC) == 17

products = CFG / "products.toml"
rarities = CFG / "rarities.toml"
levels = CFG / "item_levels.toml"
product_text = products.read_text(encoding="utf-8")
rarity_text = rarities.read_text(encoding="utf-8")
level_text = levels.read_text(encoding="utf-8")
items = [("flight_ticket", "飞行券", "rare", 100_000, 4, 2)]
for biome, name in sorted(BIOMES.items()):
    rarity = "legendary" if biome in LEGENDARY else "epic" if biome in EPIC else "rare"
    price = {"rare": 100_000, "epic": 250_000, "legendary": 1_000_000}[rarity]
    stock = {"rare": 8, "epic": 4, "legendary": 1}[rarity]
    weight = {"rare": 4, "epic": 2, "legendary": 1}[rarity]
    items.append(("biome_ball_" + biome, name + "生态球", rarity, price, stock, weight))

for path, name, rarity, price, stock, weight in items:
    item_id = "yumis_sky_island:" + path
    if f'[products."{item_id}"]' not in product_text:
        shop_category = "biome_ball" if path.startswith("biome_ball_") else "other"
        comment = "常驻商店【生态球】，按岛屿等级购买，不套常驻2.5倍。" if path.startswith("biome_ball_") else "每日／隐藏商店，岛屿0级可购买。"
        product_text += f'''
\n# {name}：{comment}
[products."{item_id}"]
productId = "{item_id}"
name = "{name}"
categoryHint = "特殊道具"
baseValueCoins = {price}
buyBaseValueCoins = {price}
tradeMode = "buy_only"
dailyStock = {stock}
offerWeight = {weight}
shopCategory = "{shop_category}"

[products."{item_id}".canonicalStack]
itemId = "{item_id}"

[products."{item_id}".canonicalStack.componentPatch]
'''
    if f'"{item_id}" =' not in rarity_text:
        rarity_text += f'\n# {name}稀有度。\n"{item_id}" = "{rarity}"\n'
    if f'"{item_id}" =' not in level_text:
        level_text += f'\n# {name}购买等级。\n"{item_id}" = 0\n'
    model = "minecraft:item/paper" if path == "flight_ticket" else "minecraft:item/ender_pearl"
    item_file = ASSETS / "items" / (path + ".json")
    item_file.parent.mkdir(parents=True, exist_ok=True)
    item_file.write_text(json.dumps({"model": {"type": "minecraft:model", "model": model}}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

FISHING_ITEMS = {
    "sky_carp": ("空岛鲤鱼", 1000, "rare", "minecraft:item/cod"),
    "lava_eel": ("熔岩鳗鱼", 2400, "epic", "minecraft:item/salmon"),
    "void_fish": ("虚空鱼", 4000, "epic", "minecraft:item/tropical_fish"),
    "frost_trout": ("霜鳟", 1400, "rare", "minecraft:item/salmon"),
    "desert_lungfish": ("沙漠肺鱼", 1400, "rare", "minecraft:item/cod"),
    "mushroom_fish": ("蘑菇鱼", 1800, "rare", "minecraft:item/pufferfish"),
    "glowing_scales": ("荧光鳞片", 10000, "rare", "minecraft:item/prismarine_shard"),
    "ash_pearl": ("余烬珍珠", 15000, "epic", "minecraft:item/magma_cream"),
    "void_fragment": ("虚空碎片", 30000, "epic", "minecraft:item/ender_pearl"),
    "coral_fragment": ("珊瑚碎片", 8000, "rare", "minecraft:item/nautilus_shell"),
}
for path, (name, price, rarity, model) in FISHING_ITEMS.items():
    item_id = "yumis_sky_island:" + path
    if f'[products."{item_id}"]' not in product_text:
        product_text += f'''
\n# {name}：钓鱼产物，仅可出售。
[products."{item_id}"]
productId = "{item_id}"
name = "{name}"
categoryHint = "钓鱼产物"
baseValueCoins = {price}
tradeMode = "sell_only"
dailyStock = 1
offerWeight = 1
shopCategory = "other"

[products."{item_id}".canonicalStack]
itemId = "{item_id}"

[products."{item_id}".canonicalStack.componentPatch]
'''
    if f'"{item_id}" =' not in rarity_text:
        rarity_text += f'\n# {name}稀有度。\n"{item_id}" = "{rarity}"\n'
    if f'"{item_id}" =' not in level_text:
        level_text += f'\n# {name}不设购买等级。\n"{item_id}" = 0\n'
    (ASSETS / "items" / (path + ".json")).write_text(json.dumps({"model": {"type": "minecraft:model", "model": model}}, indent=2) + "\n", encoding="utf-8")

products.write_text(product_text, encoding="utf-8")
rarities.write_text(rarity_text, encoding="utf-8")
levels.write_text(level_text, encoding="utf-8")
for language, ticket in (("zh_cn", "飞行券"), ("en_us", "Flight Ticket")):
    file = ASSETS / "lang" / (language + ".json")
    data = json.loads(file.read_text(encoding="utf-8"))
    data["item.yumis_sky_island.flight_ticket"] = ticket
    for biome, name in BIOMES.items():
        data["item.yumis_sky_island.biome_ball_" + biome] = name + "生态球" if language == "zh_cn" else biome.replace("_", " ").title() + " Biome Ball"
    professions = {"blacksmith": "铁匠", "stonemason": "石匠", "artisan": "工匠", "chef": "厨师", "farmer": "农夫", "hunter": "猎人"}
    for profession, name in professions.items():
        data["item.yumis_sky_island.fruit_" + profession] = name + "果实" if language == "zh_cn" else profession.title() + " Fruit"
    for path, (chinese, _, _, _) in FISHING_ITEMS.items():
        data["item.yumis_sky_island." + path] = chinese if language == "zh_cn" else path.replace("_", " ").title()
    file.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
for profession in ("blacksmith", "stonemason", "artisan", "chef", "farmer", "hunter"):
    (ASSETS / "items" / ("fruit_" + profession + ".json")).write_text(
        json.dumps({"model": {"type": "minecraft:model", "model": "minecraft:item/apple"}}, indent=2) + "\n", encoding="utf-8")

# Icon items exist only as GUI renderables; ownership and active state live in the player journal.
accessory_source = (ROOT / "src/main/java/cn/yumis/skyisland/server/AccessoryService.java").read_text(encoding="utf-8")
accessories = re.findall(r'(?:special|bonus)\("([a-z_]+)","([^\"]+)"', accessory_source)
assert len(accessories) == 25
for name, locale in (("zh_cn", True), ("en_us", False)):
    file = ASSETS / "lang" / (name + ".json")
    data = json.loads(file.read_text(encoding="utf-8"))
    for item_id, chinese in accessories:
        data["item.yumis_sky_island.accessory_" + item_id] = chinese if locale else item_id.replace("_", " ").title()
    file.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
for item_id, _ in accessories:
    file = ASSETS / "items" / ("accessory_" + item_id + ".json")
    file.write_text(json.dumps({"model": {"type": "minecraft:model", "model": "yumis_sky_island:item/accessory/" + item_id}}, indent=2) + "\n", encoding="utf-8")
    model = ASSETS / "models/item/accessory" / (item_id + ".json")
    model.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "yumis_sky_island:item/accessory/" + item_id}}, indent=2) + "\n", encoding="utf-8")

catalog = ["# 生态球目录", "", "生态球使用后把玩家所在的 16×16 区块（所有高度）改成目标生态群系。价格为默认购买基价；实际商店可用售价按对应商店规则显示。", ""]
for tier, label, price, names in (("rare", "稀有", 100_000, sorted(set(BIOMES)-LEGENDARY-EPIC)), ("epic", "史诗", 250_000, sorted(EPIC)), ("legendary", "传奇", 1_000_000, sorted(LEGENDARY))):
    catalog.extend((f"## {label} · {price:,} 摩拉／金币 · {len(names)} 种", "", "| 生态群系 | 原版 ID |", "| --- | --- |"))
    catalog.extend(f"| {BIOMES[biome]} | `minecraft:{biome}` |" for biome in names)
    catalog.append("")
(ROOT / "docs/BIOME_BALLS.md").write_text("\n".join(catalog), encoding="utf-8")
