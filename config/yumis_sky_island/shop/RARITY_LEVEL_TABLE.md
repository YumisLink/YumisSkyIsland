# 稀有度与物品等级表（审阅草案）

物品稀有度和职业制造品质是独立轴。此表岛等级范围为0～100。

| 稀有度 | 标准商店 | 每日权重 | 隐藏权重 | 默认库存 |
|---|---|---:|---:|---:|
| 普通 | 常驻 | 0 | 0 | 不限制 |
| 稀有 | 不上架 | 70 | 15 | 64 |
| 史诗 | 不上架 | 25 | 50 | 16 |
| 传说 | 不上架 | 5 | 35 | 1 |

先抽稀有度，再按产品权重从已解锁候选抽一项；抽完移除该商品，避免同日重复。
某稀有度没有候选时剔除并对剩余权重重新归一化；12/16为最多栏位，不用未解锁物品补足。
关键材料 offerWeight=15，提高选中机会但不构成保底。默认末地框架单次库存12。
上架按刷新时队伍等级快照筛选；购买时仍检查当前等级。升降级不会重抽，新等级商品下一轮才可能上架。

| 物品 | 商品ID | 稀有度 | 岛等级 | 渠道 | 抽取权重 | 库存 |
|---|---|---|---:|---|---:|---:|
| 木炭 | minecraft:charcoal | 普通 | 0 | 仅出售/自行合成 | — | — |
| 砂土 | minecraft:coarse_dirt | 普通 | 0 | 仅出售/自行合成 | — | — |
| 圆石 | minecraft:cobblestone | 普通 | 0 | 标准常驻 | — | — |
| 泥土 | minecraft:dirt | 普通 | 0 | 标准常驻 | — | — |
| 草方块 | minecraft:grass_block | 普通 | 0 | 标准常驻 | — | — |
| 橡木原木 | minecraft:oak_log | 普通 | 0 | 标准常驻 | — | — |
| 橡木木板 | minecraft:oak_planks | 普通 | 0 | 标准常驻 | — | — |
| 橡树树苗 | minecraft:oak_sapling | 普通 | 0 | 标准常驻 | — | — |
| 石头 | minecraft:stone | 普通 | 0 | 标准常驻 | — | — |
| 小麦 | minecraft:wheat | 普通 | 0 | 仅出售/自行合成 | — | — |
| 小麦种子 | minecraft:wheat_seeds | 普通 | 0 | 标准常驻 | — | — |
| 白桦原木 | minecraft:birch_log | 稀有 | 0 | 每日/隐藏 | 1 | 64 |
| 白桦木板 | minecraft:birch_planks | 稀有 | 0 | 每日/隐藏 | 1 | 64 |
| 白桦树苗 | minecraft:birch_sapling | 稀有 | 0 | 每日/隐藏 | 1 | 64 |
| 腐肉 | minecraft:rotten_flesh | 稀有 | 0 | 每日/隐藏 | 1 | 64 |
| 蜘蛛眼 | minecraft:spider_eye | 稀有 | 0 | 每日/隐藏 | 1 | 64 |
| 云杉原木 | minecraft:spruce_log | 稀有 | 0 | 每日/隐藏 | 1 | 64 |
| 云杉木板 | minecraft:spruce_planks | 稀有 | 0 | 每日/隐藏 | 1 | 64 |
| 云杉树苗 | minecraft:spruce_sapling | 稀有 | 0 | 每日/隐藏 | 1 | 64 |
| 绒球葱 | minecraft:allium | 普通 | 3 | 标准常驻 | — | — |
| 苹果 | minecraft:apple | 普通 | 3 | 标准常驻 | — | — |
| 犰狳鳞甲 | minecraft:armadillo_scute | 普通 | 3 | 标准常驻 | — | — |
| 蓝花美耳草 | minecraft:azure_bluet | 普通 | 3 | 标准常驻 | — | — |
| 烤马铃薯 | minecraft:baked_potato | 普通 | 3 | 仅出售/自行合成 | — | — |
| 竹子 | minecraft:bamboo | 普通 | 3 | 标准常驻 | — | — |
| 竹板 | minecraft:bamboo_planks | 普通 | 3 | 标准常驻 | — | — |
| 甜菜根 | minecraft:beetroot | 普通 | 3 | 仅出售/自行合成 | — | — |
| 甜菜种子 | minecraft:beetroot_seeds | 普通 | 3 | 标准常驻 | — | — |
| 甜菜汤 | minecraft:beetroot_soup | 普通 | 3 | 仅出售/自行合成 | — | — |
| 蓝色染料 | minecraft:blue_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 兰花 | minecraft:blue_orchid | 普通 | 3 | 标准常驻 | — | — |
| 骨块 | minecraft:bone_block | 普通 | 3 | 仅出售/自行合成 | — | — |
| 波纹边旗帜图案 | minecraft:bordure_indented_banner_pattern | 普通 | 3 | 仅出售/自行合成 | — | — |
| 碗 | minecraft:bowl | 普通 | 3 | 仅出售/自行合成 | — | — |
| 脑纹珊瑚 | minecraft:brain_coral | 普通 | 3 | 标准常驻 | — | — |
| 脑纹珊瑚块 | minecraft:brain_coral_block | 普通 | 3 | 标准常驻 | — | — |
| 脑纹珊瑚扇 | minecraft:brain_coral_fan | 普通 | 3 | 标准常驻 | — | — |
| 面包 | minecraft:bread | 普通 | 3 | 仅出售/自行合成 | — | — |
| 气泡珊瑚 | minecraft:bubble_coral | 普通 | 3 | 标准常驻 | — | — |
| 气泡珊瑚块 | minecraft:bubble_coral_block | 普通 | 3 | 标准常驻 | — | — |
| 气泡珊瑚扇 | minecraft:bubble_coral_fan | 普通 | 3 | 标准常驻 | — | — |
| 胡萝卜 | minecraft:carrot | 普通 | 3 | 标准常驻 | — | — |
| 闭合的眼眸花 | minecraft:closed_eyeblossom | 普通 | 3 | 标准常驻 | — | — |
| 蜘蛛网 | minecraft:cobweb | 普通 | 3 | 标准常驻 | — | — |
| 矢车菊 | minecraft:cornflower | 普通 | 3 | 标准常驻 | — | — |
| 嘎枝之心 | minecraft:creaking_heart | 普通 | 3 | 仅出售/自行合成 | — | — |
| 旗帜图案 | minecraft:creeper_banner_pattern | 普通 | 3 | 仅出售/自行合成 | — | — |
| 青色染料 | minecraft:cyan_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 蒲公英 | minecraft:dandelion | 普通 | 3 | 标准常驻 | — | — |
| 唱片残片 | minecraft:disc_fragment_5 | 普通 | 3 | 标准常驻 | — | — |
| 回响碎片 | minecraft:echo_shard | 普通 | 3 | 标准常驻 | — | — |
| 附魔之瓶 | minecraft:experience_bottle | 普通 | 3 | 标准常驻 | — | — |
| 羽毛 | minecraft:feather | 普通 | 3 | 标准常驻 | — | — |
| 火珊瑚 | minecraft:fire_coral | 普通 | 3 | 标准常驻 | — | — |
| 火珊瑚块 | minecraft:fire_coral_block | 普通 | 3 | 标准常驻 | — | — |
| 火珊瑚扇 | minecraft:fire_coral_fan | 普通 | 3 | 标准常驻 | — | — |
| 萤火虫灌木丛 | minecraft:firefly_bush | 普通 | 3 | 标准常驻 | — | — |
| 燧石 | minecraft:flint | 普通 | 3 | 标准常驻 | — | — |
| 旗帜图案 | minecraft:flow_banner_pattern | 普通 | 3 | 标准常驻 | — | — |
| 旗帜图案 | minecraft:flower_banner_pattern | 普通 | 3 | 仅出售/自行合成 | — | — |
| 镶金黑石 | minecraft:gilded_blackstone | 普通 | 3 | 标准常驻 | — | — |
| 旗帜图案 | minecraft:globe_banner_pattern | 普通 | 3 | 标准常驻 | — | — |
| 发光地衣 | minecraft:glow_lichen | 普通 | 3 | 标准常驻 | — | — |
| 山羊角 | minecraft:goat_horn | 普通 | 3 | 标准常驻 | — | — |
| 灰色染料 | minecraft:gray_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 旗帜图案 | minecraft:guster_banner_pattern | 普通 | 3 | 标准常驻 | — | — |
| 干草捆 | minecraft:hay_block | 普通 | 3 | 仅出售/自行合成 | — | — |
| 鹿角珊瑚 | minecraft:horn_coral | 普通 | 3 | 标准常驻 | — | — |
| 鹿角珊瑚块 | minecraft:horn_coral_block | 普通 | 3 | 标准常驻 | — | — |
| 鹿角珊瑚扇 | minecraft:horn_coral_fan | 普通 | 3 | 标准常驻 | — | — |
| 南瓜灯 | minecraft:jack_o_lantern | 普通 | 3 | 仅出售/自行合成 | — | — |
| 淡蓝色染料 | minecraft:light_blue_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 淡灰色染料 | minecraft:light_gray_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 丁香 | minecraft:lilac | 普通 | 3 | 标准常驻 | — | — |
| 铃兰 | minecraft:lily_of_the_valley | 普通 | 3 | 标准常驻 | — | — |
| 睡莲 | minecraft:lily_pad | 普通 | 3 | 仅出售/自行合成 | — | — |
| 品红色染料 | minecraft:magenta_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 西瓜 | minecraft:melon | 普通 | 3 | 仅出售/自行合成 | — | — |
| 西瓜种子 | minecraft:melon_seeds | 普通 | 3 | 标准常驻 | — | — |
| 西瓜片 | minecraft:melon_slice | 普通 | 3 | 仅出售/自行合成 | — | — |
| 覆地苔藓 | minecraft:moss_carpet | 普通 | 3 | 仅出售/自行合成 | — | — |
| 菌丝体 | minecraft:mycelium | 普通 | 3 | 标准常驻 | — | — |
| 张开的眼眸花 | minecraft:open_eyeblossom | 普通 | 3 | 标准常驻 | — | — |
| 橙色染料 | minecraft:orange_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 橙色郁金香 | minecraft:orange_tulip | 普通 | 3 | 标准常驻 | — | — |
| 滨菊 | minecraft:oxeye_daisy | 普通 | 3 | 标准常驻 | — | — |
| 苍白苔藓块 | minecraft:pale_moss_block | 普通 | 3 | 标准常驻 | — | — |
| 苍白覆地苔藓 | minecraft:pale_moss_carpet | 普通 | 3 | 仅出售/自行合成 | — | — |
| 纸 | minecraft:paper | 普通 | 3 | 仅出售/自行合成 | — | — |
| 牡丹 | minecraft:peony | 普通 | 3 | 标准常驻 | — | — |
| 旗帜图案 | minecraft:piglin_banner_pattern | 普通 | 3 | 标准常驻 | — | — |
| 粉红色染料 | minecraft:pink_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 粉红色花簇 | minecraft:pink_petals | 普通 | 3 | 标准常驻 | — | — |
| 粉红色郁金香 | minecraft:pink_tulip | 普通 | 3 | 标准常驻 | — | — |
| 瓶子草 | minecraft:pitcher_plant | 普通 | 3 | 仅出售/自行合成 | — | — |
| 瓶子草荚果 | minecraft:pitcher_pod | 普通 | 3 | 标准常驻 | — | — |
| 灰化土 | minecraft:podzol | 普通 | 3 | 标准常驻 | — | — |
| 毒马铃薯 | minecraft:poisonous_potato | 普通 | 3 | 仅出售/自行合成 | — | — |
| 虞美人 | minecraft:poppy | 普通 | 3 | 标准常驻 | — | — |
| 马铃薯 | minecraft:potato | 普通 | 3 | 标准常驻 | — | — |
| 南瓜 | minecraft:pumpkin | 普通 | 3 | 仅出售/自行合成 | — | — |
| 南瓜种子 | minecraft:pumpkin_seeds | 普通 | 3 | 标准常驻 | — | — |
| 紫色染料 | minecraft:purple_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 兔子皮 | minecraft:rabbit_hide | 普通 | 3 | 标准常驻 | — | — |
| 红色染料 | minecraft:red_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 红色郁金香 | minecraft:red_tulip | 普通 | 3 | 标准常驻 | — | — |
| 树脂块 | minecraft:resin_block | 普通 | 3 | 仅出售/自行合成 | — | — |
| 树脂砖 | minecraft:resin_brick | 普通 | 3 | 仅出售/自行合成 | — | — |
| 树脂团 | minecraft:resin_clump | 普通 | 3 | 仅出售/自行合成 | — | — |
| 玫瑰丛 | minecraft:rose_bush | 普通 | 3 | 标准常驻 | — | — |
| 沙子 | minecraft:sand | 普通 | 3 | 标准常驻 | — | — |
| 幽匿块 | minecraft:sculk | 普通 | 3 | 标准常驻 | — | — |
| 幽匿感测体 | minecraft:sculk_sensor | 普通 | 3 | 标准常驻 | — | — |
| 幽匿尖啸体 | minecraft:sculk_shrieker | 普通 | 3 | 标准常驻 | — | — |
| 幽匿脉络 | minecraft:sculk_vein | 普通 | 3 | 标准常驻 | — | — |
| 海泡菜 | minecraft:sea_pickle | 普通 | 3 | 标准常驻 | — | — |
| 小型垂滴叶 | minecraft:small_dripleaf | 普通 | 3 | 标准常驻 | — | — |
| 嗅探兽蛋 | minecraft:sniffer_egg | 普通 | 3 | 标准常驻 | — | — |
| 甘蔗 | minecraft:sugar_cane | 普通 | 3 | 标准常驻 | — | — |
| 向日葵 | minecraft:sunflower | 普通 | 3 | 标准常驻 | — | — |
| 甜浆果 | minecraft:sweet_berries | 普通 | 3 | 标准常驻 | — | — |
| 火把花 | minecraft:torchflower | 普通 | 3 | 仅出售/自行合成 | — | — |
| 火把花种子 | minecraft:torchflower_seeds | 普通 | 3 | 标准常驻 | — | — |
| 管珊瑚 | minecraft:tube_coral | 普通 | 3 | 标准常驻 | — | — |
| 管珊瑚块 | minecraft:tube_coral_block | 普通 | 3 | 标准常驻 | — | — |
| 管珊瑚扇 | minecraft:tube_coral_fan | 普通 | 3 | 标准常驻 | — | — |
| 海龟鳞甲 | minecraft:turtle_scute | 普通 | 3 | 标准常驻 | — | — |
| 缠怨藤 | minecraft:twisting_vines | 普通 | 3 | 标准常驻 | — | — |
| 藤蔓 | minecraft:vine | 普通 | 3 | 标准常驻 | — | — |
| 垂泪藤 | minecraft:weeping_vines | 普通 | 3 | 标准常驻 | — | — |
| 白色染料 | minecraft:white_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 白色郁金香 | minecraft:white_tulip | 普通 | 3 | 标准常驻 | — | — |
| 野花簇 | minecraft:wildflowers | 普通 | 3 | 标准常驻 | — | — |
| 黄色染料 | minecraft:yellow_dye | 普通 | 3 | 仅出售/自行合成 | — | — |
| 骨头 | minecraft:bone | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 玻璃瓶 | minecraft:glass_bottle | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 赭黄蛙明灯 | minecraft:ochre_froglight | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 珠光蛙明灯 | minecraft:pearlescent_froglight | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 河豚 | minecraft:pufferfish | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 兔子脚 | minecraft:rabbit_foot | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 幽匿催发体 | minecraft:sculk_catalyst | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 线 | minecraft:string | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 热带鱼 | minecraft:tropical_fish | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 青翠蛙明灯 | minecraft:verdant_froglight | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 湿海绵 | minecraft:wet_sponge | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 凋灵玫瑰 | minecraft:wither_rose | 稀有 | 3 | 每日/隐藏 | 1 | 64 |
| 杜鹃花丛 | minecraft:azalea | 普通 | 5 | 标准常驻 | — | — |
| 生牛肉 | minecraft:beef | 普通 | 5 | 标准常驻 | — | — |
| 黑色收纳袋 | minecraft:black_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 黑色染料 | minecraft:black_dye | 普通 | 5 | 仅出售/自行合成 | — | — |
| 黑色挽具 | minecraft:black_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 蓝色收纳袋 | minecraft:blue_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 蓝色挽具 | minecraft:blue_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 书 | minecraft:book | 普通 | 5 | 仅出售/自行合成 | — | — |
| 棕色收纳袋 | minecraft:brown_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 棕色染料 | minecraft:brown_dye | 普通 | 5 | 仅出售/自行合成 | — | — |
| 棕色挽具 | minecraft:brown_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 棕色蘑菇 | minecraft:brown_mushroom | 普通 | 5 | 标准常驻 | — | — |
| 仙人掌 | minecraft:cactus | 普通 | 5 | 标准常驻 | — | — |
| 生鸡肉 | minecraft:chicken | 普通 | 5 | 标准常驻 | — | — |
| 可可豆 | minecraft:cocoa_beans | 普通 | 5 | 标准常驻 | — | — |
| 牛排 | minecraft:cooked_beef | 普通 | 5 | 仅出售/自行合成 | — | — |
| 熟鸡肉 | minecraft:cooked_chicken | 普通 | 5 | 仅出售/自行合成 | — | — |
| 熟鳕鱼 | minecraft:cooked_cod | 普通 | 5 | 仅出售/自行合成 | — | — |
| 熟羊肉 | minecraft:cooked_mutton | 普通 | 5 | 仅出售/自行合成 | — | — |
| 熟猪排 | minecraft:cooked_porkchop | 普通 | 5 | 仅出售/自行合成 | — | — |
| 熟兔肉 | minecraft:cooked_rabbit | 普通 | 5 | 仅出售/自行合成 | — | — |
| 熟鲑鱼 | minecraft:cooked_salmon | 普通 | 5 | 仅出售/自行合成 | — | — |
| 曲奇 | minecraft:cookie | 普通 | 5 | 仅出售/自行合成 | — | — |
| 青色收纳袋 | minecraft:cyan_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 青色挽具 | minecraft:cyan_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 干海带 | minecraft:dried_kelp | 普通 | 5 | 仅出售/自行合成 | — | — |
| 干海带块 | minecraft:dried_kelp_block | 普通 | 5 | 仅出售/自行合成 | — | — |
| 盛开的杜鹃花丛 | minecraft:flowering_azalea | 普通 | 5 | 标准常驻 | — | — |
| 灰色收纳袋 | minecraft:gray_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 灰色挽具 | minecraft:gray_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 绿色收纳袋 | minecraft:green_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 绿色染料 | minecraft:green_dye | 普通 | 5 | 仅出售/自行合成 | — | — |
| 绿色挽具 | minecraft:green_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 墨囊 | minecraft:ink_sac | 普通 | 5 | 仅出售/自行合成 | — | — |
| 海带 | minecraft:kelp | 普通 | 5 | 标准常驻 | — | — |
| 淡蓝色收纳袋 | minecraft:light_blue_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 淡蓝色挽具 | minecraft:light_blue_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 淡灰色收纳袋 | minecraft:light_gray_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 淡灰色挽具 | minecraft:light_gray_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 黄绿色收纳袋 | minecraft:lime_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 黄绿色染料 | minecraft:lime_dye | 普通 | 5 | 仅出售/自行合成 | — | — |
| 黄绿色挽具 | minecraft:lime_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 品红色收纳袋 | minecraft:magenta_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 品红色挽具 | minecraft:magenta_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 苔藓块 | minecraft:moss_block | 普通 | 5 | 标准常驻 | — | — |
| 蘑菇煲 | minecraft:mushroom_stew | 普通 | 5 | 仅出售/自行合成 | — | — |
| 生羊肉 | minecraft:mutton | 普通 | 5 | 标准常驻 | — | — |
| 橙色收纳袋 | minecraft:orange_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 橙色挽具 | minecraft:orange_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 粉红色收纳袋 | minecraft:pink_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 粉红色挽具 | minecraft:pink_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 紫色收纳袋 | minecraft:purple_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 紫色挽具 | minecraft:purple_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 生兔肉 | minecraft:rabbit | 普通 | 5 | 标准常驻 | — | — |
| 兔肉煲 | minecraft:rabbit_stew | 普通 | 5 | 仅出售/自行合成 | — | — |
| 红色收纳袋 | minecraft:red_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 红色挽具 | minecraft:red_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 红色蘑菇 | minecraft:red_mushroom | 普通 | 5 | 标准常驻 | — | — |
| 雪 | minecraft:snow | 普通 | 5 | 仅出售/自行合成 | — | — |
| 雪块 | minecraft:snow_block | 普通 | 5 | 仅出售/自行合成 | — | — |
| 白色收纳袋 | minecraft:white_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 白色挽具 | minecraft:white_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 黄色收纳袋 | minecraft:yellow_bundle | 普通 | 5 | 仅出售/自行合成 | — | — |
| 黄色挽具 | minecraft:yellow_harness | 普通 | 5 | 仅出售/自行合成 | — | — |
| 金合欢原木 | minecraft:acacia_log | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 金合欢木板 | minecraft:acacia_planks | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 金合欢树苗 | minecraft:acacia_sapling | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 鸡刷怪蛋 | minecraft:chicken_spawn_egg | 稀有 | 5 | 每日/隐藏 | 1 | 1 |
| 生鳕鱼 | minecraft:cod | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 牛刷怪蛋 | minecraft:cow_spawn_egg | 稀有 | 5 | 每日/隐藏 | 1 | 1 |
| 火药 | minecraft:gunpowder | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 丛林原木 | minecraft:jungle_log | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 丛林木板 | minecraft:jungle_planks | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 丛林树苗 | minecraft:jungle_sapling | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 皮革 | minecraft:leather | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 猪刷怪蛋 | minecraft:pig_spawn_egg | 稀有 | 5 | 每日/隐藏 | 1 | 1 |
| 生猪排 | minecraft:porkchop | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 兔子刷怪蛋 | minecraft:rabbit_spawn_egg | 稀有 | 5 | 每日/隐藏 | 1 | 1 |
| 生鲑鱼 | minecraft:salmon | 稀有 | 5 | 每日/隐藏 | 1 | 64 |
| 绵羊刷怪蛋 | minecraft:sheep_spawn_egg | 稀有 | 5 | 每日/隐藏 | 1 | 1 |
| 深色橡木原木 | minecraft:dark_oak_log | 稀有 | 8 | 每日/隐藏 | 1 | 64 |
| 深色橡木木板 | minecraft:dark_oak_planks | 稀有 | 8 | 每日/隐藏 | 1 | 64 |
| 深色橡树树苗 | minecraft:dark_oak_sapling | 稀有 | 8 | 每日/隐藏 | 1 | 64 |
| 铜锭 | minecraft:copper_ingot | 普通 | 10 | 标准常驻 | — | — |
| 发光浆果 | minecraft:glow_berries | 普通 | 10 | 标准常驻 | — | — |
| 铁锭 | minecraft:iron_ingot | 普通 | 10 | 标准常驻 | — | — |
| 滞留型水瓶 | minecraft:lingering_potion__water | 普通 | 10 | 仅出售/自行合成 | — | — |
| 水瓶 | minecraft:potion__water | 普通 | 10 | 仅出售/自行合成 | — | — |
| 红沙 | minecraft:red_sand | 普通 | 10 | 标准常驻 | — | — |
| 喷溅型水瓶 | minecraft:splash_potion__water | 普通 | 10 | 仅出售/自行合成 | — | — |
| 樱花原木 | minecraft:cherry_log | 稀有 | 10 | 每日/隐藏 | 1 | 64 |
| 樱花木板 | minecraft:cherry_planks | 稀有 | 10 | 每日/隐藏 | 1 | 64 |
| 樱花树苗 | minecraft:cherry_sapling | 稀有 | 10 | 每日/隐藏 | 1 | 64 |
| 深板岩圆石 | minecraft:cobbled_deepslate | 稀有 | 10 | 每日/隐藏 | 1 | 64 |
| 深板岩 | minecraft:deepslate | 稀有 | 10 | 每日/隐藏 | 1 | 64 |
| 滞留型粗制的药水 | minecraft:lingering_potion__awkward | 稀有 | 10 | 仅出售/自行合成 | — | — |
| 滞留型平凡的药水 | minecraft:lingering_potion__mundane | 稀有 | 10 | 仅出售/自行合成 | — | — |
| 滞留型浓稠的药水 | minecraft:lingering_potion__thick | 稀有 | 10 | 仅出售/自行合成 | — | — |
| 红树原木 | minecraft:mangrove_log | 稀有 | 10 | 每日/隐藏 | 1 | 64 |
| 红树木板 | minecraft:mangrove_planks | 稀有 | 10 | 每日/隐藏 | 1 | 64 |
| 红树胎生苗 | minecraft:mangrove_propagule | 稀有 | 10 | 每日/隐藏 | 1 | 64 |
| 粗制的药水 | minecraft:potion__awkward | 稀有 | 10 | 仅出售/自行合成 | — | — |
| 平凡的药水 | minecraft:potion__mundane | 稀有 | 10 | 仅出售/自行合成 | — | — |
| 浓稠的药水 | minecraft:potion__thick | 稀有 | 10 | 仅出售/自行合成 | — | — |
| 喷溅型粗制的药水 | minecraft:splash_potion__awkward | 稀有 | 10 | 仅出售/自行合成 | — | — |
| 喷溅型平凡的药水 | minecraft:splash_potion__mundane | 稀有 | 10 | 仅出售/自行合成 | — | — |
| 喷溅型浓稠的药水 | minecraft:splash_potion__thick | 稀有 | 10 | 仅出售/自行合成 | — | — |
| 凝灰岩 | minecraft:tuff | 稀有 | 10 | 每日/隐藏 | 1 | 64 |
| 蛋糕 | minecraft:cake | 普通 | 15 | 仅出售/自行合成 | — | — |
| 发酵蛛眼 | minecraft:fermented_spider_eye | 普通 | 15 | 仅出售/自行合成 | — | — |
| 荧光墨囊 | minecraft:glow_ink_sac | 普通 | 15 | 标准常驻 | — | — |
| 荧石 | minecraft:glowstone | 普通 | 15 | 仅出售/自行合成 | — | — |
| 荧石粉 | minecraft:glowstone_dust | 普通 | 15 | 标准常驻 | — | — |
| 蜂蜜块 | minecraft:honey_block | 普通 | 15 | 仅出售/自行合成 | — | — |
| 蜂蜜瓶 | minecraft:honey_bottle | 普通 | 15 | 标准常驻 | — | — |
| 蜜脾 | minecraft:honeycomb | 普通 | 15 | 标准常驻 | — | — |
| 蜜脾块 | minecraft:honeycomb_block | 普通 | 15 | 仅出售/自行合成 | — | — |
| 南瓜派 | minecraft:pumpkin_pie | 普通 | 15 | 仅出售/自行合成 | — | — |
| 红石粉 | minecraft:redstone | 普通 | 15 | 标准常驻 | — | — |
| 黏液块 | minecraft:slime_block | 普通 | 15 | 仅出售/自行合成 | — | — |
| 蜜蜂刷怪蛋 | minecraft:bee_spawn_egg | 稀有 | 15 | 每日/隐藏 | 1 | 1 |
| 方解石 | minecraft:calcite | 稀有 | 15 | 每日/隐藏 | 1 | 64 |
| 水下速掘 1 附魔书 | minecraft:enchanted_book__aqua_affinity_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 节肢杀手 1 附魔书 | minecraft:enchanted_book__bane_of_arthropods_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 爆炸保护 1 附魔书 | minecraft:enchanted_book__blast_protection_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 破甲 1 附魔书 | minecraft:enchanted_book__breach_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 引雷 1 附魔书 | minecraft:enchanted_book__channeling_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 致密 1 附魔书 | minecraft:enchanted_book__density_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 深海探索者 1 附魔书 | minecraft:enchanted_book__depth_strider_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 效率 1 附魔书 | minecraft:enchanted_book__efficiency_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 摔落缓冲 1 附魔书 | minecraft:enchanted_book__feather_falling_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 火焰附加 1 附魔书 | minecraft:enchanted_book__fire_aspect_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 火焰保护 1 附魔书 | minecraft:enchanted_book__fire_protection_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 火矢 1 附魔书 | minecraft:enchanted_book__flame_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 时运 1 附魔书 | minecraft:enchanted_book__fortune_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 穿刺 1 附魔书 | minecraft:enchanted_book__impaling_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 无限 1 附魔书 | minecraft:enchanted_book__infinity_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 击退 1 附魔书 | minecraft:enchanted_book__knockback_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 抢夺 1 附魔书 | minecraft:enchanted_book__looting_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 忠诚 1 附魔书 | minecraft:enchanted_book__loyalty_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 海之眷顾 1 附魔书 | minecraft:enchanted_book__luck_of_the_sea_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 突进 1 附魔书 | minecraft:enchanted_book__lunge_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 饵钓 1 附魔书 | minecraft:enchanted_book__lure_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 多重射击 1 附魔书 | minecraft:enchanted_book__multishot_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 穿透 1 附魔书 | minecraft:enchanted_book__piercing_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 力量 1 附魔书 | minecraft:enchanted_book__power_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 弹射物保护 1 附魔书 | minecraft:enchanted_book__projectile_protection_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 保护 1 附魔书 | minecraft:enchanted_book__protection_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 冲击 1 附魔书 | minecraft:enchanted_book__punch_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 快速装填 1 附魔书 | minecraft:enchanted_book__quick_charge_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 水下呼吸 1 附魔书 | minecraft:enchanted_book__respiration_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 激流 1 附魔书 | minecraft:enchanted_book__riptide_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 锋利 1 附魔书 | minecraft:enchanted_book__sharpness_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 精准采集 1 附魔书 | minecraft:enchanted_book__silk_touch_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 亡灵杀手 1 附魔书 | minecraft:enchanted_book__smite_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 横扫之刃 1 附魔书 | minecraft:enchanted_book__sweeping_edge_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 荆棘 1 附魔书 | minecraft:enchanted_book__thorns_1 | 稀有 | 15 | 仅出售/自行合成 | — | — |
| 耐久 1 附魔书 | minecraft:enchanted_book__unbreaking_1 | 稀有 | 15 | 每日/隐藏 | 1 | 4 |
| 黏液球 | minecraft:slime_ball | 稀有 | 15 | 每日/隐藏 | 1 | 64 |
| 糖 | minecraft:sugar | 稀有 | 15 | 每日/隐藏 | 1 | 64 |
| 蓝冰 | minecraft:blue_ice | 普通 | 20 | 仅出售/自行合成 | — | — |
| 冰 | minecraft:ice | 普通 | 20 | 标准常驻 | — | — |
| 浮冰 | minecraft:packed_ice | 普通 | 20 | 仅出售/自行合成 | — | — |
| 烈性硫黄 | minecraft:potent_sulfur | 普通 | 20 | 仅出售/自行合成 | — | — |
| 硫黄 | minecraft:sulfur | 普通 | 20 | 标准常驻 | — | — |
| 垂钓纹样陶片 | minecraft:angler_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 弓箭纹样陶片 | minecraft:archer_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 举臂纹样陶片 | minecraft:arms_up_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 利刃纹样陶片 | minecraft:blade_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 佳酿纹样陶片 | minecraft:brewer_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 烈焰纹样陶片 | minecraft:burn_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 危机纹样陶片 | minecraft:danger_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 节肢杀手 2 附魔书 | minecraft:enchanted_book__bane_of_arthropods_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 爆炸保护 2 附魔书 | minecraft:enchanted_book__blast_protection_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 破甲 2 附魔书 | minecraft:enchanted_book__breach_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 致密 2 附魔书 | minecraft:enchanted_book__density_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 深海探索者 2 附魔书 | minecraft:enchanted_book__depth_strider_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 效率 2 附魔书 | minecraft:enchanted_book__efficiency_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 摔落缓冲 2 附魔书 | minecraft:enchanted_book__feather_falling_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 火焰附加 2 附魔书 | minecraft:enchanted_book__fire_aspect_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 火焰保护 2 附魔书 | minecraft:enchanted_book__fire_protection_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 时运 2 附魔书 | minecraft:enchanted_book__fortune_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 穿刺 2 附魔书 | minecraft:enchanted_book__impaling_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 击退 2 附魔书 | minecraft:enchanted_book__knockback_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 抢夺 2 附魔书 | minecraft:enchanted_book__looting_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 忠诚 2 附魔书 | minecraft:enchanted_book__loyalty_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 海之眷顾 2 附魔书 | minecraft:enchanted_book__luck_of_the_sea_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 突进 2 附魔书 | minecraft:enchanted_book__lunge_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 饵钓 2 附魔书 | minecraft:enchanted_book__lure_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 穿透 2 附魔书 | minecraft:enchanted_book__piercing_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 力量 2 附魔书 | minecraft:enchanted_book__power_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 弹射物保护 2 附魔书 | minecraft:enchanted_book__projectile_protection_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 保护 2 附魔书 | minecraft:enchanted_book__protection_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 冲击 2 附魔书 | minecraft:enchanted_book__punch_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 快速装填 2 附魔书 | minecraft:enchanted_book__quick_charge_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 水下呼吸 2 附魔书 | minecraft:enchanted_book__respiration_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 激流 2 附魔书 | minecraft:enchanted_book__riptide_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 锋利 2 附魔书 | minecraft:enchanted_book__sharpness_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 亡灵杀手 2 附魔书 | minecraft:enchanted_book__smite_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 横扫之刃 2 附魔书 | minecraft:enchanted_book__sweeping_edge_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 荆棘 2 附魔书 | minecraft:enchanted_book__thorns_2 | 稀有 | 20 | 仅出售/自行合成 | — | — |
| 耐久 2 附魔书 | minecraft:enchanted_book__unbreaking_2 | 稀有 | 20 | 每日/隐藏 | 1 | 4 |
| 探险纹样陶片 | minecraft:explorer_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 涡流纹样陶片 | minecraft:flow_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 挚友纹样陶片 | minecraft:friend_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 旋风纹样陶片 | minecraft:guster_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 爱心纹样陶片 | minecraft:heart_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 心碎纹样陶片 | minecraft:heartbreak_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 狼嚎纹样陶片 | minecraft:howl_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 采矿纹样陶片 | minecraft:miner_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 悲恸纹样陶片 | minecraft:mourner_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 幻翼膜 | minecraft:phantom_membrane | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 富饶纹样陶片 | minecraft:plenty_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 珍宝纹样陶片 | minecraft:prize_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 刮削纹样陶片 | minecraft:scrape_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 麦捆纹样陶片 | minecraft:sheaf_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 树荫纹样陶片 | minecraft:shelter_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 头颅纹样陶片 | minecraft:skull_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 嗅探纹样陶片 | minecraft:snort_pottery_sherd | 稀有 | 20 | 每日/隐藏 | 1 | 64 |
| 村民刷怪蛋 | minecraft:villager_spawn_egg | 稀有 | 20 | 每日/隐藏 | 1 | 1 |
| 玄武岩 | minecraft:basalt | 普通 | 25 | 标准常驻 | — | — |
| 红砖 | minecraft:brick | 普通 | 25 | 仅出售/自行合成 | — | — |
| 黏土 | minecraft:clay | 普通 | 25 | 仅出售/自行合成 | — | — |
| 黏土球 | minecraft:clay_ball | 普通 | 25 | 标准常驻 | — | — |
| 滴水石块 | minecraft:dripstone_block | 普通 | 25 | 标准常驻 | — | — |
| 砖纹旗帜图案 | minecraft:field_masoned_banner_pattern | 普通 | 25 | 仅出售/自行合成 | — | — |
| 金苹果 | minecraft:golden_apple | 普通 | 25 | 仅出售/自行合成 | — | — |
| 下界岩 | minecraft:netherrack | 普通 | 25 | 标准常驻 | — | — |
| 黑曜石 | minecraft:obsidian | 普通 | 25 | 标准常驻 | — | — |
| 滴水石锥 | minecraft:pointed_dripstone | 普通 | 25 | 标准常驻 | — | — |
| 悦灵刷怪蛋 | minecraft:allay_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 犰狳刷怪蛋 | minecraft:armadillo_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 美西螈刷怪蛋 | minecraft:axolotl_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 蝙蝠刷怪蛋 | minecraft:bat_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 骆驼刷怪蛋 | minecraft:camel_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 猫刷怪蛋 | minecraft:cat_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 鳕鱼刷怪蛋 | minecraft:cod_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 铜傀儡刷怪蛋 | minecraft:copper_golem_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 海豚刷怪蛋 | minecraft:dolphin_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 驴刷怪蛋 | minecraft:donkey_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 节肢杀手 3 附魔书 | minecraft:enchanted_book__bane_of_arthropods_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 爆炸保护 3 附魔书 | minecraft:enchanted_book__blast_protection_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 破甲 3 附魔书 | minecraft:enchanted_book__breach_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 致密 3 附魔书 | minecraft:enchanted_book__density_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 深海探索者 3 附魔书 | minecraft:enchanted_book__depth_strider_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 效率 3 附魔书 | minecraft:enchanted_book__efficiency_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 摔落缓冲 3 附魔书 | minecraft:enchanted_book__feather_falling_3 | 稀有 | 25 | 每日/隐藏 | 1 | 4 |
| 火焰保护 3 附魔书 | minecraft:enchanted_book__fire_protection_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 时运 3 附魔书 | minecraft:enchanted_book__fortune_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 穿刺 3 附魔书 | minecraft:enchanted_book__impaling_3 | 稀有 | 25 | 每日/隐藏 | 1 | 4 |
| 抢夺 3 附魔书 | minecraft:enchanted_book__looting_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 忠诚 3 附魔书 | minecraft:enchanted_book__loyalty_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 海之眷顾 3 附魔书 | minecraft:enchanted_book__luck_of_the_sea_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 突进 3 附魔书 | minecraft:enchanted_book__lunge_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 饵钓 3 附魔书 | minecraft:enchanted_book__lure_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 穿透 3 附魔书 | minecraft:enchanted_book__piercing_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 力量 3 附魔书 | minecraft:enchanted_book__power_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 弹射物保护 3 附魔书 | minecraft:enchanted_book__projectile_protection_3 | 稀有 | 25 | 每日/隐藏 | 1 | 4 |
| 保护 3 附魔书 | minecraft:enchanted_book__protection_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 快速装填 3 附魔书 | minecraft:enchanted_book__quick_charge_3 | 稀有 | 25 | 每日/隐藏 | 1 | 4 |
| 水下呼吸 3 附魔书 | minecraft:enchanted_book__respiration_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 激流 3 附魔书 | minecraft:enchanted_book__riptide_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 锋利 3 附魔书 | minecraft:enchanted_book__sharpness_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 亡灵杀手 3 附魔书 | minecraft:enchanted_book__smite_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 横扫之刃 3 附魔书 | minecraft:enchanted_book__sweeping_edge_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 荆棘 3 附魔书 | minecraft:enchanted_book__thorns_3 | 稀有 | 25 | 每日/隐藏 | 1 | 4 |
| 耐久 3 附魔书 | minecraft:enchanted_book__unbreaking_3 | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 狐狸刷怪蛋 | minecraft:fox_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 发光鱿鱼刷怪蛋 | minecraft:glow_squid_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 山羊刷怪蛋 | minecraft:goat_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 快乐恶魂刷怪蛋 | minecraft:happy_ghast_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 马刷怪蛋 | minecraft:horse_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 铁傀儡刷怪蛋 | minecraft:iron_golem_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 滞留型抗火药水 | minecraft:lingering_potion__fire_resistance | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型伤害药水 | minecraft:lingering_potion__harming | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型治疗药水 | minecraft:lingering_potion__healing | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型隐身药水 | minecraft:lingering_potion__invisibility | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型跳跃药水 | minecraft:lingering_potion__leaping | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型夜视药水 | minecraft:lingering_potion__night_vision | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型剧毒药水 | minecraft:lingering_potion__poison | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型再生药水 | minecraft:lingering_potion__regeneration | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型缓降药水 | minecraft:lingering_potion__slow_falling | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型迟缓药水 | minecraft:lingering_potion__slowness | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型力量药水 | minecraft:lingering_potion__strength | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型迅捷药水 | minecraft:lingering_potion__swiftness | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型神龟药水 | minecraft:lingering_potion__turtle_master | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型水肺药水 | minecraft:lingering_potion__water_breathing | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 滞留型虚弱药水 | minecraft:lingering_potion__weakness | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 羊驼刷怪蛋 | minecraft:llama_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 哞菇刷怪蛋 | minecraft:mooshroom_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 骡刷怪蛋 | minecraft:mule_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 鹦鹉螺刷怪蛋 | minecraft:nautilus_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 豹猫刷怪蛋 | minecraft:ocelot_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 熊猫刷怪蛋 | minecraft:panda_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 鹦鹉刷怪蛋 | minecraft:parrot_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 北极熊刷怪蛋 | minecraft:polar_bear_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 抗火药水 | minecraft:potion__fire_resistance | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 伤害药水 | minecraft:potion__harming | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 治疗药水 | minecraft:potion__healing | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 隐身药水 | minecraft:potion__invisibility | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 跳跃药水 | minecraft:potion__leaping | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 夜视药水 | minecraft:potion__night_vision | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 剧毒药水 | minecraft:potion__poison | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 再生药水 | minecraft:potion__regeneration | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 缓降药水 | minecraft:potion__slow_falling | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 迟缓药水 | minecraft:potion__slowness | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 力量药水 | minecraft:potion__strength | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 迅捷药水 | minecraft:potion__swiftness | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 神龟药水 | minecraft:potion__turtle_master | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 水肺药水 | minecraft:potion__water_breathing | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 虚弱药水 | minecraft:potion__weakness | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 河豚刷怪蛋 | minecraft:pufferfish_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 鲑鱼刷怪蛋 | minecraft:salmon_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 骷髅马刷怪蛋 | minecraft:skeleton_horse_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 嗅探兽刷怪蛋 | minecraft:sniffer_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 雪傀儡刷怪蛋 | minecraft:snow_golem_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 灵魂土 | minecraft:soul_soil | 稀有 | 25 | 每日/隐藏 | 1 | 64 |
| 喷溅型抗火药水 | minecraft:splash_potion__fire_resistance | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型伤害药水 | minecraft:splash_potion__harming | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型治疗药水 | minecraft:splash_potion__healing | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型隐身药水 | minecraft:splash_potion__invisibility | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型跳跃药水 | minecraft:splash_potion__leaping | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型夜视药水 | minecraft:splash_potion__night_vision | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型剧毒药水 | minecraft:splash_potion__poison | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型再生药水 | minecraft:splash_potion__regeneration | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型缓降药水 | minecraft:splash_potion__slow_falling | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型迟缓药水 | minecraft:splash_potion__slowness | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型力量药水 | minecraft:splash_potion__strength | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型迅捷药水 | minecraft:splash_potion__swiftness | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型神龟药水 | minecraft:splash_potion__turtle_master | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型水肺药水 | minecraft:splash_potion__water_breathing | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 喷溅型虚弱药水 | minecraft:splash_potion__weakness | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 海绵 | minecraft:sponge | 稀有 | 25 | 仅出售/自行合成 | — | — |
| 鱿鱼刷怪蛋 | minecraft:squid_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 炽足兽刷怪蛋 | minecraft:strider_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 蝌蚪刷怪蛋 | minecraft:tadpole_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 行商羊驼刷怪蛋 | minecraft:trader_llama_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 热带鱼刷怪蛋 | minecraft:tropical_fish_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 海龟刷怪蛋 | minecraft:turtle_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 流浪商人刷怪蛋 | minecraft:wandering_trader_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 狼刷怪蛋 | minecraft:wolf_spawn_egg | 稀有 | 25 | 每日/隐藏 | 1 | 1 |
| 闪烁的西瓜片 | minecraft:glistering_melon_slice | 普通 | 30 | 仅出售/自行合成 | — | — |
| 金锭 | minecraft:gold_ingot | 普通 | 30 | 标准常驻 | — | — |
| 金胡萝卜 | minecraft:golden_carrot | 普通 | 30 | 仅出售/自行合成 | — | — |
| 金蒲公英 | minecraft:golden_dandelion | 普通 | 30 | 标准常驻 | — | — |
| 岩浆块 | minecraft:magma_block | 普通 | 30 | 仅出售/自行合成 | — | — |
| 烈焰粉 | minecraft:blaze_powder | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 烈焰棒 | minecraft:blaze_rod | 稀有 | 30 | 每日/隐藏 | 15 | 64 |
| 绯红菌 | minecraft:crimson_fungus | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 绯红菌岩 | minecraft:crimson_nylium | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 绯红木板 | minecraft:crimson_planks | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 绯红菌柄 | minecraft:crimson_stem | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 哭泣的黑曜石 | minecraft:crying_obsidian | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 节肢杀手 4 附魔书 | minecraft:enchanted_book__bane_of_arthropods_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 爆炸保护 4 附魔书 | minecraft:enchanted_book__blast_protection_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 破甲 4 附魔书 | minecraft:enchanted_book__breach_4 | 稀有 | 30 | 每日/隐藏 | 1 | 4 |
| 致密 4 附魔书 | minecraft:enchanted_book__density_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 效率 4 附魔书 | minecraft:enchanted_book__efficiency_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 摔落缓冲 4 附魔书 | minecraft:enchanted_book__feather_falling_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 火焰保护 4 附魔书 | minecraft:enchanted_book__fire_protection_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 穿刺 4 附魔书 | minecraft:enchanted_book__impaling_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 穿透 4 附魔书 | minecraft:enchanted_book__piercing_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 力量 4 附魔书 | minecraft:enchanted_book__power_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 弹射物保护 4 附魔书 | minecraft:enchanted_book__projectile_protection_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 保护 4 附魔书 | minecraft:enchanted_book__protection_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 锋利 4 附魔书 | minecraft:enchanted_book__sharpness_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 亡灵杀手 4 附魔书 | minecraft:enchanted_book__smite_4 | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 恶魂之泪 | minecraft:ghast_tear | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 岩浆膏 | minecraft:magma_cream | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 鹦鹉螺壳 | minecraft:nautilus_shell | 稀有 | 30 | 仅出售/自行合成 | — | — |
| 下界疣 | minecraft:nether_wart | 稀有 | 30 | 每日/隐藏 | 15 | 64 |
| 诡异菌 | minecraft:warped_fungus | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 诡异菌岩 | minecraft:warped_nylium | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 诡异木板 | minecraft:warped_planks | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 诡异菌柄 | minecraft:warped_stem | 稀有 | 30 | 每日/隐藏 | 1 | 64 |
| 苍白橡木原木 | minecraft:pale_oak_log | 史诗 | 30 | 每日/隐藏 | 1 | 16 |
| 苍白橡木木板 | minecraft:pale_oak_planks | 史诗 | 30 | 每日/隐藏 | 1 | 16 |
| 苍白橡树树苗 | minecraft:pale_oak_sapling | 史诗 | 30 | 每日/隐藏 | 1 | 16 |
| 安山岩 | minecraft:andesite | 普通 | 35 | 标准常驻 | — | — |
| 黑石 | minecraft:blackstone | 普通 | 35 | 标准常驻 | — | — |
| 闪长岩 | minecraft:diorite | 普通 | 35 | 标准常驻 | — | — |
| 失水恶魂 | minecraft:dried_ghast | 普通 | 35 | 仅出售/自行合成 | — | — |
| 花岗岩 | minecraft:granite | 普通 | 35 | 标准常驻 | — | — |
| 沙砾 | minecraft:gravel | 普通 | 35 | 标准常驻 | — | — |
| 下界砖 | minecraft:nether_brick | 普通 | 35 | 仅出售/自行合成 | — | — |
| 下界石英 | minecraft:quartz | 普通 | 35 | 标准常驻 | — | — |
| 灵魂沙 | minecraft:soul_sand | 普通 | 35 | 标准常驻 | — | — |
| 煤炭 | minecraft:coal | 稀有 | 35 | 每日/隐藏 | 1 | 64 |
| 钻石 | minecraft:diamond | 稀有 | 35 | 每日/隐藏 | 15 | 64 |
| 节肢杀手 5 附魔书 | minecraft:enchanted_book__bane_of_arthropods_5 | 稀有 | 35 | 每日/隐藏 | 1 | 4 |
| 致密 5 附魔书 | minecraft:enchanted_book__density_5 | 稀有 | 35 | 每日/隐藏 | 1 | 4 |
| 效率 5 附魔书 | minecraft:enchanted_book__efficiency_5 | 稀有 | 35 | 每日/隐藏 | 1 | 4 |
| 穿刺 5 附魔书 | minecraft:enchanted_book__impaling_5 | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 力量 5 附魔书 | minecraft:enchanted_book__power_5 | 稀有 | 35 | 每日/隐藏 | 1 | 4 |
| 锋利 5 附魔书 | minecraft:enchanted_book__sharpness_5 | 稀有 | 35 | 每日/隐藏 | 1 | 4 |
| 亡灵杀手 5 附魔书 | minecraft:enchanted_book__smite_5 | 稀有 | 35 | 每日/隐藏 | 1 | 4 |
| 滞留型抗火药水（延长） | minecraft:lingering_potion__long_fire_resistance | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型隐身药水（延长） | minecraft:lingering_potion__long_invisibility | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型跳跃药水（延长） | minecraft:lingering_potion__long_leaping | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型夜视药水（延长） | minecraft:lingering_potion__long_night_vision | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型剧毒药水（延长） | minecraft:lingering_potion__long_poison | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型再生药水（延长） | minecraft:lingering_potion__long_regeneration | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型缓降药水（延长） | minecraft:lingering_potion__long_slow_falling | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型迟缓药水（延长） | minecraft:lingering_potion__long_slowness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型力量药水（延长） | minecraft:lingering_potion__long_strength | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型迅捷药水（延长） | minecraft:lingering_potion__long_swiftness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型神龟药水（延长） | minecraft:lingering_potion__long_turtle_master | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型水肺药水（延长） | minecraft:lingering_potion__long_water_breathing | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型虚弱药水（延长） | minecraft:lingering_potion__long_weakness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型伤害药水（强化） | minecraft:lingering_potion__strong_harming | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型治疗药水（强化） | minecraft:lingering_potion__strong_healing | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型跳跃药水（强化） | minecraft:lingering_potion__strong_leaping | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型剧毒药水（强化） | minecraft:lingering_potion__strong_poison | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型再生药水（强化） | minecraft:lingering_potion__strong_regeneration | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型迟缓药水（强化） | minecraft:lingering_potion__strong_slowness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型力量药水（强化） | minecraft:lingering_potion__strong_strength | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型迅捷药水（强化） | minecraft:lingering_potion__strong_swiftness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 滞留型神龟药水（强化） | minecraft:lingering_potion__strong_turtle_master | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 猪灵刷怪蛋 | minecraft:piglin_spawn_egg | 稀有 | 35 | 每日/隐藏 | 1 | 1 |
| 抗火药水（延长） | minecraft:potion__long_fire_resistance | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 隐身药水（延长） | minecraft:potion__long_invisibility | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 跳跃药水（延长） | minecraft:potion__long_leaping | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 夜视药水（延长） | minecraft:potion__long_night_vision | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 剧毒药水（延长） | minecraft:potion__long_poison | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 再生药水（延长） | minecraft:potion__long_regeneration | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 缓降药水（延长） | minecraft:potion__long_slow_falling | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 迟缓药水（延长） | minecraft:potion__long_slowness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 力量药水（延长） | minecraft:potion__long_strength | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 迅捷药水（延长） | minecraft:potion__long_swiftness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 神龟药水（延长） | minecraft:potion__long_turtle_master | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 水肺药水（延长） | minecraft:potion__long_water_breathing | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 虚弱药水（延长） | minecraft:potion__long_weakness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 伤害药水（强化） | minecraft:potion__strong_harming | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 治疗药水（强化） | minecraft:potion__strong_healing | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 跳跃药水（强化） | minecraft:potion__strong_leaping | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 剧毒药水（强化） | minecraft:potion__strong_poison | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 再生药水（强化） | minecraft:potion__strong_regeneration | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 迟缓药水（强化） | minecraft:potion__strong_slowness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 力量药水（强化） | minecraft:potion__strong_strength | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 迅捷药水（强化） | minecraft:potion__strong_swiftness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 神龟药水（强化） | minecraft:potion__strong_turtle_master | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型抗火药水（延长） | minecraft:splash_potion__long_fire_resistance | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型隐身药水（延长） | minecraft:splash_potion__long_invisibility | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型跳跃药水（延长） | minecraft:splash_potion__long_leaping | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型夜视药水（延长） | minecraft:splash_potion__long_night_vision | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型剧毒药水（延长） | minecraft:splash_potion__long_poison | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型再生药水（延长） | minecraft:splash_potion__long_regeneration | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型缓降药水（延长） | minecraft:splash_potion__long_slow_falling | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型迟缓药水（延长） | minecraft:splash_potion__long_slowness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型力量药水（延长） | minecraft:splash_potion__long_strength | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型迅捷药水（延长） | minecraft:splash_potion__long_swiftness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型神龟药水（延长） | minecraft:splash_potion__long_turtle_master | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型水肺药水（延长） | minecraft:splash_potion__long_water_breathing | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型虚弱药水（延长） | minecraft:splash_potion__long_weakness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型伤害药水（强化） | minecraft:splash_potion__strong_harming | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型治疗药水（强化） | minecraft:splash_potion__strong_healing | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型跳跃药水（强化） | minecraft:splash_potion__strong_leaping | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型剧毒药水（强化） | minecraft:splash_potion__strong_poison | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型再生药水（强化） | minecraft:splash_potion__strong_regeneration | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型迟缓药水（强化） | minecraft:splash_potion__strong_slowness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型力量药水（强化） | minecraft:splash_potion__strong_strength | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型迅捷药水（强化） | minecraft:splash_potion__strong_swiftness | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 喷溅型神龟药水（强化） | minecraft:splash_potion__strong_turtle_master | 稀有 | 35 | 仅出售/自行合成 | — | — |
| 孢子花 | minecraft:spore_blossom | 史诗 | 35 | 每日/隐藏 | 1 | 16 |
| 紫水晶碎片 | minecraft:amethyst_shard | 普通 | 40 | 标准常驻 | — | — |
| 青蛙刷怪蛋 | minecraft:frog_spawn_egg | 稀有 | 40 | 每日/隐藏 | 1 | 1 |
| 紫水晶母岩 | minecraft:budding_amethyst | 史诗 | 40 | 每日/隐藏 | 15 | 16 |
| 朱砂 | minecraft:cinnabar | 史诗 | 40 | 每日/隐藏 | 1 | 16 |
| 绿宝石 | minecraft:emerald | 普通 | 45 | 标准常驻 | — | — |
| 青金石 | minecraft:lapis_lazuli | 普通 | 45 | 标准常驻 | — | — |
| 灾厄之瓶 1 | minecraft:ominous_bottle__1 | 稀有 | 45 | 每日/隐藏 | 5 | 8 |
| 灾厄之瓶 2 | minecraft:ominous_bottle__2 | 稀有 | 45 | 每日/隐藏 | 5 | 8 |
| 灾厄之瓶 3 | minecraft:ominous_bottle__3 | 稀有 | 45 | 每日/隐藏 | 5 | 8 |
| 灾厄之瓶 4 | minecraft:ominous_bottle__4 | 稀有 | 45 | 每日/隐藏 | 5 | 8 |
| 灾厄之瓶 5 | minecraft:ominous_bottle__5 | 稀有 | 45 | 每日/隐藏 | 5 | 8 |
| 海晶砂粒 | minecraft:prismarine_crystals | 稀有 | 45 | 每日/隐藏 | 1 | 64 |
| 海晶碎片 | minecraft:prismarine_shard | 稀有 | 45 | 每日/隐藏 | 1 | 64 |
| 锻造模板 | minecraft:bolt_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:coast_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:dune_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 冰霜行者 1 附魔书 | minecraft:enchanted_book__frost_walker_1 | 史诗 | 50 | 每日/隐藏 | 1 | 1 |
| 冰霜行者 2 附魔书 | minecraft:enchanted_book__frost_walker_2 | 史诗 | 50 | 仅出售/自行合成 | — | — |
| 经验修补 1 附魔书 | minecraft:enchanted_book__mending_1 | 史诗 | 50 | 仅出售/自行合成 | — | — |
| 灵魂疾行 1 附魔书 | minecraft:enchanted_book__soul_speed_1 | 史诗 | 50 | 每日/隐藏 | 1 | 1 |
| 灵魂疾行 2 附魔书 | minecraft:enchanted_book__soul_speed_2 | 史诗 | 50 | 每日/隐藏 | 1 | 1 |
| 灵魂疾行 3 附魔书 | minecraft:enchanted_book__soul_speed_3 | 史诗 | 50 | 每日/隐藏 | 1 | 1 |
| 迅捷潜行 1 附魔书 | minecraft:enchanted_book__swift_sneak_1 | 史诗 | 50 | 每日/隐藏 | 1 | 1 |
| 迅捷潜行 2 附魔书 | minecraft:enchanted_book__swift_sneak_2 | 史诗 | 50 | 每日/隐藏 | 1 | 1 |
| 迅捷潜行 3 附魔书 | minecraft:enchanted_book__swift_sneak_3 | 史诗 | 50 | 每日/隐藏 | 1 | 1 |
| 锻造模板 | minecraft:host_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:raiser_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:rib_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:sentry_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:shaper_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:silence_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 旗帜图案 | minecraft:skull_banner_pattern | 史诗 | 50 | 仅出售/自行合成 | — | — |
| 锻造模板 | minecraft:snout_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:tide_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:vex_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:ward_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:wayfinder_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:wild_armor_trim_smithing_template | 史诗 | 50 | 每日/隐藏 | 1 | 16 |
| 紫颂果 | minecraft:chorus_fruit | 普通 | 55 | 仅出售/自行合成 | — | — |
| 爆裂紫颂果 | minecraft:popped_chorus_fruit | 普通 | 55 | 仅出售/自行合成 | — | — |
| 紫颂花 | minecraft:chorus_flower | 稀有 | 55 | 每日/隐藏 | 15 | 64 |
| 末地石 | minecraft:end_stone | 稀有 | 55 | 每日/隐藏 | 1 | 64 |
| 不祥试炼钥匙 | minecraft:ominous_trial_key | 稀有 | 55 | 每日/隐藏 | 1 | 1 |
| 试炼钥匙 | minecraft:trial_key | 稀有 | 55 | 每日/隐藏 | 1 | 1 |
| 旋风棒 | minecraft:breeze_rod | 史诗 | 55 | 每日/隐藏 | 1 | 16 |
| 末地传送门框架 | minecraft:end_portal_frame | 史诗 | 55 | 每日/隐藏 | 15 | 12 |
| 锻造模板 | minecraft:eye_armor_trim_smithing_template | 史诗 | 55 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:flow_armor_trim_smithing_template | 史诗 | 55 | 每日/隐藏 | 1 | 16 |
| 海洋之心 | minecraft:heart_of_the_sea | 史诗 | 55 | 每日/隐藏 | 1 | 16 |
| 锻造模板 | minecraft:spire_armor_trim_smithing_template | 史诗 | 55 | 每日/隐藏 | 1 | 16 |
| 试炼刷怪笼 | minecraft:trial_spawner | 传说 | 55 | 每日/隐藏 | 1 | 1 |
| 潜影壳 | minecraft:shulker_shell | 稀有 | 65 | 每日/隐藏 | 15 | 16 |
| 远古残骸 | minecraft:ancient_debris | 史诗 | 65 | 每日/隐藏 | 15 | 16 |
| 龙息 | minecraft:dragon_breath | 史诗 | 65 | 每日/隐藏 | 1 | 16 |
| 附魔金苹果 | minecraft:enchanted_golden_apple | 史诗 | 65 | 仅出售/自行合成 | — | — |
| 旗帜图案 | minecraft:mojang_banner_pattern | 史诗 | 65 | 仅出售/自行合成 | — | — |
| 下界合金碎片 | minecraft:netherite_scrap | 史诗 | 65 | 仅出售/自行合成 | — | — |
| 锻造模板 | minecraft:netherite_upgrade_smithing_template | 史诗 | 65 | 每日/隐藏 | 15 | 16 |
| 滞留型虫蚀药水 | minecraft:lingering_potion__infested | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 滞留型渗浆药水 | minecraft:lingering_potion__oozing | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 滞留型盘丝药水 | minecraft:lingering_potion__weaving | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 滞留型蓄风药水 | minecraft:lingering_potion__wind_charged | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 虫蚀药水 | minecraft:potion__infested | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 渗浆药水 | minecraft:potion__oozing | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 盘丝药水 | minecraft:potion__weaving | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 蓄风药水 | minecraft:potion__wind_charged | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 喷溅型虫蚀药水 | minecraft:splash_potion__infested | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 喷溅型渗浆药水 | minecraft:splash_potion__oozing | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 喷溅型盘丝药水 | minecraft:splash_potion__weaving | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 喷溅型蓄风药水 | minecraft:splash_potion__wind_charged | 史诗 | 75 | 仅出售/自行合成 | — | — |
| 滞留型幸运药水 | minecraft:lingering_potion__luck | 传说 | 75 | 仅出售/自行合成 | — | — |
| 下界合金锭 | minecraft:netherite_ingot | 传说 | 75 | 仅出售/自行合成 | — | — |
| 幸运药水 | minecraft:potion__luck | 传说 | 75 | 仅出售/自行合成 | — | — |
| 喷溅型幸运药水 | minecraft:splash_potion__luck | 传说 | 75 | 仅出售/自行合成 | — | — |
| 下界之星 | minecraft:nether_star | 传说 | 80 | 每日/隐藏 | 1 | 1 |
| 风爆 1 附魔书 | minecraft:enchanted_book__wind_burst_1 | 传说 | 85 | 每日/隐藏 | 1 | 1 |
| 风爆 2 附魔书 | minecraft:enchanted_book__wind_burst_2 | 传说 | 85 | 每日/隐藏 | 1 | 1 |
| 风爆 3 附魔书 | minecraft:enchanted_book__wind_burst_3 | 传说 | 85 | 每日/隐藏 | 1 | 1 |
| 沉重核心 | minecraft:heavy_core | 传说 | 85 | 每日/隐藏 | 1 | 1 |
| 刷怪笼 | minecraft:spawner | 传说 | 100 | 每日/隐藏 | 1 | 1 |
