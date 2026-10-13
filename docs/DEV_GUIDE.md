# 源码运行与配置

源码目标：Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.156.0+26.2、Java 25。当前版本为 `0.1.0-dev`。工程采用 Loom 1.17.21、Gradle 9.5.1，26.2 使用原版未混淆 API，不添加旧版 Yarn 映射。

## 直接运行开发客户端

在项目根目录打开 PowerShell：

```powershell
.\tools\dev.ps1 -Task Client
```

这会直接从编译后的类和资源启动 Fabric 开发客户端，不导出发布 JAR。首次启动需要下载 Minecraft、依赖和官方资源。开发客户端使用项目内 `run` 目录，与 PCL 的 `xplus` 实例分开。

新建世界时，在世界类型里选择 **Yumi 空岛**；采用生存模式。普通世界类型不会启动空岛队伍/领地服务。初次进入先落在远处草方块大厅，输入 `/is` 建岛并领取水桶、岩浆桶。三维度保留原版生物群系，但没有自然地形和结构。跨维度传送由本队领地路由；地狱与末地只创建必要安全落脚平台。

默认按 **I** 开商店，也可 `/shop`；悬停商品按 **A** 收藏。商品名、价格和操作文字使用原版正常字号，名称最多两行。格子默认48、可调40～96，窗口宽度决定实际列数，最多30列。旧版26格子偏好会自动调整至可读尺寸。

按 **C** 使用“梯云纵”，沿玩家朝向斜上跃起；按住 **空格**（或重新绑定的跳跃键）再按 **C**，改为更强的向上跳跃。地面和空中都可施放，两次成功施放间隔至少 **0.25 秒（5 个游戏刻）**。每次成功施放获得／刷新 **10 秒（200 个游戏刻）**的“踏云护体”，仅免疫摔落伤害，原版状态栏和背包会显示图标与剩余时间；不会减缓下落。普通生存世界也可使用，可在控制设置里重新绑定 C。冷却中的按键不缓存，打开菜单时不触发，骑乘、睡眠、飞行和空岛传送等待期间不可施放。

图标采用原版状态效果的 18×18 透明像素尺寸，参考跳跃提升与漂浮的箭头、云朵和浅蓝描边。可编辑源文件为 `art/mob_effect/cloud_step.aseprite`，逐像素绘制脚本为 `tools/draw_cloud_step.lua`。

常用命令：`/is help`、`/is invite <玩家>`、`/is accept`、`/is transfer <队员>`、`/is leave`、`/is trust <玩家>`、`/is visit <玩家>`、`/refresh`、`/sell hand`、`/sell inventory`。岛主有其他队员时必须先手动移交才能离队。重开使用 `/is reset` 返回的确认码；清除本队主世界及金币，保留其他已确认的进度。

管理员可从游戏或控制台输入 `/is admin coins <在线玩家> <金额>` 设置该队共享余额，适合测试。普通玩家没有此权限。

## 本地检查

```powershell
.\tools\dev.ps1 -Task Check -Offline
.\tools\dev.ps1 -Task GameTest -Offline
```

`Check` 编译服务端、客户端与场景测试，并运行 JUnit。`GameTest` 打开实际 Minecraft 客户端，创建隔离的临时集成服务器存档，自动操作后退出。测试存档、日志、截图都在 `build/run/clientGameTest`；此测试不启动独立服务器，也不自动接受服务器 EULA。

JUnit 报告位于 `build/reports/tests/test/index.html`，XML 位于 `build/test-results/test`。客户端场景日志位于 `build/run/clientGameTest/logs/latest.log`。已经下载依赖后可使用 `-Offline`；新环境首次运行请去掉此参数。

若本地 SDK 缺失，先用 Python 运行：

```text
python tools/bootstrap_development.py
```

安装器只写项目 `.tools`，校验下载归档 SHA-256，不修改系统 Java、环境变量或启动器。`dev.ps1` 只在本次调用期间设置 Java/Gradle 路径，结束后恢复环境变量；三个任务均不调用 `jar`、`build` 或发布任务。

## 配置生效位置

仓库 `config/yumis_sky_island` 是默认配置源，编译时进入资源包。首次加载模组时，仅补齐开发客户端 `run/config/yumis_sky_island` 中缺失的文件；已有管理员改价不会被覆盖。运行测试时配置在 `build/run/clientGameTest/config/yumis_sky_island`。

修改运行目录的 JSON 后重启客户端/服务器生效；当前没有热重载命令。修改仓库默认 JSON 不会自动覆盖已有运行配置。客户端收藏和格子大小保存在客户端的 `config/yumis_sky_island/client.json`，不进入队伍存档。

本次Q34～Q48商店改版已经更新本项目的默认、`run`和测试配置；重启开发客户端即可使用。旧配置备份在 `.tools/shop-backups`，不修改世界和PCL配置。如果另有本项目内的旧开发运行配置，可在关闭游戏后执行：

```text
python tools/revise_shop.py --config-root run/config/yumis_sky_island
```

迁移按保留的旧基价只乘一次10；检测到同一修订已完成则退出，保留后续手改价格。此工具只允许项目内配置路径。旧世界当天已抽出的货架记录沿用，已禁购商品隐藏；新候选在下个自然游戏轮次抽取，不免费重抽当天库存。

| 文件 | 控制内容 |
|---|---|
| `shop/products.json` | 基础估值、独立购入倍率、买卖开关、分类、标准组件、库存与抽取权重 |
| `shop/item_levels.json`、`shop/rarities.json` | 岛等级解锁、普通/稀有/史诗/传说 |
| `shop/shops.json` | 自然日长度、行情范围、每日/隐藏池、收购除数和基础价下限 |
| `shop/enchantments.json` | 43 种附魔按实际等级的乘算系数 |
| `shop/sale_quotas.json` | 阶梯倍率、铁粒/铁块等精确等份 |
| `islands/scoring.json`、`levels.json` | 方块存量分数、同类递减、0～100 阈值 |
| `islands/refresh.json` | 刷新计算预算、区段与异步读数量 |
| `products/professions.json`、`goods.json`、`quality.json` | 六职业、375 个有效商品、经验与品质概率/属性 |
| `server_rules.json` | 村民与流浪商人禁交易开关 |

生电核心的购买门槛已写入商品解锁等级。`automation/production.json` 是生产链设计资料，`shipping_chest.json` 是合成/性能规则说明；修改其中任意字段不代表运行逻辑都会跟随改变。当前出货箱为 27 格、普通箱外观、只在红石上升沿出售，合成配方位于源码 `src/main/resources/data/yumis_sky_island/recipe/shipping_chest.json`。

更新价格审阅 MD 而保留手改 JSON：

```text
python tools/prepare_economy.py --render-only
```

## 存档与验证范围

每个空岛世界的队伍、金币、库存、额度、职业和刷新/重开任务存储在该世界目录下的 `yumis_sky_island`。采用有校验的写前日志及定期检查点，交易先写入持久记录再交付结果。方块和玩家物品仍由 Minecraft 原生存档保存，恢复凭据用于衔接两者。

当前完成的验证和尚未完成的压力/崩溃场景见 [实现与验证记录](IMPLEMENTATION_STATUS.md)。维护服务器前应停止服务器并备份整个世界目录，包括原生维度、玩家存档和 `yumis_sky_island`；只复制金币文件无法构成完整世界备份。
