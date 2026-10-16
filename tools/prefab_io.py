"""Bounded NBT reader and offline conversion for server-local prefab assets.

No Minecraft mods are required by players to use the normalized .yprefab files.
Original files and their source metadata must be retained alongside conversions.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import math
import struct
import zipfile
from dataclasses import dataclass
from pathlib import Path


@dataclass
class Tag:
    kind: int
    value: object


class NbtReader:
    def __init__(self, data: bytes):
        self.data = io.BytesIO(data)
        self.nodes = 0

    def take(self, count: int) -> bytes:
        if not 0 <= count <= 128 * 1024 * 1024:
            raise ValueError("NBT长度越界")
        result = self.data.read(count)
        if len(result) != count:
            raise ValueError("NBT文件被截断")
        return result

    def number(self, code):
        return struct.unpack(">" + code, self.take(struct.calcsize(code)))[0]

    def string(self):
        return self.take(self.number("H")).decode("utf-8")

    def payload(self, kind, depth=0):
        self.nodes += 1
        if depth > 128 or self.nodes > 20000000:
            raise ValueError("NBT层数或节点数过多")
        if kind in (1, 2, 3, 4, 5, 6):
            return Tag(kind, self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind]))
        if kind == 7:
            return Tag(kind, self.take(self.number("i")))
        if kind == 8:
            return Tag(kind, self.string())
        if kind == 9:
            subtype, length = self.number("B"), self.number("i")
            if not 0 <= length <= 16777216:
                raise ValueError("NBT列表长度越界")
            return Tag(kind, [self.payload(subtype, depth + 1) for _ in range(length)])
        if kind == 10:
            result = {}
            while (subtype := self.number("B")) != 0:
                name = self.string()
                if name in result:
                    raise ValueError("NBT键重复")
                result[name] = self.payload(subtype, depth + 1)
            return Tag(kind, result)
        if kind in (11, 12):
            length = self.number("i")
            if not 0 <= length <= 16777216:
                raise ValueError("NBT数组长度越界")
            size, code = (4, "i") if kind == 11 else (8, "q")
            return Tag(kind, [v[0] for v in struct.iter_unpack(">" + code, self.take(length * size))])
        raise ValueError(f"未知NBT类型 {kind}")

    def root(self):
        if self.number("B") != 10:
            raise ValueError("文件不是NBT Compound，可能下载成了网页")
        self.string()
        return self.payload(10)


def plain(tag):
    if isinstance(tag, Tag):
        if tag.kind == 10:
            return {key: plain(value) for key, value in tag.value.items()}
        if tag.kind == 9:
            return [plain(value) for value in tag.value]
        return tag.value
    return tag


def snbt(tag):
    kind, value = tag.kind, tag.value
    if kind == 10:
        return "{" + ",".join(json.dumps(k, ensure_ascii=False) + ":" + snbt(v) for k, v in value.items()) + "}"
    if kind == 9:
        return "[" + ",".join(snbt(v) for v in value) + "]"
    if kind == 8:
        return json.dumps(value, ensure_ascii=False)
    if kind in (7, 11, 12):
        prefix = {7: "B", 11: "I", 12: "L"}[kind]
        values = [(v - 256 if v > 127 else v) for v in value] if kind == 7 else value
        suffix = {7: "b", 11: "", 12: "L"}[kind]
        return "[" + prefix + ";" + ",".join(str(v) + suffix for v in values) + "]"
    return str(value) + {1: "b", 2: "s", 3: "", 4: "L", 5: "f", 6: "d"}[kind]


def read_nbt(path):
    data = Path(path).read_bytes()
    if len(data) > 32 * 1024 * 1024:
        raise ValueError("压缩文件超过32MiB")
    if data.startswith(b"PK"):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            files = [f for f in archive.infolist() if Path(f.filename).suffix.lower() in (".schem", ".schematic", ".litematic", ".nbt")]
            if len(files) != 1:
                raise ValueError(f"ZIP内有{len(files)}份结构，请明确选取文件")
            if files[0].file_size > 128 * 1024 * 1024:
                raise ValueError("ZIP结构过大")
            data = archive.read(files[0])
    if data.startswith(b"\x1f\x8b"):
        with gzip.GzipFile(fileobj=io.BytesIO(data)) as stream:
            data = stream.read(128 * 1024 * 1024 + 1)
    if len(data) > 128 * 1024 * 1024:
        raise ValueError("解压后的结构超过128MiB")
    return NbtReader(data).root()


def state_string(tag):
    value = plain(tag)
    name = value["Name"]
    properties = value.get("Properties", {})
    return name + ("[" + ",".join(f"{k}={v}" for k, v in sorted(properties.items())) + "]" if properties else "")


def modern_state(state):
    """Explicit vanilla renames only; unknown states still fail the Java registry check."""
    import re
    name, _, tail = state.partition("[")
    properties = dict(part.split("=", 1) for part in tail.rstrip("]").split(",") if "=" in part)
    name = {"minecraft:chain": "minecraft:iron_chain", "minecraft:grass": "minecraft:short_grass", "minecraft:grass_path": "minecraft:dirt_path"}.get(name, name)
    if name == "minecraft:cauldron" and "level" in properties:
        if int(properties["level"]) > 0:
            name = "minecraft:water_cauldron"
        else:
            properties.pop("level")
    if name.endswith("_wall"):
        for direction in ("north", "south", "west", "east"):
            if properties.get(direction) in ("true", "false"):
                properties[direction] = "low" if properties[direction] == "true" else "none"
    return name + ("[" + ",".join(f"{k}={v}" for k, v in sorted(properties.items())) + "]" if properties else "")


def varints(data, count):
    output, value, bits = [], 0, 0
    for byte in data:
        value |= (byte & 127) << bits
        if byte & 128:
            bits += 7
            if bits > 28:
                raise ValueError("方块VarInt过长")
        else:
            output.append(value)
            value, bits = 0, 0
    if bits or len(output) != count:
        raise ValueError("方块数量与结构尺寸不匹配")
    return output


def unpack_litematic(data, bits, count):
    mask = (1 << bits) - 1
    words = [v & ((1 << 64) - 1) for v in data]
    for index in range(count):
        bit = index * bits
        word, shift = divmod(bit, 64)
        if word >= len(words):
            raise ValueError("Litematic方块数组被截断")
        value = words[word] >> shift
        if shift + bits > 64:
            value |= words[word + 1] << (64 - shift)
        yield value & mask


OPERATIONAL = {"minecraft:bone_meal", "minecraft:coal", "minecraft:charcoal", "minecraft:blaze_powder", "minecraft:glass_bottle", "minecraft:shears", "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tnt"}


def sanitize_block_entity(tag, automation, state="", operational_items=frozenset()):
    if tag is None:
        return None, []
    fields = dict(tag.value)
    block_id = plain(fields.get("id", fields.get("Id", Tag(8, state.split("[")[0]))))
    allowed = {"id", "Id", "disabled_slots", "Patterns", "patterns", "Base", "base_color", "profile", "note", "color", "Color"}
    clean = {k: v for k, v in fields.items() if k in allowed}
    ingredients = []
    items = fields.get("Items", fields.get("items"))
    if automation and items and any(name in block_id for name in ("hopper", "dropper", "dispenser", "crafter")):
        kept = []
        for raw in items.value:
            entry = raw.value
            item = plain(entry.get("id", Tag(8, "minecraft:air")))
            count = int(plain(entry.get("count", entry.get("Count", Tag(3, 0)))))
            slot = int(plain(entry.get("Slot", entry.get("slot", Tag(3, -1)))))
            if not isinstance(item, str) or not item.startswith("minecraft:") or item in OPERATIONAL or item in operational_items or not 0 < count <= 64 or not 0 <= slot < 54:
                continue
            converted = {"id": Tag(8, item), "count": Tag(3, count), "Slot": Tag(1, slot)}
            components = entry.get("components", Tag(10, {})).value
            name = components.get("minecraft:custom_name")
            legacy = entry.get("tag", Tag(10, {})).value.get("display", Tag(10, {})).value.get("Name")
            if name or legacy:
                converted["components"] = Tag(10, {"minecraft:custom_name": name or legacy})
            kept.append(Tag(10, converted))
            ingredients.append([item, count])
        if kept:
            clean["Items"] = Tag(9, kept)
    return Tag(10, clean), ingredients


def cells_from_nbt(root):
    d = root.value
    cells, entities = {}, []
    if "Regions" in d:
        for region in d["Regions"].value.values():
            region = region.value
            position = plain(region["Position"])
            size = plain(region["Size"])
            dims = [abs(int(size[a])) for a in "xyz"]
            if not all(dims) or math.prod(dims) > 16777216:
                raise ValueError("Litematic区域尺寸越界")
            minimum = [position[a] + min(0, size[a] + 1) for a in "xyz"]
            palette = [state_string(t) for t in region["BlockStatePalette"].value]
            bits = max(2, (len(palette) - 1).bit_length())
            block_entities = {}
            for tag in region.get("TileEntities", Tag(9, [])).value:
                value = plain(tag)
                block_entities[(value["x"], value["y"], value["z"])] = tag
            for i, p in enumerate(unpack_litematic(region["BlockStates"].value, bits, math.prod(dims))):
                x = i % dims[0]
                z = (i // dims[0]) % dims[2]
                y = i // (dims[0] * dims[2])
                key = (minimum[0] + x, minimum[1] + y, minimum[2] + z)
                if palette[p].startswith("minecraft:structure_void"):
                    continue
                cells[key] = (palette[p], block_entities.get((x, y, z)))
            for entry in region.get("Entities", Tag(9, [])).value:
                tag = entry.value.get("EntityData", entry)
                fields = dict(tag.value)
                pos = fields.get("Pos")
                if pos:
                    # Entity coordinates are relative to the signed region origin;
                    # block-entity coordinates are relative to its minimum corner.
                    fields["Pos"] = Tag(9, [Tag(6, plain(v) + position["xyz"[i]]) for i, v in enumerate(pos.value)])
                entities.append(Tag(10, fields))
    elif "Schematic" in d or "Palette" in d or "Blocks" in d and d.get("Blocks").kind == 10:
        d = d["Schematic"].value if "Schematic" in d else d
        dims = [int(plain(d[a])) for a in ("Width", "Height", "Length")]
        if not all(n > 0 for n in dims) or math.prod(dims) > 16777216:
            raise ValueError("Schem尺寸越界")
        blocks = d["Blocks"].value if "Blocks" in d and d["Blocks"].kind == 10 else d
        palette = {int(plain(v)): k for k, v in blocks["Palette"].value.items()}
        packed = blocks.get("Data", blocks.get("BlockData"))
        indices = varints(packed.value, math.prod(dims))
        block_entities = {}
        for tag in blocks.get("BlockEntities", d.get("BlockEntities", Tag(9, []))).value:
            fields = dict(tag.value)
            position = plain(fields.pop("Pos"))
            if "Id" in fields:
                fields["id"] = fields.pop("Id")
            data = fields.pop("Data", None)
            if data:
                fields.update(data.value)
            block_entities[tuple(position)] = Tag(10, fields)
        for i, index in enumerate(indices):
            x, z, y = i % dims[0], (i // dims[0]) % dims[2], i // (dims[0] * dims[2])
            if palette[index].startswith("minecraft:structure_void"):
                continue
            cells[(x, y, z)] = (palette[index], block_entities.get((x, y, z)))
        entities = d.get("Entities", Tag(9, [])).value
    elif "palette" in d and "blocks" in d:
        palette = [state_string(t) for t in d["palette"].value]
        for raw in d["blocks"].value:
            raw = raw.value
            position = tuple(plain(raw["pos"]))
            cells[position] = (palette[plain(raw["state"])], raw.get("nbt"))
        for raw in d.get("entities", Tag(9, [])).value:
            entities.append(raw.value.get("nbt", raw))
    else:
        raise ValueError("不支持此结构格式；旧数字ID的.schematic须先转为现代Sponge .schem")
    return cells, entities


def convert(source, destination, automation=True, operational_items=frozenset()):
    root = read_nbt(source)
    cells, entities = cells_from_nbt(root)
    if not cells:
        raise ValueError("结构为空")
    foundation_count=0
    if automation:
        floor=min(p[1] for p in cells)
        supports=[]
        for (x,y,z),(state,_) in cells.items():
            name=state.split("[")[0]
            needs_floor=name in ("minecraft:redstone_wire","minecraft:repeater","minecraft:comparator","minecraft:torch","minecraft:redstone_torch","minecraft:rail","minecraft:powered_rail","minecraft:detector_rail","minecraft:activator_rail","minecraft:tripwire") or name.endswith("_pressure_plate") or name.endswith("_carpet") or ("face=floor" in state and (name.endswith("_button") or name=="minecraft:lever"))
            if y==floor and needs_floor:
                supports.append((x,y-1,z))
        for p in supports:
            cells[p]=("minecraft:stone",None)
        foundation_count=len(supports)
    minimum = [min(p[a] for p in cells) for a in range(3)]
    maximum = [max(p[a] for p in cells) for a in range(3)]
    size = [maximum[a] - minimum[a] + 1 for a in range(3)]
    if math.prod(size) > 16777216:
        raise ValueError("结构合并体积超过限制")
    palette, palette_map, blocks, air_positions = [], {}, [], []
    notes, inventory_count = set(), 0
    if foundation_count:
        notes.add("补齐作者选区省略的最低层红石／铁轨承托，新增石头"+str(foundation_count)+"块")
    for position, (state, entity) in cells.items():
        state = modern_state(state)
        if not automation:
            name=state.split("[")[0]
            stable={"minecraft:gravel":"minecraft:andesite","minecraft:sand":"minecraft:sandstone","minecraft:red_sand":"minecraft:red_sandstone"}.get(name)
            if name.endswith("_concrete_powder"):
                stable=name.removesuffix("_powder")
            if stable:
                state=stable
                notes.add("外观模板的重力装饰改用相近非重力建材，避免悬空路径坠落")
        position = [position[a] - minimum[a] for a in range(3)]
        # Author editing markers are not survival construction materials.
        if state.split("[")[0] in ("minecraft:light", "minecraft:barrier"):
            notes.add("剔除作者辅助光源／屏障标记")
            air_positions.append(position)
            continue
        if state in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
            air_positions.append(position)
            continue
        # Flowing liquid is produced by its sources after construction, not purchased as separate buckets.
        if (state.startswith("minecraft:water[") or state.startswith("minecraft:lava[")) and "level=0" not in state:
            air_positions.append(position)
            continue
        if state not in palette_map:
            palette_map[state] = len(palette)
            palette.append(state)
        clean, ingredients = sanitize_block_entity(entity, automation, state, operational_items)
        inventory_count += sum(v[1] for v in ingredients)
        row = position + [palette_map[state], snbt(clean) if clean and clean.value else None]
        if ingredients:
            row += [-1, ingredients]
        blocks.append(row)
    air_positions.sort(key=lambda p: (p[1], p[2], p[0]))
    runs = []
    for x, y, z in air_positions:
        if runs and runs[-1][1:3] == [y, z] and runs[-1][0] + runs[-1][3] == x:
            runs[-1][3] += 1
        else:
            runs.append([x, y, z, 1])
    normalized_entities = []
    allowed = {"minecraft:minecart", "minecraft:hopper_minecart", "minecraft:chest_minecart", "minecraft:boat", "minecraft:oak_boat", "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:armor_stand", "minecraft:painting"}
    for tag in entities:
        fields = dict(tag.value)
        if plain(fields.get("id", fields.get("Id", Tag(8, "")))) not in allowed:
            continue
        for key in ("UUID", "UUIDMost", "UUIDLeast", "Passengers", "Leash", "Items", "Item", "ArmorItems", "HandItems", "equipment", "Equipment", "body_armor_item", "LootTable", "CustomName", "Tags"):
            fields.pop(key, None)
        fields["Motion"] = Tag(9, [Tag(6, 0.0), Tag(6, 0.0), Tag(6, 0.0)])
        if "Pos" in fields:
            fields["Pos"] = Tag(9, [Tag(6, plain(v) - minimum[i]) for i, v in enumerate(fields["Pos"].value)])
        normalized_entities.append(snbt(Tag(10, fields)))
    result = {"format": 1, "size": size, "palette": palette, "blocks": blocks, "air": runs, "entities": normalized_entities}
    output = Path(destination)
    output.parent.mkdir(parents=True, exist_ok=True)
    data = gzip.compress(json.dumps(result, ensure_ascii=False, separators=(",", ":")).encode(), mtime=0)
    output.write_bytes(data)
    metadata=plain(root.value.get("Metadata", Tag(10,{})))
    metadata={k:v for k,v in metadata.items() if k in ("Author","Name","Description","TimeCreated","TimeModified")}
    return {"sourceFile": str(source), "file": str(output), "originalMetadata":metadata, "size": size, "blocks": len(blocks), "airCells": len(air_positions), "entities": len(normalized_entities), "initializationItems": inventory_count, "foundationBlocks":foundation_count, "conversionNotes": sorted(notes), "sha256": hashlib.sha256(data).hexdigest()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--decorative", action="store_true", help="清空全部容器，不导入机器过滤物品")
    args = parser.parse_args()
    print(json.dumps(convert(args.source, args.destination, not args.decorative), ensure_ascii=False))


if __name__ == "__main__":
    main()
