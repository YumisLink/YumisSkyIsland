# 源码运行与配置

源码目标：Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.156.0+26.2、Java 25。当前版本为 `0.1.0-dev`。工程采用 Loom 1.17.21、Gradle 9.5.1，26.2 使用原版未混淆 API，不添加旧版 Yarn 映射。

JEI 是可选客户端集成。`gradle.properties` 中 `jei_api_version = 30.16.0.134` 为编译接口基线，`jei_runtime_version = 30.29.0.201` 为默认开发运行版本；兼容回归可通过 Gradle 的 `-Pjei_runtime_version=30.16.0.134` 切换到基线版本。钓鱼文字定位使用两版共有的 `IPlaceable` 接口，概率说明由分类提示接口提供。

## 直接运行开发客户端

在项目根目录打开 PowerShell：

```powershell
.\tools\dev.ps1 -Task Client
```

这会直接从编译后的类和资源启动 Fabric 开发客户端，不导出发布 JAR。首次启动需要下载 Minecraft、依赖和官方资源。开发客户端使用项目内 `run` 目录，与 PCL 的 `xplus` 实例分开。

新建世界时，在世界类型里选择 **Yumi 空岛**；采用生存模式。普通世界类型不会启动空岛队伍/领地服务。初次进入先落在远处草方块大厅，输入 `/is` 建岛并领取水桶、岩浆桶。三维度保留原版生物群系，但没有自然地形和结构。跨维度传送由本队领地路由；地狱与末地只创建必要安全落脚平台。

默认按 **I** 打开控制面板，也可 `/shop`；悬停商品按 **A** 收藏。商品名、价格和操作文字使用原版正常字号，名称最多两行。格子默认48、可调40～96，窗口宽度决定实际列数，最多30列。旧版26格子偏好会自动调整至可读尺寸。

I页同级“居民”Tab可领取本队首张免费许可证。手持许可证逐一右键屋内所有门、工作站、光源、一张床和两个独立普通箱子，在屋内Shift＋右键空气提交；面板可查看供养、委托、免费补登记、迁居和正版账号皮肤。每游戏日04:00才抽取新居民，房屋需有30～200格连通空气。规则与职业表见[居民经营说明](NPC_SETTLEMENT.md)。

按 **O** 打开同页“饰品／抽奖”Tab，原神模式称“祈愿”；按 **P** 打开职业。单池每次消耗1张抽奖券（纠缠之缘）给1件等级合格的配置奖品；30级经验或160个人代币（原石）可提前兑换1张券。默认32件装备，最低传说75级解锁；个人保底和每日队伍UP见[抽奖说明](LOTTERY_DELIVERY.md)。26.2原版O／P冲突处理和半透明黑灰背景沿既有规则。

按 **C** 使用“梯云纵”，沿玩家朝向斜上跃起；按住 **空格**（或重新绑定的跳跃键）再按 **C**，改为更强的向上跳跃。地面和空中都可施放，两次成功施放间隔至少 **0.25 秒（5 个游戏刻）**。每次成功施放获得／刷新 **10 秒（200 个游戏刻）**的“踏云护体”，仅免疫摔落伤害，原版状态栏和背包会显示图标与剩余时间；不会减缓下落。普通生存世界也可使用，可在控制设置里重新绑定 C。冷却中的按键不缓存，打开菜单时不触发，骑乘、睡眠、飞行和空岛传送等待期间不可施放。

预制建筑先手持图纸右键定位，可移动、上下调整、旋转和切换替换；Esc走动查看、G返回设置。保存图纸后装入本队蓝图大炮，选择自备材料或购买成品逐块施工。特殊弹药实际1000货币／1000额度；默认每5tick最多1块。建筑库安装在实例 `prefabs/yumis_sky_island`，不随公共JAR打包；说明与验收边界见[预制建筑交付说明](PREFAB_BUILDING_DELIVERY.md)。

图标采用原版状态效果的 18×18 透明像素尺寸，参考跳跃提升与漂浮的箭头、云朵和浅蓝描边。可编辑源文件为 `art/mob_effect/cloud_step.aseprite`，逐像素绘制脚本为 `tools/draw_cloud_step.lua`。

常用命令：`/is help`、`/is invite <玩家>`、`/is accept`、`/is transfer <队员>`、`/is leave`、`/is trust <玩家>`、`/is visit <玩家>`、`/refresh`、`/sell hand`、`/sell inventory`。岛主有其他队员时必须先手动移交才能离队。重开使用 `/is reset` 返回的确认码；清除本队主世界、共享金币、最高等级、队伍主线、悬赏、首获及售出历史；保留个人物品、职业、个人支线与职业任务、地狱、末地、当日行情、库存和额度，现有三个默认开放功能在初始化后可用。

按 **P → 渔夫**查看当前总渔力、基础值、渔夫等级和已启用饰品的加成来源；较小窗口内用滚轮查看完整内容与鱼饵。渔力注册为原版同步属性 `yumis_sky_island:fishing_power`，有权限的玩家可用 `/attribute @s yumis_sky_island:fishing_power get` 查询。基础值默认0，渔夫每级增加1，启用幸运浮标增加25、渔夫护符增加10，最终范围0～1000。管理员设置的基础值与其它属性修正也参与真实钓鱼计算并列入面板；职业等级修正由个人存档维护，不覆盖管理员设置的基础值。

管理员可从游戏或控制台输入 `/is admin coins <在线玩家> <金额>` 设置该队共享余额，适合测试。普通玩家没有此权限。游戏玩家使用 `/is admin edit on|off` 统一切换所有编辑模式，断线后清空；旧命令中的 `tasks` 已移除。开启后，任务和商店使用控制面板内嵌编辑器，按 **Ctrl＋】** 打开独立的实时配置表单。已有任务ID固定，保存立即生效；全服单任务重置覆盖对应队伍或全部玩家（含离线），保留其它进度、既有奖励及功能权限。

实时配置表单提供经济与死亡、原版交易、职业经验、职业商店、血月、抽奖／祈愿、刷新预算及高分计分八个同级分组，点击“保存并生效”写入服务端实际运行TOML并同步在线玩家。每项显示单位、范围和生效说明；已判定的血月、每日货架和已持久化奖励不重抽。人工修改TOML后请重启载入，再打开表单；外改文件和过期编辑不会被静默覆盖。

每日／隐藏商店在编辑模式下提供“查看所有”，显示全部配置商品与有效动态图纸。普通和每日点击商品均可内嵌修改全服购入基价，预览含行情与普通店2.5倍率的价格。收购估值、解锁、库存及建筑成品费不变；仅收购商品和特殊弹药显示只读说明。图纸价格覆盖单独保存在 `shop/products.toml` 的 `buyBaseValueOverrides` 映射，不把动态商品元数据写成静态商品。

## 本地检查

```powershell
.\tools\dev.ps1 -Task Check -Offline
.\tools\dev.ps1 -Task GameTest -Offline
```

`Check` 只编译正式服务端、客户端与TOML资源，完全不加载本地测试配置。单元测试另用 `.\local-tests\run.ps1 -Task Unit -Offline`；全部测试源码、场景、脚本与自检集中在被Git忽略的 `local-tests/`，通过本地init脚本单独注册。`GameTest` 打开实际 Minecraft 客户端，创建隔离的临时集成服务器存档，自动操作后退出。测试存档、日志、截图都在 `build/run/clientGameTest`；此测试不启动独立服务器，也不自动接受服务器 EULA。

JUnit 报告位于 `build/reports/tests/test/index.html`，XML 位于 `build/test-results/test`。客户端场景日志位于 `build/run/clientGameTest/logs/latest.log`。已经下载依赖后可使用 `-Offline`；新环境首次运行请去掉此参数。

若本地 SDK 缺失，先用 Python 运行：

```text
python tools/bootstrap_development.py
```

安装器只写项目 `.tools`，校验下载归档 SHA-256，不修改系统 Java、环境变量或启动器。`dev.ps1` 只在本次调用期间设置 Java/Gradle 路径，结束后恢复环境变量；`Check`、`Client`、`GameTest` 不导出 JAR，`Export` 才执行打包。

## 配置生效位置

仓库 `config/yumis_sky_island` 是默认配置源，编译时进入资源包。首次加载模组时，仅补齐开发客户端 `run/config/yumis_sky_island` 中缺失的文件；已有管理员改价不会被覆盖。运行测试时配置在 `build/run/clientGameTest/config/yumis_sky_island`。

当前共24份可编辑TOML（含服务端规则与客户端偏好），同类字段的自动中文注释只解释一次。人工修改运行目录TOML后重启生效；管理员任务、商店和实时配置白名单通过编辑器保存后立即全服生效。仓库默认配置只补缺失文件，保留运行目录人工值与注释。旧JSON配置首次导入后存为 `.before-toml.bak`，不做旧世界历史区块回补。客户端收藏和格子大小保存在客户端的 `config/yumis_sky_island/client.toml`，不进入队伍存档。

方块计分由 `islands/scoring.toml` 控制。按方块ID独立统计主世界当前存量；条目、`basePoints` 或整个 `blocks` 表省略均默认1分。空气、水、岩浆、气泡柱固定排除，其余必须至少1分。基础分1～5恒定计分，>5才使用256／2048／8192档及100%／25%／5%／1%倍率，每档每块向下取整并保底1；铁块基础15的四段为15／3／1／1。`notices` 控制手动／建造杖跨档时的队伍聊天提示，文案支持 `{block}`、`{threshold}`、`{nextPoints}`、`{nextMultiplier}`。基础分手改重启；档位和通告可实时保存，重算本身不发跨档提示。本轮使用新测试世界，不迁移旧计数或旧刷新任务。

`gameplay/construction_wand.toml` 与 `gameplay/prefabs.toml` 各有 `acquisitionEnabled = true`。改为 `false` 并重启后，前者停止六档建造杖与五张升级图纸的商店、随机悬赏供给；后者停止建筑图纸、蓝图大炮和特殊弹药的同类供给。已持有物品与五条锻造升级配方继续可用；每日库存保留，重新开启后继续当前轮次的剩余库存。缺省字段按开启处理。

独立的`gameplay/lottery.toml`错误时保留原文件并停用抽取，避免误补奖品分档；修正后重启。其余 `config/yumis_sky_island` 运行配置会先尝试读取：文件无法解码或无法解析，以及启动时的数值、结构校验不通过，都删除该文件并按模组内置默认重新生成，只重试这一次。默认文件仍然无效才中止启动。能正常解析但缺字段或字段类型不符时，仍保留可读取项并用默认值补齐，原文件存为同目录的 `.broken.<随机标识>.bak`。未出错的文件不会重写。外置建筑库 `catalog.toml` 与客户端 `client.toml` 继续只做补齐备份，不整文件回退。

旧JSON运行配置由首次启动加载器导入TOML并备份；当前control-panel-v4不会再次套用历史乘价修订。已有人工TOML保留，同日库存不会因升级、重登或界面切换重抽。

| 文件 | 控制内容 |
|---|---|
| `shop/products.toml` | 基础估值、独立购入倍率、买卖开关、分类、标准组件、库存与抽取权重 |
| `shop/item_levels.toml`、`shop/rarities.toml` | 岛等级解锁、普通/稀有/史诗/传说 |
| `shop/shops.toml` | 自然日长度、行情范围、每日/隐藏池、收购除数和基础价下限 |
| `shop/enchantments.toml` | 43 种附魔按实际等级的乘算系数 |
| `shop/sale_quotas.toml` | 阶梯倍率、铁粒/铁块等精确等份 |
| `islands/scoring.toml`、`levels.toml` | 方块存量分数、同类递减、0～100 阈值 |
| `islands/refresh.toml` | 刷新计算预算、区段与异步读数量 |
| `products/professions.toml`、`goods.toml`、`quality.toml` | 七职业、独立的375项制造目录、成功获钓经验与品质概率/属性 |
| `server_rules.toml` | 村民与流浪商人禁交易开关 |
| `gameplay/lottery.toml` | 单池奖品及组件／独立等级与稀有度、保底、UP、兑换成本和个人代币奖励 |
| `gameplay/residents.toml` | 房屋边界、入住概率、容量声望、每日食物／用品／产出、委托和团队增益；手改重启，不进入实时白名单 |

生电核心的购买门槛写入商品解锁等级。纯资料用的 `automation/production.toml`、`unlocks.toml`、`shipping_chest.toml` 已删除；设计说明保留在文档。实际配方估值仍读取 `shop/recipe_values.toml`。当前出货箱为27格、普通箱外观、只在红石上升沿出售，合成配方位于源码 `src/main/resources/data/yumis_sky_island/recipe/shipping_chest.json`。`tasks/bounties.toml` 的稀有／史诗／传说权重实际控制新悬赏奖励抽取，默认70／25／5。

管理员可输入 `/is admin accessory draws <在线玩家> <次数>` 增加饰品抽奖次数；`/is admin nextday` 快进到下次04:00并按正常流程刷新每日玩法，悬赏剩余时间不快进；`/is admin bloodmoon tonight` 快进今日夜晚并开启血月，已经入夜则立即开启而不倒退时间。控制台同样可执行。

生电类别外置图纸的售价与成品费均降为原来25%；蓝图大炮基价10万、岛1级常驻，实际购买仍按常驻倍率与行情计算。默认和项目开发实例已同步；价格同步工具 `tools/apply_gameplay_revision_20261016.py` 保留本轮前基准，重复运行不会再次打折。

更新价格审阅 MD 而保留手改 TOML：

```text
python tools/prepare_economy.py --render-only
```

生成工具所需依赖仅安装在项目内：

```powershell
python -m pip install --target .tools/python-libs -r tools/requirements.txt
```

I控制面板五区域直接切换；出售、编辑及物品选择内嵌，确认使用浮层。常驻和每日／隐藏收藏分别在左侧末尾，每日收藏换日仍保留、缺货库存0。隐藏先保底至少1传说、2史诗，再加权补齐至16种；仅从刷新时等级可购买的候选抽取并逐格向低稀有度回退。任务需求附魔／品质按最低条件、药水按类型精确匹配，未指定属性不限制。

“使用出货箱”为无前置个人支线，本人于本队岛真实成功放置后点击领取64橡木原木、3铁锭及全新耐久IV／效率V金镐；队友放置、加载和命令生成不计。管理员重置该任务后需要重新放置，背包不足保留资格。

## 本轮商店与任务规则

普通商店购价额外系数位于 `shop/shops.toml`：`regularBuyMultiplierBps = 25000`，即×2.5。每日／隐藏商店不应用此项，即使隐藏保底抽到普通商品。商品可用 `buyBaseValueCoins` 独立设置购入基价；省略时沿用 `baseValueCoins`。已有收购价、耐久与额度继续按 `baseValueCoins` 计算。购价先按独立基价计算行情、基础价下限、品质和附魔，再乘商品倍率及所在商店倍率，最后取整。

幽匿尖啸体、感测体、唱片残片购入基价10万；钥匙5万／不祥钥匙10万，均55级稀有。试炼刷怪笼100万、55级传奇；刷怪笼1000万、100级传奇，均仅购买，库存1，为原版空白模板。使用刷怪蛋设定生物；试炼笼沿用原版空白默认奖池。原版 `spawner_blocks_work` 游戏规则继续有效。

默认46种供货刷怪蛋购入基价×10，全部蛋至少稀有，42种禁售蛋保持禁售。管理员可自行修改TOML开放具体物品；启动加载器不按历史禁售表过滤有效TOML商品。

主线全队共享；个人支线及64项职业任务换队、重开均保留。职业任务只累计当前已解锁目标，领奖后开启下一项，七条线可以同时推进。任务不扣产物，金币入当前队伍钱包，物品和原版经验给本人。渔夫每次真实获钓12职业经验；任务6钓10本附魔书，任务10钓8鹦鹉螺壳。宝藏按原版开放水域规则；制造经验继续由原制造目录决定。

采集目标按本人破坏产生的真实掉落计算，不需要捡入背包；农夫树苗只计橡树叶掉落，剪叶要求剪刀。制造按实际输出数量统计，包括普通点击和Shift提取；熔炼按本人手动取出结果统计。来自商店、任务奖励、转交和自动化的物品不增加职业任务计数。

共享金币正增量会通知全队在线成员，在游戏HUD和控制面板右下角余额上方显示“+金额”；固定200ms合并，1.8秒上浮渐隐。重新登录和切换队伍不弹入账提示。

### 直接添加或调整商品

编辑**运行实例** `config/yumis_sky_island/shop/products.toml`（开发实例为 `run/config/...`），保存后重启。若物品表已存在，修改原表，避免重复表键。三种模式为 `buy_sell` 可买可卖、`buy_only` 仅购买、`sell_only` 仅收购；删除商品表即禁交易。

下面是完整商品条目示例，选择需要的模式即可。刷怪蛋例展示管理员如何自行开放一个默认禁售物品；默认配置的禁售策略仍由当前价目表记录。

```toml
[products."minecraft:zombie_spawn_egg"]
# 商品ID与表键必须相同
productId = "minecraft:zombie_spawn_egg"
name = "僵尸刷怪蛋"
categoryHint = "刷怪蛋"
shopCategory = "minecraft:spawn_eggs"
# 估值基价与独立购入基价；省略后者则使用前者
baseValueCoins = 3000000
buyBaseValueCoins = 30000000
buyMultiplierBps = 10000
# 可改为 buy_sell 或 sell_only
tradeMode = "buy_only"
dailyStock = 1
offerWeight = 1
priceOrigin = "administrator"
acquisitionNote = "管理员自定义供货"

[products."minecraft:zombie_spawn_egg".canonicalStack]
itemId = "minecraft:zombie_spawn_egg"
[products."minecraft:zombie_spawn_egg".canonicalStack.componentPatch]
```

在 `shop/item_levels.toml` 的现有 `[itemLevels]` 表内设置 `"minecraft:zombie_spawn_egg" = 55`；在 `shop/rarities.toml` 的现有 `[itemRarities]` 表内设置 `"minecraft:zombie_spawn_egg" = "rare"`。等级范围0～100，稀有度为 `common`、`rare`、`epic`、`legendary`。普通商品常驻；较高稀有度进入每日／隐藏候选，满足等级不保证当天抽到。库存必须为正数或-1（无限），抽取权重必须为正数，物品及组件必须能通过26.2实际注册表校验。

手改后可以运行 `python tools/prepare_economy.py --render-only` 更新审阅表；该命令不改商品配置。`confirmed_gameplay_defaults.py` 是测试期显式生成确认默认数据的工具，会重置本轮默认字段和64项任务；日常手改配置无需运行它。

本轮商店、64项职业任务与金币提示的交付和验证见[GAMEPLAY_REVISION_DELIVERY](GAMEPLAY_REVISION_DELIVERY.md)；此前控制面板记录见[CONTROL_PANEL_DELIVERY](CONTROL_PANEL_DELIVERY.md)。

导出测试JAR使用 `.\tools\dev.ps1 -Task Export -Offline`，输出 `build/libs/YumisSkyIsland-0.1.0-dev.jar`；脚本校验21份包内默认TOML并排除测试内容，同时生成 `build/libs/YumisSkyIsland-0.1.0-dev-config.zip`。已有测试实例需要关闭后同步本轮 `config/yumis_sky_island` 再替换JAR、重启，单换JAR只补缺失配置。配置ZIP可解压到实例根目录。新实例自动生成默认配置。

本轮规则入口：`economy.toml` 设置原神模式与死亡费，`products/quality.toml` 设置银星／金星效果，`products/professions.toml` 与 `products/goods.toml` 设置职业经验、产物等级和品质概率，`shop/sale_quotas.toml` 按物品ID设置日售出阶梯，`shop/shops.toml` 的 `refreshAtDayTick = 22000` 表示主世界04:00刷新，`islands/levels.toml` 设置无限等级的每级基础分。测试期旧存档作废，不提供品质或任务字段迁移。

## 存档与验证范围

每个空岛世界的队伍、金币、库存、额度、职业和刷新/重开任务存储在该世界目录下的 `yumis_sky_island`。采用有校验的写前日志及定期检查点，交易先写入持久记录再交付结果。方块和玩家物品仍由 Minecraft 原生存档保存，恢复凭据用于衔接两者。

当前完成的验证和尚未完成的压力/崩溃场景见 [实现与验证记录](IMPLEMENTATION_STATUS.md)。维护服务器前应停止服务器并备份整个世界目录，包括原生维度、玩家存档和 `yumis_sky_island`；只复制金币文件无法构成完整世界备份。
