package cn.yumis.skyisland.core;

/** Personal chain limits include the first block and never exceed the server's hard cap. */
public final class VeinMiningRules {
    public static final int BASE_LIMIT = 8;
    public static final int HAMMER_BONUS = 8;
    public static final int MAX_LIMIT = 128;

    private VeinMiningRules() {}

    public static int unlockedLimit(int minerLevel, boolean chainHammer) {
        return Math.min(MAX_LIMIT, BASE_LIMIT + Math.max(0, minerLevel) / 5 + (chainHammer ? HAMMER_BONUS : 0));
    }

    public static int checkedLimit(int requested) {
        if (requested < 1 || requested > MAX_LIMIT) throw new IllegalArgumentException("连锁数量须为1～128的整数");
        return requested;
    }

    public static int effectiveLimit(int minerLevel, boolean chainHammer, int requested) {
        return Math.min(unlockedLimit(minerLevel, chainHammer), checkedLimit(requested));
    }
}
