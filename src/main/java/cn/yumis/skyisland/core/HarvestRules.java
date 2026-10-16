package cn.yumis.skyisland.core;

/** Harvest XP eligibility is independent of drop quality and quest accounting. */
public final class HarvestRules {
    private HarvestRules() {}

    public static boolean canAwardFarmerXp(HarvestContext source, String dropId) {
        return switch (source.sourceBlockId()) {
            case "minecraft:wheat" -> dropId.equals("minecraft:wheat") && source.age() == 7;
            case "minecraft:carrots" -> dropId.equals("minecraft:carrot") && source.age() == 7;
            case "minecraft:potatoes" -> dropId.equals("minecraft:potato") && source.age() == 7;
            case "minecraft:beetroots" -> dropId.equals("minecraft:beetroot") && source.age() == 3;
            case "minecraft:cocoa" -> dropId.equals("minecraft:cocoa_beans") && source.age() == 2;
            case "minecraft:nether_wart" -> dropId.equals("minecraft:nether_wart") && source.age() == 3;
            case "minecraft:sweet_berry_bush" -> dropId.equals("minecraft:sweet_berries") && (source.age() == 2 || source.age() == 3);
            case "minecraft:chorus_plant" -> dropId.equals("minecraft:chorus_fruit");
            default -> false;
        };
    }
}
