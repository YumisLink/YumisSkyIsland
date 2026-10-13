package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FireBlock.class)
public abstract class FireMixin {
    @Inject(method="tick",at=@At("HEAD")) private void before(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) { Protection.push(level,pos); }
    @Inject(method="tick",at=@At("RETURN")) private void after(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) { Protection.pop(level); }
    @Inject(method="checkBurnOut",at=@At("HEAD"),cancellable=true)private void burn(Level level,BlockPos pos,int chance,RandomSource random,int age,CallbackInfo ci){if(!Protection.contextAllowed(level,pos))ci.cancel();}
}
