package cn.yumis.skyisland.core;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

public final class DailyShopSelector {
    public record Candidate(String productId, int rarity, int minimumLevel, int weight, int stock) {}
    public record Offer(String productId, int remainingStock) {}
    public record Selection(long epoch, boolean hidden, int levelAtRoll, List<Offer> offers) {}
    private DailyShopSelector() {}
    public static Selection roll(long worldSeed, UUID partyId, long epoch, int level, List<Candidate> all,
                                 int hiddenChanceBps, int ordinarySlots, int hiddenSlots, int[] ordinaryWeights, int[] hiddenWeights) {
        long seed = mix(worldSeed ^ partyId.getMostSignificantBits() ^ mix(partyId.getLeastSignificantBits()) ^ mix(epoch));
        SplittableRandom random = new SplittableRandom(seed);
        boolean hidden = random.nextInt(10000) < hiddenChanceBps;
        int[] weights = hidden ? hiddenWeights : ordinaryWeights;
        List<Candidate> candidates = new ArrayList<>(all.stream().filter(c -> c.rarity() > 0 && c.minimumLevel() <= level && c.weight() > 0 && c.stock() > 0).toList());
        List<Offer> offers = new ArrayList<>();
        int slots = hidden ? hiddenSlots : ordinarySlots;
        for (int slot = 0; slot < slots && !candidates.isEmpty(); slot++) {
            long total = 0;
            boolean[] available = new boolean[4];
            for (Candidate c : candidates) available[c.rarity()] = true;
            for (int rank = 1; rank < 4; rank++) if (available[rank]) total = Math.addExact(total, weights[rank]);
            if (total <= 0) break;
            long roll = random.nextLong(total);
            int chosenRank = -1;
            for (int rank = 1; rank < 4; rank++) {
                if (!available[rank]) continue;
                roll -= weights[rank];
                if (roll < 0) { chosenRank = rank; break; }
            }
            List<Candidate> bucket = new ArrayList<>();
            long bucketWeight = 0;
            for (Candidate c : candidates) if (c.rarity() == chosenRank) { bucket.add(c); bucketWeight = Math.addExact(bucketWeight, c.weight()); }
            long itemRoll = random.nextLong(bucketWeight);
            Candidate chosen = null;
            for (Candidate c : bucket) { itemRoll -= c.weight(); if (itemRoll < 0) { chosen = c; break; } }
            if (chosen == null) throw new IllegalStateException("Invalid weighted selection");
            offers.add(new Offer(chosen.productId(), chosen.stock()));
            candidates.remove(chosen);
        }
        return new Selection(epoch, hidden, level, List.copyOf(offers));
    }
    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
