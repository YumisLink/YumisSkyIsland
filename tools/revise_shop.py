"""Apply the approved Q34-Q48 shop revision, preserving its original price basis."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import shutil
import zipfile
from collections import Counter
from pathlib import Path

from prepare_economy import ingredient_options, read_recipes, write_json

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tools" / "data"
DEFAULT_CFG = ROOT / "config" / "yumis_sky_island"
JAR = ROOT / ".tools" / "minecraft-26.2-reference.jar"
REVISION = "q34-q48-material-shop-v2"
REMOVED_TABS = {"colored_blocks", "functional_blocks", "tools_and_utilities", "combat"}
MINERALS = {"coal", "iron_ingot", "copper_ingot", "gold_ingot", "diamond", "emerald",
            "lapis_lazuli", "redstone", "quartz", "amethyst_shard", "glowstone_dust", "cinnabar"}
COMPRESSED = {"coal_block", "iron_block", "gold_block", "copper_block", "diamond_block",
              "emerald_block", "lapis_block", "redstone_block", "netherite_block", "amethyst_block",
              "raw_iron", "raw_gold", "raw_copper", "raw_iron_block", "raw_gold_block", "raw_copper_block"}
STARTERS = {"wheat_seeds", "beetroot_seeds", "carrot", "potato", "melon_seeds", "pumpkin_seeds",
            "torchflower_seeds", "pitcher_pod", "sugar_cane", "bamboo", "cactus", "sweet_berries",
            "glow_berries", "cocoa_beans", "kelp", "sea_pickle", "nether_wart", "chorus_flower",
            "red_mushroom", "brown_mushroom", "crimson_fungus", "warped_fungus", "moss_block",
            "pale_moss_block", "azalea", "flowering_azalea", "vine", "weeping_vines", "twisting_vines",
            "glow_lichen", "pink_petals", "wildflowers", "lily_of_the_valley", "lilac", "peony",
            "rose_bush", "sunflower"}
FARM_SEEDS = {"wheat_seeds", "beetroot_seeds", "carrot", "potato", "melon_seeds", "pumpkin_seeds",
              "torchflower_seeds", "pitcher_pod", "sweet_berries", "glow_berries", "cocoa_beans", "nether_wart"}
CROP_OUTPUTS = {"wheat", "beetroot", "melon", "melon_slice", "pumpkin", "chorus_fruit", "torchflower", "pitcher_plant"}
NATURAL_DERIVED = {"red_mushroom_block", "brown_mushroom_block", "mushroom_stem", "chorus_plant",
                   "farmland", "dirt_path", "hanging_roots", "pale_hanging_moss", "short_grass", "tall_grass",
                   "fern", "large_fern", "short_dry_grass", "tall_dry_grass", "bush", "leaf_litter",
                   "amethyst_cluster", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud",
                   "shroomlight", "nether_wart_block", "warped_wart_block", "rooted_dirt", "mud",
                   "carved_pumpkin", "frogspawn", "turtle_egg", "cactus_flower", "seagrass", "big_dripleaf",
                   "sulfur_spike", "nether_sprouts", "crimson_roots", "warped_roots", "bee_nest"}
SUBSTRATES = {"grass_block", "mycelium", "podzol", "crimson_nylium", "warped_nylium", "end_stone",
              "netherrack", "pointed_dripstone", "sulfur"}
RAW_FOOD = {"apple", "beef", "chicken", "porkchop", "mutton", "rabbit", "egg", "honey_bottle"}
SCULK = {"sculk", "sculk_vein", "sculk_sensor", "sculk_catalyst", "sculk_shrieker"}
WOOD_TYPES = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
              "pale_oak", "crimson", "warped", "bamboo"}
WOOD_ENDINGS = {"log", "wood", "stem", "hyphae", "planks", "stairs", "slab", "fence", "fence_gate",
                "door", "trapdoor", "button", "pressure_plate", "sign", "hanging_sign", "block", "mosaic",
                "mosaic_stairs", "mosaic_slab", "shelf"}
WOOD_MISC = {"chest", "trapped_chest", "barrel", "bookshelf", "chiseled_bookshelf", "crafting_table",
             "cartography_table", "fletching_table", "loom", "beehive", "composter", "jukebox", "note_block",
             "mangrove_roots", "muddy_mangrove_roots", "scaffolding"}
REDSTONE_DEVICES = {"stone_button", "polished_blackstone_button", "stone_pressure_plate",
                    "polished_blackstone_pressure_plate", "light_weighted_pressure_plate", "heavy_weighted_pressure_plate"}
COLORS = "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split()
COLOR_ENDINGS = {"wool", "carpet", "terracotta", "concrete", "concrete_powder", "glazed_terracotta",
                 "stained_glass", "stained_glass_pane", "shulker_box", "bed", "candle", "banner"}
HINTS = {"建筑方块": "building_blocks", "自然方块": "natural_blocks", "功能方块": "functional_blocks",
         "彩色方块": "colored_blocks", "红石方块": "redstone_blocks", "工具与实用物品": "tools_and_utilities",
         "战斗用品": "combat", "食物与饮品": "food_and_drinks", "材料": "ingredients", "刷怪蛋": "spawn_eggs"}


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def walk(value):
    yield value
    if isinstance(value, dict):
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def loot_items(z: zipfile.ZipFile, paths: list[str]) -> set[str]:
    found, visited = set(), set()
    pending = list(paths)
    while pending:
        path = pending.pop()
        if path in visited or path not in z.namelist():
            continue
        visited.add(path)
        for node in walk(json.loads(z.read(path))):
            if not isinstance(node, dict):
                continue
            if node.get("type") == "minecraft:item":
                found.add(node["name"].removeprefix("minecraft:"))
            if node.get("type") == "minecraft:loot_table" and isinstance(node.get("value"), str):
                pending.append("data/" + node["value"].replace(":", "/loot_table/") + ".json")
    return found


def facts(jar: Path = JAR) -> dict:
    destination = DATA / "shop-facts-26.2.json"
    if destination.exists():
        result = load(destination)
        if hashlib.sha1(jar.read_bytes()).hexdigest() != result["jarSha1"]:
            raise SystemExit("Minecraft reference JAR differs from the reviewed 26.2 source")
        return result
    source = load(ROOT / ".tools" / "creative-categories-facts.json")
    entity_lines = (ROOT / ".tools" / "entity-types-bytecode.txt").read_text(encoding="utf-8-sig").splitlines()
    entities, category = {}, None
    for line in entity_lines:
        match = re.search(r"MobCategory\.([A-Z_]+):", line)
        if match:
            category = match[1]
        field = re.search(r"putstatic.*// Field ([A-Z_]+):Lnet/minecraft/world/entity/EntityType;", line)
        if field and category:
            entities[field[1].lower()] = category
            category = None
    assert entities["zombie"] == entities["piglin"] == "MONSTER"
    assert entities["cow"] == "CREATURE"
    with zipfile.ZipFile(jar) as z:
        valid = {n[len("assets/minecraft/items/"):-5] for n in z.namelist() if n.startswith("assets/minecraft/items/") and n.endswith(".json")}
        recipes = read_recipes(z, valid)
        cache = {}
        starter_items = set(STARTERS)
        for tag in ("saplings", "small_flowers", "tall_flowers"):
            starter_items.update(ingredient_options("#minecraft:" + tag, z, valid, cache))
        starter_items -= {"wither_rose", "torchflower", "pitcher_plant"}
        fishing = loot_items(z, ["data/minecraft/loot_table/gameplay/fishing.json"])
        monster = loot_items(z, ["data/minecraft/loot_table/entities/" + entity + ".json" for entity, group in entities.items() if group == "MONSTER"])
        # Raider captain bottles are a native runtime death drop, not an entity JSON entry.
        monster.add("ominous_bottle")
        book_tag_path = "data/minecraft/tags/enchantment/on_random_loot.json"
        def enchant_tag(path, seen=frozenset()):
            if path in seen:
                return set()
            out = set()
            for child in json.loads(z.read(path))["values"]:
                value = child if isinstance(child, str) else child["id"]
                if value.startswith("#"):
                    out.update(enchant_tag("data/" + value[1:].replace(":", "/tags/enchantment/") + ".json", seen | {path}))
                else:
                    out.add(value)
            return out
        fishing_books = {}
        for eid in sorted(enchant_tag(book_tag_path)):
            definition = json.loads(z.read("data/" + eid.replace(":", "/enchantment/") + ".json"))
            levels = set()
            # Native 30-level fishing on an enchantable(1) book: rounded power 26..36.
            for power in range(26, 37):
                for level in range(definition["max_level"], 0, -1):
                    low, high = definition["min_cost"], definition["max_cost"]
                    if low["base"] + low["per_level_above_first"] * (level - 1) <= power <= high["base"] + high["per_level_above_first"] * (level - 1):
                        levels.add(level)
                        break
            fishing_books[eid] = sorted(levels)
        result = {
            "minecraftVersion": "26.2", "jarSha1": source["sourceJarSha1"],
            "firstCreativeCategory": source["firstCategoryByDirectAndConfirmedDynamicItemId"],
            "categoryEvidence": "CreativeModeTabs displayItems bytecode; dynamic color/copper families are handled explicitly",
            "entityCategory": entities, "recipeOutputs": sorted({r["output"] for r in recipes}),
            "monsterDrops": sorted(monster), "fishingItems": sorted(fishing),
            "fishingBookLevels": fishing_books, "fishingBookEnchantability": 1,
            "plantStarters": sorted(starter_items | {"small_dripleaf", "firefly_bush"}),
        }
    write_json(destination, result)
    return result


def wood_block(item: str) -> bool:
    if item in WOOD_MISC:
        return True
    item = item.removeprefix("stripped_")
    return any(item.startswith(kind + "_") and item[len(kind) + 1:] in WOOD_ENDINGS for kind in WOOD_TYPES)


def classify(item: str, p: dict, f: dict) -> tuple[str, int, str, str]:
    native = f["firstCreativeCategory"].get("minecraft:" + item)
    if isinstance(native, dict):
        native = native.get("category") or native.get("id")
    tab = native or "minecraft:" + HINTS.get(p["categoryHint"], "ingredients")
    section = tab.removeprefix("minecraft:")
    if p["legacyTradeMode"] == "disabled":
        return "disabled", 10000, tab, "原禁交易／需显式变体"
    if item in {"stick", "petrified_oak_slab"}:
        return "disabled", 10000, tab, "明确删除的非方块木制品／不可生存取得装饰"
    if item.endswith("_spawn_egg"):
        entity = item.removesuffix("_spawn_egg")
        group = f["entityCategory"].get(entity)
        if group is None:
            raise ValueError("Unverified egg category: " + entity)
        if group == "MONSTER" and entity != "piglin":
            return "disabled", 10000, "minecraft:spawn_eggs", "敌对／中立怪物蛋禁交易，猪灵例外"
        return "buy_sell", 10000, "minecraft:spawn_eggs", "动物／友善中立生物或猪灵蛋"
    if item in {"end_portal_frame", "budding_amethyst", "ancient_debris"} or item.endswith("_smithing_template"):
        return "buy_sell", 10000, "minecraft:ingredients", "不可从零取得的推进／首件例外"
    if item in f["plantStarters"]:
        return "buy_sell", 1000000, "minecraft:food_and_drinks" if item in FARM_SEEDS else "minecraft:natural_blocks", "首种购买为旧价1000倍，回收独立"
    if item.endswith(("_log", "_stem")) and not item.startswith("stripped_") and item != "mushroom_stem":
        return "buy_sell", 10000, "minecraft:building_blocks", "原木原料可买可卖"
    if wood_block(item) or p["canonicalStack"]["itemId"] == "yumis_sky_island:shipping_chest":
        return "sell_only", 10000, "minecraft:building_blocks", "木材系方块仅收购（Q47覆盖原删除规则）"
    if ("copper" in item and item != "copper_ingot") or item.endswith("lightning_rod"):
        return "disabled", 10000, tab, "铜制品仅保留铜锭"
    if item in COMPRESSED or item.endswith(("_ore", "_nugget")):
        return "disabled", 10000, tab, "矿物压缩块／矿石／粒状衍生禁交易"
    if item in MINERALS:
        return "buy_sell", 10000, "minecraft:ingredients", "基础矿物／粉末例外"
    if item == "cobblestone":
        return "buy_sell", 10000, "minecraft:building_blocks", "明确保留的基础刷石原料"
    if item in {"netherite_scrap", "netherite_ingot"}:
        return "sell_only", 10000, "minecraft:ingredients", "由远古残骸自行熔炼／制作，仅收购"
    if any(item == color + "_" + ending for color in COLORS for ending in COLOR_ENDINGS):
        return "disabled", 10000, "minecraft:colored_blocks", "颜色方块整类禁交易"
    if item == "string":
        # Q46 explicitly retains string as a monster material; its first tab is redstone.
        section, tab = "ingredients", "minecraft:ingredients"
    if section in REMOVED_TABS or item in REDSTONE_DEVICES or (section == "redstone_blocks" and item not in SCULK):
        return "disabled", 10000, tab, "整类删除购买、收购和经济提示"
    if item in NATURAL_DERIVED or item.endswith("_leaves") or item.startswith("dead_") or item.startswith(("cut_", "chiseled_", "infested_")):
        return "disabled", 10000, tab, "种植／直接转化／切制衍生方块禁交易"
    if item in {"potion", "splash_potion", "lingering_potion"}:
        return "sell_only", 10000, "minecraft:food_and_drinks", "所有药水仅收购"
    if item in f["monsterDrops"]:
        return "buy_sell", 1000000, "minecraft:redstone_blocks" if item in SCULK else "minecraft:ingredients", "怪物掉落优先，仅特殊商店100倍新基础价购买"
    if item in SCULK:
        return "buy_sell", 10000, "minecraft:redstone_blocks", "保留不可合成的幽匿系列"
    if item in SUBSTRATES:
        return "buy_sell", 10000, "minecraft:natural_blocks", "首次生产所需自然基底"
    if item == "enchanted_book":
        enchantments = p["canonicalStack"]["componentPatch"].get("minecraft:stored_enchantments", {})
        if any(level in f["fishingBookLevels"].get(eid, []) for eid, level in enchantments.items()):
            return "sell_only", 10000, "minecraft:ingredients", "该附魔等级可由26.2钓鱼产生，仅收购"
        return "buy_sell", 10000, "minecraft:ingredients", "钓鱼无法产生的附魔等级，保留特殊供货"
    if item in CROP_OUTPUTS:
        return "sell_only", 10000, tab, "种植产物仅收购，购买对应首种"
    if item in f["fishingItems"]:
        return "sell_only", 10000, tab, "钓鱼产物仅收购"
    if item in RAW_FOOD:
        return "buy_sell", 10000, "minecraft:food_and_drinks", "无种子的原始食材"
    if item in f["recipeOutputs"]:
        return "sell_only", 10000, tab, "可自行制作，仅收购"
    if section == "food_and_drinks" and item not in RAW_FOOD:
        return "sell_only", 10000, tab, "食物购买以首种／原始材料为主"
    return "buy_sell", 10000, tab, "基础原料／无法制作的剩余物资"


def apply(cfg: Path, jar: Path = JAR, rebuild: bool = False):
    paths = {name: cfg / "shop" / (name + ".json") for name in ("products", "rarities", "shops", "item_levels")}
    data = {key: load(path) for key, path in paths.items()}
    if data["products"].get("shopRevision") == "materials-tasks-v3":
        if rebuild:
            raise SystemExit("Current v3 rules cannot be rebuilt with the legacy tool. Edit products.json directly.")
        print(json.dumps({"configRoot": str(cfg), "shopRevision": "materials-tasks-v3", "alreadyCurrent": True}))
        return
    if data["products"].get("shopRevision") == REVISION and not rebuild:
        print(json.dumps({"configRoot": str(cfg), "shopRevision": REVISION, "alreadyCurrent": True}))
        return
    f = facts(jar)
    # Native special death/spawn outputs outside entity loot-table JSON.
    f["monsterDrops"] = sorted(set(f["monsterDrops"]) | {"nether_star", "wither_rose"})
    f["plantStarters"] = sorted(set(f["plantStarters"]) | {"small_dripleaf", "firefly_bush"})
    f["runtimeDropNotes"] = "Raider captain ominous bottle, wither nether star, wither-kill wither rose are native runtime outputs"
    write_json(DATA / "shop-facts-26.2.json", f)
    products = data["products"]["products"]
    counts = Counter()
    for key, p in products.items():
        p.setdefault("legacyBaseValueCoins", p["baseValueCoins"])
        p.setdefault("legacyTradeMode", p["tradeMode"])
        p.setdefault("legacyRarity", data["rarities"]["itemRarities"][key])
        p["baseValueCoins"] = p["legacyBaseValueCoins"] * 10 if p["legacyBaseValueCoins"] is not None else None
        item = p["canonicalStack"]["itemId"].removeprefix("minecraft:")
        mode, multiplier, tab, reason = classify(item, p, f)
        p.update(tradeMode=mode, buyMultiplierBps=multiplier, shopCategory=tab, shopRule=reason)
        rarity = p["legacyRarity"]
        if mode == "buy_sell" and reason.startswith("怪物掉落") and rarity == "common":
            rarity = "rare"
            p["dailyStock"] = 64
        data["rarities"]["itemRarities"][key] = rarity
        counts[mode] += 1
    for data_file in data.values():
        data_file["shopRevision"] = REVISION
    shops = data["shops"]
    shops["market"].update(minimumCurrentBuyCoins=100, minimumCurrentBaseValueCoins=100)
    shops.update(saleDivisor=100, effectivePrice="max(100, floor(baseValue * marketMultiplier)) * qualityMultiplier * enchantmentMultipliers",
                 buyRule="floor(effectiveFullValue * product.buyMultiplierBps / 10000)",
                 sellRule="floor(sum(effectiveFullValue * durabilityMultiplier / saleDivisor * quota_segment_weight))")
    data["products"]["effectivePrice"] = shops["effectivePrice"]
    for key, path in paths.items():
        write_json(path, data[key])
    unlock_path = cfg / "automation" / "unlocks.json"
    unlocks = load(unlock_path)
    unlocks.setdefault("legacyCorePurchaseLevels", unlocks["corePurchaseLevels"])
    unlocks["corePurchaseLevels"] = {k: v for k, v in unlocks["legacyCorePurchaseLevels"].items() if products[k]["tradeMode"] in {"buy_sell", "buy_only"}}
    unlocks["shopRevision"] = REVISION
    write_json(unlock_path, unlocks)
    shipping_path = cfg / "automation" / "shipping_chest.json"
    shipping = load(shipping_path)
    shipping["baseValueCoins"] = products[shipping["itemId"]]["baseValueCoins"]
    write_json(shipping_path, shipping)
    print(json.dumps({"configRoot": str(cfg), "shopRevision": REVISION, "modes": counts}, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config-root", type=Path, default=DEFAULT_CFG)
    parser.add_argument("--jar", type=Path, default=JAR)
    parser.add_argument("--rebuild-rules", action="store_true", help="Explicitly regenerate this revision from preserved legacy prices")
    args = parser.parse_args()
    cfg = args.config_root.resolve()
    # This tool is an explicit offline migration. It never touches saves or the PCL directory.
    if cfg != DEFAULT_CFG.resolve() and ROOT.resolve() not in cfg.parents:
        parser.error("Only this project's config directories may be migrated")
    backup = ROOT / ".tools" / "shop-backups" / cfg.relative_to(ROOT)
    if not backup.exists():
        shutil.copytree(cfg, backup)
    apply(cfg, args.jar, args.rebuild_rules)


if __name__ == "__main__":
    main()
