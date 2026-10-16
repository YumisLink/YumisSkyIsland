package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla mobs do not generally use the ATTACK_SPEED attribute for AI cooldowns. */
@Mixin(MeleeAttackGoal.class)
public abstract class BloodMoonMeleeSpeedMixin {
    @Shadow protected PathfinderMob mob;
    @Inject(method="getAttackInterval",at=@At("RETURN"),cancellable=true)
    private void yumis$quickerBloodMoonMelee(CallbackInfoReturnable<Integer> cir){
        if(!(mob.level() instanceof ServerLevel level))return;
        SkyServer runtime=SkyServer.get(level.getServer());
        if(runtime!=null && level==level.getServer().overworld() && runtime.bloodMoon.active())
            cir.setReturnValue(Math.max(1,(int)Math.ceil(cir.getReturnValue()/1.5)));
    }
}
