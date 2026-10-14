# 原版附魔价值乘算表（Minecraft 26.2）

43个附魔来自本机原版附魔数据，最大等级和诅咒/宝藏标签来自原版；倍率为本项目可编辑初稿。

修改enchantments.toml中的levelMultiplierBps；10000表示×1，15000表示×1.5。

组合价值按每个附魔实际等级的系数相乘，不把附魔系数相加。普通附魔与附魔书分别读取ENCHANTMENTS/STORED_ENCHANTMENTS。
品质倍率、所有附魔倍率、耐久倍率与1/100一起计算，最后才对金币向下取整。
附魔超过原版最高等级、互斥组合、未配置附魔拒绝自动收购；不把无法判断的物品当普通装备卖掉。
附魔书售卖模板默认是单附魔；多附魔书需要单独配置。诅咒书默认不出售，装备上的合法诅咒按其折减系数估值。

| 附魔 | ID | 最大等级 | I | II | III | IV | V | 宝藏 | 诅咒 |
|---|---|---:|---:|---:|---:|---:|---:|---|---|
| 水下速掘 | minecraft:aqua_affinity | 1 | ×1.1 | — | — | — | — | 否 | 否 |
| 节肢杀手 | minecraft:bane_of_arthropods | 5 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | ×1.5 | 否 | 否 |
| 绑定诅咒 | minecraft:binding_curse | 1 | ×0.6 | — | — | — | — | 是 | 是 |
| 爆炸保护 | minecraft:blast_protection | 4 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | — | 否 | 否 |
| 破甲 | minecraft:breach | 4 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | — | 否 | 否 |
| 引雷 | minecraft:channeling | 1 | ×1.1 | — | — | — | — | 否 | 否 |
| 致密 | minecraft:density | 5 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | ×1.5 | 否 | 否 |
| 深海探索者 | minecraft:depth_strider | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 效率 | minecraft:efficiency | 5 | ×1.1 | ×1.2 | ×1.35 | ×1.45 | ×1.6 | 否 | 否 |
| 摔落缓冲 | minecraft:feather_falling | 4 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | — | 否 | 否 |
| 火焰附加 | minecraft:fire_aspect | 2 | ×1.1 | ×1.2 | — | — | — | 否 | 否 |
| 火焰保护 | minecraft:fire_protection | 4 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | — | 否 | 否 |
| 火矢 | minecraft:flame | 1 | ×1.1 | — | — | — | — | 否 | 否 |
| 时运 | minecraft:fortune | 3 | ×1.2 | ×1.45 | ×1.8 | — | — | 否 | 否 |
| 冰霜行者 | minecraft:frost_walker | 2 | ×1.1 | ×1.2 | — | — | — | 是 | 否 |
| 穿刺 | minecraft:impaling | 5 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | ×1.5 | 否 | 否 |
| 无限 | minecraft:infinity | 1 | ×1.1 | — | — | — | — | 否 | 否 |
| 击退 | minecraft:knockback | 2 | ×1.1 | ×1.2 | — | — | — | 否 | 否 |
| 抢夺 | minecraft:looting | 3 | ×1.2 | ×1.45 | ×1.8 | — | — | 否 | 否 |
| 忠诚 | minecraft:loyalty | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 海之眷顾 | minecraft:luck_of_the_sea | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 突进 | minecraft:lunge | 3 | ×1.15 | ×1.35 | ×1.6 | — | — | 否 | 否 |
| 饵钓 | minecraft:lure | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 经验修补 | minecraft:mending | 1 | ×1.5 | — | — | — | — | 是 | 否 |
| 多重射击 | minecraft:multishot | 1 | ×1.1 | — | — | — | — | 否 | 否 |
| 穿透 | minecraft:piercing | 4 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | — | 否 | 否 |
| 力量 | minecraft:power | 5 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | ×1.5 | 否 | 否 |
| 弹射物保护 | minecraft:projectile_protection | 4 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | — | 否 | 否 |
| 保护 | minecraft:protection | 4 | ×1.1 | ×1.2 | ×1.35 | ×1.5 | — | 否 | 否 |
| 冲击 | minecraft:punch | 2 | ×1.1 | ×1.2 | — | — | — | 否 | 否 |
| 快速装填 | minecraft:quick_charge | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 水下呼吸 | minecraft:respiration | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 激流 | minecraft:riptide | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 锋利 | minecraft:sharpness | 5 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | ×1.5 | 否 | 否 |
| 精准采集 | minecraft:silk_touch | 1 | ×1.8 | — | — | — | — | 否 | 否 |
| 亡灵杀手 | minecraft:smite | 5 | ×1.1 | ×1.2 | ×1.3 | ×1.4 | ×1.5 | 否 | 否 |
| 灵魂疾行 | minecraft:soul_speed | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 是 | 否 |
| 横扫之刃 | minecraft:sweeping_edge | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 迅捷潜行 | minecraft:swift_sneak | 3 | ×1.25 | ×1.6 | ×2 | — | — | 是 | 否 |
| 荆棘 | minecraft:thorns | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 耐久 | minecraft:unbreaking | 3 | ×1.1 | ×1.2 | ×1.3 | — | — | 否 | 否 |
| 消失诅咒 | minecraft:vanishing_curse | 1 | ×0.6 | — | — | — | — | 是 | 是 |
| 风爆 | minecraft:wind_burst | 3 | ×1.5 | ×2.2 | ×3 | — | — | 是 | 否 |
