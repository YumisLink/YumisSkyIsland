package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(HopperBlockEntity.class)
public abstract class HopperMixin {
    @Inject(method="addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",at=@At("HEAD"),cancellable=true)
    private static void transfer(Container from,Container to,ItemStack stack,Direction side,CallbackInfoReturnable<ItemStack> ci) { if (!Protection.transfer(from,to)) ci.setReturnValue(stack); }
    @Inject(method="addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",at=@At("HEAD"),cancellable=true)
    private static void absorb(Container to,ItemEntity item,CallbackInfoReturnable<Boolean> ci) { if (!Protection.absorb(to,item)) ci.setReturnValue(false); }
    @ModifyVariable(method="addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",at=@At("HEAD"),argsOnly=true)
    private static ItemStack ordinary(ItemStack stack,Container from,Container to,ItemStack original,Direction side) {
        if (from instanceof BlockEntity be && be.getLevel() instanceof ServerLevel level) { var rt = SkyServer.get(level.getServer()); if (rt != null) return rt.professions.automated(from,stack); }
        return stack;
    }
    @Inject(method="getAttachedContainer",at=@At("HEAD"),cancellable=true)
    private static void attached(Level level,BlockPos pos,HopperBlockEntity hopper,CallbackInfoReturnable<Container> ci) {
        Direction face = level.getBlockState(pos).getValue(HopperBlock.FACING); if (!Protection.sameClaim(level,pos,pos.relative(face))) ci.setReturnValue(null);
    }
    @Inject(method="getSourceContainer",at=@At("HEAD"),cancellable=true)
    private static void source(Level level,Hopper hopper,BlockPos sourcePos,BlockState state,CallbackInfoReturnable<Container> ci) {
        if (!Protection.sameClaim(level,BlockPos.containing(hopper.getLevelX(),hopper.getLevelY(),hopper.getLevelZ()),sourcePos)) ci.setReturnValue(null);
    }
    @Inject(method="getSourceContainer",at=@At("RETURN"))
    private static void locateSource(Level level,Hopper hopper,BlockPos pos,BlockState state,CallbackInfoReturnable<Container> ci) { Protection.locate(ci.getReturnValue(),level,pos); }
    @Inject(method="getContainerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;DDD)Lnet/minecraft/world/Container;",at=@At("RETURN"))
    private static void locate(Level level,BlockPos pos,BlockState state,double x,double y,double z,CallbackInfoReturnable<Container> ci) { Protection.locate(ci.getReturnValue(),level,pos); }
}
