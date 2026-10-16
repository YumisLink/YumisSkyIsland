package cn.yumis.skyisland.core;

/** Names follow the already synchronized economy mode, including existing ticket stacks. */
public final class LotteryNames {
    private LotteryNames() {}
    private static boolean genshin() { return Money.currencyName().equals("摩拉"); }
    public static String draw() { return genshin() ? "祈愿" : "抽奖"; }
    public static String ticket() { return genshin() ? "纠缠之缘" : "抽奖券"; }
    public static String token() { return genshin() ? "原石" : "代币"; }
}
