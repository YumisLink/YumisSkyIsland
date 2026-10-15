package cn.yumis.skyisland.core;

import java.math.BigInteger;
import java.util.function.IntUnaryOperator;

/** Exact, unbounded profession progression and the three production qualities. */
public final class ProfessionRules {
    private ProfessionRules() {}

    public static BigInteger required(int level, long base, long growth, long laterGrowth) {
        if (level < 0 || base < 1 || growth < 0 || laterGrowth < 0) throw new IllegalArgumentException("Invalid profession curve");
        BigInteger n = BigInteger.valueOf(level), g = BigInteger.valueOf(growth), b = BigInteger.valueOf(base);
        if (level <= 99) return b.add(g.multiply(n).multiply(n.add(BigInteger.ONE)).divide(BigInteger.TWO));
        BigInteger m = n.subtract(BigInteger.valueOf(99));
        return required(99, base, growth, laterGrowth)
            .add(g.multiply(BigInteger.valueOf(99)).multiply(m))
            .add(BigInteger.valueOf(laterGrowth).multiply(m).multiply(m.add(BigInteger.ONE)).divide(BigInteger.TWO));
    }

    /** Total experience at the start of a level. */
    public static BigInteger threshold(int level, long base, long growth, long laterGrowth) {
        if (level < 0) throw new IllegalArgumentException("Negative level");
        BigInteger n = BigInteger.valueOf(Math.min(level, 100));
        BigInteger total = BigInteger.valueOf(base).multiply(n)
            .add(BigInteger.valueOf(growth).multiply(n.subtract(BigInteger.ONE)).multiply(n).multiply(n.add(BigInteger.ONE)).divide(BigInteger.valueOf(6)));
        if (level <= 100) return total;
        BigInteger k = BigInteger.valueOf((long)level - 100);
        return total.add(required(99,base,growth,laterGrowth).multiply(k))
            .add(BigInteger.valueOf(growth).multiply(BigInteger.valueOf(99)).multiply(k).multiply(k.add(BigInteger.ONE)).divide(BigInteger.TWO))
            .add(BigInteger.valueOf(laterGrowth).multiply(k).multiply(k.add(BigInteger.ONE)).multiply(k.add(BigInteger.TWO)).divide(BigInteger.valueOf(6)));
    }

    public static int level(long xp, long base, long growth, long laterGrowth) {
        return level(BigInteger.valueOf(xp),base,growth,laterGrowth);
    }
    public static int level(BigInteger target, long base, long growth, long laterGrowth) {
        if (target.signum() < 0) throw new IllegalArgumentException("Negative experience");
        int lo = 0, hi = 1;
        while (hi < Integer.MAX_VALUE && threshold(hi,base,growth,laterGrowth).compareTo(target) <= 0)
            hi = hi > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : hi * 2;
        while (lo + 1 < hi) {
            int mid = lo + (hi-lo)/2;
            if (threshold(mid,base,growth,laterGrowth).compareTo(target) <= 0) lo = mid; else hi = mid;
        }
        return threshold(hi,base,growth,laterGrowth).compareTo(target) <= 0 ? hi : lo;
    }

    public static long experiencePerUnit(long base, int levelDifference) {
        if (base < 0) throw new IllegalArgumentException("Negative experience");
        if (base == 0) return 0;
        return levelDifference >= 30 ? 1 : levelDifference >= 10 ? Math.max(1, base / 2) : base;
    }

    /** 0 ordinary, 1 silver, 2 gold; chance values are basis points. */
    public static int roll(int levelDifference, int silverBps, int goldBps, IntUnaryOperator randomBounded) {
        if (levelDifference < 0) return 0;
        if (silverBps < 0 || goldBps < 0 || silverBps + goldBps > 10000) throw new IllegalArgumentException("Invalid quality chances");
        int result = randomBounded.applyAsInt(10000);
        return result < goldBps ? 2 : result < goldBps + silverBps ? 1 : 0;
    }
    public static boolean unbreakable(int grade,int chanceBps,IntUnaryOperator randomBounded) {
        if(chanceBps<0||chanceBps>10000)throw new IllegalArgumentException("Invalid unbreakable chance");
        return grade==2 && randomBounded.applyAsInt(10000)<chanceBps;
    }
}
