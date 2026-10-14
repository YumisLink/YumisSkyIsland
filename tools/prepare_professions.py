"""Maintain a recipe based manufacture directory independently of the sale catalog."""
import json
from pathlib import Path
from config_io import read, write

ROOT = Path(__file__).resolve().parents[1] / "config/yumis_sky_island"
registered = read(ROOT / "shop/rarities.toml")["itemRarities"]
facts = json.loads((ROOT.parents[1] / "tools/data/shop-facts-26.2.json").read_text(encoding="utf-8-sig"))
products = {"minecraft:" + item: {"canonicalStack": {"componentPatch": {}}, "priceOrigin": "standard_recipe"} for item in facts["recipeOutputs"] if "minecraft:" + item in registered}
products["yumis_sky_island:shipping_chest"] = {"canonicalStack": {"componentPatch": {}}, "priceOrigin": "custom_shipping_chest"}
foods = {"bread", "cake", "cookie", "pumpkin_pie", "beetroot_soup", "mushroom_stew", "rabbit_stew", "suspicious_stew", "golden_carrot", "golden_apple", "baked_potato", "cooked_beef", "cooked_chicken", "cooked_cod", "cooked_mutton", "cooked_porkchop", "cooked_rabbit", "cooked_salmon", "dried_kelp"}
crafts = {"chest", "barrel", "hopper", "dispenser", "dropper", "observer", "piston", "sticky_piston", "repeater", "comparator", "furnace", "blast_furnace", "smoker", "anvil", "enchanting_table", "bookshelf", "chiseled_bookshelf", "lectern", "bell", "beacon", "conduit", "daylight_detector", "note_block", "jukebox", "crafter", "redstone_lamp", "target", "rail", "powered_rail", "detector_rail", "activator_rail", "lantern", "soul_lantern", "brewing_stand", "cauldron", "lodestone", "respawn_anchor", "composter", "bee_nest", "beehive", "scaffolding", "flower_pot", "glass", "tinted_glass"}
path = ROOT / "products/goods.toml"
data = read(path) if path.exists() else {"schemaVersion": 1, "items": {}}
goods = data["items"]
for key, row in products.items():
    if row["canonicalStack"]["componentPatch"] or row["priceOrigin"] not in {"recipe_path", "standard_recipe", "custom_shipping_chest"}:
        continue
    name = key.split(":", 1)[1]
    profession = None
    if name.endswith(("_pickaxe", "_axe", "_hoe", "_shovel", "_sword", "_spear", "_helmet", "_chestplate", "_leggings", "_boots")) or name in {"mace", "shield", "shears", "flint_and_steel"}:
        profession = "blacksmith"
    elif name in foods:
        profession = "chef"
    elif name in {"bow", "crossbow", "arrow", "spectral_arrow", "fishing_rod", "carrot_on_a_stick", "warped_fungus_on_a_stick"}:
        profession = "hunter"
    elif name.endswith(("_stairs", "_slab", "_wall", "_bricks", "_tiles")) and not any(x in name for x in ("oak", "birch", "spruce", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak")):
        profession = "stonemason"
    elif name in {"hay_block", "bone_meal", "sugar", "wheat_seeds", "melon_seeds", "pumpkin_seeds"}:
        profession = "farmer"
    elif name in crafts or name.endswith(("_boat", "_chest_boat", "_bed", "_sign", "_hanging_sign", "_banner", "_shelf")) or key == "yumis_sky_island:shipping_chest":
        profession = "artisan"
    if profession:
        goods.setdefault(key, {"profession": profession, "qualityAllowed": True})
write(path, data)
print(f"{len(goods)} editable manufactured goods")
