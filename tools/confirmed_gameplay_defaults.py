"""Confirmed test-phase defaults; explicit generation, never a runtime migration."""
from __future__ import annotations
from copy import deepcopy
from pathlib import Path
from config_io import read, write

ROOT = Path(__file__).resolve().parents[1]
COINS = [500] * 5 + [2000, 3000, 3000, 5000, 10000]
PROFESSIONS = {"fisherman": "渔夫", "farmer": "农夫", "blacksmith": "铁匠", "stonemason": "石匠", "artisan": "工匠", "chef": "厨师", "hunter": "猎人"}

def goal(kind, item, quantity, **options):
    return dict(type=kind, targetId=item if ":" in item else "minecraft:" + item, quantity=quantity, **options)

def rewards(*pairs):
    return [dict(id=item if ":" in item else "minecraft:" + item, count=count) for item, count in pairs]

def task_lines():
    # name, action, targets, item rewards; quantities count actual outputs, not recipes.
    lines = {
        "fisherman": [
            ("渔夫的最爱", "craft", [("fishing_rod", 1)], []),
            ("初次丰收", "fish", [("cod", 10)], [("coal", 16)]),
            ("炭火不愁", "fish", [("cod", 25)], [("iron_ingot", 8)]),
            ("河豚的诱惑", "fish", [("pufferfish", 3)], [("oak_log", 16)]),
            ("深海之味", "fish", [("salmon", 15)], [("iron_block", 1)]),
            ("渔夫的好运", "fish", [("enchanted_book", 10)], [("lapis_lazuli", 3)]),
            ("做记号", "fish", [("name_tag", 1)], []),
            ("来历不明的弓", "fish", [("bow", 1)], [("string", 16)]),
            ("水下呼吸", "fish", [("tropical_fish", 20)], []),
            ("海洋之心", "fish", [("nautilus_shell", 8)], [("prismarine_shard", 8)]),
        ],
        "farmer": [
            ("锄头", "craft", [("stone_hoe", 1)], [("wheat_seeds", 10)]),
            ("种树", "harvest", [("oak_log", 16)], [("dirt", 16)]),
            ("生根发芽", "harvest", [("oak_sapling", 16)], [("shears", 1), ("bone_meal", 16)]),
            ("修剪枝叶", "harvest", [("oak_leaves", 64)], [("birch_sapling", 1), ("spruce_sapling", 1)]),
        ],
        "blacksmith": [
            ("开炉准备", "craft", [("stone_pickaxe", 1)], [("coal", 16)]),
            ("第一炉铁", "smelt", [("iron_ingot", 16)], [("copper_ingot", 16)]),
            ("铁镐上手", "craft", [("iron_pickaxe", 1)], [("iron_ingot", 8)]),
            ("伐木利器", "craft", [("iron_axe", 1)], [("charcoal", 16)]),
            ("打造锋刃", "craft", [("iron_sword", 1)], [("iron_block", 1)]),
            ("举盾而立", "craft", [("shield", 1)], [("redstone", 16)]),
            ("护住头颅", "craft", [("iron_helmet", 1)], [("lapis_lazuli", 8)]),
            ("铁甲成形", "craft", [("iron_chestplate", 1)], [("gold_ingot", 8)]),
            ("钻石开矿", "craft", [("diamond_pickaxe", 1)], [("diamond", 3)]),
            ("钻石铠甲", "craft", [("diamond_chestplate", 1)], [("diamond", 4)]),
        ],
        "stonemason": [
            ("开山第一步", "harvest", [("cobblestone", 32)], [("coal", 16)]),
            ("炉火锻石", "smelt", [("stone", 64)], [("bone_meal", 16)]),
            ("铺平道路", "craft", [("stone_slab", 64)], [("torch", 32)]),
            ("砖石之美", "craft", [("stone_bricks", 64)], [("oak_log", 16)]),
            ("层层台阶", "craft", [("stone_brick_stairs", 32)], [("iron_ingot", 8)]),
            ("三色岩石", "craft", [("polished_granite", 32), ("polished_andesite", 32), ("polished_diorite", 32)], [("coal_block", 4)]),
            ("雕琢细节", "craft", [("chiseled_stone_bricks", 32)], [("lapis_lazuli", 8)]),
            ("深色基石", "craft", [("deepslate_bricks", 64)], [("redstone", 16)]),
            ("精磨成材", "smelt", [("smooth_stone", 64)], [("quartz", 16)]),
            ("白石之柱", "craft", [("quartz_pillar", 32)], [("amethyst_shard", 16)]),
        ],
        "artisan": [
            ("收纳空间", "craft", [("chest", 4)], [("oak_log", 16)]),
            ("桶中乾坤", "craft", [("barrel", 4)], [("coal", 16)]),
            ("向上攀登", "craft", [("ladder", 32)], [("string", 16)]),
            ("知识角落", "craft", [("bookshelf", 4)], [("leather", 8)]),
            ("照亮工坊", "craft", [("lantern", 16)], [("iron_ingot", 8)]),
            ("物流之始", "craft", [("hopper", 4)], [("iron_block", 2)]),
            ("推动机械", "craft", [("piston", 4)], [("redstone", 16)]),
            ("敏锐观察", "craft", [("observer", 4)], [("quartz", 16)]),
            ("信号比较", "craft", [("comparator", 4)], [("redstone_block", 2)]),
            ("自动工坊", "craft", [("crafter", 1)], [("yumis_sky_island:shipping_chest", 1)]),
        ],
        "chef": [
            ("第一炉面包", "craft", [("bread", 8)], [("wheat", 16)]),
            ("热乎土豆", "smelt", [("baked_potato", 16)], [("potato", 8)]),
            ("鳕鱼上桌", "smelt", [("cooked_cod", 16)], [("coal", 16)]),
            ("鲑鱼宴", "smelt", [("cooked_salmon", 16)], [("coal", 16)]),
            ("林间鲜汤", "craft", [("mushroom_stew", 8)], [("red_mushroom", 8), ("brown_mushroom", 8)]),
            ("南瓜甜点", "craft", [("pumpkin_pie", 8)], [("egg", 16)]),
            ("曲奇时光", "craft", [("cookie", 32)], [("cocoa_beans", 16)]),
            ("炖锅飘香", "craft", [("rabbit_stew", 4)], [("carrot", 16)]),
            ("庆祝蛋糕", "craft", [("cake", 3)], [("sugar", 16)]),
            ("金色佳肴", "craft", [("golden_carrot", 16)], [("gold_block", 1)]),
        ],
        "hunter": [
            ("备好猎弓", "craft", [("bow", 1)], [("arrow", 32)]),
            ("箭袋充足", "craft", [("arrow", 64)], [("flint", 16)]),
            ("扫清尸群", "kill", [("zombie", 16)], [("iron_ingot", 8)]),
            ("迎战骷髅", "kill", [("skeleton", 16)], [("bone", 16)]),
            ("蛛网之间", "kill", [("spider", 12)], [("string", 16)]),
            ("火药猎手", "kill", [("creeper", 12)], [("redstone", 16)]),
            ("精准狙击", "craft", [("crossbow", 1)], [("arrow", 64)]),
            ("末影追踪", "kill", [("enderman", 8)], [("lapis_lazuli", 8)]),
            ("下界狩猎", "kill", [("zombified_piglin", 16)], [("obsidian", 4)]),
            ("地狱回声", "kill", [("ghast", 4)], [("diamond", 1)]),
        ],
    }
    result = []
    for profession, entries in lines.items():
        for index, (name, kind, targets, items) in enumerate(entries, 1):
            coins = [1000, 500, 1000, 1000][index-1] if profession == "farmer" else COINS[index-1]
            row = dict(id=f"{profession}_{index:02}", name=name, kind="profession", profession=profession, objective="tracked", requirements=[], goals=[goal(kind, item, count) for item, count in targets], rewardItems=rewards(*items), rewardCoins=coins, rewardExperiencePoints=500 if profession == "fisherman" and index == 7 else 0, unlockFeatures=[])
            if index > 1:
                row["previousQuestId"] = f"{profession}_{index-1:02}"
            if profession == "farmer" and index == 2:
                row["goals"][0]["sourceBlockId"] = "minecraft:oak_log"
            if profession == "farmer" and index in (3, 4):
                row["goals"][0]["sourceBlockId"] = "minecraft:oak_leaves"
            if profession == "farmer" and index == 4:
                row["goals"][0]["toolId"] = "minecraft:shears"
            if profession == "fisherman" and index == 9:
                row["rewardItems"] = [dict(id="minecraft:potion", count=3, components={"minecraft:potion_contents": {"potion": "minecraft:long_water_breathing"}})]
            result.append(row)
    assert len(result) == 64
    return result

def apply_shop(products_document, levels_document, rarities_document, shops_document):
    products, levels, rarities = products_document["products"], levels_document["itemLevels"], rarities_document["itemRarities"]
    shops_document["regularBuyMultiplierBps"] = 25000
    shops_document["buyRule"] = "floor(purchaseBaseAfterMarket * quality * enchantments * productBuyMultiplier * regularShopMultiplier)"
    for item, price in {"sculk_shrieker": 100000, "sculk_sensor": 100000, "disc_fragment_5": 100000, "trial_key": 50000, "ominous_trial_key": 100000}.items():
        products["minecraft:" + item]["buyBaseValueCoins"] = price
    for item in ("trial_key", "ominous_trial_key"):
        levels["minecraft:" + item] = 55
        rarities["minecraft:" + item] = "rare"
    for key, rarity in list(rarities.items()):
        if key.endswith("_spawn_egg"):
            if rarity == "common":
                rarities[key] = "rare"
            if key in products:
                products[key]["buyBaseValueCoins"] = products[key]["baseValueCoins"] * 10
    for item, name, price, level in [("trial_spawner", "试炼刷怪笼", 1000000, 55), ("spawner", "刷怪笼", 10000000, 100)]:
        key = "minecraft:" + item
        products[key] = dict(productId=key, name=name, categoryHint="功能方块", shopCategory="minecraft:functional_blocks", baseValueCoins=price, buyMultiplierBps=10000, tradeMode="buy_only", dailyStock=1, offerWeight=1, canonicalStack=dict(itemId=key, componentPatch={}), priceOrigin="confirmed_gameplay", acquisitionNote="传奇空白笼；放置后用原版刷怪蛋设置生物。", shopRule="仅购买，不收购")
        levels[key], rarities[key] = level, "legendary"

def apply_root(root: Path):
    paths = ["shop/products.toml", "shop/item_levels.toml", "shop/rarities.toml", "shop/shops.toml"]
    docs = [read(root / p) for p in paths]
    apply_shop(*docs)
    for relative, document in zip(paths, docs):
        write(root / relative, document)
    profession = read(root / "products/professions.toml")
    profession["successfulFishingXp"] = 12
    write(root / "products/professions.toml", profession)
    quests = read(root / "tasks/quests.toml")
    ours = {row["id"] for row in task_lines()}
    quests["quests"] = [q for q in quests["quests"] if q["id"] not in ours] + task_lines()
    for q in quests["quests"]:
        q.pop("description", None)
    quests["schemaVersion"] = 3
    write(root / "tasks/quests.toml", quests)

if __name__ == "__main__":
    for config in (ROOT / "config/yumis_sky_island", ROOT / "run/config/yumis_sky_island"):
        if config.exists():
            apply_root(config)
            print(f"Updated confirmed test defaults: {config}")
