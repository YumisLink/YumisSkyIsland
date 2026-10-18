package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayerGameMode.class)
public abstract class BreakBlockMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @Unique private BlockState yumis$startingState;
    @Unique private Item yumis$tool;
    @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true)
    private void destroy(BlockPos pos,CallbackInfoReturnable<Boolean> ci) {
        yumis$tool=player.getMainHandItem().getItem();
        if (!Protection.canBuild(level,player,pos)) ci.setReturnValue(false); else {var rt=SkyServer.get(level.getServer());if(rt!=null&&!rt.veinMining.breaking(player))yumis$startingState=level.getBlockState(pos);}
    }
    @Inject(method="destroyBlock",at=@At("RETURN"))
    private void yumis$chain(BlockPos pos,CallbackInfoReturnable<Boolean> ci){
        if(!ci.getReturnValue())return;
        var rt=SkyServer.get(level.getServer());
        if(rt==null)return;
        rt.professions.mined(player,yumis$tool);
        if(!rt.veinMining.breaking(player)&&yumis$startingState!=null){
            if(player.preventsBlockDrops()){
                var drops=rt.professions.hammerDrops(player,yumis$startingState,player.getMainHandItem().getItem());
                if(drops!=null){
                    BlockPos at=rt.veinMining.held(player)?player.blockPosition():pos;
                    for(var stack:drops)if(!stack.isEmpty())net.minecraft.world.level.block.Block.popResource(level,at,stack);
                }
            }
            rt.veinMining.broken(player,level,pos,yumis$startingState);
        }
    }
}
