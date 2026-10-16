package cn.yumis.skyisland.shop;

import cn.yumis.skyisland.config.LotterySettings;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** A prize's rarity is independent from manufacture quality and economic valuation. */
public final class LotteryItemRarity {
    public static final String KEY="yumis_lottery_rarity";
    private LotteryItemRarity() {}
    public static String get(ItemStack stack) {
        String value=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr(KEY,"");
        return LotterySettings.RARITIES.contains(value)?value:"";
    }
    public static void apply(ItemStack stack,String rarity) {
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        tag.remove(KEY);if(!rarity.isEmpty())tag.putString(KEY,rarity);
        if(tag.isEmpty())stack.remove(DataComponents.CUSTOM_DATA);else stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    public static void inherit(ItemStack input,ItemStack result) {
        if(!result.isEmpty())apply(result,ItemStack.isSameItem(input,result)?get(input):"");
    }
}
