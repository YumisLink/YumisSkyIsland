"""TOML editing shared by configuration generation tools; never imports local tests."""
from __future__ import annotations
import json
import pathlib
import sys
import tempfile
import os
import tomllib

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".tools" / "python-libs"))
try:
    import tomlkit
except ImportError as exc:
    raise RuntimeError(
        "请先安装项目内 TOML 工具：python -m pip install --target .tools/python-libs -r tools/requirements.txt"
    ) from exc

LABELS = {
    "regularBuyMultiplierBps": "普通常驻商店额外购价倍率；25000=2.5倍，不影响收购或每日隐藏商店",
    "buyBaseValueCoins": "独立购入基价；正整数，省略沿用基础价值，不改变收购价",
    "successfulFishingXp": "每次真实成功获钓的渔夫职业经验；默认12，空收杆／钩实体不计",
    "profession": "职业ID：fisherman、farmer、blacksmith、stonemason、artisan、chef、hunter",
    "previousQuestId": "同任务线前置任务ID；前项领奖后解锁，省略为起点",
    "goals": "本人行动追踪条件列表；不扣物，只累计当前已解锁目标",
    "type": "行动：craft 制作加工；smelt 手动领取熔炼；fish 钓鱼；harvest 采集掉落；kill 本人击杀",
    "targetId": "目标物品或生物注册ID；kill 使用生物ID，其余使用物品ID",
    "sourceBlockId": "采集来源方块ID；省略为不限",
    "toolId": "采集工具ID；省略为不限",
    "rewardExperiencePoints": "原版经验点奖励；非负整数，按点数而非等级发放",
    "schemaVersion": "配置结构版本；资料字段，勿随意降低",
    "status": "配置审阅状态；资料字段，不改变运行规则",
    "minecraftVersion": "目标 Minecraft 版本；资料字段",
    "registryVerified": "资料提取时的校验标记；启动时仍逐项进行真实注册表验证",
    "source": "资料来源与版本追踪；说明字段",
    "products": "可交易商品目录；每项必须正价且能通过实际注册表／组件校验",
    "canonicalStack": "标准物品模板；用于同名不同组件的商品匹配",
    "componentPatch": "Minecraft 组件变体；空表表示基础物品",
    "itemId": "有效注册物品 ID，例如 minecraft:oak_log",
    "productId": "商品固定 ID；须与表键一致",
    "name": "界面显示名称",
    "categoryHint": "分类说明；实际分类使用 shopCategory",
    "baseValueCoins": "基础全价，单位金币；商品必须为正整数，收购另除 saleDivisor",
    "buyMultiplierBps": "额外购入倍率，万分比；10000=1倍，必须为正，不抬高收购价",
    "tradeMode": "交易方式：buy_sell 可买可卖，buy_only 仅购买，sell_only 仅收购",
    "dailyStock": "每日／隐藏每轮全队共享库存；正整数",
    "offerWeight": "同稀有度中的抽取权重；正整数，越大越容易抽到",
    "shopCategory": "创造分类注册 ID；决定控制面板左侧分类",
    "firstAcquisitionPremium": "是否首次整批购入加价；队伍首次获得后永久解除",
    "acquisitionNote": "获取途径说明；不直接改变运行价格",
    "priceOrigin": "基础价格的推导来源；资料字段",
    "shopRule": "交易政策说明；实际开关由 tradeMode 决定",
    "legacyBaseValueCoins": "旧价格资料，仅用于追踪，不参与当前计算",
    "legacyTradeMode": "旧交易方式资料，不参与当前运行",
    "legacyRarity": "旧稀有度资料，不参与当前运行",
    "shopRevision": "商品政策版本说明，不用于重复涨价",
    "market": "全服市场行情与轮次参数",
    "ticksPerEpoch": "每市场轮次的有效服务器游戏 tick；默认24000，必须大于0",
    "minPriceBps": "行情最低万分比；9000表示基础价的90%",
    "maxPriceBps": "行情最高万分比；11000表示基础价的110%，不得小于最低值",
    "minimumCurrentBaseValueCoins": "行情后的基础全价下限，单位金币；正整数",
    "minimumCurrentBuyCoins": "旧版下限资料；现使用 minimumCurrentBaseValueCoins",
    "saleDivisor": "基础全价到正常收购的除数；默认100，必须大于0",
    "daily": "每日商店参数；默认12种，全队共享库存",
    "hidden": "隐藏商店参数；当轮替代每日商店",
    "chanceBps": "隐藏概率，万分比；500=5%，范围0～10000",
    "slots": "当轮最多商品种数；正整数，候选不足时只显示可抽数量",
    "guaranteedLegendary": "隐藏保底传说目标格数；默认1，非负整数，等级池不足逐级下降",
    "guaranteedEpic": "隐藏保底史诗目标格数；默认2，非负整数，等级池不足逐级下降",
    "guaranteeFallback": "保底回退策略说明：legendary→epic→rare→common，仍遵守等级",
    "stockDefaults": "库存资料；实际各商品库存以 dailyStock 为准",
    "dailyWeight": "每日稀有度抽取权重；非负整数，候选存在时参与抽取",
    "hiddenWeight": "隐藏补余商品的稀有度权重；非负整数，先抽保底再补余",
    "definitions": "稀有度定义与市场抽取权重",
    "itemRarities": "全部有效物品／变体的稀有度，不限于可交易商品",
    "itemLevels": "商品购买等级门槛；0～100，出售不要求购买等级",
    "entries": "附魔估值定义；任务奖励不受商店附魔上限限制",
    "levelMultiplierBps": "按实际附魔等级配置的价值倍率；万分比，非负整数",
    "exclusiveSet": "原版互斥附魔资料；省略表示没有互斥集合",
    "supportedItems": "原版附魔适用物品标签；资料字段",
    "isCurse": "是否诅咒附魔；原版资料",
    "isTreasure": "是否宝藏附魔；原版资料",
    "scope": "共享范围说明；目前按队伍共享，不通过此字段切换范围",
    "tiers": "递减阶梯；数量上限须递增，最后省略上限表示无限",
    "upToCount": "本档累计方块数量上限；省略表示无限，非负整数",
    "upToUnits": "本档累计原料等份上限；省略表示无限，非负整数",
    "multiplierBps": "计分倍率，万分比；10000=100%，非负整数",
    "saleMultiplierBps": "收购阶梯倍率，万分比；10000=100%，非负整数",
    "itemEquivalents": "物品的精确原料等份；保留历史映射不意味着开放交易",
    "groups": "原料额度组定义",
    "quotaGroup": "共享原料额度组 ID；同组共用当日计数",
    "numerator": "每件原料等份分子；正整数",
    "denominator": "每件原料等份分母；正整数",
    "unitItemId": "原料单位物品 ID；资料与分组参考",
    "unitName": "原料单位中文名称",
    "blocks": "独立方块计分目录；删除商品价格不删除方块计分",
    "basePoints": "该方块基础计分权重；非负整数，0表示不计分",
    "scoreGroup": "递减计分组 ID；同组方块共用数量",
    "tierProfile": "递减档位说明；目前实际读取 default 档位",
    "thresholds": "0～100级所需分数表；级数连续、门槛严格递增",
    "level": "岛屿等级；整数0～100",
    "requiredPoints": "达到该等级的累计分数门槛；非负整数",
    "pointScale": "内部定点分数精度说明；目前固定10000",
    "globalComputeBudgetMicrosPerTick": "全服每刻刷新计算预算，单位微秒；默认1000，正整数",
    "maxSectionsPerTick": "每刻最多计数区段数量；默认2，正整数",
    "maxNewChunkRequestsPerTick": "每刻最多新异步区块读取数；默认1，正整数",
    "maxConcurrentChunkRequests": "同时异步读取上限；默认2，正整数",
    "grades": "制造品质档位；顺序必须为普通、精良、稀有、史诗、传说",
    "valueBps": "品质价值倍率；万分比，10000=1倍，非负整数",
    "durabilityBps": "品质最大耐久倍率；万分比，10000=1倍，正整数",
    "attackAndMiningBonusBps": "品质攻击／挖掘加成；万分比，运行时限制0～1000",
    "professions": "个人职业 ID 清单",
    "items": "独立的手工制造商品资格，不能随可交易商品一起删除",
    "profession": "手工制造所属职业 ID",
    "qualityAllowed": "此物品的手工制造是否可判定品质；true/false",
    "xpCurve": "个人职业经验等级曲线",
    "perLevelBase": "每级基础经验要求；非负整数",
    "perLevelGrowth": "每级增加的经验要求；非负整数",
    "maxLevel": "等级上限资料；岛／职业默认为100",
    "minLevel": "本区间起始等级；非负整数",
    "qualityChanceBands": "按职业等级分段的品质抽取权重",
    "weights": "五档品质权重；非负整数，总和必须大于0",
    "successfulCraftXp": "每件手工制作成功给予的职业经验；非负整数",
    "noXpItems": "无损压缩／还原等不提供制作经验的物品 ID 列表",
    "diamondPickaxeMinimumQualityLevel": "钻石镐产生高品质的铁匠最低等级；默认65",
    "villagerTradingEnabled": "是否允许村民交易；默认false",
    "wanderingTraderTradingEnabled": "是否允许流浪商人交易；默认false",
    "piglinBarteringEnabled": "猪灵以物易物规则说明，保留原版行为",
    "villagerBreedingEnabled": "村民繁殖规则说明，保留原版行为",
    "villagerWorkAiEnabled": "村民工作 AI 规则说明，保留原版行为",
    "ironGolemSpawningEnabled": "铁傀儡生成规则说明，保留原版行为",
    "globalMobGriefingOverride": "省略表示沿用原版规则；不会全局禁止生物改造",
    "quests": "全服成长任务定义；按数组顺序排列主线，支线独立",
    "id": "固定 ID；已有任务不能改 ID，新增任务需唯一",
    "description": "任务详情说明，支持中文",
    "kind": "任务类型：main 队伍主线；side 个人支线；profession 个人职业任务",
    "objective": "目标：materials 提交材料；place_shipping_chest 本人放置；tracked 追踪本人行动",
    "requirements": "任务材料列表；只限制明确配置的属性，附魔／品质至少达到指定值",
    "quantity": "所需物品数量；正整数",
    "components": "合法 Minecraft 物品组件；用于附魔／药水等条件或奖励",
    "minimumQuality": "最低品质：0普通、1精良、2稀有、3史诗、4传说；省略表示不限",
    "rewardItems": "领奖物品列表；空间不足时材料与奖励均不改变",
    "rewardCoins": "金币奖励；非负64位整数，0表示无金币",
    "unlockFeatures": "任务完成时永久授予的预设功能 ID；删除／重置不撤销",
    "features": "独立的预设功能目录；权限不由任务数量决定",
    "defaultUnlocked": "新队伍／重开初始化时是否默认开放；现有三功能true，未来默认false",
    "supportedFeatures": "旧版可解锁功能 ID 清单；现采用 features 定义",
    "revision": "任务定义修订号；编辑器保存时递增，用于拒绝旧请求",
    "initialSlots": "初始全队共享悬赏格数；默认3，1～maximumSlots",
    "maximumSlots": "悬赏格上限；默认50，1～50",
    "levelsPerSlot": "最高岛等级每达到多少级永久增加一格；默认10，正整数",
    "firstSlotCost": "第一格额外悬赏格价格，金币；默认100000，非负整数",
    "subsequentSlotCostStep": "后续购格阶梯基价，金币；默认500000，正整数",
    "durationGameDays": "每条悬赏独立有效天数；默认7，正整数",
    "materialAttemptsPerReward": "每种奖励最多尝试材料种数；1～5",
    "maximumMaterialQuantity": "每条悬赏最多提交数量；1～1536",
    "rarityWeights": "悬赏奖励的稀有／史诗／传说权重；非负，总和大于0",
    "layoutVersion": "控制面板偏好结构版本，由程序维护",
    "cellSize": "商品格子大小，GUI像素；40～96，默认48",
    "showLockedItems": "显示等级不足商品；默认false，显示不会解除购买限制",
    "permanentFavorites": "常驻商店收藏 ID；在左侧最后的收藏分类查看",
    "specialFavorites": "每日／隐藏共用收藏 ID；未抽到时保留并显示库存0",
}

def read(path):
    path = pathlib.Path(path)
    data = tomllib.loads(path.read_text(encoding="utf-8-sig"))
    def restore(node):
        if isinstance(node, list):
            for child in node: restore(child)
        elif isinstance(node, dict):
            if path.name == "scoring.toml" and "multiplierBps" in node: node.setdefault("upToCount", None)
            if path.name == "sale_quotas.toml" and "saleMultiplierBps" in node: node.setdefault("upToUnits", None)
            if path.name == "enchantments.toml" and "levelMultiplierBps" in node: node.setdefault("exclusiveSet", None)
            for child in node.values(): restore(child)
    restore(data)
    if path.name == "shipping_chest.toml": data.setdefault("baseValueCoins", None)
    if path.name == "server_rules.toml": data.setdefault("globalMobGriefingOverride", None)
    return data

def _hint(key, value):
    label = LABELS.get(key)
    if label is None:
        if ":" in key:
            label = "注册 ID／组件参数；保持 ID 原样"
        elif str(key).isdigit():
            label = "等级／阶段 " + str(key) + " 对应值"
        else:
            label = "说明字段 " + key + "；资料用途，不单独改变运行行为"
    if not isinstance(value, (dict, list)):
        label += "；当前默认：" + json.dumps(value, ensure_ascii=False)
    return label

def _merge(table, values):
    for key in list(table):
        if key not in values or values[key] is None:
            del table[key]
    for key, value in values.items():
        if value is None:
            continue
        if isinstance(value, dict):
            existing = table.get(key)
            child = existing if hasattr(existing, "add") and not isinstance(existing, tomlkit.items.Array) else tomlkit.table()
            _merge(child, value)
            if key not in table: table.add(tomlkit.comment(_hint(key, value)))
            table[key] = child
        elif isinstance(value, list) and value and all(isinstance(x, dict) for x in value):
            old = list(table.get(key, []))
            array = tomlkit.aot()
            for index, row in enumerate(value):
                candidate = next((t for t in old if row.get("id") and t.get("id") == row["id"]), None)
                if candidate is None and index < len(old): candidate = old[index]
                child = candidate if isinstance(candidate, tomlkit.items.Table) else tomlkit.table()
                _merge(child, row)
                array.append(child)
            if key not in table: table.add(tomlkit.comment(_hint(key, value)))
            table[key] = array
        else:
            if key not in table: table.add(tomlkit.comment(_hint(key, value)))
            table[key] = value

def write(path, data):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    doc = tomlkit.parse(path.read_text(encoding="utf-8-sig")) if path.exists() else tomlkit.document()
    if not path.exists():
        doc.add(tomlkit.comment("Yumi 空岛可编辑配置；UTF-8。服务端规则重启生效，任务编辑器保存立即生效。"))
        doc.add(tomlkit.comment("省略原空值字段保留无限／继承含义。带冒号或点号的注册 ID 必须保持引号。"))
        doc.add(tomlkit.nl())
    _merge(doc, data)
    text = tomlkit.dumps(doc)
    # Parse before replacing: no malformed generated TOML can replace the last valid file.
    tomllib.loads(text)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, suffix=".tmp", delete=False) as out:
            temporary = pathlib.Path(out.name)
            out.write(text); out.flush(); os.fsync(out.fileno())
        os.replace(temporary, path)
    finally:
        if temporary and temporary.exists(): temporary.unlink()
