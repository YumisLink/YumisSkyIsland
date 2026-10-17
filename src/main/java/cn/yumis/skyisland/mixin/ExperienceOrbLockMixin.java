package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.AccessoryService;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the orb, including its mending value, intact while a receipt is being written. */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbLockMixin {
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)
    private void locked(Player player,CallbackInfo ci){
        if(player instanceof ServerPlayer serverPlayer){
            var runtime=SkyServer.get(serverPlayer.level().getServer());
            if(runtime!=null&&runtime.inventoryLocks.contains(serverPlayer.getUUID()))ci.cancel();
        }
    }
    @Redirect(method="playerTouch",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;giveExperiencePoints(I)V"))
    private void studyPendant(Player player,int experience){
        int bonus=player instanceof ServerPlayer server?AccessoryService.studyBonus(server,experience):0;
        player.giveExperiencePoints(Math.addExact(experience,bonus));
    }
}
