package cn.yumis.skyisland.server;

import cn.yumis.skyisland.core.BloodAccessoryRules;
import cn.yumis.skyisland.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/** Blood-moon rewards run after vanilla/hunter loot, so neither quality nor extra copies apply. */
public final class BloodAccessories {
    private BloodAccessories() {}
    public static final String ANKLET = "anklet", SHARK = "shark_necklace";
    public static boolean exclusive(String id) { return id.equals(ANKLET) || id.equals(SHARK); }
    public static int chance(SkyServer runtime, String field, int fallback) {
        var config = runtime.config.object("gameplay/blood_moon.toml");
        return config.has(field) ? Math.clamp(config.get(field).getAsInt(), 0, 10000) : fallback;
    }
    public static void defeated(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof Enemy) || !(victim.level() instanceof ServerLevel level)
                || !(source.getEntity() instanceof ServerPlayer player) || player.level() != level
                || AccessoryService.externalHit() || BowHits.splashing()
                || !level.getGameRules().get(GameRules.MOB_DROPS)) return;
        SkyServer runtime = SkyServer.get(level.getServer());
        if (runtime == null || level != runtime.server.overworld() || !runtime.bloodMoon.active()) return;
        int rate = chance(runtime, "accessoryMobDropBps", 10);
        for (String id : new String[]{ANKLET, SHARK}) {
            if (BloodAccessoryRules.drops(victim.getRandom().nextDouble(), rate)) {
                var drop = new net.minecraft.world.entity.item.ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(),
                        new ItemStack(ModItems.ACCESSORY_ICONS.get(id)));
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
    }
    public static float armor(DamageSource source, float armor) {
        if (AccessoryService.externalHit() || BowHits.splashing()
                || !(source.getEntity() instanceof ServerPlayer player)
                || !(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.ARROW) || source.is(DamageTypes.TRIDENT))) return armor;
        var runtime = SkyServer.get(player.level().getServer());
        return runtime != null && AccessoryService.active(runtime, player, SHARK) ? BloodAccessoryRules.armor(armor) : armor;
    }
}
