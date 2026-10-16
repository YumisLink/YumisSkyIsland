package cn.yumis.skyisland.core;

import java.math.BigInteger;
import java.util.List;

public final class BlockScorer {
    private static volatile long requirementBasePoints = 20;
    public record Tier(Long upToCount, int multiplierBps) {}
    public static final List<Tier> DEFAULT = List.of(new Tier(256L, 10000), new Tier(2048L, 2500), new Tier(8192L, 500), new Tier(null, 100));
    private BlockScorer() {}
    public static void requirementBasePoints(long points) {
        if(points<1)throw new IllegalArgumentException("岛屿升级基础分须为正数");
        requirementBasePoints=points;
    }
    public static BigInteger scaledPoints(long count, long basePoints, List<Tier> profile) {
        if (count < 0 || basePoints < 0) throw new IllegalArgumentException("Negative stock or score");
        if (basePoints <= 5) return BigInteger.valueOf(count).multiply(BigInteger.valueOf(basePoints)).multiply(BigInteger.valueOf(10000));
        long lower = 0;
        BigInteger total = BigInteger.ZERO;
        for (Tier tier : profile) {
            long upper = tier.upToCount() == null ? count : Math.min(count, tier.upToCount());
            long span = Math.max(0, upper - lower);
            total = total.add(BigInteger.valueOf(span).multiply(BigInteger.valueOf(perBlock(basePoints, tier.multiplierBps()))).multiply(BigInteger.valueOf(10000)));
            if (tier.upToCount() == null || count <= tier.upToCount()) break;
            lower = tier.upToCount();
        }
        return total;
    }
    public static Rational marginal(long existing, long added, long basePoints, List<Tier> profile) {
        return new Rational(scaledPoints(Math.addExact(existing, added), basePoints, profile).subtract(scaledPoints(existing, basePoints, profile)), BigInteger.valueOf(10000));
    }
    public static long perBlock(long basePoints, int multiplierBps) {
        return BigInteger.valueOf(basePoints).multiply(BigInteger.valueOf(multiplierBps)).divide(BigInteger.valueOf(10000)).max(BigInteger.ONE).longValueExact();
    }
    public static BigInteger required(int currentLevel) {
        if (currentLevel < 0) throw new IllegalArgumentException("Negative island level");
        BigInteger n = BigInteger.valueOf((long)currentLevel + 1);
        return n.multiply(n.add(BigInteger.ONE)).multiply(BigInteger.valueOf(requirementBasePoints)).divide(BigInteger.TWO);
    }
    public static BigInteger threshold(int level) {
        if (level < 0) throw new IllegalArgumentException("Negative island level");
        BigInteger n = BigInteger.valueOf(level);
        return n.multiply(n.add(BigInteger.ONE)).multiply(n.add(BigInteger.TWO)).multiply(BigInteger.valueOf(requirementBasePoints)).divide(BigInteger.valueOf(6));
    }
    public static int level(BigInteger scaledPoints) {
        if (scaledPoints.signum() < 0) throw new IllegalArgumentException("Negative island score");
        BigInteger points = scaledPoints.divide(BigInteger.valueOf(10000));
        int lo = 0, hi = 1;
        while (hi < Integer.MAX_VALUE && threshold(hi).compareTo(points) <= 0)
            hi = hi > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : hi * 2;
        while (lo + 1 < hi) {
            int mid = lo + (hi-lo)/2;
            if (threshold(mid).compareTo(points) <= 0) lo = mid; else hi = mid;
        }
        return threshold(hi).compareTo(points) <= 0 ? hi : lo;
    }
}
