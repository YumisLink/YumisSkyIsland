package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.server.FishingMediumAccess;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional bridge to XPlus's own scheduler; no XPlus classes are linked by this mod. */
@Pseudo
@Mixin(targets="troy.autofish.Autofish",remap=false)
public abstract class AutoFishCompatMixin {
    @Shadow public abstract void catchFish();
    @Unique private UUID yumis$lastHook;
    @Unique private boolean yumis$handledBite;

    // XPlus has already checked its enabled flag, held rod, local hook, and MP detector here.
    @Inject(method="tick(Lnet/minecraft/client/Minecraft;)V",at=@At(value="INVOKE",target="Ltroy/autofish/monitor/FishMonitorMP;hookTick(Ltroy/autofish/Autofish;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/entity/projectile/FishingHook;)V",shift=At.Shift.AFTER),require=1)
    private void yumis$detectSpecialBite(Minecraft client,CallbackInfo ci){
        FishingHook hook=client.player==null?null:client.player.fishing;
        if(hook==null)return;
        if(!hook.getUUID().equals(yumis$lastHook)){yumis$lastHook=hook.getUUID();yumis$handledBite=false;}
        if(!(hook instanceof FishingMediumAccess medium)||medium.yumis$medium().equals("water")){yumis$handledBite=false;return;}
        boolean biting=medium.yumis$biting();
        if(biting&&!yumis$handledBite)catchFish();
        yumis$handledBite=biting;
    }

    @Inject(method="detectOpenWater(Lnet/minecraft/world/entity/projectile/FishingHook;)V",at=@At("HEAD"),cancellable=true,require=1)
    private void yumis$acceptValidatedSpecialWater(FishingHook hook,CallbackInfo ci){
        if(hook instanceof FishingMediumAccess medium&&!medium.yumis$medium().equals("water"))ci.cancel();
    }
}
