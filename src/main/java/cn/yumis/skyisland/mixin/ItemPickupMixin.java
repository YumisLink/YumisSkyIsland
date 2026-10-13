package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntity.class)
public abstract class ItemPickupMixin{
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)private void touch(Player player,CallbackInfo ci){var item=(ItemEntity)(Object)this;if(item.level() instanceof ServerLevel level&&!Protection.canBuild(level,player,item.blockPosition()))ci.cancel();}
}
