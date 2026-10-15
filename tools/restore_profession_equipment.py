"""Seed sell-only profession equipment from the cheapest valid vanilla recipe.

Existing product tables are never rewritten. Run with --check to inspect missing
ingredient values; plain execution appends only absent equipment products.
"""
from __future__ import annotations

from collections import defaultdict
from fractions import Fraction
from pathlib import Path
import argparse
import json
import tomllib
import zipfile

root = Path(__file__).resolve().parents[1]
config = root / "config/yumis_sky_island"
products_file = config / "shop/products.toml"
goods = tomllib.loads((config / "products/goods.toml").read_text(encoding="utf-8"))["items"]
products = tomllib.loads(products_file.read_text(encoding="utf-8"))["products"]
archive = zipfile.ZipFile(root / ".tools/minecraft-26.2-reference.jar")
members = set(archive.namelist())
recipes: dict[str, list[dict]] = defaultdict(list)
for path in members:
    if not path.startswith("data/minecraft/recipe/") or not path.endswith(".json"):
        continue
    recipe = json.loads(archive.read(path))
    result = recipe.get("result")
    if isinstance(result, dict) and "id" in result:
        recipes[result["id"]].append(recipe)

def options(ingredient: object, visiting: frozenset[str] = frozenset()) -> set[str]:
    if isinstance(ingredient, list):
        return set().union(*(options(value, visiting) for value in ingredient))
    if isinstance(ingredient, dict):
        if "item" in ingredient:
            return options(ingredient["item"], visiting)
        if "tag" in ingredient:
            return options("#" + ingredient["tag"], visiting)
        return set()
    if not isinstance(ingredient, str):
        return set()
    if not ingredient.startswith("#"):
        return {ingredient}
    tag = ingredient[1:]
    if tag in visiting:
        return set()
    namespace, _, name = tag.partition(":")
    path = f"data/{namespace or 'minecraft'}/tags/item/{name}.json"
    if path not in members:
        return set()
    definition = json.loads(archive.read(path))
    result: set[str] = set()
    for entry in definition.get("values", []):
        result |= options(entry if isinstance(entry, str) else entry.get("id", ""), visiting | {tag})
    return result

def ingredients(recipe: dict) -> list[set[str]] | None:
    kind = recipe.get("type", "")
    if kind == "minecraft:crafting_shaped":
        key = recipe["key"]
        return [options(key[letter]) for line in recipe["pattern"] for letter in line if letter != " "]
    if kind == "minecraft:crafting_shapeless":
        return [options(value) for value in recipe["ingredients"]]
    if kind == "minecraft:smithing_transform":
        return [options(recipe[key]) for key in ("template", "base", "addition")]
    return None

memo: dict[str, tuple[Fraction, list[str]] | None] = {}
derived: dict[str, Fraction] = {}
def value(item: str, stack: frozenset[str] = frozenset()) -> tuple[Fraction, list[str]] | None:
    if item in products:
        return Fraction(int(products[item]["baseValueCoins"])), [item]
    if item in stack:
        return None
    if item in memo:
        return memo[item]
    candidates = []
    for recipe in recipes.get(item, []):
        slots = ingredients(recipe)
        if slots is None or not slots or any(not slot for slot in slots):
            continue
        chosen = []
        for slot in slots:
            priced = [(price, option) for option in slot if (result := value(option, stack | {item})) is not None for price in [result[0]]]
            if not priced:
                break
            chosen.append(min(priced, key=lambda row: (row[0],row[1])))
        if len(chosen) != len(slots):
            continue
        amount = int(recipe["result"].get("count", 1))
        cost = sum((price for price, _ in chosen), Fraction()) / amount
        candidates.append((cost, [option for _, option in chosen]))
    memo[item] = min(candidates, key=lambda row: (row[0],row[1])) if candidates else None
    if memo[item] is not None:
        derived[item] = memo[item][0]
    return memo[item]

suffixes = ("_sword", "_axe", "_pickaxe", "_shovel", "_hoe", "_helmet", "_chestplate", "_leggings", "_boots", "_spear")
targets = sorted(item for item in goods if item.endswith(suffixes) or item in {"minecraft:bow", "minecraft:crossbow", "minecraft:mace", "minecraft:trident"})
missing = []
rows = []
for item in targets:
    if item in products:
        continue
    result = value(item)
    if result is None:
        missing.append(item)
        continue
    price, direct = result
    if price.denominator != 1:
        raise ValueError(f"Non-integral equipment base value: {item}: {price}")
    rows.append((item,int(price),direct))

parser = argparse.ArgumentParser()
parser.add_argument("--check", action="store_true")
arguments = parser.parse_args()
print(f"equipment: {len(targets)}, seedable: {len(rows)}, missing: {len(missing)}")
for item in missing:
    print("MISSING",item)
if arguments.check:
    raise SystemExit(0 if not missing else 1)
if missing:
    raise SystemExit("Missing recipe material valuation; no file was changed")

if rows:
    with products_file.open("a", encoding="utf-8", newline="\n") as target:
        target.write("\n# 职业目录装备仅收购；基础价值按原版有效配方中最低材料成本生成。\n")
        for item, price, materials in rows:
            target.write(f'\n[products."{item}"]\n')
            target.write(f'productId = "{item}"\nname = "{item}"\ncategoryHint = "职业装备"\n')
            target.write(f'baseValueCoins = {price}\ntradeMode = "sell_only"\ndailyStock = 1\nofferWeight = 1\n')
            target.write('priceOrigin = "profession_recipe"\n')
            target.write('recipeMaterials = [' + ', '.join(json.dumps(material) for material in materials) + ']\n')
            target.write(f'\n[products."{item}".canonicalStack]\nitemId = "{item}"\n')
            target.write(f'\n[products."{item}".canonicalStack.componentPatch]\n')

extra = {item:price for item, price in derived.items() if item not in products and any(item in materials for _,_,materials in rows)}
values_file = config / "shop/recipe_values.toml"
if rows:
    with values_file.open("w", encoding="utf-8", newline="\n") as target:
        target.write("# 非交易中间材料的配方基础价值；装备配方使用，删去被引用的值会导致配置校验报错。\n")
        target.write("schemaVersion = 1\n\n[ingredientValues]\n")
        for item, price in sorted(extra.items()):
            if price.denominator != 1:
                raise ValueError(f"Non-integral intermediate cost: {item}: {price}")
            target.write(f'"{item}" = {int(price)}\n')
