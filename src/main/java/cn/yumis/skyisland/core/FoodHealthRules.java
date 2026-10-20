package cn.yumis.skyisland.core;

/** Permanent health gained continuously while eating the same food. */
public final class FoodHealthRules {
    private FoodHealthRules() {}

    /** The first group earns full saturation, later groups earn 10%, including partial groups. */
    public static double total(double saturation, double stacks) {
        if (!(stacks > 0) || !Double.isFinite(stacks) || !(saturation > 0) || !Double.isFinite(saturation)) return 0;
        return saturation * (Math.min(1, stacks) + 0.1 * Math.max(0, stacks - 1));
    }

    public static double stacks(int count) { return Math.max(0, count) / 64.0; }

    /** Items still needed to finish the next group. A count of 0 or an exact multiple of 64 still has 64 to go. */
    public static int untilNext(int count) {
        int safe = Math.max(0, count);
        int remainder = safe % 64;
        return remainder == 0 ? 64 : 64 - remainder;
    }

    /** Use the current food's saturation without repricing any previous meals. */
    public static double added(double saturation, int before, int after) {
        if (!(saturation > 0) || !Double.isFinite(saturation) || after <= before) return 0;
        int start = Math.max(0, before), end = Math.max(start, after);
        int first = Math.max(0, Math.min(64, end) - Math.min(64, start));
        return saturation * (first / 64.0 + ((long) end - start - first) / 640.0);
    }

    public static String number(double value) {
        if (!Double.isFinite(value)) return "0";
        return java.math.BigDecimal.valueOf(value).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
