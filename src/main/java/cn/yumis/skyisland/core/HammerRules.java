package cn.yumis.skyisland.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Hammer conversion and miner bonus rolls. Chances are basis points and each line is independent. */
public final class HammerRules {
    public record Bonus(String itemId, int count) {}
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

    public static List<Bonus> bonuses(int level, IntUnaryOperator random) {
        List<Bonus> drops = new ArrayList<>();
        if (level >= 100) {
            if (hit(random, 2000)) drops.add(new Bonus("minecraft:iron_ingot", 1));
            if (hit(random, 5000)) drops.add(new Bonus("minecraft:gold_nugget", 1));
            if (hit(random, 1500)) drops.add(new Bonus("minecraft:lapis_lazuli", 1));
            if (hit(random, 1500)) drops.add(new Bonus("minecraft:redstone", 1));
            if (hit(random, 300)) drops.add(new Bonus("minecraft:diamond", 1));
            if (hit(random, 10)) drops.add(new Bonus("minecraft:ancient_debris", 1));
            return List.copyOf(drops);
        }
        if (level >= 75) {
            drops.add(new Bonus("minecraft:iron_nugget", 1));
            if (hit(random, 800)) drops.add(new Bonus("minecraft:gold_nugget", 1));
            gem(drops, random, 300);
            if (hit(random, 100)) drops.add(new Bonus("minecraft:diamond", 1));
        } else if (level >= 50) {
            if (hit(random, 2500)) drops.add(new Bonus("minecraft:iron_nugget", 1));
            if (hit(random, 300)) drops.add(new Bonus("minecraft:gold_nugget", 1));
            gem(drops, random, 100);
        } else if (level >= 30) {
            if (hit(random, 500)) drops.add(new Bonus("minecraft:iron_nugget", 1));
            if (hit(random, 100)) drops.add(new Bonus("minecraft:gold_nugget", 1));
        } else if (level >= 10) {
            if (hit(random, 100)) drops.add(new Bonus("minecraft:iron_nugget", 1));
        }
        return List.copyOf(drops);
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
