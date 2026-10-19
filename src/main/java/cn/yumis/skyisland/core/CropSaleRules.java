package cn.yumis.skyisland.core;

import com.google.gson.*;
import java.util.*;

/** Sale-only crop bonuses, shared by server quotes and client previews. */
public record CropSaleRules(Set<String> items, long baseMultiplierBps, List<Long> gradeMultiplierBps) {
    public static final List<String> GRADES = List.of("ordinary", "copper", "silver", "gold", "diamond", "prismatic");
    public static final CropSaleRules NONE = new CropSaleRules(Set.of(), 10000, Collections.nCopies(6, 10000L));
    public CropSaleRules {
        items = Set.copyOf(items);
        gradeMultiplierBps = List.copyOf(gradeMultiplierBps);
        if (baseMultiplierBps < 0 || baseMultiplierBps > 10000000 || gradeMultiplierBps.size() != GRADES.size()
                || gradeMultiplierBps.stream().anyMatch(v -> v < 0 || v > 10000000))
            throw new IllegalArgumentException("农作物出售倍率须为0～10000000万分比，并包含全部六档品质");
    }
    public static CropSaleRules parse(JsonObject row) {
        if (row == null) return NONE;
        Set<String> items = new LinkedHashSet<>();
        for (JsonElement item : row.getAsJsonArray("items")) {
            String id = item.getAsString();
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || !items.add(id))
                throw new IllegalArgumentException("农作物出售物品ID无效或重复：" + id);
        }
        JsonObject grades = row.getAsJsonObject("gradeMultiplierBps");
        if (!grades.keySet().equals(new HashSet<>(GRADES)))
            throw new IllegalArgumentException("农作物出售品质倍率必须恰好包含普通和五种星级");
        List<Long> multipliers = GRADES.stream().map(grade -> integer(grades.get(grade))).toList();
        return new CropSaleRules(items, integer(row.get("baseMultiplierBps")), multipliers);
    }
    private static long integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("农作物出售倍率必须为整数万分比");
        return value.getAsBigDecimal().longValueExact();
    }
    public Rational multiplier(String item, int grade) {
        if (!items.contains(item)) return Rational.ONE;
        return Rational.of(baseMultiplierBps, 10000).multiply(Rational.of(gradeMultiplierBps.get(Math.clamp(grade, 0, 5)), 10000));
    }
}
