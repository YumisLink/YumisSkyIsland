package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BucketItem.class)
public abstract class BucketMixin {
    @Inject(method="emptyContents",at=@At("HEAD"),cancellable=true)
    private void empty(LivingEntity user,Level level,BlockPos pos,BlockHitResult hit,CallbackInfoReturnable<Boolean> ci) { if (user instanceof Player p && level instanceof ServerLevel world && !Protection.canBuild(world,p,pos)) ci.setReturnValue(false); }
}
