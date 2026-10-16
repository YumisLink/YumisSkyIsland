package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.shop.LotteryItemRarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RepairItemRecipe.class)
public abstract class LotteryRepairMixin {
    @Inject(method="assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;",at=@At("RETURN"))
    private void lottery$repair(CraftingInput input,CallbackInfoReturnable<ItemStack> cir){
        for(int slot=0;slot<input.size();slot++)if(!input.getItem(slot).isEmpty()){LotteryItemRarity.inherit(input.getItem(slot),cir.getReturnValue());break;}
    }
}
