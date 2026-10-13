package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.*;
import cn.yumis.skyisland.effect.ModEffects;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayer.class)
public abstract class PlayerSafetyMixin {
    @Inject(method="hurtServer",at=@At("HEAD"),cancellable=true)
    private void hurt(ServerLevel level,DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci) {
        ServerPlayer p = (ServerPlayer)(Object)this;
        if (source.is(DamageTypes.FALL) && p.hasEffect(ModEffects.CLOUD_STEP)) {
            ci.setReturnValue(false);
            return;
        }
        var rt = SkyServer.get(level.getServer());
        if (rt != null && (rt.inventoryLocks.contains(p.getUUID()) || rt.worlds.waitingPlayers.contains(p.getUUID()))) ci.setReturnValue(false);
    }
}
