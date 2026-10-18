package cn.yumis.skyisland.client;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

/** Suppress use-item fallback when the original target was a block or entity. */
public final class QuiverClient {
    private QuiverClient() {}
    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.getMainHandItem().is(Items.ARROW) || player.isSpectator()) return InteractionResult.PASS;
            Minecraft client = Minecraft.getInstance();
            var hit = client.hitResult;
            if (hit == null || hit.getType() != HitResult.Type.MISS) return InteractionResult.FAIL;
            // Fabric's successful callback bypasses vanilla ensureHasSentCarriedItem().
            // Preserve packet order when a number-key slot change and use happen in the same frame.
            if (client.getConnection() != null)
                client.getConnection().send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
            return InteractionResult.SUCCESS;
        });
    }
}
