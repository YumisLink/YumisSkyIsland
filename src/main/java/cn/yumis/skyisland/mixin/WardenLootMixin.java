package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.item.ModItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player who lands the killing blow receives the bag. Only a fished warden qualifies. */
@Mixin(LivingEntity.class)
public abstract class WardenLootMixin {
    @Inject(method = "die", at = @At("TAIL"))
    private void yumis$bag(DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof Warden warden)) return;
        if (!(warden.level() instanceof ServerLevel level)) return;
        if (!Boolean.TRUE.equals(((AttachmentTarget) warden).getAttached(YumisSkyIsland.FISHED_WARDEN))) return;
        if (!(source.getEntity() instanceof ServerPlayer player) || !level.getGameRules().get(GameRules.MOB_DROPS)) return;
        ((AttachmentTarget) warden).setAttached(YumisSkyIsland.FISHED_WARDEN, false);
        ItemStack bag = new ItemStack(ModItems.WARDEN_TREASURE_BAG);
        if (!player.getInventory().add(bag)) player.drop(bag, false);
    }
}
