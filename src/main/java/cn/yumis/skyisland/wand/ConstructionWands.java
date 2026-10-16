package cn.yumis.skyisland.wand;

import cn.yumis.skyisland.YumisSkyIsland;
import java.util.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;

public final class ConstructionWands {
    public static final int[] TIERS = {3,9,14,18,64,256};
    public static final Map<Item,Integer> WANDS = new LinkedHashMap<>();
    public static final Map<Item,Integer> BLUEPRINTS = new LinkedHashMap<>();
    private ConstructionWands() {}
    public static void register() {
        for (int i=0;i<TIERS.length;i++) {
            int tier=TIERS[i];
            WANDS.put(register("construction_wand_"+tier,1,i),i);
            if (i>0) BLUEPRINTS.put(register("wand_blueprint_"+tier,64,i),i);
        }
    }
    private static Item register(String name,int size,int tier) {
        var id=YumisSkyIsland.id(name);
        Rarity rarity=tier==0?Rarity.COMMON:tier<3?Rarity.UNCOMMON:tier<5?Rarity.RARE:Rarity.EPIC;
        return Registry.register(BuiltInRegistries.ITEM,id,new Item(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM,id)).stacksTo(size).rarity(rarity)));
    }
    public static boolean isWand(ItemStack stack) { return !stack.isEmpty()&&WANDS.containsKey(stack.getItem()); }
    public static ItemStack held(net.minecraft.world.entity.player.Player player) {
        return isWand(player.getMainHandItem())?player.getMainHandItem():isWand(player.getOffhandItem())?player.getOffhandItem():ItemStack.EMPTY;
    }
    public static int tier(ItemStack stack) { return WANDS.getOrDefault(stack.getItem(),-1); }
    public static String rarity(int tier) { return tier==0?"common":tier<3?"rare":tier<5?"epic":"legendary"; }
    public static int specialTier(ItemStack stack) { return WANDS.getOrDefault(stack.getItem(),BLUEPRINTS.getOrDefault(stack.getItem(),-1)); }
}
