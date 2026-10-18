package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.AccessoryCombat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One saved projectile, even when multishot emptied several charged copies. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowSaveMixin {
    @Unique private final ThreadLocal<ItemStack> yumis$charged = new ThreadLocal<>();

    @Inject(method = "performShooting", at = @At("HEAD"))
    private void yumis$beforeShot(Level level, LivingEntity shooter, InteractionHand hand, ItemStack stack, float speed, float divergence, LivingEntity target, CallbackInfo ci) {
        yumis$charged.remove();
        ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (charged != null && !charged.itemCopies().isEmpty()) yumis$charged.set(charged.itemCopies().getFirst().copy());
    }

    @Inject(method = "performShooting", at = @At("RETURN"))
    private void yumis$saveShot(Level level, LivingEntity shooter, InteractionHand hand, ItemStack stack, float speed, float divergence, LivingEntity target, CallbackInfo ci) {
        ItemStack sample = yumis$charged.get();
        yumis$charged.remove();
        if (sample == null || sample.isEmpty() || !(shooter instanceof ServerPlayer player) || player.getAbilities().instabuild) return;
        ChargedProjectiles left = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (left != null && !left.isEmpty()) return;
        if (!AccessoryCombat.rollSave(player)) return;
        ItemStack refund = sample.copyWithCount(1);
        if (!player.getInventory().add(refund)) player.drop(refund, false);
    }
}
