package cn.yumis.skyisland.core;

import java.util.List;

/** Per-party resource equivalents. The integrals also split single compressed items. */
public final class QuotaCurve {
    public record Tier(Rational upToUnits, int multiplierBps) {}
    public static final QuotaCurve DEFAULT = new QuotaCurve(List.of(
            new Tier(Rational.of(1000), 10000), new Tier(Rational.of(10000), 5000), new Tier(null, 1000)));
    private final List<Tier> tiers;

    public QuotaCurve(List<Tier> tiers) {
        this.tiers = List.copyOf(tiers);
        if (tiers.isEmpty() || tiers.getLast().upToUnits() != null) throw new IllegalArgumentException("Missing unlimited quota tier");
        Rational previous = Rational.ZERO;
        for (int i = 0; i < tiers.size(); i++) {
            Tier tier = tiers.get(i);
            if (tier.multiplierBps() < 0 || tier.multiplierBps() > 10000) throw new IllegalArgumentException("Invalid quota factor");
            if (tier.upToUnits() == null) {
                if (i != tiers.size() - 1) throw new IllegalArgumentException("Unlimited quota must be last");
            } else {
                if (tier.upToUnits().compareTo(previous) <= 0) throw new IllegalArgumentException("Unordered quotas");
                previous = tier.upToUnits();
            }
        }
    }
    public Rational integral(Rational units) {
        if (units.signum() < 0) throw new IllegalArgumentException("Negative quota counter");
        Rational lower = Rational.ZERO, result = Rational.ZERO;
        for (Tier tier : tiers) {
            Rational upper = tier.upToUnits() == null ? units : units.min(tier.upToUnits());
            Rational span = upper.subtract(lower).max(Rational.ZERO);
            result = result.add(span.multiply(Rational.of(tier.multiplierBps(), 10000)));
            if (tier.upToUnits() == null || units.compareTo(tier.upToUnits()) <= 0) break;
            lower = tier.upToUnits();
        }
        return result;
    }
    public Rational quote(Rational used, int quantity, Rational unitsPerItem, Rational normalItemSale) {
        if (quantity <= 0 || unitsPerItem.signum() <= 0 || normalItemSale.signum() < 0) throw new IllegalArgumentException("Invalid sale line");
        Rational units = unitsPerItem.multiply(quantity);
        Rational weightedUnits = integral(used.add(units)).subtract(integral(used));
        return normalItemSale.multiply(quantity).multiply(weightedUnits).divide(units);
    }
}
