package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PistonBaseBlock.class)
public abstract class PistonMixin {
    @Inject(method="triggerEvent",at=@At("HEAD"),cancellable=true)
    private void guard(BlockState state,Level level,BlockPos pos,int type,int data,CallbackInfoReturnable<Boolean> ci) {
        Direction direction = state.getValue(PistonBaseBlock.FACING); boolean extending = type == 0;
        if (!Protection.sameClaim(level,pos,pos.relative(direction))) { ci.setReturnValue(false); return; }
        var resolver = new PistonStructureResolver(level,pos,direction,extending);
        if (resolver.resolve()) { for (var from : resolver.getToPush()) if (!Protection.sameClaim(level,pos,from) || !Protection.sameClaim(level,pos,from.relative(resolver.getPushDirection()))) { ci.setReturnValue(false); return; } for (var destroyed : resolver.getToDestroy()) if (!Protection.sameClaim(level,pos,destroyed)) { ci.setReturnValue(false); return; } }
    }
}
