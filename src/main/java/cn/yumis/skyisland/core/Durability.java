package cn.yumis.skyisland.core;

public final class Durability {
    private Durability() {}
    public static Rational multiplier(int remaining, int maximum) {
        if (maximum <= 0) return Rational.ONE;
        if (remaining < 0 || remaining > maximum) throw new IllegalArgumentException("Invalid durability");
        long r = remaining, m = maximum;
        if (5 * r >= 4 * m) return Rational.of(5 * r - 3 * m, 2 * m);
        if (2 * r >= m) return Rational.of(5 * (2 * r - m), 6 * m);
        return Rational.ZERO;
    }
}
