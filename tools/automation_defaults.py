"""Editable technical-production design data and reference sale mathematics."""
from __future__ import annotations

from fractions import Fraction

MASS_PRICES = {
    "iron_ingot": (90, "common", 10), "iron_nugget": (10, "common", 10),
    "iron_block": (810, "common", 10),
    "copper_ingot": (90, "common", 10), "copper_nugget": (10, "common", 10),
    "copper_block": (810, "common", 10),
    "gold_ingot": (90, "common", 30), "gold_nugget": (10, "common", 30),
    "gold_block": (810, "common", 30),
    "raw_iron": (300, "rare", 20), "raw_copper": (150, "rare", 20),
    "raw_gold": (300, "rare", 30),
    "redstone": (30, "common", 15), "redstone_block": (270, "common", 15),
    "emerald": (90, "common", 45), "emerald_block": (810, "common", 45),
    "lapis_lazuli": (50, "common", 45), "lapis_block": (450, "common", 45),
    "coal": (200, "rare", 35), "coal_block": (1800, "rare", 35),
    "charcoal": (10, "common", 0),
    "quartz": (20, "common", 35),
    "sulfur_spike": (10, "common", 20), "sulfur": (40, "common", 20),
    "potent_sulfur": (360, "common", 20),
    "spore_blossom": (50000, "epic", 35),
    "cinnabar": (3000, "epic", 40),
    "bamboo": (10, "common", 3), "bamboo_block": (90, "common", 3),
    "stripped_bamboo_block": (90, "common", 3),
    "sugar_cane": (10, "common", 3), "cactus": (10, "common", 5),
    "kelp": (10, "common", 5), "dried_kelp": (10, "common", 5),
    "dried_kelp_block": (90, "common", 5),
    "bone": (30, "common", 3), "bone_meal": (10, "common", 3),
    "bone_block": (90, "common", 3), "string": (10, "common", 3),
    "gunpowder": (10, "common", 5), "rotten_flesh": (10, "common", 0),
    "arrow": (10, "common", 3), "slime_ball": (10, "common", 15),
    "slime_block": (90, "common", 15),
    "honey_bottle": (10, "common", 15), "honey_block": (40, "common", 15),
    "honeycomb": (10, "common", 15), "honeycomb_block": (40, "common", 15),
    "gravel": (10, "common", 35), "blackstone": (10, "common", 35),
    "soul_sand": (10, "common", 35), "nether_brick": (10, "common", 35),
    "leather": (20, "common", 5), "feather": (10, "common", 3),
    "egg": (10, "common", 3), "ender_pearl": (20, "common", 25),
    "glowstone_dust": (10, "common", 15), "snowball": (10, "common", 5),
    "snow_block": (40, "common", 5), "ice": (10, "common", 20),
    "packed_ice": (90, "common", 20), "blue_ice": (810, "common", 20),
    "clay_ball": (10, "common", 25), "clay": (40, "common", 25),
    "totem_of_undying": (2000, "rare", 45), "saddle": (100, "common", 45),
    "wheat": (10, "common", 0), "wheat_seeds": (10, "common", 0),
    "carrot": (10, "common", 3), "potato": (10, "common", 3),
    "beetroot": (10, "common", 3), "beetroot_seeds": (10, "common", 3),
    "cocoa_beans": (10, "common", 5),
    "beef": (20, "common", 5), "porkchop": (20, "common", 5),
    "chicken": (20, "common", 5), "mutton": (20, "common", 5),
    "rabbit": (30, "common", 5), "cod": (20, "common", 5),
    "salmon": (20, "common", 5),
    "nether_gold_ore": (600, "rare", 35),
    "chorus_fruit": (20, "common", 55),
    "ink_sac": (10, "common", 5), "glow_ink_sac": (20, "common", 15),
    "brown_mushroom": (10, "common", 5), "red_mushroom": (10, "common", 5),
    "melon_slice": (10, "common", 3), "melon": (90, "common", 3),
    "pumpkin": (10, "common", 3), "sweet_berries": (10, "common", 3),
    "glow_berries": (10, "common", 10),
    "white_wool": (20, "common", 5),
    "shulker_shell": (2000, "rare", 65),
    "chorus_flower": (100, "rare", 55), "nether_wart": (20, "rare", 30),
    "budding_amethyst": (10000, "epic", 40),
    "amethyst_shard": (50, "common", 40), "amethyst_block": (200, "common", 40),
    "amethyst_cluster": (200, "common", 40),
    "small_amethyst_bud": (50, "rare", 40), "medium_amethyst_bud": (100, "rare", 40),
    "large_amethyst_bud": (150, "rare", 40),
    "prismarine_shard": (500, "rare", 45), "prismarine_crystals": (500, "rare", 45),
    "ghast_tear": (20, "common", 30), "magma_cream": (10, "common", 30),
    "spider_eye": (10, "common", 0), "phantom_membrane": (10, "common", 20),
    "dragon_breath": (10000, "epic", 65),
}
for _color in ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
               "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"):
    MASS_PRICES[_color + "_wool"] = (20, "common", 5)
CORE_LEVELS = {
    "grass_block": 0, "oak_sapling": 0, "wheat_seeds": 0,
    "moss_block": 5, "azalea": 5, "flowering_azalea": 5,
    "composter": 5, "hopper": 15, "hopper_minecart": 15,
    "piston": 15, "sticky_piston": 15, "repeater": 15,
    "dispenser": 20, "dropper": 20, "observer": 20, "comparator": 20,
    "pointed_dripstone": 25, "dripstone_block": 25, "cauldron": 25,
    "villager_spawn_egg": 20, "zombie_spawn_egg": 20,
    "piglin_spawn_egg": 35, "turtle_egg": 30,
    "bee_spawn_egg": 15, "bee_nest": 15, "beehive": 15,
    "sulfur": 20, "sulfur_spike": 20,
    "blaze_rod": 30, "brewing_stand": 30, "crafter": 35,
    "chorus_flower": 55, "end_stone": 55,
    "shulker_spawn_egg": 65, "shulker_shell": 65,
    "frog_spawn_egg": 40, "tadpole_bucket": 40,
    "ominous_bottle": 45,
    "budding_amethyst": 40,
}
MATRIX = [
    ("wood", "树木/木炭", "树苗、土壤；高速需骨粉与破坏收集", "原木、木制品、木炭", 0, "可再生；全自动采集须另核验合法破坏方法", "TNT复制不列入基线"),
    ("stone", "刷石/石材", "开局水与岩浆", "圆石、石头及加工物", 0, "可自动生成；采集方式决定自动化程度", "起始能力不作等级封锁"),
    ("crops", "植物/作物", "首株/种子、水、适合基底", "竹、甘蔗、仙人掌、海带、农作物", 3, "可扩种；成熟作物和甘蔗等可收集", "Java甘蔗不接受骨粉，村民收种田仍可用"),
    ("moss", "苔藓/骨粉/泥土", "首块苔藓、骨粉、刷石、堆肥桶", "苔藓、骨粉、杜鹃、根系泥土/泥土", 5, "可设计再生闭环", "杜鹃树下根系泥土是原版；不是复制"),
    ("hostile", "普通刷怪", "黑暗平台、合适生物群系与玩家距离", "骨头、箭、线、火药、腐肉", 0, "可量产，玩家击杀条件另算", "不提供女巫小屋/要塞结构效率承诺"),
    ("wool", "羊毛", "羊、草、剪刀；发射器与观察者", "各色羊毛、对应地毯", 20, "吃草恢复羊毛，可发射器剪毛", "白色羊毛也可用线制作床，允许提前获得"),
    ("bees", "蜂蜜", "树苗邻近花得蜂巢/初始蜜蜂", "蜜瓶、蜜脾及对应压缩方块", 20, "支持原版发射器采集", "不必用蜂蛋作为唯一启动途径"),
    ("iron", "铁农场", "村民、床、有效生成平台", "铁锭、虞美人", 20, "无交易仍可生成铁傀儡", "关闭交易不关闭繁殖、工作或造傀儡"),
    ("copper", "溺尸铜", "僵尸转化或自然溺尸，玩家击杀", "铜锭", 20, "可再生，需要玩家击杀收益条件", "转化溺尸不能推定有自然装备的三叉戟/贝壳来源"),
    ("lava", "钟乳石/熔岩", "熔岩源、钟乳石、炼药锅", "熔岩、钟乳石锥", 25, "再生；取桶不等于原版全自动", "硫磺尖刺不能替代钟乳石锥灌锅"),
    ("clay", "泥土/泥巴/黏土", "泥土、水瓶、钟乳石锥；或猪灵砂砾", "泥土、泥巴、黏土、砖、陶瓦", 25, "有原版再生转换链", "砂土还需锄耕；瓶与水源按真实流程"),
    ("snow", "雪", "雪块、雕刻南瓜、雪傀儡", "雪球、雪块", 5, "可生产，铲取/装置条件需分别说明", "不产冰；不能全局关闭mobGriefing"),
    ("ice", "冰", "寒冷条件、水源、精准采集", "冰、浮冰、蓝冰", 20, "可再生；通常需玩家采集", "蓝冰=81普通冰"),
    ("gold", "地狱金塔", "地狱可用生物群系/平台/击杀收集", "金粒、金锭、金装备、腐肉", 30, "全空地狱可建平台", "海龟蛋只是部分设计的诱导手段"),
    ("barter", "猪灵交易", "猪灵与持续金锭", "石英、砂砾、黑石、黑曜石、灵魂沙等", 35, "保留，以物易物可自动输送", "不产下界岩或灵魂土，交易产物低价"),
    ("sulfur", "硫磺", "硫磺块+母尖刺", "尖刺、硫磺、浓硫磺与建材", 20, "26.2原版自然生长可量产", "4尖刺=1硫磺；9硫磺=1浓硫磺"),
    ("raid", "袭击", "人工村庄、非袭击队长灾厄瓶、有效平台", "绿宝石、图腾、鞍、女巫产物", 45, "无自然村庄仍可建；难度/玩家条件另算", "袭击队长不能自行无限续瓶"),
    ("gifts", "村庄英雄赠礼", "完成袭击、职业村民、英雄效果", "青金石、红石、书/羊毛等职业礼物", 45, "交易关闭仍可能运行", "牧师启动需酿造台与首根烈焰棒"),
    ("chorus", "紫颂", "首朵紫颂花+末地石", "紫颂果与紫珀材料", 55, "启动后可扩种", "纯空末地没有第一朵花"),
    ("amethyst", "紫水晶", "紫水晶母块、留空生长面与采收", "晶簇、碎片、紫水晶块", 40, "母块可持续生长；普通块不能", "母块无配方且精准采集也不掉落；特殊商店供应"),
    ("shulker", "潜影贝", "首只潜影贝、弹道/传送/收集设计", "潜影壳/盒", 65, "被潜影弹命中繁殖为原版机制", "不是漏洞；首只无自然末地城来源"),
    ("crafter", "自动合成器", "铁、红石、工作台、投掷器", "可再生原料的压缩/加工成品", 35, "原版自动合成", "不会产生缺失原料，也不给个人制造XP"),
    ("fortress", "堡垒限定产物", "需要堡垒刷怪范围/额外刷怪源", "烈焰棒、凋灵头、煤", 35, "当前纯空无结构不能默认量产", "一枚烈焰人/凋灵骷髅蛋不是无限农场启动器"),
    ("missing", "不可默认量产", "依赖商店初始来源", "钻石、远古残骸、首张模板、朱砂、孢子花、海晶材料等", 35, "不按原版量产物压价", "无神殿结构不能默认守卫者场；Carpet扩展、复制漏洞不混入基线"),
]


def make_rules(header: dict, products: dict) -> dict:
    groups, mappings = {}, {}

    def group(gid: str, canonical: str, label: str, entries: dict) -> None:
        groups[gid] = {"unitItemId": "minecraft:" + canonical, "unitName": label}
        for item, fraction in entries.items():
            if "minecraft:" + item in products:
                f = Fraction(fraction)
                mappings["minecraft:" + item] = {"quotaGroup": gid, "numerator": f.numerator, "denominator": f.denominator}

    for metal in ("iron", "copper", "gold"):
        entries = {metal + "_ingot": 1, metal + "_nugget": Fraction(1, 9),
                   metal + "_block": 9, "raw_" + metal: 1, "raw_" + metal + "_block": 9,
                   metal + "_ore": 1, "deepslate_" + metal + "_ore": 1}
        if metal == "gold":
            entries["nether_gold_ore"] = 1
        group("metal:" + metal, metal + "_ingot", {"iron": "铁锭", "copper": "铜锭", "gold": "金锭"}[metal], entries)
    for name, label in (("diamond", "钻石"), ("emerald", "绿宝石"), ("redstone", "红石粉"),
                        ("coal", "煤"), ("lapis", "青金石")):
        item = "lapis_lazuli" if name == "lapis" else name
        group("mineral:" + name, item, label, {item: 1, name + "_block": 9, name + "_ore": 1, "deepslate_" + name + "_ore": 1})
    group("mineral:netherite", "netherite_ingot", "下界合金锭", {
        "netherite_ingot": 1, "netherite_block": 9, "netherite_scrap": Fraction(1, 4), "ancient_debris": Fraction(1, 4)})
    group("mineral:quartz", "quartz", "石英", {
        "quartz": 1, "nether_quartz_ore": 1, "quartz_block": 4, "quartz_pillar": 4,
        "chiseled_quartz_block": 4, "quartz_bricks": 4, "smooth_quartz": 4,
        "quartz_slab": 2, "smooth_quartz_slab": 2, "quartz_stairs": 4, "smooth_quartz_stairs": 4})
    group("bio:bone", "bone", "骨头", {"bone": 1, "bone_meal": Fraction(1, 3), "bone_block": 3})
    group("bio:slime", "slime_ball", "黏液球", {"slime_ball": 1, "slime_block": 9})
    group("bio:honey", "honey_bottle", "蜂蜜瓶", {"honey_bottle": 1, "honey_block": 4})
    group("bio:honeycomb", "honeycomb", "蜜脾", {"honeycomb": 1, "honeycomb_block": 4})
    group("bio:kelp", "dried_kelp", "海带", {"kelp": 1, "dried_kelp": 1, "dried_kelp_block": 9})
    group("bio:wheat", "wheat", "小麦", {"wheat": 1, "hay_block": 9})
    group("bio:nether_wart", "nether_wart", "下界疣", {"nether_wart": 1, "nether_wart_block": 9})
    group("bio:melon", "melon_slice", "西瓜片", {"melon_slice": 1, "melon": 9})
    group("stone:dirt", "dirt", "泥土", {"dirt": 1, "coarse_dirt": 1, "rooted_dirt": 1, "mud": 1})
    group("stone:cobblestone", "cobblestone", "圆石", {
        "cobblestone": 1, "stone": 1, "smooth_stone": 1,
        "cobblestone_stairs": 1, "cobblestone_slab": Fraction(1, 2), "cobblestone_wall": 1,
        "stone_stairs": 1, "stone_slab": Fraction(1, 2), "smooth_stone_slab": Fraction(1, 2),
        "stone_bricks": 1, "cracked_stone_bricks": 1, "chiseled_stone_bricks": 1,
        "stone_brick_stairs": 1, "stone_brick_slab": Fraction(1, 2), "stone_brick_wall": 1})
    wood_entries = {}
    for wood in ("oak", "spruce", "birch", "acacia", "jungle", "dark_oak", "mangrove", "cherry", "pale_oak", "crimson", "warped"):
        stem = "stem" if wood in {"crimson", "warped"} else "log"
        bark = "hyphae" if stem == "stem" else "wood"
        for prefix in ("", "stripped_"):
            wood_entries[prefix + wood + "_" + stem] = 1
            wood_entries[prefix + wood + "_" + bark] = Fraction(4, 3)
        for form, units in {
            "planks": Fraction(1, 4), "slab": Fraction(1, 8), "stairs": Fraction(3, 8),
            "button": Fraction(1, 4), "pressure_plate": Fraction(1, 2),
            "door": Fraction(1, 2), "trapdoor": Fraction(3, 4),
        }.items():
            wood_entries[wood + "_" + form] = units
    group("bio:wood", "oak_log", "原木", wood_entries)
    group("bio:bamboo", "bamboo", "竹子", {
        "bamboo": 1, "bamboo_block": 9, "stripped_bamboo_block": 9,
        "bamboo_planks": Fraction(9, 2), "bamboo_slab": Fraction(9, 4),
        "bamboo_stairs": Fraction(27, 4), "bamboo_mosaic": Fraction(9, 2),
        "bamboo_mosaic_slab": Fraction(9, 4), "bamboo_mosaic_stairs": Fraction(27, 4)})
    group("bio:chorus", "chorus_fruit", "紫颂果", {
        "chorus_fruit": 1, "popped_chorus_fruit": 1,
        "purpur_block": 4, "purpur_pillar": 4, "purpur_stairs": 4, "purpur_slab": 2})
    group("mineral:amethyst", "amethyst_shard", "紫水晶碎片", {
        "amethyst_shard": 1, "amethyst_block": 4, "amethyst_cluster": 4})
    sulfur_entries = {"sulfur_spike": Fraction(1, 4), "sulfur": 1, "potent_sulfur": 9,
                      "polished_sulfur": 1, "sulfur_bricks": 1, "chiseled_sulfur": 1}
    for form in ("sulfur", "polished_sulfur", "sulfur_brick"):
        sulfur_entries[form + "_stairs"] = 1
        sulfur_entries[form + "_wall"] = 1
        sulfur_entries[form + "_slab"] = Fraction(1, 2)
    group("stone:sulfur", "sulfur", "硫磺块", sulfur_entries)
    group("stone:ice", "ice", "普通冰", {"ice": 1, "packed_ice": 9, "blue_ice": 81})
    group("stone:snow", "snowball", "雪球", {"snowball": 1, "snow_block": 4})
    group("stone:clay", "clay_ball", "黏土球", {"clay_ball": 1, "clay": 4, "brick": 1, "bricks": 4})
    group("end:shulker", "shulker_shell", "潜影壳", {"shulker_shell": 1, "shulker_box": 2})
    colors = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
              "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")
    group("bio:wool", "white_wool", "羊毛", {
        **{color + "_wool": 1 for color in colors},
        **{color + "_carpet": Fraction(2, 3) for color in colors}})
    for color in colors:
        key = "minecraft:" + color + "_shulker_box"
        if key in products:
            mappings[key] = {"quotaGroup": "end:shulker", "numerator": 2, "denominator": 1}
    # Explicit primary-material mappings cover these otherwise mixed recipes.
    equipment_materials = {"copper": "metal:copper", "iron": "metal:iron", "golden": "metal:gold", "diamond": "mineral:diamond"}
    equipment_units = {"pickaxe": 3, "axe": 3, "shovel": 1, "hoe": 2, "sword": 2,
                       "spear": 1, "helmet": 5, "chestplate": 8, "leggings": 7, "boots": 4}
    for material, gid in equipment_materials.items():
        for form, units in equipment_units.items():
            key = "minecraft:" + material + "_" + form
            if key in products:
                mappings[key] = {"quotaGroup": gid, "numerator": units, "denominator": 1}
    # Copper state/wax transformations do not alter raw material units.
    copper_forms = {
        "copper": 9, "cut_copper": Fraction(9, 4), "chiseled_copper": Fraction(9, 4),
        "copper_grate": Fraction(9, 4), "copper_bulb": Fraction(27, 4),
        "cut_copper_stairs": Fraction(9, 4), "cut_copper_slab": Fraction(9, 8),
        "copper_door": 2, "copper_trapdoor": 4,
        "copper_bars": Fraction(3, 8), "copper_chain": Fraction(11, 9), "copper_chest": 8,
        "copper_torch": Fraction(1, 36), "copper_lantern": Fraction(11, 12), "lightning_rod": 3,
    }
    for state in ("", "exposed_", "weathered_", "oxidized_"):
        for wax in ("", "waxed_"):
            for form, units in copper_forms.items():
                actual_form = "copper_block" if form == "copper" and not state else state + form
                key = "minecraft:" + wax + actual_form
                if key in products:
                    f = Fraction(units)
                    mappings[key] = {"quotaGroup": "metal:copper", "numerator": f.numerator, "denominator": f.denominator}
    # Products without explicit equivalents share the vanilla item ID, not grade/enchantment/product variant.
    sale = dict(header,
        scope="party", epochSource="shop_marketEpoch",
        defaultGroup="canonical_item_id", defaultUnitsPerItem={"numerator": 1, "denominator": 1},
        groups=groups, itemEquivalents=mappings,
        tiers=[
            {"upToUnits": 1000, "saleMultiplierBps": 10000},
            {"upToUnits": 10000, "saleMultiplierBps": 5000},
            {"upToUnits": None, "saleMultiplierBps": 1000}],
        counterUsesExactRationals=True, splitAnItemAcrossTierBoundaries=True,
        sumAllLinesThenFloorCoins=True, zeroCoinTransaction="refuse_without_removing_items_or_advancing_counters",
        bypassTriggers={"reset": False, "login": False, "quality_change": False, "enchantment_change": False,
                        "compression": False, "sell_channel_change": False},
        channels=["shop_ui", "sell_hand", "sell_inventory", "shipping_chest"],
        multiIngredientGoods="independent_item_quota_unless_explicit_equivalent",
        tierCounterChangesOnlyOnSuccessfulSale=True)
    server = dict(header, villagerTradingEnabled=False, wanderingTraderTradingEnabled=False,
                  piglinBarteringEnabled=True, villagerBreedingEnabled=True,
                  villagerWorkAiEnabled=True, ironGolemSpawningEnabled=True,
                  globalMobGriefingOverride=None, automationUnlockPolicy="shop_purchase_only")
    core = dict(header, purchaseOnly=True, blockCrafting=False, blockPlacement=False,
                blockNaturalGeneration=False,
                corePurchaseLevels={"minecraft:" + k: v for k, v in CORE_LEVELS.items() if "minecraft:" + k in products})
    production = dict(header, baseline="vanilla_normal_survival_no_dupes_no_Carpet_features",
                      rules=[{"id": r[0], "name": r[1], "startupCore": r[2], "outputs": r[3],
                              "suggestedCoreShopLevel": r[4], "availability": r[5], "notes": r[6]} for r in MATRIX])
    shipping = dict(header,
        blockId="yumis_sky_island:shipping_chest", itemId="yumis_sky_island:shipping_chest",
        appearance="vanilla_normal_chest_with_original_renderer",
        inventorySlots=27, doubleChestEnabled=False, hopperCompatible=True,
        recipe={"type": "minecraft:crafting_shaped", "pattern": ["IRI", "RCR", "IRI"],
                "key": {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "C": "minecraft:chest"},
                "result": {"id": "yumis_sky_island:shipping_chest", "count": 1}},
        shopPurchaseEnabled=False, craftRequiresLevel=False,
        baseValueCoins=sum(products["minecraft:" + item]["baseValueCoins"] * count
                           for item, count in (("iron_ingot", 4), ("redstone", 4), ("chest", 1))),
        trigger="redstone_rising_edge_only", scanOnContinuousPower=False,
        binding="party_of_actual_claim_at_server_placement",
        islandEpochScope="party_and_dimension", resetInvalidatesEpoch="overworld_only",
        revalidateClaimOnEverySale=True, persistPowerBaseline=True, loadDoesNotCreateNewPulse=True,
        transactionService="shared_SaleService",
        normalPricesAndSaleQuotas=True, profits="party_wallet",
        keepUnpricedUnsafeAndZeroValueItems=True,
        zeroCoinBatch="keep_all_and_wait_for_larger_batch",
        execution="deferred_server_tick_transaction",
        inventoryOrder="ascending_slot_index",
        maxPendingJobsPerBox=1, duplicateNotifications="coalesce_existing_job",
        pulsesWhileBusy="not_queued_as_additional_jobs", requireNewRisingEdgeAfterCompletion=True,
        sharedGlobalComputeBudgetMicrosPerTick=1000, forceLoadUnloadedChunks=False,
        stateFields=["partyId", "dimensionId", "islandEpoch", "boxId", "powerBaseline", "activationSeq", "pendingSaleId", "lastCommittedSaleId"],
        durabilityPolicy="same_as_manual_sale", protection="normal_island_container_and_cross_boundary_hopper_rules",
        restartRecovery="durable_journal_and_idempotent_commit_before_accepting_further_transactions")
    return {"sale_quotas": sale, "server_rules": server, "unlocks": core,
            "production": production, "shipping": shipping}
