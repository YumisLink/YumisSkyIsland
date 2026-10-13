# Yumi’s Sky Island

Minecraft 26.2 / Fabric 空岛服务器模组。

已实现可直接启动的 26.2 Fabric 开发源码：三维度空岛、队伍/领地、共享金币、动态等级、慢速刷新、商店、每日/隐藏库存、职业品质、红石出货箱和梯云纵。附带可编辑价格与规则配置。当前为开发版，未导出发布 JAR，未修改现有 xplus 实例。

21 项单元测试全部通过，实际客户端与集成服务器的完整场景也已通过。Q34～Q48商店改版已实施：正常字号、两行名称、按窗口调整列数；原料购买与收购分开定价。商店实图见[截图](docs/screenshots/shop-wide.png)。50 人负载、全面崩溃场景与长线经济平衡仍列为后续验收。

## 审阅入口

- [源码运行说明](docs/DEV_GUIDE.md)：直接启动开发客户端、自动测试、配置生效位置。
- [实现与验证记录](docs/IMPLEMENTATION_STATUS.md)：已实现功能、实际验证和后续验收范围。
- [第一版开发基线](docs/PROJECT_PLAN.md)：三维度空岛、party、权限、指令、UI和完整实施顺序。
- [经济系统设计](docs/ECONOMY_DESIGN.md)：悬停估值、耐久折价、附魔乘算、100级曲线、限量每日与隐藏商店。
- [已确认的商店改版](docs/SHOP_REVISION_REVIEW.md)：商品删除与例外、独立购买加价、正常字号布局。
- [生电、经济与出货箱计划](docs/AUTOMATION_DESIGN.md)：原版生产链、分级、共享原料额度、禁交易和实现顺序。
- [生电产出与核心购买等级](config/yumis_sky_island/automation/TECHNICAL_PRODUCTION.md)。
- [每日售出阶梯与等份表](config/yumis_sky_island/shop/SALE_QUOTA_TABLE.md)。
- [出货箱合成与触发方案](config/yumis_sky_island/automation/SHIPPING_CHEST.md)。
- [完整原版价目](config/yumis_sky_island/shop/PRICE_GUIDE.md)：来自本机 26.2 原版资源的逐项草案。
- [稀有度与物品解锁等级](config/yumis_sky_island/shop/RARITY_LEVEL_TABLE.md)。
- [附魔倍率表](config/yumis_sky_island/shop/ENCHANTMENT_TABLE.md)。
- [校验报告](docs/ECONOMY_VALIDATION.md)。

## 配置约定

根目录 `config/yumis_sky_island` 是默认配置源。模组首次运行会补齐运行目录内 `config/yumis_sky_island` 中缺失的 JSON，保留已有改价。修改运行配置后重启生效。

shop/products.json 保存价格与可交易范围；shop/item_levels.json 保存物品岛等级门槛；shop/rarities.json 保存物品稀有度和抽取权重；shop/enchantments.json 保存附魔等级倍率；shop/shops.json 保存每日库存、自然游戏日和隐藏概率。

islands/scoring.json 保存按存量递减的计分规则；islands/levels.json 保存0～100级阈值；islands/refresh.json 保存刷新预算；products/quality.json、professions.json、goods.json 保存品质、职业与商品资格。

server_rules.json 保存禁村民/流浪商人交易、保留猪灵和原版村民生产规则；shop/sale_quotas.json 保存全队每日售出额度与精确原料等份；automation下保存核心购买等级、生电规划和出货箱规则。

价格审阅文档由上述数据生成。价目生成工具使用 Python 标准库，只读原版客户端 JAR、资产索引和语言资源，不写入 Minecraft 实例。开发工具和客户端测试另在项目内运行。

已使用实际 26.2 注册表解码、回编码并匹配全部 1045 个启用商品，开发客户端也加载了商品目录。其中128项可常规购买、221项可从每日／隐藏店购买、696项仅收购；806项禁交易。配置仍保留价格草案标记：注册成功不等于全部价格与生产收益完成平衡，180 个启用商品的分类兜底价还需要逐项审阅。完整覆盖范围见实现记录。

## 启动源码

```powershell
.\tools\dev.ps1 -Task Client
```

在开发客户端中新建 **Yumi 空岛** 世界，进入后 `/is` 建岛，按 **I** 开商店。命令编译并直接启动源码，不导出发布 JAR。

按 **C** 使用梯云纵，沿朝向斜上跃起；按住 **空格** 再按 **C**，强力向上跃起。可在空中再次使用，施放间隔0.25秒，每次刷新10秒免摔伤效果。[游戏内 buff 图标与倒计时](docs/screenshots/cloud-leap-buff.png)。

```powershell
.\tools\dev.ps1 -Task Check -Offline
.\tools\dev.ps1 -Task GameTest -Offline
```

SDK 已准备在项目 `.tools`。迁移到新电脑时先运行 `python tools/bootstrap_development.py`，首次下载依赖时去掉 `-Offline`。

## 生成与校验

已有配置的审阅表更新（保留手工改价）：

```text
python tools/prepare_economy.py --render-only
```

首次从本机原版资源播种，必须提供客户端JAR和资产目录；已有配置的重新播种需要显式传入 --overwrite-draft：

```text
python tools/prepare_economy.py --jar "C:\Users\63569\Desktop\pcl\.minecraft\versions\xplus\xplus.jar" --assets "C:\Users\63569\Desktop\pcl\.minecraft\assets"
```

价格表的数据校验、Java 单元测试和实际客户端场景测试分别记录；单客户端测试不能替代 50 人负载与完整崩溃恢复验收。
