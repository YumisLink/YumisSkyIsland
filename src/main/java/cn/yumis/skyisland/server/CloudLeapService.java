package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.effect.ModEffects;
import cn.yumis.skyisland.network.CloudLeapPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

public final class CloudLeapService {
    public static final int COOLDOWN_TICKS = 5;
    public static final int PROTECTION_TICKS = 200;
    public static final double FORWARD_SPEED = 1.08;
    public static final double UPWARD_SPEED = 0.49;
    public static final double VERTICAL_SPEED = 1.1;
    private static final AttachmentType<Integer> LAST_LEAP = AttachmentRegistry.create(
            YumisSkyIsland.id("last_cloud_leap"), builder -> builder.initializer(() -> -COOLDOWN_TICKS));

    private CloudLeapService() {}

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(CloudLeapPayload.TYPE, CloudLeapPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CloudLeapPayload.TYPE,
                (payload, context) -> leap(context.player(), payload.vertical()));
    }

    public static boolean leap(ServerPlayer player, boolean vertical) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || player.isSleeping() || player.getAbilities().flying) return false;
        var server = player.level().getServer();
        var runtime = SkyServer.get(server);
        if (runtime != null && (runtime.closing || runtime.inventoryLocks.contains(player.getUUID())
                || runtime.worlds.waitingPlayers.contains(player.getUUID()))) return false;

        int now = server.getTickCount();
        var attachments = (AttachmentTarget) player;
        if (now - attachments.getAttachedOrCreate(LAST_LEAP) < COOLDOWN_TICKS) return false;
        attachments.setAttached(LAST_LEAP, now);

        // Use yaw only, so looking down or straight up never reverses the leap.
        double yaw = Math.toRadians(player.getYRot());
        Vec3 velocity = vertical ? new Vec3(0, VERTICAL_SPEED, 0)
                : new Vec3(-Math.sin(yaw) * FORWARD_SPEED, UPWARD_SPEED, Math.cos(yaw) * FORWARD_SPEED);
        player.stopFallFlying();
        player.setDeltaMovement(velocity);
        player.setOnGround(false);
        player.resetFallDistance();
        // Vanilla broadcasts marked motion to tracking players and the player themselves.
        player.hurtMarked = true;
        player.connection.resetFlyingTicks();
        player.addEffect(new MobEffectInstance(ModEffects.CLOUD_STEP, PROTECTION_TICKS, 0, false, false, true));
        return true;
    }
}
