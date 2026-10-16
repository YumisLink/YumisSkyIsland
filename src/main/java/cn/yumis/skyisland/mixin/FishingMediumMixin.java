package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.AccessoryService;
import cn.yumis.skyisland.server.FishingMediumAccess;
import cn.yumis.skyisland.server.FishingService;
import cn.yumis.skyisland.server.SkyServer;
import cn.yumis.skyisland.world.VoidWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Server-approved media share vanilla bobbing, bite timing, sound, and reel-in. */
@Mixin(FishingHook.class)
public abstract class FishingMediumMixin implements FishingMediumAccess {
    @Shadow private boolean biting;
    @Shadow private int nibble;
    @Shadow private int timeUntilLured;
    @Shadow private int timeUntilHooked;
    @Shadow @Final private static EntityDataAccessor<Boolean> DATA_BITING;
    @Unique private static final byte yumis$WATER=0, yumis$LAVA=1, yumis$VOID=2;
    @Unique private static final EntityDataAccessor<Byte> yumis$MEDIUM=SynchedEntityData.defineId(FishingHook.class,EntityDataSerializers.BYTE);
    @Unique private static final EntityDataAccessor<Float> yumis$VOID_SURFACE=SynchedEntityData.defineId(FishingHook.class,EntityDataSerializers.FLOAT);
    @Unique private boolean yumis$voidTested;
    @Unique private boolean yumis$savedGravity;
    @Unique private boolean yumis$previousNoGravity;

    @Inject(method="defineSynchedData",at=@At("TAIL"))
    private void yumis$defineMedium(SynchedEntityData.Builder builder,CallbackInfo ci){
        builder.define(yumis$MEDIUM,yumis$WATER);
        builder.define(yumis$VOID_SURFACE,0.0f);
    }
    @Unique private byte yumis$mode(){return ((FishingHook)(Object)this).getEntityData().get(yumis$MEDIUM);}
    @Override public String yumis$medium(){return switch(yumis$mode()){case yumis$LAVA->"lava";case yumis$VOID->"void";default->"water";};}
    @Override public boolean yumis$biting(){return biting;}

    /** The invocation is after vanilla's owner, distance, and lifetime checks. */
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private void yumis$selectMedium(CallbackInfo ci){
        FishingHook hook=(FishingHook)(Object)this;
        if(!(hook.level() instanceof ServerLevel level))return;
        SkyServer runtime=SkyServer.get(level.getServer());
        if(runtime==null||!VoidWorlds.isSky(level)||!(hook.getPlayerOwner() instanceof ServerPlayer player)||hook.getHookedIn()!=null){
            if(yumis$mode()!=yumis$WATER)yumis$clearMedium();
            return;
        }
        if(yumis$mode()==yumis$VOID){
            if(!AccessoryService.owns(runtime,player,"void_bait"))yumis$clearMedium();
            return;
        }
        BlockPos pos=hook.blockPosition();
        FluidState fluid=level.getFluidState(pos);
        boolean lavaAllowed=AccessoryService.owns(runtime,player,"lava_bait");
        if(fluid.is(FluidTags.LAVA)&&lavaAllowed&&FishingService.validLava(level,pos)){
            hook.getEntityData().set(yumis$MEDIUM,yumis$LAVA);
            return;
        }
        if(yumis$mode()==yumis$LAVA){
            // Vanilla may briefly bob above a fluid block while preserving its bite window.
            if(fluid.isEmpty()&&lavaAllowed&&FishingService.validLava(level,pos.below())&&hook.getY()<pos.getY()+0.35)return;
            yumis$clearMedium();
        }
        if(!yumis$voidTested&&fluid.isEmpty()&&AccessoryService.owns(runtime,player,"void_bait")&&hook.getY()<player.getY()-3){
            yumis$voidTested=true;
            if(FishingService.validVoid(level,pos)){
                yumis$previousNoGravity=hook.isNoGravity();
                yumis$savedGravity=true;
                hook.setNoGravity(true);
                hook.setDeltaMovement(Vec3.ZERO);
                hook.getEntityData().set(yumis$VOID_SURFACE,(float)hook.getY());
                hook.getEntityData().set(yumis$MEDIUM,yumis$VOID);
            }
        }
    }
    @Inject(method="onSyncedDataUpdated",at=@At("TAIL"))
    private void yumis$stopInitialVoidFall(EntityDataAccessor<?> accessor,CallbackInfo ci){
        if(accessor.equals(yumis$MEDIUM)&&yumis$mode()==yumis$VOID)((FishingHook)(Object)this).setDeltaMovement(Vec3.ZERO);
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean yumis$specialActsAsWater(FluidState fluid,TagKey<Fluid> tag){
        return tag==FluidTags.WATER&&(yumis$mode()==yumis$VOID||yumis$mode()==yumis$LAVA&&fluid.is(FluidTags.LAVA))||fluid.is(tag);
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float yumis$virtualWaterSurface(FluidState fluid,BlockGetter level,BlockPos pos){
        return yumis$mode()==yumis$VOID?((FishingHook)(Object)this).getEntityData().get(yumis$VOID_SURFACE)-pos.getY():fluid.getHeight(level,pos);
    }
    /** 26.2 tick local 5 is isInWater. The fixed virtual surface can be below the current block. */
    @ModifyVariable(method="tick",at=@At("STORE"),index=5)
    private boolean yumis$virtualWaterIsPresent(boolean vanilla){return vanilla||yumis$mode()==yumis$VOID;}
    @Inject(method="calculateOpenWater",at=@At("HEAD"),cancellable=true)
    private void yumis$openSpecialArea(BlockPos pos,CallbackInfoReturnable<Boolean> cir){if(yumis$mode()!=yumis$WATER)cir.setReturnValue(true);}
    @Inject(method="isOpenWaterFishing",at=@At("HEAD"),cancellable=true)
    private void yumis$openSpecialFishing(CallbackInfoReturnable<Boolean> cir){if(yumis$mode()!=yumis$WATER)cir.setReturnValue(true);}

    @Unique private void yumis$clearMedium(){
        FishingHook hook=(FishingHook)(Object)this;
        if(yumis$savedGravity){hook.setNoGravity(yumis$previousNoGravity);yumis$savedGravity=false;}
        hook.getEntityData().set(yumis$MEDIUM,yumis$WATER);
        hook.getEntityData().set(yumis$VOID_SURFACE,0.0f);
        nibble=0;timeUntilLured=0;timeUntilHooked=0;
        hook.getEntityData().set(DATA_BITING,false);
    }
    @Inject(method="remove",at=@At("HEAD"))
    private void yumis$finishSpecialFishing(Entity.RemovalReason reason,CallbackInfo ci){
        FishingHook hook=(FishingHook)(Object)this;
        if(!hook.level().isClientSide()&&yumis$mode()!=yumis$WATER)yumis$clearMedium();
    }
}
