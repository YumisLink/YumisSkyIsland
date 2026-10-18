package cn.yumis.skyisland.server;

import net.minecraft.server.level.ServerPlayer;

/** Equipped bow pieces. A crafted piece already displaces its ingredients. */
public final class AccessoryCombat {
    private AccessoryCombat() {}

    public static double bowBonus(SkyServer runtime, ServerPlayer player) {
        if (AccessoryService.active(runtime, player, "bow_heart_gold")) return 0.35;
        if (AccessoryService.active(runtime, player, "bow_heart_iron")) return 0.15;
        if (AccessoryService.active(runtime, player, "bow_heart_stone")) return 0.05;
        return 0;
    }

    public static boolean vulnerability(SkyServer runtime, ServerPlayer player) {
        return AccessoryService.active(runtime, player, "vulnerability_mark")
                || AccessoryService.active(runtime, player, "bow_heart_iron")
                || AccessoryService.active(runtime, player, "bow_heart_gold");
    }

    public static boolean saveArrows(SkyServer runtime, ServerPlayer player) {
        return AccessoryService.active(runtime, player, "arrow_saver")
                || AccessoryService.active(runtime, player, "bow_heart_split")
                || AccessoryService.active(runtime, player, "bow_heart_gold");
    }

    public static boolean splash(SkyServer runtime, ServerPlayer player) {
        return AccessoryService.active(runtime, player, "arrow_splash")
                || AccessoryService.active(runtime, player, "bow_heart_split")
                || AccessoryService.active(runtime, player, "bow_heart_gold");
    }

    public static boolean rollSave(ServerPlayer player) {
        SkyServer runtime = SkyServer.get(player.level().getServer());
        return runtime != null && saveArrows(runtime, player) && player.getRandom().nextDouble() < 0.15;
    }
}
