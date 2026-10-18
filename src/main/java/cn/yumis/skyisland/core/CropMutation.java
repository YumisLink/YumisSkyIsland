package cn.yumis.skyisland.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Extra harvest drops. One roll per ripe crop, independent of how many items the block drops. */
public final class CropMutation {
    public static final String SKY_FRUIT = "yumis_sky_island:sky_fruit";
    private CropMutation() {}

    public static String mutantFor(String blockId) {
        return switch (blockId) {
            case "minecraft:wheat" -> "yumis_sky_island:barley";
            case "minecraft:carrots" -> "yumis_sky_island:white_radish";
            case "minecraft:potatoes" -> "yumis_sky_island:purple_potato";
            case "minecraft:beetroots" -> "yumis_sky_island:golden_beet";
            case "minecraft:nether_wart" -> "yumis_sky_island:crimson_wart";
            default -> null;
        };
    }

    public static boolean ripe(String blockId, int age) {
        return switch (blockId) {
            case "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes" -> age == 7;
            case "minecraft:beetroots", "minecraft:nether_wart" -> age == 3;
            default -> false;
        };
    }

    /** Level 100 uses the configured rates. Levels 50 through 99 use half, rounded down. Below 50, nothing. */
    public static int chanceBps(int farmerLevel, int fullBps) {
        if (farmerLevel < 50 || fullBps <= 0) return 0;
        if (fullBps > 10000) throw new IllegalArgumentException("突变概率不能超过10000");
        return farmerLevel >= 100 ? fullBps : fullBps / 2;
    }

    public static List<String> extras(int farmerLevel, String blockId, int age, int mutantBps, int fruitBps, IntUnaryOperator roll) {
        String mutant = mutantFor(blockId);
        if (mutant == null || !ripe(blockId, age)) return List.of();
        int mutantChance = chanceBps(farmerLevel, mutantBps);
        int fruitChance = chanceBps(farmerLevel, fruitBps);
        List<String> found = new ArrayList<>();
        if (mutantChance > 0 && roll.applyAsInt(10000) < mutantChance) found.add(mutant);
        if (fruitChance > 0 && roll.applyAsInt(10000) < fruitChance) found.add(SKY_FRUIT);
        return List.copyOf(found);
    }
}
