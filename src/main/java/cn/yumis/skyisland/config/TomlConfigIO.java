package cn.yumis.skyisland.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.gson.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** TOML is the editable boundary; Gson remains the wire and journal representation. */
public final class TomlConfigIO {
    private TomlConfigIO() {}

    public static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject value = json(new TomlParser().parse(reader)).getAsJsonObject();
            normalizeNulls(file.getFileName().toString(), value);
            return value;
        } catch (RuntimeException e) {
            throw new IOException("无法读取 TOML " + file + "：" + e.getMessage(), e);
        }
    }

    /** Preserve existing comments, including comments on reordered task rows. */
    public static void write(Path file, JsonObject data) throws IOException {
        CommentedConfig document = CommentedConfig.inMemory();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                document = new TomlParser().parse(reader);
            } catch (RuntimeException e) {
                throw new IOException("原 TOML 语法有误，保留原文件：" + file, e);
            }
        }
        merge(document, data);
        StringWriter out = new StringWriter();
        new TomlWriter().write(document, out);
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void merge(CommentedConfig target, JsonObject source) {
        for (String key : new ArrayList<>(target.valueMap().keySet())) {
            if (!source.has(key) || source.get(key).isJsonNull()) target.remove(List.of(key));
        }
        for (var entry : source.entrySet()) {
            String key = entry.getKey();
            if (entry.getValue().isJsonNull()) continue;
            Object existing = target.get(List.of(key));
            target.set(List.of(key), value(entry.getValue(), existing));
            if (target.getComment(List.of(key)) == null) target.setComment(List.of(key), hint(key, entry.getValue()));
        }
    }

    private static Object value(JsonElement element, Object existing) {
        if (element.isJsonObject()) {
            CommentedConfig config = existing instanceof CommentedConfig c ? c : CommentedConfig.inMemory();
            merge(config, element.getAsJsonObject());
            return config;
        }
        if (element.isJsonArray()) {
            List<?> previous = existing instanceof List<?> l ? l : List.of();
            List<Object> result = new ArrayList<>();
            int index = 0;
            for (JsonElement child : element.getAsJsonArray()) {
                Object old = index < previous.size() ? previous.get(index) : null;
                if (child.isJsonObject() && child.getAsJsonObject().has("id")) {
                    String id = child.getAsJsonObject().get("id").getAsString();
                    old = previous.stream().filter(x -> x instanceof UnmodifiableConfig c && Objects.equals(c.get("id"), id)).findFirst().orElse(null);
                }
                result.add(value(child, old));
                index++;
            }
            return result;
        }
        if (element.isJsonNull()) throw new IllegalArgumentException("TOML 数组中不能有 null");
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isString()) return p.getAsString();
        BigDecimal number = p.getAsBigDecimal();
        if (number.stripTrailingZeros().scale() <= 0) return number.longValueExact();
        return number.doubleValue();
    }

    private static JsonElement json(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof UnmodifiableConfig config) {
            JsonObject result = new JsonObject();
            config.valueMap().forEach((key, child) -> result.add(key, json(child)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            JsonArray result = new JsonArray();
            collection.forEach(x -> result.add(json(x)));
            return result;
        }
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        return new JsonPrimitive(value.toString());
    }

    private static void normalizeNulls(String filename, JsonElement element) {
        if (element.isJsonArray()) { element.getAsJsonArray().forEach(x -> normalizeNulls(filename, x)); return; }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (filename.equals("scoring.toml") && object.has("multiplierBps") && !object.has("upToCount")) object.add("upToCount", JsonNull.INSTANCE);
        if (filename.equals("sale_quotas.toml") && object.has("saleMultiplierBps") && !object.has("upToUnits")) object.add("upToUnits", JsonNull.INSTANCE);
        if (filename.equals("server_rules.toml") && object.has("villagerTradingEnabled") && !object.has("globalMobGriefingOverride")) object.add("globalMobGriefingOverride", JsonNull.INSTANCE);
        if (filename.equals("enchantments.toml") && object.has("levelMultiplierBps") && !object.has("exclusiveSet")) object.add("exclusiveSet", JsonNull.INSTANCE);
        if (filename.equals("shipping_chest.toml") && object.has("inventorySlots") && !object.has("baseValueCoins")) object.add("baseValueCoins", JsonNull.INSTANCE);
        for (JsonElement child : object.asMap().values()) normalizeNulls(filename, child);
    }

    private static String hint(String key, JsonElement value) {
        String label = switch (key) {
            case "id" -> "固定标识；已有任务标识不可修改";
            case "name" -> "界面显示名称";
            case "description" -> "任务说明，支持中文";
            case "kind" -> "任务类型：main 队伍主线，side 个人支线，profession 个人职业任务";
            case "objective" -> "完成目标：materials 提交材料，place_shipping_chest 本人放置，tracked 追踪本人行动";
            case "profession" -> "职业ID：fisherman、farmer、blacksmith、stonemason、artisan、chef、hunter";
            case "previousQuestId" -> "同任务线的前置任务ID；前项领奖后解锁，省略为起点";
            case "goals" -> "本人行动追踪条件列表；不扣物，只累计当前解锁任务";
            case "type" -> "行动类型：craft 制作／加工，smelt 手动领取熔炼，fish 钓鱼，harvest 采集掉落，kill 本人击杀";
            case "targetId" -> "目标物品或生物注册ID；kill 使用生物ID，其余使用物品ID";
            case "sourceBlockId" -> "采集来源方块ID；省略表示不限";
            case "toolId" -> "采集工具物品ID；省略表示不限";
            case "rewardExperiencePoints" -> "原版经验点奖励；非负整数，按点数而非等级发放";
            case "regularBuyMultiplierBps" -> "普通常驻商店额外购价倍率；25000=2.5倍，不影响收购或每日隐藏商店";
            case "buyBaseValueCoins" -> "独立购入基价；正整数，省略沿用基础价值，不改变收购价";
            case "successfulFishingXp" -> "每次真实成功获钓的渔夫经验；默认12，空收杆与钩实体不计";
            case "requirements" -> "需求列表；附魔与品质至少达到指定值，未指定属性不限制";
            case "itemId" -> "有效注册物品 ID，例如 minecraft:oak_log";
            case "quantity", "count" -> "物品数量；正整数";
            case "components" -> "Minecraft 物品组件；保留合法的附魔、药水及其它属性";
            case "minimumQuality" -> "最低品质：0普通、1精良、2稀有、3史诗、4传说；省略表示不限";
            case "rewardItems" -> "领取的物品奖励列表；背包不足时不扣材料";
            case "rewardCoins" -> "金币奖励；非负64位整数，0表示不发金币";
            case "unlockFeatures" -> "完成后永久授予的预设功能 ID；删除或重置任务不会撤销";
            case "revision" -> "任务定义修订号；由服务器保存时递增，勿手工回退";
            case "features" -> "独立的预设功能定义；不由任务数量决定是否开放";
            case "defaultUnlocked" -> "新队伍和重开初始化时是否默认开放；未来新增功能默认 false";
            case "layoutVersion" -> "客户端布局版本，由程序维护";
            case "cellSize" -> "商品格子大小；40～96 GUI像素，默认48";
            case "showLockedItems" -> "是否显示等级不足商品；默认false，显示也不能越级购买";
            case "permanentFavorites" -> "常驻商店收藏商品 ID 列表";
            case "specialFavorites" -> "每日／隐藏共用收藏；换日未抽到时库存为0";
            default -> key.contains(":") ? "注册表键／组件值，保持 ID 原样" : "配置字段 " + key;
        };
        String sample = value.isJsonPrimitive() ? "；当前默认：" + value : "";
        return label + sample;
    }
}
