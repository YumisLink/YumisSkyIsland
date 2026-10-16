package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayerGameMode.class)
public abstract class BreakBlockMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @Unique private BlockState yumis$startingState;
    @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true)
    private void destroy(BlockPos pos,CallbackInfoReturnable<Boolean> ci) { if (!Protection.canBuild(level,player,pos)) ci.setReturnValue(false); else {var rt=SkyServer.get(level.getServer());if(rt!=null&&!rt.veinMining.breaking(player))yumis$startingState=level.getBlockState(pos);} }
    @Inject(method="destroyBlock",at=@At("RETURN"))
    private void yumis$chain(BlockPos pos,CallbackInfoReturnable<Boolean> ci){
        if(!ci.getReturnValue())return;
        var rt=SkyServer.get(level.getServer());
        if(rt!=null&&!rt.veinMining.breaking(player)&&yumis$startingState!=null)rt.veinMining.broken(player,level,pos,yumis$startingState);
    }
}
