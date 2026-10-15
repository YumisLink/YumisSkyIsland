package cn.yumis.skyisland.core;

public final class Money {
    private static volatile String currencyName = "摩拉";
    private Money() {}
    public static void currencyName(String name) { currencyName = name; }
    public static String currencyName() { return currencyName; }
    public static long add(long balance, long amount) {
        requireNonnegative(amount);
        return Math.addExact(balance, amount);
    }
    public static long subtract(long balance, long amount) {
        requireNonnegative(amount);
        if (balance < 0 || amount > balance) throw new IllegalArgumentException("余额不足，无法购买");
        return balance - amount;
    }
    public static void requireNonnegative(long value) {
        if (value < 0) throw new IllegalArgumentException("金额不能为负");
    }
    public static String format(long coins) {
        java.math.BigInteger magnitude = java.math.BigInteger.valueOf(coins).abs();
        java.math.BigInteger[] yiPart = magnitude.divideAndRemainder(java.math.BigInteger.valueOf(100_000_000L));
        long yi = yiPart[0].longValue(), wan = yiPart[1].longValue() / 10_000L, rest = yiPart[1].longValue() % 10_000L;
        StringBuilder text = new StringBuilder();
        if (coins < 0) text.append('-');
        if (yi != 0) text.append(yi).append('亿');
        if (wan != 0) text.append(wan).append('万');
        if (rest != 0 || text.isEmpty()) text.append(rest);
        return text.append(currencyName).toString();
    }
}
