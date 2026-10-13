"""Generate the editable manufacture directory from the reviewed catalog, not all registry items."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "config/yumis_sky_island"
products = json.loads((ROOT / "shop/products.json").read_text(encoding="utf-8"))["products"]
foods = {"bread", "cake", "cookie", "pumpkin_pie", "beetroot_soup", "mushroom_stew", "rabbit_stew", "suspicious_stew", "golden_carrot", "golden_apple", "baked_potato", "cooked_beef", "cooked_chicken", "cooked_cod", "cooked_mutton", "cooked_porkchop", "cooked_rabbit", "cooked_salmon", "dried_kelp"}
crafts = {"chest", "barrel", "hopper", "dispenser", "dropper", "observer", "piston", "sticky_piston", "repeater", "comparator", "furnace", "blast_furnace", "smoker", "anvil", "enchanting_table", "bookshelf", "chiseled_bookshelf", "lectern", "bell", "beacon", "conduit", "daylight_detector", "note_block", "jukebox", "crafter", "redstone_lamp", "target", "rail", "powered_rail", "detector_rail", "activator_rail", "lantern", "soul_lantern", "brewing_stand", "cauldron", "lodestone", "respawn_anchor", "composter", "bee_nest", "beehive", "scaffolding", "flower_pot", "glass", "tinted_glass"}
goods = {}
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
        goods[key] = {"profession": profession, "qualityAllowed": True}
(ROOT / "products/goods.json").write_text(json.dumps({"schemaVersion": 1, "items": goods}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{len(goods)} editable manufactured goods")
