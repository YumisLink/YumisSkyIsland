package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.AccessoryService;
import cn.yumis.skyisland.server.FoodHealth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Count a food only when finishing it actually removes one item. */
@Mixin(LivingEntity.class)
public abstract class FoodEatMixin {
    @Shadow protected ItemStack useItem;
    @Unique private ItemStack yumis$started = ItemStack.EMPTY;

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void yumis$beforeEat(CallbackInfo ci) { yumis$started = useItem.copy(); }

    @Inject(method = "completeUsingItem", at = @At("RETURN"))
    private void yumis$afterEat(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player) {
            ItemStack after = player.getItemInHand(player.getUsedItemHand());
            if (ateOne(yumis$started, after)) AccessoryService.onFood(player, yumis$started);
            FoodHealth.consumed(player, yumis$started, after);
        }
        yumis$started = ItemStack.EMPTY;
    }
    @Unique private static boolean ateOne(ItemStack before, ItemStack after) {
        if (before == null || before.isEmpty()) return false;
        if (after == null || after.isEmpty() || !after.is(before.getItem())) return true;
        return after.getCount() < before.getCount();
    }
}
