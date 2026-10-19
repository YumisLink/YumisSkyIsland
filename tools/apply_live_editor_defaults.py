"""Explicit, idempotent test-period synchronization for the confirmed scoring rules."""
from pathlib import Path
import re
from config_io import read, write, compact_generated_comments

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:water", "minecraft:lava", "minecraft:bubble_column"}
MESSAGE = "本岛{block}已达{threshold}块；后续每块{nextPoints}分（倍率{nextMultiplier}%，最低1分）。"

def apply(directory):
    path = directory / "islands/scoring.toml"
    if not path.exists():
        return
    data = read(path)
    data["tiers"] = {"default": [
        {"upToCount": 256, "multiplierBps": 10000},
        {"upToCount": 2048, "multiplierBps": 2500},
        {"upToCount": 8192, "multiplierBps": 500},
        {"upToCount": None, "multiplierBps": 100},
    ]}
    data["notices"] = {"enabled": True, "message": MESSAGE}
    for block_id, row in data.get("blocks", {}).items():
        if block_id in EXCLUDED:
            row["basePoints"] = 0
        row.pop("scoreGroup", None)
        row.pop("tierProfile", None)
    write(path, data)
    text = path.read_text(encoding="utf-8")
    text = text.replace("服务端规则重启生效，任务编辑器保存立即生效。", "基础分手改后重启；高分递减档位和通告可在管理员实时编辑器保存生效。")
    text = text.replace("# 仅空气、水、岩浆、气泡柱不计分，其余至少1分", "# 非负整数，0表示不计分；省略默认1")
    text = text.replace("# 递减阶梯；数量上限须递增，最后省略上限表示无限", "# 仅基础分>5应用本阶梯；≤5不递减。每档逐块向下取整且最低1分。阈值须递增，最后无限。")
    score_note = "# 方块基础分；非负整数，0不参与计分；条目、字段或整个blocks表省略默认1；仅>5递减并逐块取整最低1分"
    text = text.replace("# 该方块基础计分权重；非负整数，0表示不计分", score_note)
    text = text.replace("# 方块基础分；条目、字段或整个blocks表省略默认1。空气、水、岩浆、气泡柱固定不计分，其余须≥1。", score_note)
    text = text.replace("# 递减档位说明；目前实际读取 default 档位\n", "").replace("# 递减计分组 ID；同组方块共用数量\n", "")
    notice_note = "# 手动放置和建造杖跨档时，通知本队在线正式成员；跌破再到达会重发"
    if "\n[blocks]\n" in text:
        text = text.replace(notice_note + "\n", "")
        section = re.search(r"(?ms)^\[notices\]\n.*?(?=^\[|\Z)", text)
        if section:
            content = section.group().strip().replace("# 配置字段 enabled", "# 是否启用本队跨档通告")
            text = text[:section.start()] + text[section.end():]
            text = text.replace("\n[blocks]\n", "\n" + notice_note + "\n" + content + "\n\n[blocks]\n", 1)
    path.write_text(compact_generated_comments(text), encoding="utf-8", newline="\n")

if __name__ == "__main__":
    for directory in (ROOT / "config/yumis_sky_island", ROOT / "run/config/yumis_sky_island"):
        apply(directory)
        for relative in ("economy.toml", "server_rules.toml", "products/professions.toml", "products/profession_shop.toml", "gameplay/blood_moon.toml", "gameplay/lottery.toml", "islands/refresh.toml", "shop/products.toml"):
            path = directory / relative
            if not path.exists():
                continue
            text = path.read_text(encoding="utf-8")
            first, separator, rest = text.partition("\n")
            first = first.replace("服务端规则重启生效，任务编辑器保存立即生效。", "手改后重启；管理员白名单字段通过编辑器保存立即生效。")
            first = first.replace("修改后重启服务器生效。", "手改后重启；实时白名单保存立即生效。")
            first = first.replace("修改后重启生效。", "手改后重启；实时白名单保存立即生效。")
            text = first + separator + rest
            header = "# 管理员编辑：/is admin edit on；本文件仅白名单字段支持游戏内保存后实时生效。\n"
            if header not in text:
                text = header + text
            path.write_text(text, encoding="utf-8", newline="\n")
    for language, label in (("zh_cn", "实时配置（需Ctrl＋编辑模式）"), ("en_us", "Live configuration (Ctrl + editor mode)")):
        import json
        path = ROOT / f"src/main/resources/assets/yumis_sky_island/lang/{language}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data["key.yumis_sky_island.configuration"] = label
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Confirmed scoring defaults synchronized; unrelated gameplay values retained.")
