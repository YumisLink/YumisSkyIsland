package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {
    @Inject(method="calculateExplodedPositions",at=@At("RETURN"),cancellable=true)
    private void blocks(CallbackInfoReturnable<List<BlockPos>> ci) { ci.setReturnValue(new java.util.ArrayList<>(ci.getReturnValue().stream().filter(p -> Protection.explosion((ServerExplosion)(Object)this,p)).toList())); }
    @Redirect(method="hurtEntities",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> entities(ServerLevel level,Entity source,AABB box) { return level.getEntities(source,box).stream().filter(e -> Protection.explosion((ServerExplosion)(Object)this,e.blockPosition())).toList(); }
}
