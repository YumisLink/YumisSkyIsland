"""Render review tables from editable automation/economy configuration."""
from fractions import Fraction
from pathlib import Path


def cell(value) -> str:
    return str(value).replace("|", "\\|").replace("\n", " ")


def table_row(values) -> str:
    return "| " + " | ".join(map(cell, values)) + " |"


def render_automation(cfg: Path, data: dict) -> None:
    products = data["products"]["products"]
    levels = data["levels"]["itemLevels"]
    quota = data["sale_quotas"]
    divisor = data["shops"].get("saleDivisor", 10)
    production = [
        "# 原版生电产出与购买解锁表（26.2审阅草案）", "",
        "基线：正常生存、纯空地形、无自然结构，只依赖Fabric API与本模组；不把Carpet扩展或复制漏洞算作原版产出。",
        "本表的等级用于商店购买核心材料。自行获得、合成、摆放、繁殖和正常原版生产不受该等级阻止。",
        "生产链的建议准备阶段不是机器购买资格；实际购买门槛在下一张表中列出。",
        "Q34～Q48删除了机器、颜色方块、工具／战斗装备等商品，仍能生产或制作的物品未必可收购。",
        "潜影贝蛋和硫磺尖刺已禁交易；全空世界缺少首只潜影贝／母尖刺，当前不能从零启动这两类农场。",
        "表内建议阶段不等于硬性封锁，也不保证某个农场在所有生物群系或服务器设置下运行。", "",
        "## 生产链", "",
        "| 链条 | 核心材料/条件 | 主要产物 | 建议准备阶段 | 原版可用性 | 注意事项 |",
        "|---|---|---|---:|---|---|",
    ]
    for row in data["production"]["rules"]:
        production.append(table_row([row["name"], row["startupCore"], row["outputs"],
                                     row["suggestedCoreShopLevel"], row["availability"], row["notes"]]))
    production += [
        "", "## 核心材料的实际购买门槛", "",
        "所需材料可先自行获取；可合成机器不再上架，也不限制提前自行合成。这里只列保留购买资格的核心材料。",
        "全队岛等级为当前存量等级；职业等级是另一套个人等级。稀有商品解锁后仍需每日池实际抽中。", "",
        "| 核心材料 | ID | 商店岛等级 | 基础价值 | 基准购买价 | 购买渠道 |",
        "|---|---|---:|---:|---:|---|",
    ]
    for key, level in sorted(data["unlocks"]["corePurchaseLevels"].items(), key=lambda pair: (pair[1], pair[0])):
        product = products[key]
        rarity = data["rarities"]["itemRarities"][key]
        price = product["baseValueCoins"] * product.get("buyMultiplierBps", 10000) // 10000
        production.append(table_row([product["name"], key, level, product["baseValueCoins"], price,
                                     "常规" if rarity == "common" else "每日／隐藏"] ))
    production += [
        "", "## 产出与稀缺物价格对照", "",
        "收购样例使用基准行情、普通品质、没有额外附魔、当日未超额度；实际行情每日±10%。",
        "新基础价值100金币起，收购按基础价值÷100；首种和允许购入的怪物材料购买加价独立。禁交易的产物不再提供价值。", "",
        "| 产物 | 基准全价 | 正常收购/件 |",
        "|---|---:|---:|",
    ]
    for short in ("cobblestone", "bamboo", "sugar_cane", "bone_meal", "gunpowder", "sulfur_spike",
                  "sulfur", "iron_ingot", "gold_ingot", "copper_ingot", "redstone", "emerald",
                  "lapis_lazuli", "quartz", "chorus_fruit", "nether_wart", "amethyst_shard",
                  "shulker_shell", "totem_of_undying", "diamond", "elytra"):
        item = products.get("minecraft:" + short, {"name": short, "tradeMode": "disabled"})
        active = item["tradeMode"] != "disabled"
        production.append(table_row([item["name"], item["baseValueCoins"] if active else "禁交易",
                                     str(Fraction(item["baseValueCoins"], divisor)) if active else "—"]))
    production += [
        "", "## 核验依据", "",
        "26.2本机原版配方、掉落与相关只读字节码核查见[来源记录](../../../docs/SOURCE_MANIFEST.json)。运行产量与服务器性能仍待游戏内测试。",
        "[26.2官方更新说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2)明确说明硫磺尖刺在硫磺下生长及4尖刺合1硫磺。",
        "[CarpetSkyAdditions的功能文档](https://github.com/jsorrell/CarpetSkyAdditions/blob/main/docs/en_us/features.md)用于识别扩展规则；其中煤变钻石等属于额外改动，不作为本模组的原版基线。",
    ]
    (cfg / "automation" / "TECHNICAL_PRODUCTION.md").write_text("\n".join(production) + "\n", encoding="utf-8")

    quotas = [
        "# 全队每日售出阶梯与原料等份表", "",
        "按稳定partyId + marketEpoch + quotaGroup保存。队员、三维度、普通商店、指令与出货箱共用计数。",
        "自然日沿用商店轮次：每24000有效服务器游戏tick前进一次；睡觉、时间指令、重登、重开不清空当日计数。", "",
        "| 今日累计原料等份 | 正常收购价倍率 |",
        "|---|---:|",
        "| 0～1000份 | ×1 |",
        "| 超过1000～10000份 | ×0.5 |",
        "| 超过10000份 | ×0.1，不限数量 |", "",
        f"正常收购价为当前物品基础估值÷{divisor}，包含品质、附魔与耐久；额外购入倍率不计入收购。",
        "一件压缩方块也可跨两个阶梯拆分权重。先按槽位顺序算完本次所有物品，再对本次总金币向下取整。",
        "铁粒每个精确计1/9份，铁块每个9份；不能先舍掉原料等份或逐个取整金币。",
        "若本次总额舍去后为0，则整次不收取物品、不增加计数。可以积攒更多物品后再出售。",
        "品质/附魔/耐久只改价，不改原料份额；损耗至50%及以下的装备保持在箱子/背包内，不参与自动出售。", "",
        "## 基准行情示例", "",
        "- 铁块／铁粒等现已禁交易；其历史原料映射仍保留，避免清除旧世界的稳定计数组。",
        "- 例如当天已售995份铁锭，再卖9铁锭：5份正常价+4份半价，基准行情收入63金币。",
        "- 铁锭正常收购9金币：一天售1000/10000/100000铁锭，合计9000/49500/130500金币。",
        "- 正常每件1金币的材料超过10000份后，每件贡献0.1金币；整批10件得到1金币，单件0金币时保留物品。",
        "- 仍可交易的同组商品按各自报价出售，共用原料额度；历史映射里的禁交易商品继续保留物品。", "",
        "## 等份映射", "",
        "石材/金属等采用最省原料路径，包含切石机；铜氧化和涂蜡不改变份额。",
        "工具和玩家盔甲按已配置主原料用量计数；马铠和鹦鹉螺铠没有普通合成配方，不能套用玩家盔甲用量。",
        "尚未显式映射的混合材料制品按原版物品ID独立计数；标准组件变体共用其原版ID额度，不按商品变体分开。", "",
        "| 物品 | ID | 同组计数 | 每件原料等份 |",
        "|---|---|---|---:|",
    ]
    for key, entry in sorted(quota.get("itemEquivalents", {}).items(), key=lambda pair: (pair[1]["quotaGroup"], pair[0])):
        group = quota["groups"][entry["quotaGroup"]]
        quotas.append(table_row([products.get(key, {"name": key})["name"], key, group["unitName"],
                                 Fraction(entry["numerator"], entry["denominator"])]))
    quotas += [
        "", "以上表格由sale_quotas.toml生成；增补或改等份后必须保留稳定的计数组ID，避免热重载意外重置额度。",
        "配置重载不会抹去已售计数；影响计数单位的改动在下一轮生效，当前轮次保留原规则快照。",
    ]
    if quota.get("counterKey") == "item_id":
        quotas = [
            "# 全队每日逐物品售出阶梯", "",
            "每队按物品注册ID独立累计实际售出件数，所有品质与出售入口共用；04:00刷新。",
            "阶梯百分比乘当天基础估值（含行情、品质、附魔、耐久），整笔交易最后取整一次。", "",
            "| 实际售出件数 | 当天基础估值倍率 |", "|---|---:|",
        ]
        previous = 0
        for tier in quota["tiers"]:
            limit = tier.get("upToUnits")
            interval = f"第{previous+1}～{limit}件" if limit is not None else f"第{previous+1}件起"
            quotas.append(table_row([interval, f"{tier['saleMultiplierBps']/100:g}%"]))
            if limit is not None: previous = limit
        quotas += ["", "实际配置：sale_quotas.toml。修改文档不改变运行规则。"]
    (cfg / "shop" / "SALE_QUOTA_TABLE.md").write_text("\n".join(quotas) + "\n", encoding="utf-8")

    shipping = data["shipping"]
    chest = [
        "# 出货箱方案", "",
        "注册独立出货箱方块和方块实体，外观、模型、开启动画及声音复用普通箱子；27格，不组成大箱子，支持漏斗。",
        "它的能力由注册类型与服务器保存状态决定，不靠给普通箱子改名来判断。", "",
        "## 合成", "",
        "```text", *shipping["recipe"]["pattern"], "```", "",
        "I=铁锭，R=红石粉，C=普通箱子。消耗4铁锭、4红石、1普通箱子，得到1出货箱。",
        "只合成，不在商店出售；合成不要求岛等级。空出货箱不参与交易；箱内物品按各自交易规则出货。",
        "出货箱为非交易制造物，不设置整箱收购价值。支线奖励独立于商品目录。", "",
        "## 红石触发", "",
        "1. 无信号→有信号：服务端标记一次出售请求，下一次预算可用时处理。",
        "2. 持续通电或重复邻块通知：不会重复出售。",
        "3. 无信号后再次有信号：可触发下一次出售。",
        "4. 箱子已有待处理请求时合并通知，不积累无限出售任务；处理完成后需要新的上升沿。",
        "5. 区块加载不人为制造新脉冲；重启时只恢复已有的未完成交易。漏斗在持续通电期间新增物品，需要下一次脉冲才卖出。", "",
        "## 归属与估值", "",
        "放置时由服务器按真实岛屿区域绑定partyId、dimensionId与该维度islandEpoch；每次出售重新检查归属。",
        "主世界重开只更新主世界领地世代；保留的地狱/末地出货箱不因主世界重开失效。钱包清零但当日售出计数保留。",
        "收益进入该岛队伍的共享钱包，即使成员不在线也按绑定队伍处理，不要求红石装置存在触发玩家。",
        "出货箱与手动出售复用同一SaleService：今日报价、物品品质、附魔、耐久、售出额度及最后取整完全一致。",
        "按槽位编号升序计算，未定价、非法组件、命名贵重物品、非空容器、0价值装备留在原位。",
        "禁止跨界漏斗/漏斗矿车替其他队伍转移物品；权限验证覆盖源和目标容器。", "",
        "## 交易与重启", "",
        "箱内扣物、共享钱包加币、原料计数增加和交易ID记录是一笔提交。检查金币溢出与状态版本后才扣物。",
        "写入可恢复交易日志；使用boxId、activationSeq和saleId幂等提交。恢复未完成日志前锁定相关箱子与钱包，防止重新出售同一批物品。",
        "不能只分别调用箱子setChanged和钱包保存来声称跨存档原子性。主线程内的事务与崩溃恢复是两层要求。",
        "全服出货任务共享每tick最多1000微秒计算预算；不会强制加载无人区块，也不在邻块更新里同步扫描大量箱子。",
        "已实现并通过真实客户端脉冲场景；大规模负载与完整崩溃矩阵仍待进一步验证。", "",
        "出货箱结构和合成由模组代码与配方资源定义；收购额度：../shop/sale_quotas.toml；价格：../shop/products.toml。",
    ]
    (cfg / "automation" / "SHIPPING_CHEST.md").write_text("\n".join(chest) + "\n", encoding="utf-8")
