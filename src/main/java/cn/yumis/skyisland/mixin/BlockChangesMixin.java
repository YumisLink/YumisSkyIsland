package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LevelChunk.class)
public abstract class BlockChangesMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void changed(BlockPos pos,BlockState state,int flags,CallbackInfoReturnable<BlockState> ci) {
        if (ci.getReturnValue() != null && ((LevelChunk)(Object)this).getLevel() instanceof ServerLevel world) { cn.yumis.skyisland.wand.ConstructionWandService.changed(world,pos); var rt = SkyServer.get(world.getServer()); if (rt != null) rt.blockChanged(world,pos,ci.getReturnValue(),state); }
    }
}
