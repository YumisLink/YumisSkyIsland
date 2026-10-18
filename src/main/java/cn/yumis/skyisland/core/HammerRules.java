package cn.yumis.skyisland.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Hammer conversion and miner bonus rolls. Chances are basis points and each line is independent. */
public final class HammerRules {
    public record Bonus(String itemId, int count) {}
    public record Offer(String itemId, int chanceBps, int count, boolean splitGems) {}
    public record Row(String band, String itemId, int count, String chance, String note) {}
    private static final List<Offer> LEVEL_10 = List.of(new Offer("minecraft:iron_nugget", 100, 1, false));
    private static final List<Offer> LEVEL_30 = List.of(
            new Offer("minecraft:iron_nugget", 500, 1, false),
            new Offer("minecraft:gold_nugget", 100, 1, false));
    private static final List<Offer> LEVEL_50 = List.of(
            new Offer("minecraft:iron_nugget", 2500, 1, false),
            new Offer("minecraft:gold_nugget", 300, 1, false),
            new Offer("minecraft:lapis_lazuli", 100, 1, true));
    private static final List<Offer> LEVEL_75 = List.of(
            new Offer("minecraft:iron_nugget", 10000, 1, false),
            new Offer("minecraft:gold_nugget", 800, 1, false),
            new Offer("minecraft:lapis_lazuli", 300, 1, true),
            new Offer("minecraft:diamond", 100, 1, false));
    private static final List<Offer> LEVEL_100 = List.of(
            new Offer("minecraft:iron_ingot", 2000, 1, false),
            new Offer("minecraft:gold_nugget", 5000, 1, false),
            new Offer("minecraft:lapis_lazuli", 1500, 1, false),
            new Offer("minecraft:redstone", 1500, 1, false),
            new Offer("minecraft:diamond", 300, 1, false),
            new Offer("minecraft:ancient_debris", 10, 1, false));
    private HammerRules() {}

    /** Replacement drop id for a hammer break, or null when the block keeps its vanilla drop. */
    public static String conversion(String blockId) {
        return switch (blockId) {
            case "minecraft:cobblestone" -> "minecraft:gravel";
            case "minecraft:gravel" -> "minecraft:dirt";
            case "minecraft:dirt" -> "minecraft:sand";
            default -> null;
        };
    }

    /** Exclusive level band used by both the roll and the JEI pages. */
    public static List<Offer> offers(int level) {
        if (level >= 100) return LEVEL_100;
        if (level >= 75) return LEVEL_75;
        if (level >= 50) return LEVEL_50;
        if (level >= 30) return LEVEL_30;
        if (level >= 10) return LEVEL_10;
        return List.of();
    }

    public static List<Bonus> bonuses(int level, IntUnaryOperator random) {
        List<Bonus> drops = new ArrayList<>();
        for (Offer offer : offers(level)) {
            if (offer.splitGems()) gem(drops, random, offer.chanceBps());
            else if (hit(random, offer.chanceBps())) drops.add(new Bonus(offer.itemId(), offer.count()));
        }
        return List.copyOf(drops);
    }

    /** One row per item. Split gem rolls are written as two mutually exclusive halves. */
    public static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        add(rows, "10～29", LEVEL_10, "本段各条目独立判定，可以同时出现");
        add(rows, "30～49", LEVEL_30, "本段各条目独立判定，可以同时出现");
        add(rows, "50～74", LEVEL_50, "本段各条目独立判定，可以同时出现");
        add(rows, "75～99", LEVEL_75, "本段各条目独立判定，可以同时出现");
        add(rows, "100 及以上", LEVEL_100, "不再出低级铁粒。青金石和红石各自判定，可以同时出现");
        return List.copyOf(rows);
    }

    private static void add(List<Row> rows, String band, List<Offer> offers, String note) {
        for (Offer offer : offers) {
            if (offer.splitGems()) {
                String chance = percent(offer.chanceBps() / 2.0);
                String split = "与另一样共用一次判定，同一次不会都出现。锤子敲圆石时判定，连锁的每一块各掷一次";
                rows.add(new Row(band, "minecraft:lapis_lazuli", offer.count(), chance, split));
                rows.add(new Row(band, "minecraft:redstone", offer.count(), chance, split));
            } else {
                rows.add(new Row(band, offer.itemId(), offer.count(), percent(offer.chanceBps()), note + "。锤子敲圆石时判定，连锁的每一块各掷一次"));
            }
        }
    }

    private static String percent(double bps) {
        return BigDecimal.valueOf(bps).divide(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    /** One roll, then an even split between lapis and redstone. */
    private static void gem(List<Bonus> drops, IntUnaryOperator random, int chanceBps) {
        if (!hit(random, chanceBps)) return;
        drops.add(new Bonus(random.applyAsInt(2) == 0 ? "minecraft:lapis_lazuli" : "minecraft:redstone", 1));
    }

    private static boolean hit(IntUnaryOperator random, int chanceBps) {
        return chanceBps >= 10000 || random.applyAsInt(10000) < chanceBps;
    }
}
