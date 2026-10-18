package cn.yumis.skyisland.core;

/** Permanent health gained by eating stacks of the same food. */
public final class FoodHealthRules {
    private FoodHealthRules() {}

    /** Health points granted after this many completed groups of 64. The first group is the full saturation; each later group adds 10% of that. */
    public static double total(double saturation, int stacks) {
        if (stacks <= 0 || !(saturation > 0) || Double.isNaN(saturation) || Double.isInfinite(saturation)) return 0;
        return saturation * (1 + 0.1 * (stacks - 1));
    }

    public static int stacks(int count) { return Math.max(0, count) / 64; }

    /** Items still needed to finish the next group. A count of 0 or an exact multiple of 64 still has 64 to go. */
    public static int untilNext(int count) {
        int safe = Math.max(0, count);
        int remainder = safe % 64;
        return remainder == 0 ? 64 : 64 - remainder;
    }

    /** Increment stored when the eaten count crosses one or more groups, using the saturation at that moment. */
    public static double added(double saturation, int before, int after) {
        return total(saturation, stacks(after)) - total(saturation, stacks(before));
    }
}
