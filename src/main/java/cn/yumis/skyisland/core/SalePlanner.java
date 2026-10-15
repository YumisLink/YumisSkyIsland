package cn.yumis.skyisland.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure preview: never changes inventory, wallet, or counters. */
public final class SalePlanner {
    public record Input(int slot, String fingerprint, int quantity, String group, Rational unitsPerItem, Rational normalItemSale) {}
    public record Plan(long coins, long resultingWallet, Map<String, Rational> counters, List<Input> removals) {
        public boolean canCommit() { return coins > 0; }
    }
    private final QuotaCurve curve;
    public SalePlanner(QuotaCurve curve) { this.curve = curve; }
    public Plan prepare(long wallet, Map<String, Rational> oldCounters, List<Input> inputs) {
        Map<String, Rational> counters = new LinkedHashMap<>(oldCounters);
        List<Input> ordered = inputs.stream().sorted(Comparator.comparingInt(Input::slot)).toList();
        List<Input> removals = new ArrayList<>();
        HashSet<Integer> seen = new HashSet<>();
        Rational total = Rational.ZERO;
        for (Input input : ordered) {
            if (input.slot() < 0 || !seen.add(input.slot()) || input.quantity() <= 0) throw new IllegalArgumentException("Invalid or duplicated inventory slot");
            if (input.normalItemSale().signum() <= 0) continue;
            Rational used = counters.getOrDefault(input.group(), Rational.ZERO);
            total = total.add(curve.quote(used, input.quantity(), input.unitsPerItem(), input.normalItemSale()));
            counters.put(input.group(), used.add(input.unitsPerItem().multiply(input.quantity())));
            removals.add(input);
        }
        long coins = total.floorLongExact();
        if (coins == 0) return new Plan(0, wallet, Map.copyOf(oldCounters), List.of());
        return new Plan(coins, Money.add(wallet, coins), Map.copyOf(counters), List.copyOf(removals));
    }
}
