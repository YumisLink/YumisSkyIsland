package cn.yumis.skyisland.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import cn.yumis.skyisland.effect.Vulnerability;

/** Bow and crossbow accessories. Splash reuses the already scaled hit and does not scale again. */
public final class BowHits {
    private static final ThreadLocal<Boolean> SKIP = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Float> SCALED = new ThreadLocal<>();
    private static final ThreadLocal<ServerPlayer> OWNER = new ThreadLocal<>();
    private BowHits() {}

    public static boolean splashing() { return SKIP.get(); }

    public static void begin() { SCALED.remove(); OWNER.remove(); }

    public static float adjust(LivingEntity target, DamageSource source, float amount) {
        if (SKIP.get()) return amount;
        float scaled = (float) (amount * Vulnerability.multiplier(target));
        Entity direct = source.getDirectEntity();
        if (source.getEntity() instanceof ServerPlayer player && direct instanceof AbstractArrow arrow && !(arrow instanceof ThrownTrident)) {
            SkyServer runtime = SkyServer.get(player.level().getServer());
            if (runtime != null) {
                if (AccessoryCombat.vulnerability(runtime, player)) {
                    Vulnerability.apply(target, 1, Vulnerability.BOW_TICKS);
                    scaled = (float) (amount * Vulnerability.multiplier(target));
                }
                scaled *= (float) (1 + AccessoryCombat.bowBonus(runtime, player));
                SCALED.set(scaled);
                OWNER.set(player);
            }
        }
        return scaled;
    }

    public static void splash(AbstractArrow arrow, EntityHitResult hit) {
        Float damage = SCALED.get();
        ServerPlayer owner = OWNER.get();
        SCALED.remove();
        OWNER.remove();
        if (damage == null || owner == null || arrow instanceof ThrownTrident) return;
        if (!(arrow.level() instanceof ServerLevel level)) return;
        SkyServer runtime = SkyServer.get(level.getServer());
        if (runtime == null || !AccessoryCombat.splash(runtime, owner)) return;
        Entity struck = hit.getEntity();
        SKIP.set(true);
        try {
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, struck.getBoundingBox().inflate(0.5),
                    entity -> entity != struck && entity != owner && entity.isAlive())) {
                other.hurtServer(level, owner.damageSources().arrow(arrow, owner), damage);
                Vulnerability.apply(other, 1, Vulnerability.BOW_TICKS);
            }
        } finally {
            SKIP.set(false);
        }
    }
}
