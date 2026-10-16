package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.FishingService;
import cn.yumis.skyisland.server.FishingMediumAccess;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Modify the mutable catch list before vanilla advances quests or spawns the catch. */
@Mixin(FishingHook.class)
public abstract class FishingLootMixin {
    @ModifyArg(method="retrieve",at=@At(value="INVOKE",target="Lnet/minecraft/advancements/triggers/FishingRodHookedTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/projectile/FishingHook;Ljava/util/Collection;)V",ordinal=1),index=3)
    private Collection<ItemStack> yumis$regionalCatch(Collection<ItemStack> loot){
        FishingHook hook=(FishingHook)(Object)this;
        if(hook.getPlayerOwner() instanceof ServerPlayer player)FishingService.caught(player,hook,loot);
        return loot;
    }
    /** The vanilla hook creates catches inside lava or over open void; land those catches by the angler. */
    @ModifyArg(method="retrieve",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",ordinal=0),index=0)
    private Entity yumis$safeSpecialCatch(Entity entity){
        FishingHook hook=(FishingHook)(Object)this;
        if(entity instanceof ItemEntity item && hook instanceof FishingMediumAccess medium && !medium.yumis$medium().equals("water")
                && hook.getPlayerOwner() instanceof ServerPlayer player){
            item.setPos(player.getX(),player.getY()+0.5,player.getZ());
            item.setDeltaMovement(Vec3.ZERO);
        }
        return entity;
    }
    @Redirect(method="catchingFish",at=@At(value="INVOKE",target="Lnet/minecraft/util/Mth;nextInt(Lnet/minecraft/util/RandomSource;II)I",ordinal=2))
    private int yumis$powerWait(RandomSource random,int min,int max){
        int vanilla=Mth.nextInt(random,min,max);
        FishingHook hook=(FishingHook)(Object)this;
        if(!(hook.getPlayerOwner() instanceof ServerPlayer player))return vanilla;
        var runtime=cn.yumis.skyisland.server.SkyServer.get(player.level().getServer());
        return runtime==null?vanilla:Math.max(1,(int)Math.ceil(vanilla*FishingService.waitFactor(FishingService.power(runtime,player))));
    }
}
