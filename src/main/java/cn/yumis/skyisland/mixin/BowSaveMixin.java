package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.AccessoryCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refund the arrow that actually shrank. Infinity and creative leave the stack alone. */
@Mixin(BowItem.class)
public abstract class BowSaveMixin {
    @Unique private final ThreadLocal<ItemStack> yumis$ammo = new ThreadLocal<>();
    @Unique private final ThreadLocal<Integer> yumis$ammoCount = new ThreadLocal<>();
    @Unique private final ThreadLocal<ItemStack> yumis$sample = new ThreadLocal<>();

    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void yumis$beforeShot(ItemStack stack, Level level, LivingEntity entity, int time, CallbackInfoReturnable<Boolean> cir) {
        yumis$ammo.remove();
        yumis$ammoCount.remove();
        yumis$sample.remove();
        if (entity instanceof Player player) {
            ItemStack ammo = player.getProjectile(stack);
            yumis$ammo.set(ammo);
            yumis$ammoCount.set(ammo.getCount());
            yumis$sample.set(ammo.copy());
        }
    }

    @Inject(method = "releaseUsing", at = @At("RETURN"))
    private void yumis$saveArrow(ItemStack stack, Level level, LivingEntity entity, int time, CallbackInfoReturnable<Boolean> cir) {
        ItemStack ammo = yumis$ammo.get();
        Integer before = yumis$ammoCount.get();
        ItemStack sample = yumis$sample.get();
        yumis$ammo.remove();
        yumis$ammoCount.remove();
        yumis$sample.remove();
        if (!cir.getReturnValueZ() || ammo == null || before == null || sample == null || sample.isEmpty()) return;
        if (!(entity instanceof ServerPlayer player) || player.getAbilities().instabuild) return;
        if (ammo.getCount() >= before) return;
        if (!AccessoryCombat.rollSave(player)) return;
        ItemStack refund = sample.copyWithCount(1);
        if (!player.getInventory().add(refund)) player.drop(refund, false);
    }
}
