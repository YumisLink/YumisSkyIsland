package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DispenserBlock.class)
public abstract class DispenserContextMixin{
    @Inject(method="tick",at=@At("HEAD"))private void before(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci){Protection.push(level,pos);}
    @Inject(method="tick",at=@At("RETURN"))private void after(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci){Protection.pop(level);}
}
