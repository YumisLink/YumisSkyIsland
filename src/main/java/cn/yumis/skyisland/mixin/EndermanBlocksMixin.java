package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets={"net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal","net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal"})
public abstract class EndermanBlocksMixin {
    @Shadow(remap=false) @Final private EnderMan enderman;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void move(CallbackInfo ci){if(!(enderman.level() instanceof ServerLevel world))return;var rt=SkyServer.get(world.getServer());if(rt==null)return;var origin=Protection.entityParty(rt,enderman);var party=rt.claim(enderman.blockPosition());if(party==null||!party.id.equals(origin)||party.resetInProgress){ci.cancel();return;}var pos=enderman.blockPosition();for(int x:new int[]{-4,4})for(int z:new int[]{-4,4})if(!Protection.sameClaim(world,pos,pos.offset(x,0,z))){ci.cancel();return;}}
}
