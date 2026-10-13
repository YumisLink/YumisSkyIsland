"""Produce reviewable 26.2 economy data; no Minecraft runtime or third-party packages."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import zipfile
from collections import Counter
from fractions import Fraction
from pathlib import Path

from automation_defaults import CORE_LEVELS, MASS_PRICES, make_rules, validate_reference_math
from automation_views import render_automation

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "config" / "yumis_sky_island"
RANKS = ("common", "rare", "epic", "legendary")
RANK_LABELS = dict(zip(RANKS, ("普通", "稀有", "史诗", "传说")))
HEADER = {
    "schemaVersion": 1,
    "status": "review_draft",
    "minecraftVersion": "26.2",
    "registryVerified": False,
}
FORBIDDEN = {
    "air", "barrier", "light", "command_block", "chain_command_block",
    "repeating_command_block", "command_block_minecart", "structure_block",
    "structure_void", "jigsaw", "debug_stick", "knowledge_book", "test_block",
    "test_instance_block", "bedrock", "reinforced_deepslate", "spawner",
    "trial_spawner", "vault",
}
VARIANT_ONLY = {
    "enchanted_book", "potion", "splash_potion", "lingering_potion",
    "tipped_arrow", "firework_rocket", "firework_star", "written_book",
    "filled_map", "suspicious_stew", "ominous_bottle", "decorated_pot",
}
ANCHORS = {
    "oak_log": (40, "common", 0), "oak_planks": (10, "common", 0),
    "stick": (10, "common", 0), "oak_sapling": (30, "common", 0),
    "dirt": (10, "common", 0), "coarse_dirt": (20, "common", 0),
    "grass_block": (40, "common", 0), "moss_block": (40, "common", 3),
    "stone": (10, "common", 0), "cobblestone": (10, "common", 0),
    "sand": (60, "common", 3), "red_sand": (80, "common", 10),
    "gravel": (50, "common", 3), "clay_ball": (40, "common", 3),
    "clay": (160, "common", 3), "flint": (30, "common", 3),
    "water_bucket": (100, "common", 0), "lava_bucket": (250, "common", 0),
    "bucket": (900, "common", 3), "ice": (100, "common", 5),
    "coal": (60, "common", 0), "charcoal": (40, "common", 0),
    "coal_block": (540, "common", 0), "coal_ore": (240, "rare", 10),
    "raw_copper": (100, "common", 5), "copper_ingot": (100, "common", 5),
    "copper_nugget": (12, "common", 5), "copper_block": (900, "common", 5),
    "copper_ore": (400, "rare", 10),
    "raw_iron": (300, "common", 10), "iron_ingot": (300, "common", 10),
    "iron_nugget": (34, "common", 10), "iron_block": (2700, "common", 10),
    "iron_ore": (1200, "rare", 20),
    "raw_gold": (600, "common", 25), "gold_ingot": (600, "common", 25),
    "gold_nugget": (67, "common", 25), "gold_block": (5400, "common", 25),
    "gold_ore": (2400, "rare", 30),
    "redstone": (100, "common", 15), "redstone_block": (900, "common", 15),
    "redstone_ore": (400, "rare", 25),
    "lapis_lazuli": (150, "common", 15), "lapis_block": (1350, "common", 15),
    "lapis_ore": (600, "rare", 25), "quartz": (150, "common", 30),
    "nether_quartz_ore": (600, "rare", 35),
    "diamond": (10000, "rare", 35), "diamond_block": (90000, "rare", 35),
    "diamond_ore": (40000, "epic", 45), "emerald": (3000, "common", 25),
    "emerald_block": (27000, "common", 25), "emerald_ore": (12000, "epic", 35),
    "ancient_debris": (50000, "epic", 65),
    "netherite_scrap": (50000, "epic", 65),
    "netherite_ingot": (200000, "legendary", 75),
    "netherite_block": (1800000, "legendary", 75),
    "netherite_upgrade_smithing_template": (500000, "epic", 65),
    "blaze_rod": (5000, "rare", 30), "blaze_powder": (2500, "rare", 30),
    "nether_wart": (1000, "rare", 30), "ender_pearl": (2000, "common", 25),
    "ender_eye": (4500, "rare", 40),
    "obsidian": (600, "common", 25), "crying_obsidian": (900, "rare", 30),
    "end_portal_frame": (1000000, "epic", 55),
    "shulker_shell": (200000, "epic", 65),
    "elytra": (50000000, "legendary", 95),
    "dragon_egg": (100000000, "legendary", 100),
    "nether_star": (10000000, "legendary", 80),
    "wither_skeleton_skull": (300000, "epic", 50),
    "heavy_core": (10000000, "legendary", 85),
    "breeze_rod": (80000, "epic", 55),
    "heart_of_the_sea": (3000000, "epic", 55),
    "nautilus_shell": (5000, "rare", 30),
    "enchanted_golden_apple": (3000000, "epic", 65),
    "golden_apple": (4900, "common", 25),
    "trident": (2000000, "epic", 50), "totem_of_undying": (5000000, "epic", 60),
    "chorus_flower": (10000, "rare", 55), "chorus_fruit": (3000, "rare", 55),
    "wheat_seeds": (20, "common", 0), "wheat": (20, "common", 0),
    "beetroot_seeds": (30, "common", 3), "beetroot": (30, "common", 3),
    "carrot": (30, "common", 3), "potato": (30, "common", 3),
    "melon_seeds": (40, "common", 3), "pumpkin_seeds": (40, "common", 3),
    "sugar_cane": (40, "common", 3), "cactus": (60, "common", 5),
    "bamboo": (40, "common", 3), "sweet_berries": (30, "common", 3),
    "glow_berries": (100, "rare", 10), "cocoa_beans": (60, "common", 5),
    "pointed_dripstone": (150, "common", 10), "dripstone_block": (600, "common", 10),
    "sponge": (10000, "rare", 25), "amethyst_shard": (800, "rare", 15),
    "amethyst_block": (3200, "rare", 15),
    "slime_ball": (150, "common", 15), "honey_bottle": (100, "common", 5),
    "honeycomb": (100, "common", 5), "leather": (80, "common", 3),
    "bone": (50, "common", 3), "string": (30, "common", 3),
    "rotten_flesh": (10, "common", 0), "gunpowder": (100, "common", 10),
    "feather": (20, "common", 3), "egg": (20, "common", 3),
    "ink_sac": (80, "common", 5), "glow_ink_sac": (300, "rare", 15),
    "glowstone_dust": (100, "common", 15), "snowball": (20, "common", 3),
    "netherrack": (20, "common", 25), "soul_sand": (100, "common", 25),
    "soul_soil": (150, "rare", 25), "end_stone": (200, "rare", 55),
    "blackstone": (60, "common", 25), "basalt": (40, "common", 25),
    "deepslate": (60, "rare", 10), "cobbled_deepslate": (40, "rare", 10),
    "tuff": (40, "rare", 10), "calcite": (100, "rare", 15),
    "sulfur": (300, "rare", 20), "cinnabar": (3000, "epic", 40),
}
ANCHORS.update(MASS_PRICES)
# Every ore is a source anchor. Unpriced deepslate ores must not become cheap
# diamond/metal sources during the recipe cost relaxation.
for _ore in ("coal", "copper", "iron", "gold", "redstone", "lapis", "diamond", "emerald"):
    _price, _rarity, _level = ANCHORS[_ore + "_ore"]
    ANCHORS["deepslate_" + _ore + "_ore"] = (math.ceil(Fraction(_price * 5, 4)), _rarity, _level)
WOOD_LEVELS = {
    "oak": ("common", 0), "spruce": ("rare", 0), "birch": ("rare", 0),
    "acacia": ("rare", 5), "jungle": ("rare", 5), "dark_oak": ("rare", 8),
    "mangrove": ("rare", 10), "cherry": ("rare", 10),
    "pale_oak": ("epic", 30), "crimson": ("rare", 30), "warped": ("rare", 30),
}
ALIASES = {
    "redstone": "redstone_wire", "string": "tripwire",
    "powder_snow_bucket": "powder_snow", "wheat_seeds": "wheat",
    "cocoa_beans": "cocoa", "carrot": "carrots", "potato": "potatoes",
    "torchflower_seeds": "torchflower_crop", "pitcher_pod": "pitcher_crop",
    "beetroot_seeds": "beetroots", "sweet_berries": "sweet_berry_bush",
    "glow_berries": "cave_vines", "pumpkin_seeds": "pumpkin_stem",
    "melon_seeds": "melon_stem",
}
POTION_IDS = """awkward fire_resistance harming healing infested invisibility
leaping long_fire_resistance long_invisibility long_leaping long_night_vision
long_poison long_regeneration long_slow_falling long_slowness long_strength
long_swiftness long_turtle_master long_water_breathing long_weakness luck mundane
night_vision oozing poison regeneration slow_falling slowness strength
strong_harming strong_healing strong_leaping strong_poison strong_regeneration
strong_slowness strong_strength strong_swiftness strong_turtle_master swiftness
thick turtle_master water water_breathing weakness weaving wind_charged""".split()
ENCHANT_OVERRIDES = {
    "protection": [11000, 12000, 13500, 15000],
    "efficiency": [11000, 12000, 13500, 14500, 16000],
    "unbreaking": [11000, 12000, 13000],
    "fortune": [12000, 14500, 18000], "looting": [12000, 14500, 18000],
    "mending": [15000], "silk_touch": [18000],
    "wind_burst": [15000, 22000, 30000],
    "swift_sneak": [12500, 16000, 20000], "lunge": [11500, 13500, 16000],
    "binding_curse": [6000], "vanishing_curse": [6000],
}


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def config(**kwargs) -> dict:
    return dict(HEADER, **kwargs)


def ns(value: str) -> str:
    return value if ":" in value else "minecraft:" + value


def labels_from_assets(jar: Path, assets: Path, z: zipfile.ZipFile) -> dict:
    english = json.loads(z.read("assets/minecraft/lang/en_us.json"))
    manifest = json.loads(jar.with_suffix(".json").read_text(encoding="utf-8"))
    index = json.loads((assets / "indexes" / (manifest["assetIndex"]["id"] + ".json")).read_text(encoding="utf-8"))
    digest = index["objects"]["minecraft/lang/zh_cn.json"]["hash"]
    chinese = json.loads((assets / "objects" / digest[:2] / digest).read_text(encoding="utf-8"))
    return dict(english, **chinese)


def item_name(labels: dict, item: str) -> str:
    short = item.split(":")[-1]
    return labels.get("item.minecraft." + short, labels.get("block.minecraft." + short, short))


def intrinsic(item: str, blocks: set[str]) -> tuple[int, str, int, str]:
    if item in FORBIDDEN:
        return 0, "common", 0, "管理/不可开放物品，禁交易"
    if item in ANCHORS:
        price, rarity, level = ANCHORS[item]
        return price, rarity, level, "人工核查的基础价格与进度锚点"
    if item.endswith("_spawn_egg"):
        animal = item.removesuffix("_spawn_egg")
        if animal in {"ender_dragon", "wither", "giant", "illusioner", "warden", "creaking"}:
            return 0, "common", 0, "Boss或特殊机制实体，禁售"
        if animal in {"cow", "pig", "sheep", "chicken", "rabbit", "bee"}:
            return 10000, "common", 5, "普通农牧启动，原版也可自然获得"
        if animal == "blaze":
            return 2000000, "epic", 35, "无堡垒时只生成单只烈焰人，不会创建无限刷怪范围"
        if animal == "shulker":
            return 10000000, "epic", 65, "无末地城时的潜影贝启动，完全随机"
        if animal == "wither_skeleton":
            return 5000000, "epic", 50, "无堡垒时只生成单只凋灵骷髅，不会创建无限刷怪范围"
        return 300000, "rare", 25, "其他生物启动；仅在本队允许区域使用"
    if item.startswith("music_disc_"):
        return 300000, "epic", 40, "唱片收藏，来源取决于怪物掉落/结构"
    if item.endswith("_smithing_template"):
        return 200000, "epic", 50, "无结构时的模板收藏与复制启动"
    if item.endswith("_pottery_sherd"):
        return 20000, "rare", 20, "无遗迹时的陶片补链"
    if item.endswith("_ore"):
        return 1000, "rare", 20, "矿石方块本体无矿床；产物来源需分别判断"
    if item.startswith("netherite_"):
        return 500000, "legendary", 75, "终局制作，价格由完整最便宜路径修订"
    if item.startswith("diamond_"):
        return 30000, "rare", 35, "无矿床；以随机商店钻石自行制作，职业村民交易关闭"
    for wood, (rarity, level) in WOOD_LEVELS.items():
        if item.startswith(wood + "_") or item.startswith("stripped_" + wood + "_"):
            raw = ("log" in item or "stem" in item or "hyphae" in item or item.endswith("_wood"))
            price = (54 if item.endswith(("_wood", "_hyphae")) else 40) if raw else 30 if item.endswith(("_sapling", "_propagule")) else 10
            return price, rarity, level, "树种启动后可再生；派生制作按原版配方"
    if item in blocks:
        return 10, "common", 3, "建筑/功能方块；派生制作按原版配方"
    if item.endswith(("_pickaxe", "_axe", "_sword", "_shovel", "_hoe", "_spear",
                      "_helmet", "_chestplate", "_leggings", "_boots")):
        return 500, "common", 3, "装备，价格与解锁由原版配方推导"
    if item in {"potion", "splash_potion", "lingering_potion", "tipped_arrow", "enchanted_book"}:
        return 2000, "rare", 20, "只交易显式配置的标准组件变体"
    return 50, "common", 3, "候选普通材料/工具；未逐项核验的来源以运行数据为准"


def category(item: str, blocks: set[str]) -> str:
    if item.endswith("_spawn_egg") or item in {"spawner", "trial_spawner"}:
        return "刷怪蛋"
    if item.endswith(("_helmet", "_chestplate", "_leggings", "_boots", "_sword", "_spear")) or item in {"bow", "crossbow", "mace", "trident", "shield"}:
        return "战斗用品"
    if item.endswith(("_pickaxe", "_axe", "_shovel", "_hoe", "_boat", "_raft")) or item in {"elytra", "fishing_rod", "shears", "compass", "clock"}:
        return "工具与实用物品"
    if item in {"potion", "splash_potion", "lingering_potion", "tipped_arrow", "apple", "bread"} or any(t in item for t in ("cooked_", "beef", "porkchop", "carrot", "potato", "berries")):
        return "食物与饮品"
    if any(t in item for t in ("redstone", "piston", "hopper", "comparator", "repeater", "dispenser", "dropper", "crafter")):
        return "红石方块"
    if any(t in item for t in ("concrete", "terracotta", "wool", "stained_glass")):
        return "彩色方块"
    if item in blocks:
        if any(t in item for t in ("sapling", "leaves", "flower", "dirt", "grass", "ore", "moss", "log", "stem")):
            return "自然方块"
        if any(t in item for t in ("chest", "furnace", "table", "portal", "barrel", "beacon", "anvil", "bed", "campfire")):
            return "功能方块"
        return "建筑方块"
    return "材料"


def source_hint(item: str, rule: str) -> str:
    if item.startswith(("oak_", "stripped_oak_")):
        return "初始橡树/种植可再生"
    if item in {"cobblestone", "stone"}:
        return "水与岩浆刷石/烧炼"
    if item in {"raw_iron", "raw_copper", "raw_gold"}:
        return "原矿无自然矿床，商店补链；对应矿物锭的怪物/交易来源另算"
    if item in {"iron_ingot", "iron_nugget", "iron_block", "carrot", "potato"}:
        return "僵尸/铁傀儡或农场补链；原矿石另算"
    if item in {"copper_ingot", "copper_nugget", "copper_block"}:
        return "溺尸/对应加工补链"
    if item in {"redstone", "redstone_block", "glowstone_dust", "glass_bottle"}:
        return "女巫掉落/对应加工"
    if item in {"gold_ingot", "gold_nugget", "gold_block", "quartz", "blackstone", "gravel", "soul_sand"}:
        return "地狱刷怪/猪灵交易及对应加工"
    if item in {"emerald", "emerald_block", "totem_of_undying", "saddle"}:
        return "人工村庄袭击/村庄英雄赠礼；职业与流浪商人交易关闭"
    if item in {"lapis_lazuli", "lapis_block"}:
        return "村庄英雄牧师赠礼；交易关闭，不依赖自然矿床"
    if item in {"coal", "coal_block", "blaze_rod", "blaze_powder", "wither_skeleton_skull"}:
        return "纯空且无堡垒刷怪范围时不能默认量产；随机商店补链"
    if item in {"sulfur", "sulfur_spike", "potent_sulfur"}:
        return "26.2尖刺在硫磺下生长；4尖刺合1硫磺，可再生量产"
    if item in {"cinnabar", "spore_blossom"}:
        return "原版无默认再生生产链，纯空无自然结构/矿床；随机商店"
    if item in {"chorus_fruit", "popped_chorus_fruit"}:
        return "紫颂花+末地石启动后可再生；首株依赖随机商店"
    if item == "chorus_flower":
        return "末地石上生长/分枝可扩种，不限维度；首株依赖随机商店"
    if item == "nether_wart":
        return "灵魂沙上成熟收获后可再种，不限维度；首株随机商店"
    if item == "budding_amethyst":
        return "无自然晶洞时靠特殊商店供应；精准采集也无法回收，母块才会生长晶簇"
    if item in {"amethyst_shard", "amethyst_block"}:
        return "母块启动后可采晶簇生产；普通紫水晶块不生长"
    if item in {"prismarine_shard", "prismarine_crystals"}:
        return "无神殿结构刷怪范围，不能默认无限守卫者农场；特殊商店补链"
    if item == "dragon_breath":
        return "原末影龙战斗关闭，无默认采集来源；特殊商店补链"
    if item in {"shulker_shell", "shulker_box"} or item.endswith("_shulker_box"):
        return "首只潜影贝依赖随机商店；潜影弹命中繁殖后可再生，高启动难度"
    if item == "ender_pearl":
        return "末影人掉落/猪灵以物易物，可再生"
    if item in {"diamond", "diamond_ore", "ancient_debris", "netherite_scrap",
                "netherite_upgrade_smithing_template", "end_portal_frame",
                "blaze_rod", "shulker_shell", "elytra", "heavy_core"}:
        return "全空无结构时缺少原版初始来源；仅随机特殊商店补链"
    if item in {"grass_block", "oak_sapling", "water_bucket", "lava_bucket"}:
        return "常驻开局补给"
    return rule


def rarity_max(*values: str) -> str:
    return max(values, key=RANKS.index)


def read_recipes(z: zipfile.ZipFile, valid: set[str]) -> list[dict]:
    result = []
    for name in z.namelist():
        if not name.startswith("data/minecraft/recipe/") or not name.endswith(".json"):
            continue
        data = json.loads(z.read(name))
        output = data.get("result")
        if isinstance(output, str):
            output = {"id": output}
        if not isinstance(output, dict) or output.get("id", "").removeprefix("minecraft:") not in valid:
            continue
        typ = data["type"].split(":")[-1]
        ingredients = []
        if typ == "crafting_shaped":
            for symbol, count in Counter("".join(data["pattern"]).replace(" ", "")).items():
                ingredients += [data["key"][symbol]] * count
        elif typ == "crafting_shapeless":
            ingredients = data["ingredients"]
        elif typ == "crafting_transmute":
            ingredients = [data["input"], data["material"]]
        elif typ in {"stonecutting", "smelting", "blasting", "smoking", "campfire_cooking"}:
            ingredients = [data["ingredient"]]
        elif typ == "smithing_transform":
            ingredients = [data[k] for k in ("template", "base", "addition")]
        else:
            continue
        result.append({
            "recipeId": ns(name[len("data/minecraft/recipe/"):-5]),
            "output": output["id"].split(":")[-1],
            "count": output.get("count", 1),
            "ingredients": ingredients,
            "fuelCoins": Fraction(5) if typ in {"smelting", "blasting", "smoking"} else Fraction(0),
            "type": typ,
        })
    return result


def ingredient_options(value, z: zipfile.ZipFile, valid: set[str], cache: dict, seen=frozenset()) -> list[str]:
    if isinstance(value, list):
        return sorted({i for v in value for i in ingredient_options(v, z, valid, cache, seen)})
    if isinstance(value, dict):
        value = value.get("item") or ("#" + value["tag"] if "tag" in value else "")
    if not isinstance(value, str):
        return []
    if value.startswith("#"):
        tag = value[1:].split(":")[-1]
        if tag in cache:
            return cache[tag]
        if tag in seen:
            return []
        path = "data/minecraft/tags/item/" + tag + ".json"
        if path not in z.namelist():
            return []
        found = set()
        for child in json.loads(z.read(path))["values"]:
            if isinstance(child, dict):
                child = child["id"]
            found.update(ingredient_options(child, z, valid, cache, seen | {tag}))
        cache[tag] = sorted(found)
        return cache[tag]
    short = value.removeprefix("minecraft:")
    return [short] if short in valid else []


def durability_multiplier(remaining: int, maximum: int) -> Fraction:
    if maximum <= 0:
        return Fraction(1)
    if not 0 <= remaining <= maximum:
        raise ValueError("invalid durability")
    if 5 * remaining >= 4 * maximum:
        return Fraction(5 * remaining - 3 * maximum, 2 * maximum)
    if 2 * remaining >= maximum:
        return Fraction(5 * (2 * remaining - maximum), 6 * maximum)
    return Fraction(0)


def seed(jar: Path, assets: Path, overwrite: bool) -> None:
    if (CFG / "shop" / "products.json").exists() and not overwrite:
        raise SystemExit("Draft already exists. Use --render-only to retain edits; --overwrite-draft explicitly reseeds.")
    with zipfile.ZipFile(jar) as z:
        version = json.loads(z.read("version.json"))
        if version["id"] != "26.2":
            raise SystemExit("This draft requires Minecraft 26.2.")
        labels = labels_from_assets(jar, assets, z)
        valid = {n[len("assets/minecraft/items/"):-5] for n in z.namelist() if n.startswith("assets/minecraft/items/") and n.endswith(".json")}
        blocks = {n[len("assets/minecraft/blockstates/"):-5] for n in z.namelist() if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")}
        recipes = read_recipes(z, valid)
        compiled = []
        cache = {}
        for recipe in recipes:
            options = [ingredient_options(i, z, valid, cache) for i in recipe["ingredients"]]
            if options and all(options):
                compiled.append(dict(recipe, options=options))
        native_compiled_count = len(compiled)
        # Oxidation/stripping are vanilla world operations rather than recipe files.
        # A weathered block must not be treated as a new 10-coin raw source.
        for item in sorted(valid):
            unwaxed = item.removeprefix("waxed_")
            base = re.sub(r"^(exposed_|weathered_|oxidized_)", "", unwaxed)
            if base == "copper":
                base = "copper_block"
            if item != base and ("copper" in base or base == "lightning_rod") and base in valid:
                ingredients = [[base]] + ([["honeycomb"]] if item.startswith("waxed_") else [])
            elif item.startswith("stripped_") and item.removeprefix("stripped_") in valid:
                ingredients = [[item.removeprefix("stripped_")]]
            else:
                continue
            compiled.append({"recipeId": "yumis_sky_island:vanilla_transform/" + item,
                             "output": item, "count": 1, "options": ingredients,
                             "fuelCoins": Fraction(0), "type": "world_transform"})
        initial = {i: intrinsic(i, blocks) for i in valid}
        # A crafted output's fallback price must not act as a cheap raw-material source.
        recipe_outputs = {r["output"] for r in compiled}
        costs = {
            i: Fraction(initial[i][0]) for i in valid
            if initial[i][0] > 0 and (i in ANCHORS or i not in recipe_outputs)
        }
        cheapest = {}
        for fallback_pass in range(2):
            if fallback_pass:
                for item in valid:
                    if item not in costs and initial[item][0] > 0:
                        costs[item] = Fraction(initial[item][0])
            for _ in range(160):
                changed = False
                for recipe in compiled:
                    choices = [min((i for i in opts if i in costs),
                                   key=lambda i: (costs[i], RANKS.index(initial[i][1]), initial[i][2], i),
                                   default=None) for opts in recipe["options"]]
                    if any(i is None for i in choices) or initial[recipe["output"]][0] <= 0:
                        continue
                    value = (sum((costs[i] for i in choices), Fraction(0)) + recipe["fuelCoins"]) / recipe["count"]
                    if recipe["output"] not in costs or value < costs[recipe["output"]] - Fraction(1, 10000):
                        costs[recipe["output"]] = value
                        cheapest[recipe["output"]] = (recipe, choices)
                        changed = True
                if not changed:
                    break
        # Recipe output prices use the cheapest entire manufacturing path, not just retail ingredients.
        prices = {i: (initial[i][0] if i in ANCHORS else max(10, math.ceil(costs[i]))) for i in costs}
        rarity = {i: initial[i][1] for i in valid}
        levels = {i: initial[i][2] for i in valid}
        for _ in range(32):
            changed = False
            for output, (_, choices) in cheapest.items():
                if output in ANCHORS:
                    continue
                tier = rarity_max(initial[output][1], *(rarity[i] for i in choices))
                level = max(initial[output][2], *(levels[i] for i in choices))
                if tier != rarity[output] or level != levels[output]:
                    rarity[output], levels[output] = tier, level
                    changed = True
            if not changed:
                break
        # These are purchase gates only; actual vanilla crafting/generation is unrestricted.
        for item, level in CORE_LEVELS.items():
            if item in levels:
                levels[item] = level
        curse = set(json.loads(z.read("data/minecraft/tags/enchantment/curse.json"))["values"])
        treasure = set(json.loads(z.read("data/minecraft/tags/enchantment/treasure.json"))["values"])
        enchants = {}
        for path in sorted(z.namelist()):
            if not path.startswith("data/minecraft/enchantment/") or not path.endswith(".json"):
                continue
            key = path[len("data/minecraft/enchantment/"):-5]
            data = json.loads(z.read(path))
            values = ENCHANT_OVERRIDES.get(key, [10000 + 1000 * (i + 1) for i in range(data["max_level"])])
            enchants[ns(key)] = {
                "name": labels.get("enchantment.minecraft." + key, key),
                "maxLevel": data["max_level"],
                "levelMultiplierBps": {str(i + 1): v for i, v in enumerate(values)},
                "isCurse": ns(key) in curse,
                "isTreasure": ns(key) in treasure,
                "supportedItems": data.get("supported_items"),
                "exclusiveSet": data.get("exclusive_set"),
            }
        products, item_rarity, item_levels = {}, {}, {}
        for item in sorted(valid):
            price, _, _, rule = initial[item]
            disabled = price == 0 or item in VARIANT_ONLY
            canonical = {"itemId": ns(item), "componentPatch": {}}
            stock = 64 if rarity[item] == "rare" else 16 if rarity[item] == "epic" else 1
            if item.endswith("_spawn_egg") or item in {"elytra", "dragon_egg", "heavy_core", "trident", "totem_of_undying", "nether_star"}:
                stock = 1
            if item == "end_portal_frame":
                stock = 12
            if item in {"netherite_ingot", "netherite_scrap", "ancient_debris", "shulker_shell"}:
                stock = 16
            weight = 15 if item in {"diamond", "blaze_rod", "nether_wart", "end_portal_frame", "netherite_upgrade_smithing_template", "ancient_debris", "shulker_shell", "chorus_flower", "budding_amethyst"} else 1
            products[ns(item)] = {
                "productId": ns(item), "canonicalStack": canonical,
                "name": item_name(labels, item), "categoryHint": category(item, blocks),
                "baseValueCoins": prices.get(item),
                "tradeMode": "disabled" if disabled else "buy_sell",
                "acquisitionNote": source_hint(item, rule),
                "dailyStock": stock, "offerWeight": weight,
                "priceOrigin": "anchor" if item in ANCHORS else "vanilla_transform" if item in cheapest and cheapest[item][0]["type"] == "world_transform" else "recipe_path" if item in cheapest else "category_draft",
            }
            item_rarity[ns(item)] = rarity[item]
            item_levels[ns(item)] = levels[item]
        for enchant_id, definition in enchants.items():
            for lv in range(1, definition["maxLevel"] + 1):
                key = "minecraft:enchanted_book__" + enchant_id.split(":")[-1] + "_" + str(lv)
                high = definition["isTreasure"] and not definition["isCurse"]
                tier = "legendary" if enchant_id.endswith("wind_burst") else "epic" if high else "rare"
                products[key] = {
                    "productId": key, "name": definition["name"] + " " + str(lv) + " 附魔书",
                    "canonicalStack": {"itemId": "minecraft:enchanted_book", "componentPatch": {"minecraft:stored_enchantments": {enchant_id: lv}}},
                    "categoryHint": "材料", "baseValueCoins": 2000,
                    "tradeMode": "disabled" if definition["isCurse"] else "buy_sell",
                    "acquisitionNote": "附魔台/随机商店；宝藏附魔缺失来源由特殊商店补链，村民交易关闭",
                    "dailyStock": 1 if high else 4, "offerWeight": 1,
                    "priceOrigin": "standard_enchantment_variant",
                }
                item_rarity[key] = tier
                item_levels[key] = 85 if enchant_id.endswith("wind_burst") else 50 if high else min(50, 10 + lv * 5)
        for item in ("potion", "splash_potion", "lingering_potion", "tipped_arrow"):
            for potion in POTION_IDS:
                if item == "tipped_arrow" and potion in {"water", "awkward", "mundane", "thick"}:
                    continue
                key = "minecraft:" + item + "__" + potion
                special = potion in {"luck", "wind_charged", "weaving", "oozing", "infested"}
                base_effect = potion.removeprefix("long_").removeprefix("strong_")
                label_key = "item.minecraft." + item + ".effect." + base_effect
                label = labels.get(label_key, item_name(labels, item) + " [" + base_effect + "]")
                if potion.startswith("long_"):
                    label += "（延长）"
                elif potion.startswith("strong_"):
                    label += "（强化）"
                products[key] = {
                    "productId": key, "name": label,
                    "canonicalStack": {"itemId": ns(item), "componentPatch": {"minecraft:potion_contents": {"potion": ns(potion)}}},
                    "categoryHint": "战斗用品" if item == "tipped_arrow" else "食物与饮品",
                    "baseValueCoins": 100 if potion == "water" else 500 if potion in {"awkward", "mundane", "thick"} else 100000 if special else 6000 if potion.startswith(("long_", "strong_")) else 3000,
                    "tradeMode": "buy_sell", "acquisitionNote": "46种真实药水ID的标准变体；酿造链须在运行时复核",
                    "dailyStock": 16, "offerWeight": 1, "priceOrigin": "standard_potion_variant",
                }
                item_rarity[key] = "legendary" if potion == "luck" else "epic" if special else "rare" if potion != "water" else "common"
                item_levels[key] = 75 if special else 35 if potion.startswith(("long_", "strong_")) else 25 if potion not in {"water", "awkward", "mundane", "thick"} else 10
        for amplifier in range(5):
            key = "minecraft:ominous_bottle__" + str(amplifier + 1)
            products[key] = {
                "productId": key, "name": "灾厄之瓶 " + str(amplifier + 1),
                "canonicalStack": {"itemId": "minecraft:ominous_bottle", "componentPatch": {"minecraft:ominous_bottle_amplifier": amplifier}},
                "categoryHint": "食物与饮品", "baseValueCoins": 2000 * (amplifier + 1),
                "tradeMode": "buy_sell", "acquisitionNote": "非袭击队长掉落；交易关闭后仍可启动人工村庄袭击",
                "dailyStock": 8, "offerWeight": 5, "priceOrigin": "standard_ominous_variant",
            }
            item_rarity[key], item_levels[key] = "rare", CORE_LEVELS["ominous_bottle"]
        shipping_value = sum(products[ns(item)]["baseValueCoins"] * count
                             for item, count in (("iron_ingot", 4), ("redstone", 4), ("chest", 1)))
        shipping_id = "yumis_sky_island:shipping_chest"
        products[shipping_id] = {
            "productId": shipping_id, "canonicalStack": {"itemId": shipping_id, "componentPatch": {}},
            "name": "出货箱", "categoryHint": "功能方块", "baseValueCoins": shipping_value,
            "tradeMode": "sell_only", "acquisitionNote": "4铁锭+4红石+1普通箱子合成；红石上升沿出售箱内物品",
            "dailyStock": 1, "offerWeight": 1, "priceOrigin": "custom_recipe",
        }
        item_rarity[shipping_id], item_levels[shipping_id] = "common", 0
        provenance = {
            "jarName": jar.name, "jarSha1": hashlib.sha1(jar.read_bytes()).hexdigest(),
            "javaVersion": version["java_version"], "protocolVersion": version["protocol_version"],
            "itemCandidateSource": "assets/minecraft/items",
            "candidateCount": len(valid), "vanillaRecipeCount": sum(n.startswith("data/minecraft/recipe/") and n.endswith(".json") for n in z.namelist()),
            "parsedRecipeCount": native_compiled_count, "worldTransformCostPaths": len(compiled) - native_compiled_count,
            "enchantmentCount": len(enchants),
            "potionCount": len(POTION_IDS),
            "customProductCount": 1,
            "verificationRequired": ["BuiltInRegistries.ITEM", "Item.BY_BLOCK", "BuiltInRegistries.POTION", "world enchantment registry", "CreativeModeTabs"],
        }
        write_json(CFG / "shop" / "products.json", config(source=provenance, products=products))
        write_json(CFG / "shop" / "item_levels.json", config(
            levelKind="current_party_island_level", minLevel=0, maxLevel=100,
            unlockPolicy="current_level_required_for_purchase", sellRequiresUnlock=False,
            itemLevels=item_levels))
        write_json(CFG / "shop" / "rarities.json", config(
            independentOfProfessionQuality=True,
            definitions={
                "common": {"name": "普通", "regularAllowed": True, "dailyWeight": 0, "hiddenWeight": 0},
                "rare": {"name": "稀有", "regularAllowed": False, "dailyWeight": 70, "hiddenWeight": 15},
                "epic": {"name": "史诗", "regularAllowed": False, "dailyWeight": 25, "hiddenWeight": 50},
                "legendary": {"name": "传说", "regularAllowed": False, "dailyWeight": 5, "hiddenWeight": 35},
            }, itemRarities=item_rarity))
        write_json(CFG / "shop" / "enchantments.json", config(
            unit="basis_points_10000_equals_1", combineRule="multiply_all_then_round_once",
            unknownEnchantmentPolicy="refuse_automatic_sale",
            rejectAboveVanillaMaxLevel=True, rejectIncompatibleCombinations=True,
            entries=enchants))
        write_json(CFG / "shop" / "shops.json", config(
            market={"ticksPerEpoch": 24000, "clockSource": "effective_server_game_ticks",
                    "anchorToBasePrice": True, "minPriceBps": 9000, "maxPriceBps": 11000,
                    "minimumCurrentBuyCoins": 10, "sharedQuotesAcrossParties": True},
            daily={"scope": "party", "slots": 12, "onlyEligibleAtRollTime": True,
                   "sampling": "weighted_rarity_then_weighted_product_without_replacement",
                   "limitSharedAcrossParty": True, "unusedStockCarriesOver": False},
            hidden={"chanceBps": 500, "replacesDaily": True, "slots": 16},
            stockDefaults={"rare": 64, "epic": 16, "legendary": 1},
            stockSource="products.dailyStock",
            purchaseQuality="ordinary", dailyPriceMultiplierBps=10000,
            pity={"enabled": False},
            stableStateKey=["partyId", "marketEpoch"],
            rerollTriggers={"login": False, "reset": False, "levelChange": False,
                            "membershipChange": False, "sleep": False, "timeCommand": False},
            effectivePrice="max(10, floor(baseValue * marketMultiplier)) * qualityMultiplier * enchantmentMultipliers",
            sellRule="floor(sum(effectiveFullValue * durabilityMultiplier / 10 * quota_segment_weight))",
            saleQuotaConfig="shop/sale_quotas.json", rounding="once_per_transaction_after_all_lines",
            durability={"unit": "remaining_fraction", "points": [
                {"remainingBps": 10000, "valueBps": 10000},
                {"remainingBps": 8000, "valueBps": 5000},
                {"remainingBps": 5000, "valueBps": 0}],
                "interpolation": "piecewise_linear", "below50ValueBps": 0,
                "nonDamageableValueBps": 10000}))
        score_blocks = {}
        zero = {"air", "cave_air", "void_air", "water", "lava", "nether_portal", "end_portal", "end_gateway", "fire", "soul_fire", "moving_piston", "piston_head"}
        weights = {"iron_block": 20, "gold_block": 40, "emerald_block": 60,
                   "diamond_block": 100, "netherite_block": 1000,
                   "beacon": 500, "ancient_debris": 150, "end_portal_frame": 0}
        for block in sorted(blocks):
            if block in {"item_frame", "glow_item_frame"}:
                continue  # These have block-state rendering assets but are hanging entities, not registered blocks.
            weight = 0 if block in zero or block in FORBIDDEN else weights.get(block, 5 if ("log" in block or block.endswith(("_stem", "_wood", "_hyphae"))) else 1)
            score_blocks[ns(block)] = {"basePoints": weight, "tierProfile": "default", "scoreGroup": ns(block)}
        score_blocks[shipping_id] = {"basePoints": 5, "tierProfile": "default", "scoreGroup": shipping_id}
        write_json(CFG / "islands" / "scoring.json", config(
            dimension="minecraft:overworld", source="current_world_stock_not_lifetime_placements",
            pointScale=10000,
            tiers={"default": [
                {"upToCount": 64, "multiplierBps": 10000},
                {"upToCount": 320, "multiplierBps": 2500},
                {"upToCount": 2048, "multiplierBps": 500},
                {"upToCount": None, "multiplierBps": 100}]},
            blocks=score_blocks,
            placementAliasHints={ns(i): ns(b) for i, b in ALIASES.items()},
            placementMappingAuthority="runtime_Item.BY_BLOCK_and_item_use_handlers"))
        write_json(CFG / "islands" / "levels.json", config(
            maxLevel=100, keepRawPointsAboveMax=True,
            thresholds=[{"level": i, "requiredPoints": 300 * i} for i in range(101)],
            legacy30LevelMapping="new_threshold(L)=old_top_score*L/100",
            chapterLevelRequirements=[0, 3, 10, 27, 50, 100],
            chapterResearchCoins=[0, 2000, 20000, 100000, 1000000, 10000000]))
        write_json(CFG / "islands" / "refresh.json", config(
            globalComputeBudgetMicrosPerTick=1000, maxSectionsPerTick=2,
            maxNewChunkRequestsPerTick=1, maxConcurrentChunkRequests=2,
            forceGenerateMissingChunks=False, blockMainThreadOnDisk=False,
            onlyExistingChunks=True, maxJobsPerParty=1,
            duplicateRequests="reuse_existing_job", queue="round_robin_across_parties",
            captureConcurrentBlockChanges=True, progressUpdateTicks=100,
            checkpointOnChunkCompletion=True, resumeAfterRestart=True,
            publishNewScore="after_consistent_scan_complete",
            slowTickPolicy="reduce_budget_and_pause_new_load_requests"))
        write_json(CFG / "products" / "quality.json", config(
            independentOfItemRarity=True, shopsOnlyOrdinary=True,
            grades=[
                {"id": "ordinary", "name": "普通", "valueBps": 10000, "durabilityBps": 10000, "attackAndMiningBonusBps": 0},
                {"id": "fine", "name": "精良", "valueBps": 15000, "durabilityBps": 11500, "attackAndMiningBonusBps": 200},
                {"id": "rare", "name": "稀有", "valueBps": 20000, "durabilityBps": 13000, "attackAndMiningBonusBps": 500},
                {"id": "epic", "name": "史诗", "valueBps": 30000, "durabilityBps": 15000, "attackAndMiningBonusBps": 800},
                {"id": "legendary", "name": "传说", "valueBps": 50000, "durabilityBps": 17500, "attackAndMiningBonusBps": 1000}],
            professions=["blacksmith", "stonemason", "artisan", "chef", "farmer", "hunter"],
            allCanBeLearned=True, maxProfessionLevel=100,
            diamondPickaxeQualityUnlockLevel=65,
            automatedProductionQuality="ordinary", automatedProfessionXp=0,
            zeroXpRecipeKinds=["lossless_pack_unpack", "copy", "repair", "preview"],
            qualityRollTiming="once_after_material_consumption_before_output_transfer"))
        rules = make_rules(HEADER, products)
        for key, relative in {
            "sale_quotas": "shop/sale_quotas.json", "server_rules": "server_rules.json",
            "unlocks": "automation/unlocks.json", "production": "automation/production.json",
            "shipping": "automation/shipping_chest.json",
        }.items():
            write_json(CFG / relative, rules[key])
        write_json(ROOT / "docs" / "SOURCE_MANIFEST.json", config(**provenance))


def read_configs() -> dict:
    paths = {
        "products": CFG / "shop" / "products.json",
        "levels": CFG / "shop" / "item_levels.json",
        "rarities": CFG / "shop" / "rarities.json",
        "enchantments": CFG / "shop" / "enchantments.json",
        "shops": CFG / "shop" / "shops.json",
        "scoring": CFG / "islands" / "scoring.json",
        "island_levels": CFG / "islands" / "levels.json",
        "refresh": CFG / "islands" / "refresh.json",
        "quality": CFG / "products" / "quality.json",
        "sale_quotas": CFG / "shop" / "sale_quotas.json",
        "server_rules": CFG / "server_rules.json",
        "unlocks": CFG / "automation" / "unlocks.json",
        "production": CFG / "automation" / "production.json",
        "shipping": CFG / "automation" / "shipping_chest.json",
    }
    return {k: json.loads(p.read_text(encoding="utf-8")) for k, p in paths.items()}


def full_base_value(product: dict, entries: dict) -> Fraction:
    value = Fraction(product["baseValueCoins"])
    for eid, level in product["canonicalStack"]["componentPatch"].get("minecraft:stored_enchantments", {}).items():
        value *= Fraction(entries[eid]["levelMultiplierBps"][str(level)], 10000)
    return value


def validate(data: dict) -> dict:
    products = data["products"]["products"]
    rarities, levels = data["rarities"]["itemRarities"], data["levels"]["itemLevels"]
    assert products.keys() == rarities.keys() == levels.keys(), "Product metadata keys differ."
    active = {k: p for k, p in products.items() if p["tradeMode"] != "disabled"}
    divisor = data["shops"].get("saleDivisor", 10)
    floor = data["shops"]["market"].get("minimumCurrentBaseValueCoins", 10)
    assert isinstance(divisor, int) and divisor > 0
    for key, product in products.items():
        assert key == product["productId"]
        assert 0 <= levels[key] <= 100
        assert rarities[key] in RANKS
        if key in active:
            assert isinstance(product["baseValueCoins"], int) and product["baseValueCoins"] >= floor
            assert product["dailyStock"] > 0 and product["offerWeight"] > 0
            assert isinstance(product.get("buyMultiplierBps", 10000), int) and product.get("buyMultiplierBps", 10000) > 0
    for eid, entry in data["enchantments"]["entries"].items():
        assert set(entry["levelMultiplierBps"]) == {str(i) for i in range(1, entry["maxLevel"] + 1)}, eid
        assert all(isinstance(v, int) and v >= 0 for v in entry["levelMultiplierBps"].values())
    thresholds = data["island_levels"]["thresholds"]
    assert len(thresholds) == 101
    assert all(t["level"] == i and isinstance(t["requiredPoints"], int) for i, t in enumerate(thresholds))
    assert thresholds[0]["requiredPoints"] == 0
    assert all(a["requiredPoints"] < b["requiredPoints"] for a, b in zip(thresholds, thresholds[1:]))
    definitions = data["rarities"]["definitions"]
    assert definitions["common"]["dailyWeight"] == definitions["common"]["hiddenWeight"] == 0
    for mode in ("dailyWeight", "hiddenWeight"):
        assert all(isinstance(d[mode], int) and d[mode] >= 0 for d in definitions.values())
        assert sum(d[mode] for d in definitions.values()) > 0
    assert data["shops"]["pity"]["enabled"] is False
    assert data["shops"]["hidden"]["chanceBps"] == 500
    assert data["shops"]["purchaseQuality"] == "ordinary"
    assert not any(data["shops"]["rerollTriggers"].values())
    assert data["refresh"]["globalComputeBudgetMicrosPerTick"] <= 1000
    quota_math = validate_reference_math()
    quotas = data["sale_quotas"]
    assert quotas["scope"] == "party" and quotas["epochSource"] == "shop_marketEpoch"
    assert [tier["upToUnits"] for tier in quotas["tiers"]] == [1000, 10000, None]
    assert [tier["saleMultiplierBps"] for tier in quotas["tiers"]] == [10000, 5000, 1000]
    assert quotas["counterUsesExactRationals"] and quotas["sumAllLinesThenFloorCoins"]
    for item_id, entry in quotas["itemEquivalents"].items():
        assert item_id in products and entry["quotaGroup"] in quotas["groups"], item_id
        assert isinstance(entry["numerator"], int) and isinstance(entry["denominator"], int)
        assert entry["numerator"] > 0 and entry["denominator"] > 0
    for group in quotas["groups"].values():
        assert group["unitItemId"] in products
    for item_id, min_level in data["unlocks"]["corePurchaseLevels"].items():
        assert levels[item_id] == min_level, item_id
    assert data["unlocks"]["purchaseOnly"]
    assert not any(data["unlocks"][k] for k in ("blockCrafting", "blockPlacement", "blockNaturalGeneration"))
    rules, shipping = data["server_rules"], data["shipping"]
    assert not rules["villagerTradingEnabled"] and not rules["wanderingTraderTradingEnabled"]
    assert all(rules[k] for k in ("piglinBarteringEnabled", "villagerBreedingEnabled", "villagerWorkAiEnabled", "ironGolemSpawningEnabled"))
    assert shipping["trigger"] == "redstone_rising_edge_only" and not shipping["scanOnContinuousPower"]
    assert shipping["inventorySlots"] == 27 and not shipping["doubleChestEnabled"]
    assert shipping["normalPricesAndSaleQuotas"] and shipping["profits"] == "party_wallet"
    assert shipping["islandEpochScope"] == "party_and_dimension" and shipping["resetInvalidatesEpoch"] == "overworld_only"
    assert products[shipping["itemId"]]["baseValueCoins"] == shipping["baseValueCoins"]
    examples = [(100, Fraction(1)), (95, Fraction(7, 8)), (90, Fraction(3, 4)),
                (80, Fraction(1, 2)), (70, Fraction(1, 3)), (65, Fraction(1, 4)),
                (60, Fraction(1, 6)), (50, Fraction(0)), (49, Fraction(0))]
    for remaining, wanted in examples:
        assert durability_multiplier(remaining, 100) == wanted
    prior = Fraction(0)
    for remaining in range(101):
        current = durability_multiplier(remaining, 100)
        assert current >= prior and 0 <= current <= 1
        prior = current
    # Rounding must happen once, after grade, all enchantments, durability, and the sell divisor.
    sample = Fraction(1000 * divisor) * 2 * Fraction(3, 2) * Fraction(13, 10) * Fraction(3, 2) * Fraction(3, 4) / divisor
    assert math.floor(sample) == 4387
    ordinary, special, sell_only = [], [], []
    for key, product in active.items():
        if product["tradeMode"] == "sell_only":
            sell_only.append(key)
        else:
            (ordinary if rarities[key] == "common" else special).append(key)
    for lv in (0, 3, 10, 27, 50, 75, 95, 100):
        assert any(levels[k] <= lv for k in special), f"No eligible daily goods at level {lv}"
    return {
        "catalogRows": len(products), "activeRows": len(active),
        "shopRevision": data["products"].get("shopRevision", "legacy"),
        "saleDivisor": divisor, "minimumBaseCoins": floor,
        "purchasePremiumsIndependent": True,
        "ordinaryRows": len(ordinary), "dailyRows": len(special),
        "sellOnlyRows": len(sell_only),
        "quotaGroups": len(quotas["groups"]), "quotaEquivalentRows": len(quotas["itemEquivalents"]),
        "automationPurchaseGates": len(data["unlocks"]["corePurchaseLevels"]),
        "quotaReferenceMath": quota_math,
        "disabledRows": len(products) - len(active),
        "enchantments": len(data["enchantments"]["entries"]),
        "rarityCounts": dict(Counter(rarities[k] for k in active)),
        "priceOriginCounts": dict(Counter(p["priceOrigin"] for p in active.values())),
        "source": data["products"]["source"],
        "durabilityExamples": [{"remainingPercent": r, "valueMultiplier": str(v)} for r, v in examples],
        "checksPassed": ["metadata_references", "100_level_thresholds", "weighted_shop_config",
                         "no_pity_no_reroll", "stock_and_min_prices", "43_enchantment_tables",
                         "durability_boundaries_monotonicity", "single_final_rounding",
                         "party_resource_equivalent_quotas", "quota_cross_boundary_rational_math",
                         "core_purchase_only_gates", "villager_wandering_disabled_piglin_preserved",
                         "shipping_chest_rising_edge_configuration"],
        "notYetVerified": ["live_registry", "real_creative_categories", "complete_recipe_arbitrage",
                           "runtime_sale_matching", "refresh_latency", "networking_and_restart",
                           "80_150_hour_pacing", "late_game_income_rate", "trading_menu_bypass",
                           "redstone_hopper_interaction", "shipping_atomic_restart_recovery"],
    }


def escape(value) -> str:
    return str(value).replace("|", "\\|").replace("\n", " ")


def render(data: dict, report: dict) -> None:
    products = data["products"]["products"]
    rarities = data["rarities"]["itemRarities"]
    levels = data["levels"]["itemLevels"]
    enchants = data["enchantments"]["entries"]
    scores = data["scoring"]["blocks"]
    divisor = data["shops"].get("saleDivisor", 10)
    minimum = data["shops"]["market"].get("minimumCurrentBaseValueCoins", 10)
    price_lines = [
        "# Minecraft 26.2 原版商品价目表（审阅草案）", "",
        "此表为可编辑价格基线与平衡草案。启用商品的26.2注册表/组件已复核，源码与客户端测试的覆盖范围见 docs/IMPLEMENTATION_STATUS.md。", "",
        "## 口径", "",
        f"- 正常收购为当前基础估值的1/{divisor}，购买另乘逐物品buyMultiplierBps。每日同队同类原料前1000份正常、1001～10000份半价、超过10000份十分之一；整笔交易最终只取整一次。",
        f"- 每日相对基准价随机±10%，当前基础价值最低{minimum}金币。购买加价不进入收购估值。",
        "- V3：基础原木、木板、原始石材可买卖；加工建筑品删除价格。62项首种首次整批购买加价，商店／悬赏／任务首获后恢复正常购价；35项怪物材料继续固定加价。完整决定见docs/FEATURE_REVISION_REVIEW.md。",
        "- 普通稀有度常驻标准商店；稀有、史诗、传说仅进入每队每日/隐藏随机池。表内买价不表示今天一定有售。",
        "- 所有商店出售普通制造品质。高制造品质、附魔、损耗在收购时另外乘算。",
        "- 每日池完全随机无保底、全队共享限量库存；隐藏替代每日的概率为5%。",
        f"- 普通无损基础价值最低{minimum}；损耗后价值可以为0，50%耐久时已经为0。",
        "- 标准附魔书/药水按显式组件变体列出。未知组件、装有物品的容器、未配置特殊形态不自动收购。",
        "- 类别是离线提示，游戏中以原版CreativeModeTabs为准。仅材料缺矿床不等于产物不可再生。",
        "- 职业村民及流浪商人交易关闭；猪灵以物易物保留。购买等级只限制商店，不阻止原版生产/合成。",
        "- 生电批量产物采用较低基价；数量按sale_quotas.json换算，同组压缩、氧化、颜色和装备不会重置额度。",
        "- priceOrigin=category_draft 的行是分类兜底，不是已逐项验证的获取结论；运行与配方套利校验仍待实施。", "",
        "## 可编辑数据", "",
        "价格：products.json；稀有度：rarities.json；解锁等级：item_levels.json；附魔：enchantments.json；每日模式：shops.json；售出阶梯与等份：sale_quotas.json。", "",
        "## 商品逐项表", "",
        "| 物品/标准变体 | 商品ID | 分类提示 | 稀有度 | 岛等级 | 基准价值 | 基准购入价 | 购入倍率 | 无损收购价 | 购买渠道 | 每日库存 | 首件基础计分 | 价格来源 | 筛选规则 | 获取说明 |",
        "|---|---|---|---|---:|---:|---:|---:|---:|---|---:|---:|---|---|---|",
    ]
    rarity_lines = [
        "# 稀有度与物品等级表（审阅草案）", "",
        "物品稀有度和职业制造品质是独立轴。此表岛等级范围为0～100。", "",
        "| 稀有度 | 标准商店 | 每日权重 | 隐藏权重 | 默认库存 |",
        "|---|---|---:|---:|---:|",
        "| 普通 | 常驻 | 0 | 0 | 不限制 |",
        "| 稀有 | 不上架 | 70 | 15 | 64 |",
        "| 史诗 | 不上架 | 25 | 50 | 16 |",
        "| 传说 | 不上架 | 5 | 35 | 1 |", "",
        "先抽稀有度，再按产品权重从已解锁候选抽一项；抽完移除该商品，避免同日重复。",
        "某稀有度没有候选时剔除并对剩余权重重新归一化；12/16为最多栏位，不用未解锁物品补足。",
        "关键材料 offerWeight=15，提高选中机会但不构成保底。默认末地框架单次库存12。",
        "上架按刷新时队伍等级快照筛选；购买时仍检查当前等级。升降级不会重抽，新等级商品下一轮才可能上架。", "",
        "| 物品 | 商品ID | 稀有度 | 岛等级 | 渠道 | 抽取权重 | 库存 |",
        "|---|---|---|---:|---|---:|---:|",
    ]
    for key, product in sorted(products.items(), key=lambda kv: (levels[kv[0]], RANKS.index(rarities[kv[0]]), kv[0])):
        enabled = product["tradeMode"] != "disabled"
        channel = "标准常驻" if rarities[key] == "common" else "每日/隐藏"
        channel = channel if enabled else "禁交易/需变体"
        if product["tradeMode"] == "sell_only":
            channel = "仅出售/自行合成"
        value = full_base_value(product, enchants) if enabled else None
        can_buy = product["tradeMode"] in {"buy_sell", "buy_only"}
        multiplier = Fraction(product.get("buyMultiplierBps", 10000), 10000)
        score = scores.get(product["canonicalStack"]["itemId"], {}).get("basePoints", 0)
        # Not an authoritative item -> block map: unverified mapping is labelled as a reference.
        score_text = str(score) + "（同ID参考）" if score else "—"
        cells = [
            product["name"], key, product["categoryHint"], RANK_LABELS[rarities[key]], levels[key],
            math.floor(value) if value is not None else "—",
            math.floor(value * multiplier) if can_buy else "—",
            (f"首获×{multiplier}，之后×1" if product.get("firstAcquisitionPremium") else f"×{multiplier}") if can_buy else "—",
            math.floor(value / divisor) if value is not None and product["tradeMode"] != "buy_only" else "—", channel,
            product["dailyStock"] if can_buy and rarities[key] != "common" else "—",
            score_text, product["priceOrigin"], product.get("shopRule", "旧基线"), product["acquisitionNote"],
        ]
        price_lines.append("| " + " | ".join(map(escape, cells)) + " |")
        rarity_lines.append("| " + " | ".join(map(escape, [
            product["name"], key, RANK_LABELS[rarities[key]], levels[key], channel,
            product["offerWeight"] if can_buy and rarities[key] != "common" else "—",
            product["dailyStock"] if can_buy and rarities[key] != "common" else "—"])) + " |")
    (CFG / "shop" / "PRICE_GUIDE.md").write_text("\n".join(price_lines) + "\n", encoding="utf-8")
    (CFG / "shop" / "RARITY_LEVEL_TABLE.md").write_text("\n".join(rarity_lines) + "\n", encoding="utf-8")
    enchant_lines = [
        "# 原版附魔价值乘算表（Minecraft 26.2）", "",
        "43个附魔来自本机原版附魔数据，最大等级和诅咒/宝藏标签来自原版；倍率为本项目可编辑初稿。", "",
        "修改enchantments.json中的levelMultiplierBps；10000表示×1，15000表示×1.5。", "",
        "组合价值按每个附魔实际等级的系数相乘，不把附魔系数相加。普通附魔与附魔书分别读取ENCHANTMENTS/STORED_ENCHANTMENTS。",
        f"品质倍率、所有附魔倍率、耐久倍率与1/{divisor}一起计算，最后才对金币向下取整。",
        "附魔超过原版最高等级、互斥组合、未配置附魔拒绝自动收购；不把无法判断的物品当普通装备卖掉。",
        "附魔书售卖模板默认是单附魔；多附魔书需要单独配置。诅咒书默认不出售，装备上的合法诅咒按其折减系数估值。", "",
        "| 附魔 | ID | 最大等级 | I | II | III | IV | V | 宝藏 | 诅咒 |",
        "|---|---|---:|---:|---:|---:|---:|---:|---|---|",
    ]
    for eid, entry in enchants.items():
        factors = [f"×{entry['levelMultiplierBps'][str(i)] / 10000:g}" if i <= entry["maxLevel"] else "—" for i in range(1, 6)]
        enchant_lines.append("| " + " | ".join(map(escape, [entry["name"], eid, entry["maxLevel"], *factors, "是" if entry["isTreasure"] else "否", "是" if entry["isCurse"] else "否"])) + " |")
    (CFG / "shop" / "ENCHANTMENT_TABLE.md").write_text("\n".join(enchant_lines) + "\n", encoding="utf-8")
    render_automation(CFG, data)
    source = report["source"]
    validation_lines = [
        "# 经济草案校验报告", "",
        "本报告仅记录配置与数学规则检查；源码编译、真实注册表和客户端场景另见[实现与验证记录](IMPLEMENTATION_STATUS.md)。", "",
        f"- 原版候选物品：{source['candidateCount']}；包含空气占位，禁售条目另列。",
        f"- 全部目录行：{report['catalogRows']}；可交易：{report['activeRows']}；禁交易/需显式形态：{report['disabledRows']}。",
        f"- 标准商店条目：{report['ordinaryRows']}；每日/隐藏候选：{report['dailyRows']}。",
        f"- 仅出售条目：{report['sellOnlyRows']}；售出计数组：{report['quotaGroups']}；原料等份映射：{report['quotaEquivalentRows']}。",
        f"- 仅限商店购买的核心等级配置：{report['automationPurchaseGates']}。",
        f"- 原版配方：{source['vanillaRecipeCount']}；可解析成本路径：{source['parsedRecipeCount']}。",
        f"- 原版附魔：{source['enchantmentCount']}；药水类型：{source['potionCount']}。",
        f"- 原版JAR SHA-1：{source['jarSha1']}。", "",
        "## 已完成的数据检查", "",
    ] + ["- " + key for key in report["checksPassed"]] + [
        "", "## 耐久示例", "",
        "| 剩余耐久 | 收购价值倍率 |",
        "|---:|---:|",
    ] + [f"| {e['remainingPercent']}% | {float(Fraction(e['valueMultiplier'])):.4%} |" for e in report["durabilityExamples"]] + [
        "", "## 尚未验证", "",
    ] + ["- " + key for key in report["notYetVerified"]] + [
        "", "最低价格、当前级别随机池、附魔倍率与完整加工链可能相互作用；完整最坏行情/最高品质套利审查要在真实配方与组件注册完成后执行，不能把本报告的数据通过当作游戏经济已平衡。",
    ]
    (ROOT / "docs" / "ECONOMY_VALIDATION.md").write_text("\n".join(validation_lines) + "\n", encoding="utf-8")
    write_json(ROOT / "docs" / "ECONOMY_VALIDATION.json", report)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--assets", type=Path)
    parser.add_argument("--render-only", action="store_true")
    parser.add_argument("--overwrite-draft", action="store_true")
    args = parser.parse_args()
    (ROOT / "docs").mkdir(parents=True, exist_ok=True)
    if not args.render_only:
        if args.jar is None or args.assets is None:
            parser.error("--jar and --assets are required for the first extraction.")
        seed(args.jar, args.assets, args.overwrite_draft)
        from revise_shop import apply
        apply(CFG, args.jar)
    data = read_configs()
    report = validate(data)
    render(data, report)
    print(json.dumps(report, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
