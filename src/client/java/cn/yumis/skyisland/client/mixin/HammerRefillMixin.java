package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.HammerRefill;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Snapshot the chain preview when a hammer swing starts so the client can place those blocks back. */
@Mixin(MultiPlayerGameMode.class)
public abstract class HammerRefillMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void capture(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> callback) {
        HammerRefill.capture(pos);
    }
}
