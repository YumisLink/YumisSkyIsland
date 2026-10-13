package cn.yumis.skyisland.core;

import java.math.BigInteger;

/** Exact exchange arithmetic. A failed candidate is discarded rather than capped. */
public final class BountyRules {
    private BountyRules() {}
    public static int materialCount(long rewardPrice, long materialPrice, int maximum) {
        if (rewardPrice < 1 || materialPrice < 1 || maximum < 1) return 0;
        BigInteger[] parts = BigInteger.valueOf(rewardPrice).divideAndRemainder(BigInteger.valueOf(materialPrice));
        BigInteger count = parts[0].add(parts[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
        return count.compareTo(BigInteger.valueOf(maximum)) > 0 ? 0 : count.intValueExact();
    }
    public static long slotCost(int paid, long first, long step) {
        if (paid < 0 || first < 1 || step < 1) throw new IllegalArgumentException("Invalid slot cost");
        return paid == 0 ? first : Math.multiplyExact(paid, step);
    }
}
