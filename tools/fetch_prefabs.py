"""Fetch the authors' ordinary public downloads; never solve browser challenges.

The server-local library is separate from the distributable mod. Downloaded HTML
is rejected as a structure. Download records include source, SHA256 and failures.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

from prefab_io import convert

ROOT = Path(__file__).resolve().parents[1]

# Values are confirmed catalogue defaults; runtime TOML remains administrator-editable.
PRESETS = {
    "basic": (10, "common", 1000000, 20000000),
    "standard": (25, "rare", 5000000, 100000000),
    "advanced": (50, "epic", 20000000, 500000000),
    "large": (75, "legendary", 50000000, 1000000000),
    "house": (5, "common", 1000000, 20000000),
    "base": (35, "epic", 10000000, 200000000),
    "wonder": (60, "legendary", 100000000, 2000000000),
}


SOURCES = [
    dict(id="carpet_duper",name="Bulletwingsga地毯复制机",author="Bulletwingsga；transcental收集库",category="生电",tier="advanced",source="https://github.com/transcental/Litematics",download="https://raw.githubusercontent.com/transcental/Litematics/main/Files/Bulletwingsga/1.2m_carpet_dupper_per_hour.litematic",usage="公开个人结构收集库；仅本服自用候选，保留原设计署名",notes="方向与启动状态需验收；不要按旧版作者标称速率承诺本服产量"),
    dict(id="mushroom_cottage", name="奇幻蘑菇住宅", author="BuildSchem", category="房屋", tier="house", source="https://www.schemcraft.com/schematics/fantasy-mushroom-cottage-survival-base", download="https://www.schemcraft.com/schematics/fantasy-mushroom-cottage-survival-base/download", usage="CC BY 4.0；保留作者与来源署名", notes="生物和箱内物资不随建筑提供；需要检查实际结构与内装"),
    dict(id="farmhouse_tower", name="大型农庄塔楼基地", author="BuildSchem", category="生存基地", tier="base", source="https://www.schemcraft.com/schematics/ultimate-survival-farmhouse-tower-base", download="https://www.schemcraft.com/schematics/ultimate-survival-farmhouse-tower-base/download", usage="CC BY 4.0；保留作者与来源署名", notes="大型长期施工项目；清空所有原作者箱内物资"),
    dict(id="moss_chromoss", name="Chromoss苔藓骨粉机", author="maidofmoss；OpenBagTwo改版", category="生电", tier="advanced", source="https://github.com/OpenBagTwo/Schematics", download="https://github.com/OpenBagTwo/Schematics/raw/refs/heads/main/schematics/AChromoss%20v0.4.litematic", usage="AGPL-3.0；保留署名并向服务器玩家免费提供原文件及修改版", notes="启动骨粉自备；支持苔藓／苍白苔藓，必须验证净骨粉与卸载重启"),
    dict(id="iron_compact", name="紧凑刷铁机", author="Couronne6 / Courone", category="生电", tier="standard", source="https://funcube.websr.fr/compactironfarm", download="https://www.mediafire.com/file/e36retgys12wmxe/Compact_iron_farm.schem/file", usage="作者公开下载，自用服务器候选；未声明公开再分发许可", notes="村民和僵尸自备；验证睡眠、惊吓、傀儡生成范围"),
    dict(id="mob_general", name="通用刷怪塔", author="Couronne6 / Courone", category="生电", tier="advanced", source="https://funcube.websr.fr/modfarm", download="https://www.mediafire.com/file/k1kqkh6y7ucd17k/Easy_mob_farm.schem/file", usage="作者公开下载，自用服务器候选；未声明公开再分发许可", notes="挂机点与附近可刷怪平台影响产量；多人共享怪物上限"),
]

for prefab_id, name, slug, category, tier in [
    ("medieval_house", "中世纪奇幻住宅", "grand-medieval-fantasy-survival-house-detailed-minecraft-schematic", "房屋", "house"),
    ("sakura_castle", "日式樱花城堡基地", "ultimate-japanese-sakura-castle-survival-base-2", "生存基地", "base"),
    ("shield_fortress", "巨盾堡垒基地", "ultimate-player-favorite-giant-shield-fortress-survival-base", "生存基地", "base"),
    ("cherry_dragon_pagoda", "樱龙宝塔", "ultimate-cherry-dragon-pagoda-survival-base", "奇观", "wonder"),
    ("greek_temple", "希腊神殿", "ultimate-greek-temple-survival-base", "奇观", "wonder"),
    ("moon_observatory", "新月观测城堡", "ultimate-crescent-moon-observatory-castle-survival-base", "奇观", "wonder"),
]:
    source = "https://www.schemcraft.com/schematics/" + slug
    SOURCES.append(dict(id=prefab_id, name=name, author="BuildSchem", category=category, tier=tier, source=source, download=source+"/download", usage=("CC BY-NC 4.0；仅非商业使用，保留署名" if prefab_id=="medieval_house" else "CC BY 4.0；保留作者与来源署名"), notes="作者原作导入；清空箱内赠品；大型工程请预留完整范围与施工时间"))

for prefab_id, name, path, tier in [
    ("iron_single", "Casual单模块刷铁机", "farms/iron-farm-single--casual", "standard"),
    ("smelter_16", "自动装载16炉熔炉组", "redstone/autoloading-super-smelter--casual", "standard"),
    ("wool_single", "单羊自动羊毛机", "farms/wool-farm-single--casual", "basic"),
]:
    stem=path.rsplit("/",1)[-1]
    SOURCES.append(dict(id=prefab_id,name=name,author="Casual",category="生电",tier=tier,source="https://github.com/CasualMinecraft/casual-builds/tree/main/"+path,download="https://raw.githubusercontent.com/CasualMinecraft/casual-builds/main/"+path+"/schematics/"+stem+"_v1.0.litematic",usage="作者允许个人世界使用、修改及学习；保留署名，不得冒名转载",notes="原作者测试Java1.21；需26.2验收；生物、燃料及剪刀自备；启动前检查开关"))

# Personal-use source archive. Metadata.Author from each original remains in sources.json;
# the repository owner is an archivist, not automatically the design's author.
for prefab_id, name, path, tier, notes in [
    ("sugar_cane", "Tango Tek甘蔗农场", "Farms/Plant-Based/Tango Tek Sugar Cane Farm.litematic", "standard", "甘蔗自然生长；需要保持水源与采收方向"),
    ("bamboo", "竹子自动农场", "Redstone/Farm_Components/Auto-Crafting/Storage Upgrades/bamboo_farm_with_vanilla_autocrafting.litematic", "standard", "原版合成器改版；自然生长；检查观察者和矿车收集"),
    ("kelp", "骨粉海带机", "Farms/Plant-Based/bonemealed_farm_kelp.litematic", "standard", "骨粉自备；水源最后注入"),
    ("flowers_tall", "双高花骨粉机", "Farms/Plant-Based/bonemealed_farm_2_block_high_flowers.litematic", "basic", "骨粉自备；种花后启动"),
    ("cocoa", "半自动可可农场", "Farms/Plant-Based/bonemealed_farm_cocoa_beans_semi_auto_.litematic", "basic", "需要玩家补种可可豆；骨粉自备"),
    ("wheat", "全自动小麦农场", "Farms/Plant-Based/wheat_farm_full_auto.litematic", "standard", "村民自备；作物与村民职业需在开机前检查"),
    ("tree_tnt", "Ilmango改版TNT复制树场", "Farms/Plant-Based/wood_farm_ilmango_angepasst_tnt_duper_storage.litematic", "large", "固定朝向；树苗和骨粉自备；TNT复制模块需单独装配验收"),
    ("stone_generator", "自动刷石机", "Farms/Blocks/stone_generator.litematic", "advanced", "熔岩和水最后安装；必须检查防爆与收集"),
    ("basalt", "玄武岩生成机", "Farms/Blocks/basalt_generator.litematic", "standard", "灵魂土、蓝冰与熔岩关系需保持"),
    ("mud_concrete", "泥巴与混凝土转换机", "Farms/Blocks/combined_mud_farm_and_concrete_converter.litematic", "standard", "玩家供土或混凝土粉末；水瓶与采掘自备"),
    ("dripstone", "滴水石微型农场", "Farms/Blocks/dripstone_microfarm.litematic", "basic", "随机刻生长；水源和滴水石方向需保持"),
    ("ice", "制冰场", "Farms/Blocks/ice_farm.litematic", "standard", "仅寒冷群系有效；玩家精准采集"),
    ("mushroom", "蘑菇农场", "Farms/Plant-Based/mushroom_farm.litematic", "advanced", "检查骨粉供给及生长空间"),
    ("honey_wax", "模块化蜂蜜／蜜脾农场", "Farms/Mob-Based/bee_farm_tileable.litematic", "standard", "蜜蜂、玻璃瓶和剪刀自备；可调整采收种类"),
    ("chicken", "自动养鸡装置", "Farms/Mob-Based/Chicken Farm.litematic", "basic", "种鸡自备；启动前确认熔岩和蛋路"),
    ("enderman", "末影人经验塔", "Farms/Mob-Based/Endermen Farm.litematic", "advanced", "只用于末地；末影螨自备；挂机点需远离主岛"),
    ("creeper", "LogicalGeekBoy苦力怕农场", "Farms/Mob-Based/creeper_farm_logical_geek_boy.litematic", "large", "猫自备；挂机距离和多人怪物上限影响产量"),
    ("gold", "下界猪灵金币塔", "Farms/Mob-Based/gold_farm_1_21_3.litematic", "large", "仅下界；不依赖跨维度传送门；检查群系与挂机高度"),
    ("piglin_barter", "猪灵交易站", "Farms/Mob-Based/piglin_barter_station.litematic", "advanced", "仅下界；猪灵和交易金锭自备"),
    ("slime", "Ilmango史莱姆农场", "Farms/Mob-Based/slime_farm_ilmango.litematic", "large", "需要本服史莱姆生成条件；检查区块、群系、高度和挂机点"),
    ("resin", "26.2四核心树脂机", "Farms/Mob-Based/resin_farm_4_hearts_autoclicker_26.2.litematic", "advanced", "嘎枝与核心需检查；玩家攻击触发；不附带自动点击器"),
    ("brewer", "26.2自动酿药机", "Redstone/autobrewer_26.2.litematic", "advanced", "药材、烈焰粉和水瓶自备；保留筛选配置"),
    ("crafter_compactor", "单级自动合成压缩机", "Redstone/Farm_Components/Auto-Crafting/block_compactor_-_single_stage_without_shulker_crafting.litematic", "standard", "保留合成器禁用槽；输入物料自备"),
    ("item_sorter", "分类仓储单元", "Redstone/Farm_Components/Sorted_Storage/storage_slice_single_row_storage_silo.litematic", "standard", "保留必要过滤占位物；建成后按需选择分类物"),
    ("shulker_loader", "潜影盒自动打包机", "Redstone/Farm_Components/Item_Transportation_and_Packaging/shulker_box_loader.litematic", "standard", "空潜影盒和待打包物资自备"),
]:
    SOURCES.append(dict(id=prefab_id,name=name,author="见原文件Metadata.Author；mmodrow收集库",category="生电",tier=tier,source="https://github.com/mmodrow/Minecraft-Litmatica-Schematics/blob/master/"+urllib.parse.quote(path),download="https://raw.githubusercontent.com/mmodrow/Minecraft-Litmatica-Schematics/master/"+urllib.parse.quote(path),usage="公开个人结构收集库；仅本服自用候选，未声明独立公开再分发许可",notes=notes+"；26.2实机验收前不上架"))


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            self.links.append(dict(attrs))


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "YumisSkyIsland-PrefabImporter/1.0 (personal server asset download)"})
    with urllib.request.urlopen(request, timeout=25) as response:
        data = response.read(32 * 1024 * 1024 + 1)
        if len(data) > 32 * 1024 * 1024:
            raise ValueError("下载超过32MiB")
        content_type, final = response.headers.get("Content-Type", ""), response.url
    if "html" in content_type or data.lstrip().startswith((b"<!DOCTYPE", b"<html")):
        if "mediafire.com" not in urllib.parse.urlparse(final).hostname:
            raise ValueError("下载端点返回网页，未绕过浏览器验证")
        parser = Links()
        parser.feed(data.decode("utf-8", "replace"))
        link = next((a.get("href") for a in parser.links if a.get("id") == "downloadButton"), None)
        if not link:
            raise ValueError("作者下载页没有可用的普通Download链接")
        return fetch(urllib.parse.urljoin(final, link))
    if not data.startswith((b"PK", b"\x1f\x8b", b"\x0a")):
        raise ValueError("下载不是ZIP/GZIP/NBT结构")
    return data, final


def quote(text):
    return json.dumps(text, ensure_ascii=False)


def write_catalog(library, records):
    path = library / "catalog.toml"
    import tomllib
    existing = tomllib.loads(path.read_text("utf-8")).get("templates", {}) if path.exists() else {}
    output = ["# 服务器本地建筑库；status只有通过实机验收后才能改为verified。", "# 价格为固定基价，图纸售卖另沿现有商店倍率。", ""]
    for record in records:
        level, rarity, price, fee = PRESETS[record["tier"]]
        if record["category"] == "生电":
            price, fee = max(1, price // 4), max(1, fee // 4)
        previous = existing.get(record["id"], {})
        status = "verified" if previous.get("status") == "verified" and previous.get("sha256") == record.get("sha256") else "imported" if "sha256" in record else "missing"
        author=record["author"]
        if "mmodrow" in author and record.get("originalMetadata",{}).get("Author"):
            author=record["originalMetadata"]["Author"]+"；mmodrow收集库，设计署名见使用说明"
        row = dict(name=record["name"], category=record["category"], author=author, sourceUrl=record["source"], usage=record["usage"], file=record["id"] + ".yprefab", status=status, level=level, rarity=rarity, blueprintPrice=price, buildFee=fee, rotations=previous.get("rotations", [0]) if status=="verified" else [0] if record["category"]=="生电" else [0,1,2,3], notes=record["notes"], sha256=record.get("sha256", ""))
        if record["id"] in ("gold","piglin_barter"):
            row["dimension"]="minecraft:the_nether"
        if record["id"]=="gold":
            row["biome"]="minecraft:nether_wastes"
        if record["id"]=="enderman":
            row["dimension"]="minecraft:the_end"
        if record["id"]=="ice":
            row["biome"]="minecraft:snowy_plains"
        for key in ("dimension", "biome", "alignX", "alignZ", "level", "rarity", "blueprintPrice", "buildFee"):
            if key in previous:
                row[key] = previous[key]
        output.append("[templates." + record["id"] + "]")
        for key, value in row.items():
            output.append(key + " = " + (quote(value) if isinstance(value, str) else json.dumps(value)))
        output.append("")
    path.write_text("\n".join(output), encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--library", type=Path, default=ROOT / "run/prefabs/yumis_sky_island")
    parser.add_argument("--only", nargs="*")
    args = parser.parse_args()
    library = args.library.resolve()
    if not library.is_relative_to(ROOT):
        raise ValueError("建筑库输出必须位于当前项目内")
    library.mkdir(parents=True, exist_ok=True)
    originals = library / "originals"
    originals.mkdir(exist_ok=True)
    previous_records=json.loads((library / "sources.json").read_text("utf-8")) if (library / "sources.json").exists() else []
    records = [r for r in previous_records if args.only and r["id"] not in args.only]
    for source in SOURCES:
        record = dict(source)
        if args.only and source["id"] not in args.only:
            continue
        try:
            raw = originals / (source["id"] + ".download")
            previous = next((r for r in previous_records if r["id"]==source["id"]),{})
            if raw.exists() and previous.get("download") and previous["download"] != source["download"]:
                backup = raw.with_name(source["id"] + "." + hashlib.sha256(raw.read_bytes()).hexdigest()[:12] + ".download")
                shutil.move(raw,backup)
            if not raw.exists():
                data, final = fetch(source["download"])
                raw.write_bytes(data)
                record["resolvedDownloadUrl"] = final
            brewing_stock={"minecraft:"+name for name in ("breeze_rod","blaze_rod","nether_wart","redstone","glowstone_dust","sugar","rabbit_foot","glistering_melon_slice","spider_eye","fermented_spider_eye","pufferfish","magma_cream","golden_carrot","ghast_tear","turtle_helmet","phantom_membrane","stone","slime_block","cobweb","gunpowder","dragon_breath")}
            report = convert(raw, library / (source["id"] + ".yprefab"), source["category"] == "生电",brewing_stock if source["id"]=="brewer" else frozenset())
            record.update(report)
            record["originalSha256"] = hashlib.sha256(raw.read_bytes()).hexdigest()
            print(json.dumps({"id": source["id"], "blocks": report["blocks"], "size": report["size"], "entities": report["entities"]}, ensure_ascii=False), flush=True)
        except Exception as error:
            record["error"] = str(error)
            print(json.dumps({"id": source["id"], "error": str(error)}, ensure_ascii=False), flush=True)
        records.append(record)
    records.sort(key=lambda r: next((i for i,s in enumerate(SOURCES) if s["id"]==r["id"]), len(SOURCES)))
    (library / "sources.json").write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    write_catalog(library, records)


if __name__ == "__main__":
    main()
