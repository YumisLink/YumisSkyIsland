package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.shop.ItemEconomy;
import cn.yumis.skyisland.shop.ItemQuality;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anvil repair and rename carry the main input's star, without another roll. */
@Mixin(AnvilMenu.class)
public abstract class AnvilQualityMixin {
    @Inject(method="createResult",at=@At("RETURN"))
    private void preserveQuality(CallbackInfo ci){
        if(YumisSkyIsland.CONFIG==null)return;
        AnvilMenu menu=(AnvilMenu)(Object)this;
        ItemStack input=menu.getSlot(0).getItem(),result=menu.getSlot(2).getItem();
        int grade=ItemEconomy.grade(input);
        if(grade==0||result.isEmpty()||!ItemStack.isSameItem(input,result))return;
        CustomData data=input.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY);
        String profession=data.copyTag().getStringOr("yumis_profession","");
        ItemQuality.apply(result,YumisSkyIsland.CONFIG.object("products/quality.toml"),grade,profession,input.has(DataComponents.UNBREAKABLE));
        menu.getSlot(2).setChanged();
    }
}
