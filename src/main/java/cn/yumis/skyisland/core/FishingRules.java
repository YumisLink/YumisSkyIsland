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
}
