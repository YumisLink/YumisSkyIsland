package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RangedAttackGoal.class)
public abstract class BloodMoonRangedSpeedMixin {
    @Shadow private Mob mob;
    @Shadow private int attackTime;
    @Inject(method="tick",at=@At("TAIL"))
    private void yumis$quickerBloodMoonRanged(CallbackInfo ci){
        if(!(mob.level() instanceof ServerLevel level))return;
        SkyServer runtime=SkyServer.get(level.getServer());
        if(runtime!=null && level==level.getServer().overworld() && runtime.bloodMoon.active() && attackTime>0 && (mob.tickCount&1)==0)
            attackTime--;
    }
}
