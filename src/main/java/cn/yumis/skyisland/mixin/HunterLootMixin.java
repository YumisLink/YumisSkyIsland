package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/** Only the killed mob's own loot is rolled; nearby or later item entities are untouched. */
@Mixin(LivingEntity.class)
public abstract class HunterLootMixin {
    @Unique private ServerPlayer yumis$hunter;
    @Unique private boolean yumis$splitLoot;
    @Unique private boolean yumis$hunterQuality;
    @Unique private int yumis$lootCopies = 1;

    @Inject(method="dropAllDeathLoot",at=@At("HEAD"))
    private void beginLoot(ServerLevel level,DamageSource source,CallbackInfo ci){
        LivingEntity self=(LivingEntity)(Object)this;
        if(source.getEntity() instanceof ServerPlayer player){
            SkyServer runtime=SkyServer.get(level.getServer());
            String id=BuiltInRegistries.ENTITY_TYPE.getKey(self.getType()).toString();
            if(runtime!=null&&!player.isCreative()){
                yumis$hunter=player;
                yumis$hunterQuality=runtime.professions.hunterMob(id);
                yumis$lootCopies=runtime.professions.hunterLootCopies(player);
            }
        }
    }
    @Inject(method="dropAllDeathLoot",at=@At("RETURN"))
    private void endLoot(ServerLevel level,DamageSource source,CallbackInfo ci){yumis$hunter=null;yumis$hunterQuality=false;yumis$lootCopies=1;}

    /** Vanilla sends mob loot-table output through a Consumer and spawnAtLocation,
     * not through LivingEntity.drop. Split it before any item entity is created. */
    @ModifyArg(method="dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"),index=2)
    private Consumer<ItemStack> yumis$qualityLootTableDrops(Consumer<ItemStack> original){
        if(yumis$hunter==null)return original;
        ServerPlayer hunter=yumis$hunter;
        SkyServer runtime=SkyServer.get(hunter.level().getServer());
        if(runtime==null)return original;
        boolean quality=yumis$hunterQuality;
        int copies=yumis$lootCopies;
        return stack->{for(int copy=0;copy<copies;copy++){
            ItemStack part=stack.copy();
            if(quality)for(ItemStack piece:runtime.professions.qualityParts(hunter,"hunter",part))original.accept(piece);
            else original.accept(part);
        }};
    }

    @Inject(method="drop",at=@At("HEAD"),cancellable=true)
    private void splitLoot(ItemStack stack,boolean flag,boolean flag2,CallbackInfoReturnable<ItemEntity> ci){
        if(yumis$hunter==null||!yumis$hunterQuality||yumis$splitLoot||stack.isEmpty())return;
        var runtime=SkyServer.get(yumis$hunter.level().getServer());if(runtime==null)return;
        var pieces=runtime.professions.qualityParts(yumis$hunter,"hunter",stack);
        if(pieces.size()==1&&ItemStack.matches(pieces.getFirst(),stack))return;
        yumis$splitLoot=true;
        ItemEntity first=null;
        try{
            for(ItemStack piece:pieces){
                ItemEntity dropped=((LivingEntity)(Object)this).drop(piece,flag,flag2);
                if(first==null)first=dropped;
            }
        }finally{yumis$splitLoot=false;}
        ci.setReturnValue(first);
    }
}
