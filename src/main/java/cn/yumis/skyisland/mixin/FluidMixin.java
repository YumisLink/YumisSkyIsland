package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FlowingFluid.class)
public abstract class FluidMixin {
    @Inject(method="canPassThroughWall",at=@At("HEAD"),cancellable=true)
    private static void guard(Direction direction,BlockGetter level,BlockPos from,BlockState fromState,BlockPos to,BlockState toState,CallbackInfoReturnable<Boolean> ci) { if (level instanceof Level world && !Protection.sameClaim(world,from,to)) ci.setReturnValue(false); }
}
