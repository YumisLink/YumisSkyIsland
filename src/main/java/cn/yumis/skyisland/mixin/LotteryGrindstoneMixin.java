package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.shop.LotteryItemRarity;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrindstoneMenu.class)
public abstract class LotteryGrindstoneMixin {
    @Inject(method="computeResult",at=@At("RETURN"))
    private void lottery$result(ItemStack first,ItemStack second,CallbackInfoReturnable<ItemStack> cir){LotteryItemRarity.inherit(first.isEmpty()?second:first,cir.getReturnValue());}
}
