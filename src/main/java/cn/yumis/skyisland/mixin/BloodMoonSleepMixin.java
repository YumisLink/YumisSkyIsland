package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SkyServer;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class BloodMoonSleepMixin {
    @Inject(method="startSleepInBed",at=@At("HEAD"),cancellable=true)
    private void yumis$noSleepDuringBloodMoon(BlockPos pos,CallbackInfoReturnable<Either<Player.BedSleepingProblem,Unit>> cir){
        if((Object)this instanceof ServerPlayer player){
            SkyServer runtime=SkyServer.get(player.level().getServer());
            if(runtime!=null&&runtime.bloodMoon.active()&&player.level()==runtime.server.overworld()){
                SkyServer.tell(player,"血月期间无法入睡");cir.setReturnValue(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM));
            }
        }
    }
}
