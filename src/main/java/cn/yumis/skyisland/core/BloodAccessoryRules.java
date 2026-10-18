package cn.yumis.skyisland.core;

/** Absolute drop rates, independent of fishing power, quality and looting. */
public final class BloodAccessoryRules {
    private BloodAccessoryRules() {}
    public static boolean drops(double roll, int basisPoints) {
        return roll < Math.clamp(basisPoints, 0, 10000) / 10000.0;
    }
    public static float armor(float armor) { return Math.max(0, armor - 5); }
}
