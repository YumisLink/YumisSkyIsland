package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.server.ProfessionService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Player.class)
public abstract class MiningQualityMixin {
    @Inject(method="getDestroySpeed",at=@At("RETURN"),cancellable=true)
    private void speed(BlockState state,CallbackInfoReturnable<Float> ci) { if (YumisSkyIsland.CONFIG != null) ci.setReturnValue((float)(ci.getReturnValue() * (1 + ProfessionService.miningBonus(((Player)(Object)this).getMainHandItem(),YumisSkyIsland.CONFIG)))); }
}
