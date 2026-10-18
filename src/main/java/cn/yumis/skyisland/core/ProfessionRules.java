package cn.yumis.skyisland.core;

import java.math.BigInteger;
import java.util.function.IntUnaryOperator;

/** Exact, unbounded profession progression and the six production qualities. */
public final class ProfessionRules {
    private ProfessionRules() {}

    public static final int EXPERIENCE_SCALE = 10000;
    public static final int EXPERIENCE_REMAINDER_SCALE = EXPERIENCE_SCALE * EXPERIENCE_SCALE;
    public record ExperienceAward(BigInteger amount, int remainder) {}

    /** Multiply efficiency and resident effects before rounding; retain the exact fractional experience. */
    public static ExperienceAward scaleExperience(BigInteger amount, int efficiencyBps, int residentBps, int remainder) {
        if (amount.signum() < 0 || efficiencyBps < 0 || residentBps < 0 || remainder < 0 || remainder >= EXPERIENCE_REMAINDER_SCALE)
            throw new IllegalArgumentException("Invalid profession experience multiplier or remainder");
        BigInteger scaled = amount.multiply(BigInteger.valueOf(efficiencyBps)).multiply(BigInteger.valueOf(residentBps))
            .add(BigInteger.valueOf(remainder));
        BigInteger[] parts = scaled.divideAndRemainder(BigInteger.valueOf(EXPERIENCE_REMAINDER_SCALE));
        return new ExperienceAward(parts[0], parts[1].intValueExact());
    }

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

    /** Level difference applies first. Treasure stays at its flat 12, then the medium multiplier. Water stays at 1. */
    public static long fishingProfessionXp(long base, int levelDifference, boolean treasure, String medium, long lavaMultiplier, long voidMultiplier) {
        if (lavaMultiplier < 0 || voidMultiplier < 0) throw new IllegalArgumentException("钓鱼经验倍率不能为负");
        long amount = treasure ? 12L : experiencePerUnit(base, levelDifference);
        long multiplier = switch (medium == null ? "water" : medium) {
            case "lava" -> lavaMultiplier;
            case "void" -> voidMultiplier;
            default -> 1L;
        };
        return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(multiplier)).longValueExact();
    }

    /** 0 ordinary, then copper, silver, gold, diamond, prismatic. Chances are basis points, highest tier first. */
    public static int roll(int levelDifference, int[] chances, IntUnaryOperator randomBounded) {
        if (levelDifference < 0) return 0;
        if (chances == null || chances.length != 5) throw new IllegalArgumentException("Invalid quality chances");
        int sum = 0;
        for (int chance : chances) {
            if (chance < 0) throw new IllegalArgumentException("Invalid quality chances");
            sum += chance;
        }
        if (sum > 10000) throw new IllegalArgumentException("Invalid quality chances");
        int result = randomBounded.applyAsInt(10000);
        int cursor = 0;
        for (int grade = chances.length; grade >= 1; grade--) {
            cursor += chances[grade - 1];
            if (result < cursor) return grade;
        }
        return 0;
    }
    public static boolean unbreakable(int grade,int chanceBps,IntUnaryOperator randomBounded) {
        if(chanceBps<0||chanceBps>10000)throw new IllegalArgumentException("Invalid unbreakable chance");
        if(grade>0 && chanceBps>=10000)return true;
        return grade>0 && chanceBps>0 && randomBounded.applyAsInt(10000)<chanceBps;
    }
}
