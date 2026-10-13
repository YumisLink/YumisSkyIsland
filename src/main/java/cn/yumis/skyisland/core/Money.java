package cn.yumis.skyisland.core;

public final class Money {
    private Money() {}
    public static long add(long balance, long amount) {
        requireNonnegative(balance); requireNonnegative(amount);
        return Math.addExact(balance, amount);
    }
    public static long subtract(long balance, long amount) {
        requireNonnegative(balance); requireNonnegative(amount);
        if (amount > balance) throw new IllegalArgumentException("Insufficient coins");
        return balance - amount;
    }
    public static void requireNonnegative(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative coins");
    }
    public static String format(long coins) {
        requireNonnegative(coins);
        long yi = coins / 100_000_000L, wan = coins % 100_000_000L / 10_000L, rest = coins % 10_000L;
        StringBuilder text = new StringBuilder();
        if (yi != 0) text.append(yi).append('亿');
        if (wan != 0) text.append(wan).append('万');
        if (rest != 0 || text.isEmpty()) text.append(rest);
        return text.append("金币").toString();
    }
}
