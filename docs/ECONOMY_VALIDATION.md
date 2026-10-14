# 经济草案校验报告

本报告仅记录配置与数学规则检查；源码编译、真实注册表和客户端场景另见[实现与验证记录](IMPLEMENTATION_STATUS.md)。

- 原版候选物品：1537；包含空气占位，禁售条目另列。
- 全部目录行：690；可交易：690；禁交易/需显式形态：0。
- 标准商店条目：127；每日/隐藏候选：243。
- 仅出售条目：320；售出计数组：30；原料等份映射：459。
- 仅限商店购买的核心等级配置：17。
- 原版配方：1585；可解析成本路径：1536。
- 原版附魔：43；药水类型：46。
- 原版JAR SHA-1：2dc72797acbc1b63fc16a11c4ac393605f453754。

## 已完成的数据检查

- TOML_parse
- positive_sellable_products
- independent_metadata_references
- level_thresholds
- hidden_guarantee_configuration
- quota_references

## 耐久示例

| 剩余耐久 | 收购价值倍率 |
|---:|---:|
| 100% | 100.0000% |
| 90% | 75.0000% |
| 80% | 50.0000% |
| 65% | 25.0000% |
| 50% | 0.0000% |

## 尚未验证

- This offline tool cannot verify runtime registries or gameplay. See current implementation verification records.

最低价格、当前级别随机池、附魔倍率与完整加工链可能相互作用；完整最坏行情/最高品质套利审查要在真实配方与组件注册完成后执行，不能把本报告的数据通过当作游戏经济已平衡。
