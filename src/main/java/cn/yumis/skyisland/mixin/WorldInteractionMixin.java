package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerLevel.class)
public abstract class WorldInteractionMixin{
    @Inject(method="mayInteract",at=@At("HEAD"),cancellable=true)private void may(Entity entity,BlockPos pos,CallbackInfoReturnable<Boolean> ci){if(entity instanceof Player player&&!Protection.canBuild((ServerLevel)(Object)this,player,pos))ci.setReturnValue(false);}
}
