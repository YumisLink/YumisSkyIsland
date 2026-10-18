package cn.yumis.skyisland.core;

import java.util.function.BooleanSupplier;

/** Water catches split evenly with vanilla loot. Tackle gets one extra roll on the vanilla half. */
public final class FishingRules {
    private FishingRules() {}
    public static boolean regionalCatch(String medium, boolean secondChance, BooleanSupplier roll) {
        if (!"water".equals(medium)) return true;
        if (roll.getAsBoolean()) return true;
        return secondChance && roll.getAsBoolean();
    }
    public static double treasureMultiplier(boolean bonus) { return bonus ? 1.1 : 1; }
    public static boolean allowsMedium(String medium, int fishermanLevel, int lavaLevel, int voidLevel) {
        if (fishermanLevel < 0 || lavaLevel < 0 || voidLevel < 0) throw new IllegalArgumentException("钓鱼等级门槛不能为负");
        return switch (medium == null ? "water" : medium) {
            case "lava" -> fishermanLevel >= lavaLevel;
            case "void" -> fishermanLevel >= voidLevel;
            default -> true;
        };
    }
}
