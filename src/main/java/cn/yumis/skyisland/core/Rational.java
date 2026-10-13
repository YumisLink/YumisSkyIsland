package cn.yumis.skyisland.core;

import java.math.BigInteger;
import java.util.Objects;

/** Exact quantities and coin fractions. Currency rounding happens at transaction boundaries. */
public record Rational(BigInteger numerator, BigInteger denominator) implements Comparable<Rational> {
    public static final Rational ZERO = of(0);
    public static final Rational ONE = of(1);

    public Rational {
        Objects.requireNonNull(numerator);
        Objects.requireNonNull(denominator);
        if (denominator.signum() == 0) throw new ArithmeticException("Zero denominator");
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger divisor = numerator.gcd(denominator);
        numerator = numerator.divide(divisor);
        denominator = denominator.divide(divisor);
    }

    public static Rational of(long value) { return new Rational(BigInteger.valueOf(value), BigInteger.ONE); }
    public static Rational of(long numerator, long denominator) {
        return new Rational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }
    public static Rational parse(String value) {
        String[] parts = value.split("/", -1);
        if (parts.length < 1 || parts.length > 2) throw new IllegalArgumentException("Invalid fraction");
        return new Rational(new BigInteger(parts[0]), parts.length == 1 ? BigInteger.ONE : new BigInteger(parts[1]));
    }
    public Rational add(Rational other) {
        return new Rational(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)), denominator.multiply(other.denominator));
    }
    public Rational subtract(Rational other) { return add(other.negate()); }
    public Rational negate() { return new Rational(numerator.negate(), denominator); }
    public Rational multiply(Rational other) { return new Rational(numerator.multiply(other.numerator), denominator.multiply(other.denominator)); }
    public Rational multiply(long value) { return multiply(of(value)); }
    public Rational divide(Rational other) { return new Rational(numerator.multiply(other.denominator), denominator.multiply(other.numerator)); }
    public Rational divide(long value) { return divide(of(value)); }
    public Rational min(Rational other) { return compareTo(other) <= 0 ? this : other; }
    public Rational max(Rational other) { return compareTo(other) >= 0 ? this : other; }
    public int signum() { return numerator.signum(); }
    public BigInteger floor() {
        BigInteger[] parts = numerator.divideAndRemainder(denominator);
        return numerator.signum() < 0 && parts[1].signum() != 0 ? parts[0].subtract(BigInteger.ONE) : parts[0];
    }
    public long floorLongExact() { return floor().longValueExact(); }
    @Override public int compareTo(Rational other) { return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator)); }
    @Override public String toString() { return denominator.equals(BigInteger.ONE) ? numerator.toString() : numerator + "/" + denominator; }
}
