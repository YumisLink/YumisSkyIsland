package cn.yumis.skyisland.config;

import com.google.gson.JsonObject;

/** Restart-only settings shared by ordinary and sky-island saves. */
public record QuiverSettings(boolean enabled, int capacity) {
    public static QuiverSettings parse(JsonObject root) {
        try {
            var enabled = root.get("enabled");
            if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException();
            var value = root.get("capacity");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException();
            int capacity = value.getAsBigDecimal().intValueExact();
            if (capacity < 1) throw new IllegalArgumentException();
            return new QuiverSettings(enabled.getAsBoolean(), capacity);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("虚空箭袋配置：enabled须为布尔值，capacity须为1～2147483647的整数", invalid);
        }
    }
}
