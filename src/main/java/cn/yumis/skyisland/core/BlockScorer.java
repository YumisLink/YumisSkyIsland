package cn.yumis.skyisland.core;

import java.math.BigInteger;
import java.util.List;

public final class BlockScorer {
    public record Tier(Long upToCount, int multiplierBps) {}
    public static final List<Tier> DEFAULT = List.of(new Tier(64L, 10000), new Tier(320L, 2500), new Tier(2048L, 500), new Tier(null, 100));
    private BlockScorer() {}
    public static BigInteger scaledPoints(long count, long basePoints, List<Tier> profile) {
        if (count < 0 || basePoints < 0) throw new IllegalArgumentException("Negative stock or score");
        long lower = 0;
        BigInteger total = BigInteger.ZERO;
        for (Tier tier : profile) {
            long upper = tier.upToCount() == null ? count : Math.min(count, tier.upToCount());
            long span = Math.max(0, upper - lower);
            total = total.add(BigInteger.valueOf(span).multiply(BigInteger.valueOf(basePoints)).multiply(BigInteger.valueOf(tier.multiplierBps())));
            if (tier.upToCount() == null || count <= tier.upToCount()) break;
            lower = tier.upToCount();
        }
        return total;
    }
    public static Rational marginal(long existing, long added, long basePoints, List<Tier> profile) {
        return new Rational(scaledPoints(Math.addExact(existing, added), basePoints, profile).subtract(scaledPoints(existing, basePoints, profile)), BigInteger.valueOf(10000));
    }
    public static int level(BigInteger scaledPoints, List<Long> thresholds) {
        int result = 0;
        for (int i = 0; i < thresholds.size(); i++) {
            if (scaledPoints.compareTo(BigInteger.valueOf(thresholds.get(i)).multiply(BigInteger.valueOf(10000))) < 0) break;
            result = i;
        }
        return result;
    }
}
