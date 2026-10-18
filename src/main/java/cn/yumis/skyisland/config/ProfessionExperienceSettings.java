package cn.yumis.skyisland.config;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.List;

/** Decimal multipliers at the TOML boundary; exact basis points in experience calculations. */
public final class ProfessionExperienceSettings {
    public static final List<String> PROFESSIONS = List.of("blacksmith", "chef", "farmer", "hunter", "fisherman", "miner", "battle_master");
    public static final int MAX_MULTIPLIER = 1000;

    private ProfessionExperienceSettings() {}

    public static int multiplierBps(JsonElement value) {
        String error = "职业经验倍率须为0～1000的数字，最多4位小数";
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(error);
        try {
            BigDecimal multiplier = value.getAsBigDecimal();
            if (multiplier.signum() < 0 || multiplier.compareTo(BigDecimal.valueOf(MAX_MULTIPLIER)) > 0)
                throw new IllegalArgumentException(error);
            return multiplier.movePointRight(4).intValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException(error, invalid);
        }
    }

    public static int multiplierBps(JsonObject document, String profession) {
        if (!PROFESSIONS.contains(profession)) throw new IllegalArgumentException("未知职业：" + profession);
        JsonObject multipliers = document.getAsJsonObject("experienceMultipliers");
        return multiplierBps(multipliers == null ? null : multipliers.get(profession));
    }

    public static void validate(JsonObject document) {
        for (String profession : PROFESSIONS) multiplierBps(document, profession);
    }
}
