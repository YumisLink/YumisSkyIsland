package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.StonecutTransferTracker;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Slot.class)
public abstract class StonecutTransferMixin {
    @Inject(method="onQuickCraft(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"))
    private void transfer(ItemStack remaining,ItemStack original,CallbackInfo ci){
        if((Object)this instanceof StonecutTransferTracker tracker)tracker.yumis$transferred(original.copy(),Math.max(0,original.getCount()-remaining.getCount()));
    }
}
