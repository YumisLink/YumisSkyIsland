package cn.yumis.skyisland.core;

public final class RarityColors {
    private RarityColors() {}
    public static int color(String rarity) { return switch(rarity) { case "rare" -> 0xFF55AAFF; case "epic" -> 0xFFD6A7FF; case "legendary" -> 0xFFFFAA00; default -> 0xFFFFFFFF; }; }
    public static String name(String rarity) { return switch(rarity) { case "rare" -> "稀有"; case "epic" -> "史诗"; case "legendary" -> "传说"; default -> "普通"; }; }
}
