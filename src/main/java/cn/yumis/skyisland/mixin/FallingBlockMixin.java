package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.core.ClaimGrid;
import cn.yumis.skyisland.server.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockMixin {
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/item/FallingBlockEntity;applyEffectsFromBlocks()V"),cancellable=true)
    private void boundary(CallbackInfo ci){var entity=(FallingBlockEntity)(Object)this;if(!(entity.level() instanceof ServerLevel world))return;var rt=SkyServer.get(world.getServer());if(rt==null)return;var id=Protection.entityParty(rt,entity);var party=id==null?null:rt.state().parties.get(id);if(party==null||party.resetInProgress){entity.discard();ci.cancel();return;}var region=ClaimGrid.region(party.slot);if(!region.contains(entity.blockPosition().getX(),entity.blockPosition().getZ())){entity.setPos(Math.clamp(entity.getX(),region.minX()+.5,region.maxX()-.5),entity.getY(),Math.clamp(entity.getZ(),region.minZ()+.5,region.maxZ()-.5));entity.setDeltaMovement(Vec3.ZERO);}}
}
