package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.QuiverState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.literal;

/** Inventory-local transfers saved with vanilla player data, independent of island services. */
public final class QuiverService {
    public static final int TARGET_SLOT = 8;
    private static final Codec<QuiverState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("count").forGetter(QuiverState::count),
        Codec.BOOL.fieldOf("enabled").forGetter(QuiverState::enabled)
    ).apply(instance, QuiverState::new));
    public static final AttachmentType<QuiverState> STATE = AttachmentRegistry.create(
        YumisSkyIsland.id("quiver"), builder -> builder.initializer(() -> QuiverState.EMPTY).persistent(CODEC).copyOnDeath());

    private QuiverService() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer online)) return InteractionResult.PASS;
            return deposit(online, hand);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) refill(player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(literal("quiver")
            .executes(context -> status(context.getSource()))
            .then(literal("on").executes(context -> toggle(context.getSource(), true)))
            .then(literal("off").executes(context -> toggle(context.getSource(), false)))));
    }

    public static QuiverState state(ServerPlayer player) { return player.getAttachedOrCreate(STATE); }

    /** Also used by inventory receipt replay; never clamp to a newly lowered configured capacity. */
    public static void restore(ServerPlayer player, QuiverState state) { player.setAttached(STATE, state); }

    private static boolean ready(ServerPlayer player) {
        var server = player.level().getServer();
        if (!player.isAlive() || player.isSpectator() || player.isRemoved() || player.hasDisconnected()
            || server.getPlayerList().getPlayer(player.getUUID()) != player) return false;
        var runtime = SkyServer.get(server);
        if (runtime == null) return true;
        if (runtime.closing || runtime.inventoryLocks.contains(player.getUUID())
            || runtime.worlds.waitingPlayers.contains(player.getUUID())) return false;
        var receipt = runtime.state().inventoryRecovery.get(player.getUUID());
        return receipt == null || player.getAttachedOrCreate(YumisSkyIsland.TRANSACTION_SEQUENCE) >= receipt.sequence;
    }

    /** Item-use callbacks can follow a PASS on a block/entity, so validate an actual air target again. */
    public static boolean targetsAir(ServerPlayer player) {
        Vec3 start = player.getEyePosition(), view = player.getViewVector(1.0F);
        Vec3 blockEnd = start.add(view.scale(player.blockInteractionRange()));
        if (player.level().clip(new ClipContext(start, blockEnd, ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return false;
        double range = player.entityInteractionRange();
        Vec3 end = start.add(view.scale(range));
        return ProjectileUtil.getEntityHitResult(player, start, end,
            player.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0),
            entity -> !entity.isSpectator() && entity.isPickable(), range * range) == null;
    }

    public static InteractionResult deposit(ServerPlayer player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().is(Items.ARROW)
            || player.isSpectator() || !targetsAir(player)) return InteractionResult.PASS;
        if (!YumisSkyIsland.CONFIG.quiver.enabled()) {
            overlay(player, "服务器已关闭虚空箭袋");
            return InteractionResult.FAIL;
        }
        if (!ready(player) || player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()) {
            overlay(player, "背包正在操作或恢复，请稍后存箭");
            return InteractionResult.FAIL;
        }
        QuiverState before = state(player);
        ItemStack held = player.getMainHandItem();
        int count = before.accepted(held.getCount(), YumisSkyIsland.CONFIG.quiver.capacity());
        if (count == 0) {
            overlay(player, "虚空箭袋已满（" + before.count() + "/" + YumisSkyIsland.CONFIG.quiver.capacity() + "）");
            return InteractionResult.FAIL;
        }
        boolean normalized = !ItemStack.isSameItemSameComponents(held, new ItemStack(Items.ARROW));
        held.shrink(count); // Creative also transfers real arrows rather than copying them.
        restore(player, before.withCount(before.count() + count));
        changed(player);
        overlay(player, "已存入" + count + "支箭" + (normalized ? "（额外属性已清除）" : "")
            + "，箭库 " + state(player).count() + "/" + YumisSkyIsland.CONFIG.quiver.capacity());
        return InteractionResult.SUCCESS;
    }

    public static void refill(ServerPlayer player) {
        if (!YumisSkyIsland.CONFIG.quiver.enabled() || !ready(player)
            || player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()
            || !player.getInventory().getItem(TARGET_SLOT).isEmpty()) return;
        QuiverState before = state(player);
        int count = before.refillCount();
        if (count == 0) return;
        player.getInventory().setItem(TARGET_SLOT, new ItemStack(Items.ARROW, count));
        restore(player, before.withCount(before.count() - count));
        changed(player);
    }

    private static void changed(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static void overlay(ServerPlayer player, String text) { player.sendOverlayMessage(Component.literal(text)); }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        QuiverState state = state(player);
        source.sendSuccess(() -> Component.literal("虚空箭袋：" + state.count() + "/" + YumisSkyIsland.CONFIG.quiver.capacity()
            + "支，自动补箭" + (state.enabled() ? "开启" : "关闭")
            + (YumisSkyIsland.CONFIG.quiver.enabled() ? "" : "（服务器已停用）")
            + "。主手普通箭Shift＋右键空气存入；/quiver on|off切换补箭。"), false);
        return 1;
    }

    private static int toggle(CommandSourceStack source, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ready(player)) {
            source.sendFailure(Component.literal("背包正在恢复或玩家状态不可用，请稍后切换"));
            return 0;
        }
        restore(player, state(player).withEnabled(enabled));
        source.sendSuccess(() -> Component.literal("虚空箭袋自动补箭已" + (enabled ? "开启" : "关闭")
            + "，仍可Shift＋右键空气存箭。"), false);
        return 1;
    }
}
