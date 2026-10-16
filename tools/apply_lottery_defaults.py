"""Explicitly apply the confirmed test-period lottery defaults; not a world migration."""
from pathlib import Path
import json
from config_io import read, write, compact_generated_comments

ROOT = Path(__file__).resolve().parents[1]
TICKET = "yumis_sky_island:lottery_ticket"


def lottery_text():
    text = '''# 单池抽奖／祈愿；UTF-8。奖品目录和保底参数手改重启，中文注释可自行补充。
# 原神模式仅改变称呼；普通玩家不会接收真实概率或完整奖池。
enabled = true
# 每兑换一张券消耗的附魔等级／个人代币；正整数，可实时编辑。
experienceCost = 30
tokenCost = 160
# 每次成功领取任务（含悬赏）／每个首次经历的岛等级给予的个人代币；非负整数。
taskTokenReward = 20
levelTokenReward = 20
# 传说内UP概率；万分数。歪后下一件传说必为当时UP，唯一传说直接作为UP。
featuredBps = 5000

# 基础概率万分数；普通取10000减去这三项，合计不得超过10000，可实时编辑。
[rarityBps]
rare = 3500
epic = 200
legendary = 60

# 连续softStart抽未出传说后线性递增，legendaryAt抽必出；epicAt抽保底史诗。
[pity]
softStart = 40
legendaryAt = 50
epicAt = 10

# 奖品固定ID必须唯一；每次只发1件，同档等概率，islandLevel为当前岛等级要求。
# 稀有度与商店独立：common普通、rare稀有、epic史诗、legendary传说。
# components为26.2物品组件，可选；例如在对应条目下增加[prizes.components]配置组件。
# 启动取最低传说等级作为解锁门槛；该等级下须能兑现各概率档及史诗／传说保底。
'''
    for material, rarity, level in [("iron", "common", 10), ("golden", "rare", 30), ("diamond", "epic", 35), ("netherite", "legendary", 75)]:
        for kind in ["axe", "shovel", "sword", "hoe", "helmet", "chestplate", "leggings", "boots"]:
            item = material + "_" + kind
            text += f'\n[[prizes]]\nid = "{item}"\nitemId = "minecraft:{item}"\nrarity = "{rarity}"\nislandLevel = {level}\n'
    return text


def apply(folder):
    if not (folder / "shop/products.toml").exists():
        return
    lottery = folder / "gameplay/lottery.toml"
    lottery.parent.mkdir(parents=True, exist_ok=True)
    if not lottery.exists():
        lottery.write_text(lottery_text(), encoding="utf-8", newline="\n")
    obsolete = folder / "gameplay/experience_lottery.toml"
    if obsolete.exists():
        obsolete.unlink()
    products = read(folder / "shop/products.toml")
    products["products"][TICKET] = {
        "productId": TICKET, "name": "抽奖券", "categoryHint": "功能道具", "shopCategory": "yumis_sky_island:items",
        "baseValueCoins": 100000, "buyBaseValueCoins": 100000, "buyMultiplierBps": 10000,
        "tradeMode": "buy_only", "dailyStock": -1, "offerWeight": 1,
        "canonicalStack": {"itemId": TICKET, "componentPatch": {}},
    }
    write(folder / "shop/products.toml", products)
    product_file = folder / "shop/products.toml"
    comments = product_file.read_text("utf-8").replace("# 每日／隐藏每轮全队共享库存；正整数\n", "# 每日／隐藏每轮全队共享库存；正整数，-1表示不限量\n")
    product_file.write_text(compact_generated_comments(comments), encoding="utf-8", newline="\n")
    for name, table, value in [("item_levels", "itemLevels", 0), ("rarities", "itemRarities", "legendary")]:
        file = folder / f"shop/{name}.toml"
        data = read(file); data[table][TICKET] = value; write(file, data)


if __name__ == "__main__":
    for folder in [ROOT / "config/yumis_sky_island", ROOT / "run/config/yumis_sky_island", ROOT / "build/run/clientGameTest/config/yumis_sky_island"]:
        apply(folder)
    for language, name in [("zh_cn", "抽奖券"), ("en_us", "Lottery Ticket")]:
        file = ROOT / f"src/main/resources/assets/yumis_sky_island/lang/{language}.json"
        data = json.loads(file.read_text("utf-8")); data["item.yumis_sky_island.lottery_ticket"] = name
        file.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
