package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.SkyIslandClient;
import cn.yumis.skyisland.core.RarityColors;
import cn.yumis.skyisland.shop.ItemEconomy;
import cn.yumis.skyisland.shop.QualityNames;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cosmetic display only: does not introduce custom names or change stacking/trade components. */
@Mixin(ItemStack.class)
public abstract class RarityNameMixin {
    @Inject(method="getHoverName",at=@At("RETURN"),cancellable=true)
    private void rarity(CallbackInfoReturnable<Component> result){
        var mc=Minecraft.getInstance();if(mc==null||!mc.isSameThread())return;
        ItemStack stack=(ItemStack)(Object)this;
        int grade=ItemEconomy.grade(stack);
        if(grade>=5){
            result.setReturnValue(QualityNames.rainbow(result.getReturnValue().getString()+" ★★★★★",(int)(System.currentTimeMillis()/80%360)));
            return;
        }
        if(grade>0){
            result.setReturnValue(result.getReturnValue().copy().append(QualityNames.stars(grade)));
            return;
        }
        var model=SkyIslandClient.CATALOG;
        if(model==null||model.products.isEmpty()&&model.itemRarities.isEmpty()&&cn.yumis.skyisland.shop.LotteryItemRarity.get((ItemStack)(Object)this).isEmpty()&&cn.yumis.skyisland.wand.ConstructionWands.specialTier((ItemStack)(Object)this)<0)return;
        int color=RarityColors.color(model.rarity(stack))&0xFFFFFF;
        result.setReturnValue(result.getReturnValue().copy().withStyle(style->style.withColor(color)));
    }
}
