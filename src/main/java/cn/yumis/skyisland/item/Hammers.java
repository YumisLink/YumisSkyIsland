package cn.yumis.skyisland.item;

import cn.yumis.skyisland.YumisSkyIsland;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;

/** Single-block pick hammers. Mining level, speed and durability follow the matching pickaxe. */
public final class Hammers {
    public static final Map<Item, String> ITEMS = new LinkedHashMap<>();
    public static Item STONE;
    public static Item IRON;
    public static Item DIAMOND;
    public static Item NETHERITE;
    private Hammers() {}

    public static void register() {
        STONE = register("stone_hammer", ToolMaterial.STONE);
        IRON = register("iron_hammer", ToolMaterial.IRON);
        DIAMOND = register("diamond_hammer", ToolMaterial.DIAMOND);
        NETHERITE = register("netherite_hammer", ToolMaterial.NETHERITE);
    }

    private static Item register(String name, ToolMaterial material) {
        var id = YumisSkyIsland.id(name);
        Item item = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).pickaxe(material, 1.0F, -2.8F));
        Registry.register(BuiltInRegistries.ITEM, id, item);
        ITEMS.put(item, name);
        return item;
    }

    public static boolean isHammer(ItemStack stack) {
        return !stack.isEmpty() && ITEMS.containsKey(stack.getItem());
    }

    public static boolean isHammer(Item item) {
        return item != null && ITEMS.containsKey(item);
    }
}
