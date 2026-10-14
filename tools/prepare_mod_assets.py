"""Code-native assets reuse vanilla chest rendering. No game instance is edited."""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"

def write(path, value):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

with zipfile.ZipFile(ROOT / ".tools/minecraft-26.2-reference.jar") as jar:
    preset = json.loads(jar.read("data/minecraft/worldgen/world_preset/normal.json"))
    for dimension in preset["dimensions"].values():
        dimension["generator"]["type"] = "yumis_sky_island:void_noise"
    write("data/yumis_sky_island/worldgen/world_preset/sky_island.json", preset)
    write("assets/yumis_sky_island/items/shipping_chest.json", json.loads(jar.read("assets/minecraft/items/chest.json")))
    write("assets/yumis_sky_island/blockstates/shipping_chest.json", json.loads(jar.read("assets/minecraft/blockstates/chest.json")))
    loot=json.loads(jar.read("data/minecraft/loot_table/blocks/chest.json"))
    def replace_chest(value):
        if isinstance(value,dict): return {k:replace_chest(v) for k,v in value.items()}
        if isinstance(value,list): return [replace_chest(v) for v in value]
        return "yumis_sky_island:shipping_chest" if value=="minecraft:chest" else value
    write("data/yumis_sky_island/loot_table/blocks/shipping_chest.json",replace_chest(loot))

write("data/minecraft/tags/worldgen/world_preset/normal.json", {"replace": False, "values": ["yumis_sky_island:sky_island"]})
write("data/yumis_sky_island/recipe/shipping_chest.json", {"type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["IRI", "RCR", "IRI"], "key": {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "C": "minecraft:chest"}, "result": {"id": "yumis_sky_island:shipping_chest", "count": 1}})
write("data/minecraft/tags/block/mineable/axe.json",{"replace":False,"values":["yumis_sky_island:shipping_chest"]})
write("data/yumis_sky_island/advancement/recipes/shipping_chest.json",{"parent":"minecraft:recipes/root","criteria":{"has_chest":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":"minecraft:chest"}]}}},"requirements":[["has_chest"]],"rewards":{"recipes":["yumis_sky_island:shipping_chest"]}})
write("assets/yumis_sky_island/lang/zh_cn.json", {"generator.yumis_sky_island.sky_island": "Yumi 空岛", "block.yumis_sky_island.shipping_chest": "出货箱", "key.yumis_sky_island.shop": "打开控制面板", "key.yumis_sky_island.cloud_leap": "梯云纵（按住跳跃键向上跃起）", "effect.yumis_sky_island.cloud_step": "踏云护体", "key.category.yumis_sky_island.sky_island": "Yumi 空岛"})
write("assets/yumis_sky_island/lang/en_us.json", {"generator.yumis_sky_island.sky_island": "Yumi Sky Island", "block.yumis_sky_island.shipping_chest": "Shipping Chest", "key.yumis_sky_island.shop": "Open Control Panel", "key.yumis_sky_island.cloud_leap": "Cloud Leap (hold Jump to leap upward)", "effect.yumis_sky_island.cloud_step": "Cloud Step", "key.category.yumis_sky_island.sky_island": "Yumi Sky Island"})
mixins = sorted(p.stem for p in (ROOT / "src/main/java/cn/yumis/skyisland/mixin").glob("*.java"))
write("yumis_sky_island.mixins.json", {"required": True, "package": "cn.yumis.skyisland.mixin", "compatibilityLevel": "JAVA_25", "mixins": mixins, "injectors": {"defaultRequire": 1}})
print(f"Prepared world preset, recipe, chest resources, translations and {len(mixins)} mixins")
