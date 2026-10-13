package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LavaFluid.class)
public abstract class LavaFireMixin {
    @Inject(method="randomTick",at=@At("HEAD")) private void before(ServerLevel level,BlockPos pos,FluidState state,RandomSource random,CallbackInfo ci) { Protection.push(level,pos); }
    @Inject(method="randomTick",at=@At("RETURN")) private void after(ServerLevel level,BlockPos pos,FluidState state,RandomSource random,CallbackInfo ci) { Protection.pop(level); }
}
