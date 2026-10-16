package cn.yumis.skyisland.config;

import cn.yumis.skyisland.core.BlockScorer;
import com.google.gson.*;
import java.util.*;

/** Immutable rules shared by server counting, scans and client tooltips. */
public record ScoringSettings(Map<String, Long> points, List<BlockScorer.Tier> tiers, boolean notices, String message) {
    public static final Set<String> EXCLUDED = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:water", "minecraft:lava", "minecraft:bubble_column");
    public static final String DEFAULT_MESSAGE = "本岛{block}已达{threshold}块；后续每块{nextPoints}分（倍率{nextMultiplier}%，最低1分）。";
    public ScoringSettings { points = Map.copyOf(points); tiers = List.copyOf(tiers); }
    public long base(String id) { return EXCLUDED.contains(id) ? 0 : points.getOrDefault(id, 1L); }
    public static ScoringSettings parse(JsonObject document) {
        Map<String, Long> values = new LinkedHashMap<>();
        if (document.has("blocks")) for (var row : document.getAsJsonObject("blocks").entrySet()) {
            JsonObject value = row.getValue().getAsJsonObject();
            long base = value.has("basePoints") ? integer(value.get("basePoints"), 0, Long.MAX_VALUE) : 1;
            if (!EXCLUDED.contains(row.getKey()) && base < 1) throw new IllegalArgumentException("方块基础分必须至少为1：" + row.getKey());
            values.put(row.getKey(), base);
        }
        List<BlockScorer.Tier> tiers = new ArrayList<>();
        JsonArray rows = document.getAsJsonObject("tiers").getAsJsonArray("default");
        if (rows.size() != 4) throw new IllegalArgumentException("高分方块需要三个阈值及最后的无限档");
        long previous = 0;
        for (int i = 0; i < rows.size(); i++) {
            JsonObject row = rows.get(i).getAsJsonObject();
            Long upper = row.has("upToCount") && !row.get("upToCount").isJsonNull() ? integer(row.get("upToCount"), 1, Long.MAX_VALUE) : null;
            if (i < 3 && (upper == null || upper <= previous) || i == 3 && upper != null) throw new IllegalArgumentException("三个阈值须严格递增，最后一档不设置上限");
            int multiplier = (int) integer(row.get("multiplierBps"), 0, 10000);
            if (i > 0 && multiplier > tiers.getLast().multiplierBps()) throw new IllegalArgumentException("递减倍率不能逐档增加");
            tiers.add(new BlockScorer.Tier(upper, multiplier));
            if (upper != null) previous = upper;
        }
        JsonObject notice = document.has("notices") ? document.getAsJsonObject("notices") : new JsonObject();
        boolean enabled = !notice.has("enabled") || notice.get("enabled").getAsBoolean();
        String text = notice.has("message") ? notice.get("message").getAsString() : DEFAULT_MESSAGE;
        if (text.isBlank() || text.length() > 512) throw new IllegalArgumentException("通告文案须为1～512个字符");
        String rest = text;
        for (String key : List.of("block", "threshold", "nextPoints", "nextMultiplier")) rest = rest.replace("{" + key + "}", "");
        if (rest.contains("{") || rest.contains("}")) throw new IllegalArgumentException("通告包含未知占位符");
        return new ScoringSettings(values, tiers, enabled, text);
    }
    public static long integer(JsonElement value, long minimum, long maximum) {
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
            long number = value.getAsBigDecimal().longValueExact();
            if (number < minimum || number > maximum) throw new IllegalArgumentException();
            return number;
        } catch (RuntimeException error) { throw new IllegalArgumentException("数值必须为" + minimum + "～" + maximum + "的整数"); }
    }
}
