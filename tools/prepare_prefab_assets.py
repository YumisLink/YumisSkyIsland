"""Write code-native models and translations; pixel art is drawn through Aseprite MCP."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/yumis_sky_island"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def cube(name, low, high, front=False, rotation=None):
    result = dict(name=name, **{"from": low, "to": high})
    result["faces"] = {side: {"texture": "#front" if front and side == "north" else "#top" if side == "up" else "#side", "uv": [0, 0, 16, 16]} for side in ("down", "up", "north", "south", "west", "east")}
    if rotation:
        result["rotation"] = rotation
    return result


def main():
    for name in ("prefab_blueprint", "special_ammunition"):
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": "yumis_sky_island:item/" + name}})
        write(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "yumis_sky_island:item/" + name}})
    model = {"parent": "minecraft:block/block", "textures": {"particle": "yumis_sky_island:block/cannon_side", "side": "yumis_sky_island:block/cannon_side", "front": "yumis_sky_island:block/cannon_front", "top": "yumis_sky_island:block/cannon_top"}, "elements": [cube("base", [1, 0, 1], [15, 4, 15]), cube("left_wheel", [0, 2, 4], [3, 8, 12], True), cube("right_wheel", [13, 2, 4], [16, 8, 12], True), cube("saddle", [4, 4, 4], [12, 9, 12]), cube("barrel", [4, 8, 2], [12, 14, 14], True, {"origin": [8, 10, 8], "axis": "x", "angle": -22.5, "rescale": False})], "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]}, "ground": {"translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]}, "fixed": {"scale": [0.6, 0.6, 0.6]}}}
    write(ASSETS / "models/block/blueprint_cannon.json", model)
    write(ASSETS / "items/blueprint_cannon.json", {"model": {"type": "minecraft:model", "model": "yumis_sky_island:block/blueprint_cannon"}})
    write(ASSETS / "blockstates/blueprint_cannon.json", {"variants": {"facing=" + direction: {"model": "yumis_sky_island:block/blueprint_cannon", **({"y": angle} if angle else {})} for direction, angle in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]}})
    for language, rows in [("zh_cn", {"block.yumis_sky_island.blueprint_cannon": "蓝图大炮", "item.yumis_sky_island.prefab_blueprint": "建筑图纸", "item.yumis_sky_island.special_ammunition": "特殊弹药", "key.yumis_sky_island.prefab_position": "返回图纸位置操作"}), ("en_us", {"block.yumis_sky_island.blueprint_cannon": "Blueprint Cannon", "item.yumis_sky_island.prefab_blueprint": "Building Blueprint", "item.yumis_sky_island.special_ammunition": "Special Ammunition", "key.yumis_sky_island.prefab_position": "Adjust Blueprint Position"})]:
        path = ASSETS / f"lang/{language}.json"
        data = json.loads(path.read_text("utf-8"))
        data.update(rows)
        write(path, data)


if __name__ == "__main__":
    main()
