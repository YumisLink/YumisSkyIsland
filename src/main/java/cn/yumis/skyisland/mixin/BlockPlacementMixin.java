package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BlockItem.class)
public abstract class BlockPlacementMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void place(BlockPlaceContext context,CallbackInfoReturnable<InteractionResult> ci) { if (context.getLevel() instanceof ServerLevel world && context.getPlayer() != null && !Protection.canBuild(world,context.getPlayer(),context.getClickedPos())) ci.setReturnValue(InteractionResult.FAIL); }
}
