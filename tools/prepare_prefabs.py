"""Add confirmed cannon defaults without rewriting unrelated administrator TOML."""
from pathlib import Path
import shutil
import tomllib

ROOT = Path(__file__).resolve().parents[1]
ENTRIES = [
    ("blueprint_cannon", "蓝图大炮", 100000, 1),
    ("special_ammunition", "特殊弹药", 1000, 0),
]


def append(path, marker, text):
    old = path.read_text("utf-8")
    if marker not in old:
        with path.open("a", encoding="utf-8", newline="\n") as out:
            out.write("\n" + text.strip() + "\n")


def update(folder):
    for item, name, value, level in ENTRIES:
        identifier = "yumis_sky_island:" + item
        append(folder / "shop/products.toml", '[products."' + identifier + '"]', f'''
# {name}，仅购买；弹药实际套餐价由gameplay/prefabs.toml控制。
[products."{identifier}"]
productId = "{identifier}"
name = "{name}"
baseValueCoins = {value}
buyBaseValueCoins = {value}
buyMultiplierBps = 10000
tradeMode = "buy_only"
dailyStock = 1
offerWeight = 1
shopCategory = "minecraft:tools_and_utilities"
priceOrigin = "confirmed_prefabs"
[products."{identifier}".canonicalStack]
itemId = "{identifier}"
[products."{identifier}".canonicalStack.componentPatch]
''')
        # itemLevels / itemRarities are a single table; append inside it, not after another table.
        for filename, table, data in [("item_levels.toml", "itemLevels", str(level)), ("rarities.toml", "itemRarities", '"common"')]:
            path = folder / "shop" / filename
            text = path.read_text("utf-8")
            parsed = tomllib.loads(text)
            if identifier not in parsed[table]:
                header = "[" + table + "]"
                position = text.index(header) + len(header)
                text = text[:position] + f'\n# {name}\n"{identifier}" = {data}\n' + text[position:]
                path.write_text(text, encoding="utf-8", newline="\n")
    target = folder / "gameplay/prefabs.toml"
    target.parent.mkdir(parents=True, exist_ok=True)
    default = ROOT / "config/yumis_sky_island/gameplay/prefabs.toml"
    if target.resolve() != default.resolve() and not target.exists():
        shutil.copy2(default, target)


if __name__ == "__main__":
    update(ROOT / "config/yumis_sky_island")
    if (ROOT / "run/config/yumis_sky_island/shop/products.toml").exists():
        update(ROOT / "run/config/yumis_sky_island")
    print("蓝图大炮、特殊弹药默认与开发TOML已同步；其他条目保留。")
