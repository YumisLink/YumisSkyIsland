package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayerGameMode.class)
public abstract class BreakBlockMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true)
    private void destroy(BlockPos pos,CallbackInfoReturnable<Boolean> ci) { if (!Protection.canBuild(level,player,pos)) ci.setReturnValue(false); }
}
