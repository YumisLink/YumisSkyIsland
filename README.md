# Yumi’s Sky Island

Minecraft 26.2 / Fabric 空岛服务器模组。

已实现可直接启动的 26.2 Fabric 开发源码：三维度空岛、队伍/领地、共享金币、动态等级、慢速刷新、商店、每日/隐藏库存、职业品质、红石出货箱和梯云纵。附带可编辑价格与规则配置。当前为开发版，未导出发布 JAR，未修改现有 xplus 实例。

本轮副职业品质与经济规则已实现，见[交付记录](docs/PROFESSION_QUALITY_DELIVERY.md)。50项单元测试和三组隔离客户端场景通过；测试JAR与21份TOML配置包位于`build/libs`。Q34～Q48商店改版已实施：正常字号、两行名称、按窗口调整列数；原料购买与收购分开定价。50人负载、全面崩溃场景与长线经济平衡仍列为后续验收。

## 审阅入口

- [开发规则与必须注意事项](AGENTS.md)：最新配置、UI、权限、测试隔离和发布约定。
- [模块结构图与源码索引](docs/MODULE_STRUCTURE.md)：模块职责、当前调用关系与工程边界。
- [源码运行说明](docs/DEV_GUIDE.md)：直接启动开发客户端、自动测试、配置生效位置。
- [模组指令表](docs/COMMANDS.md)：全部指令、参数、权限、出售确认与重开范围。
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

根目录 `config/yumis_sky_island` 是默认配置源。模组首次运行会补齐运行目录内 `config/yumis_sky_island` 中缺失的 TOML，保留已有改价。修改运行配置后重启生效。

shop/products.toml 保存价格与可交易范围；shop/item_levels.toml 保存物品岛等级门槛；shop/rarities.toml 保存物品稀有度和抽取权重；shop/enchantments.toml 保存附魔等级倍率；shop/shops.toml 保存每日库存、自然游戏日和隐藏概率。

islands/scoring.toml 保存按存量递减的计分规则；islands/levels.toml 保存0～100级阈值；islands/refresh.toml 保存刷新预算；products/quality.toml、professions.toml、goods.toml 保存品质、职业与商品资格。

server_rules.toml 保存禁村民/流浪商人交易、保留猪灵和原版村民生产规则；shop/sale_quotas.toml 保存全队每日售出额度与精确原料等份；automation下保存核心购买等级、生电规划和出货箱规则。

价格审阅文档由上述数据生成。价目生成工具使用 Python 3.11+ 与 tomlkit（见 tools/requirements.txt），只读原版客户端 JAR、资产索引和语言资源，不写入 Minecraft 实例。开发工具和客户端测试另在项目内运行。

当前交易目录仅保留688项正价可出售商品（可买可卖368项，仅收购320项），已通过实际26.2注册表解析、组件解码及回编码匹配。完整物品稀有度、计分、职业和任务物品目录独立保留；任务可使用非交易物品。注册与组件校验不代表长期价格平衡已完成。最新源码和实际验收范围见[控制面板交付记录](docs/CONTROL_PANEL_DELIVERY.md)。

管理员任务修改模式仅通过 `/is admin tasks edit on|off` 切换，编辑器内嵌在控制面板任务区域。现有悬赏、每日／隐藏商店和出货箱默认可用。常驻及每日／隐藏收藏分别位于左侧分类最后；隐藏保底至少1传说、2史诗，候选不足按等级与稀有度向下回退。

## 启动源码

```powershell
.\tools\dev.ps1 -Task Client
```

在开发客户端中新建 **Yumi 空岛** 世界，进入后 `/is` 建岛，按 **I** 打开控制面板。命令编译并直接启动源码，不导出发布 JAR。

按 **C** 使用梯云纵，沿朝向斜上跃起；按住 **空格** 再按 **C**，强力向上跃起。可在空中再次使用，施放间隔0.25秒，每次刷新10秒免摔伤效果。[游戏内 buff 图标与倒计时](docs/screenshots/cloud-leap-buff.png)。

```powershell
.\tools\dev.ps1 -Task Check -Offline
.\local-tests\run.ps1 -Task Unit -Offline
.\local-tests\run.ps1 -Task Client -Offline
```

SDK 已准备在项目 `.tools`。迁移到新电脑时先运行 `python tools/bootstrap_development.py`，首次下载依赖时去掉 `-Offline`。

`Check` 只编译正式代码与资源；`local-tests/` 是本地保留、不上传、不打包的可选测试区。

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
