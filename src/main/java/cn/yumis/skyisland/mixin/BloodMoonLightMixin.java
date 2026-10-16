package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Monster.class)
public abstract class BloodMoonLightMixin {
    @Inject(method="isDarkEnoughToSpawn",at=@At("HEAD"),cancellable=true)
    private static void yumis$lightTwelve(ServerLevelAccessor world,BlockPos pos,RandomSource random,CallbackInfoReturnable<Boolean> cir){
        var level=world.getLevel();var runtime=SkyServer.get(level.getServer());
        if(runtime!=null&&runtime.bloodMoon.active()&&level==runtime.server.overworld()
                &&world.getMaxLocalRawBrightness(pos)<runtime.config.object("gameplay/blood_moon.toml").get("hostileLightLimit").getAsInt())
            cir.setReturnValue(true);
    }
}
