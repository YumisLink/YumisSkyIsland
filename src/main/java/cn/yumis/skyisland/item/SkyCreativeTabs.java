package cn.yumis.skyisland.item;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.block.ModBlocks;
import cn.yumis.skyisland.prefab.PrefabBlocks;
import cn.yumis.skyisland.prefab.PrefabItems;
import cn.yumis.skyisland.wand.ConstructionWands;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Creative entries for this server's validated external blueprints and registered items. */
public final class SkyCreativeTabs {
    public static CreativeModeTab PREFABS, ITEMS, TREASURE_BOXES;
    private static volatile List<ItemStack> blueprints=List.of();
    private SkyCreativeTabs() {}
    public static void register(){
        PREFABS=Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,YumisSkyIsland.id("prefabs"),FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.yumis_sky_island.prefabs"))
            .icon(()->new ItemStack(PrefabItems.BLUEPRINT))
            .displayItems((parameters,output)->{for(ItemStack blueprint:blueprints)output.accept(blueprint.copy());})
            .build());
        ITEMS=Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,YumisSkyIsland.id("items"),FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.yumis_sky_island.items"))
            .icon(()->new ItemStack(ConstructionWands.WANDS.keySet().iterator().next()))
            .displayItems((parameters,output)->{
                for(var item:ConstructionWands.WANDS.keySet())output.accept(item);
                for(var item:ConstructionWands.BLUEPRINTS.keySet())output.accept(item);
                output.accept(PrefabBlocks.CANNON);
                output.accept(PrefabItems.AMMUNITION);
                output.accept(ModBlocks.SHIPPING_CHEST);
                output.accept(ModItems.FLIGHT_TICKET);
                output.accept(ModItems.LOTTERY_TICKET);
                output.accept(cn.yumis.skyisland.npc.ResidentContent.PERMIT);
                for(var item:ModItems.FRUITS.values())output.accept(item);
                for(var item:ModItems.BIOME_BALLS.values())output.accept(item);
                for(var item:ModItems.ACCESSORY_ICONS.values())output.accept(item);
                for(var item:ModItems.FISH.values())output.accept(item);
                for(var item:ModItems.FISHING_MATERIALS.values())output.accept(item);
            }).build());
        TREASURE_BOXES=Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,YumisSkyIsland.id("treasure_boxes"),FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.yumis_sky_island.treasure_boxes"))
            .icon(()->BiomeTreasureBox.create("plains","water",false))
            .displayItems((parameters,output)->{
                for(String biome:ModItems.BIOME_BALLS.keySet())output.accept(BiomeTreasureBox.create(biome,"water",false));
                for(String biome:List.of("basalt_deltas","crimson_forest","nether_wastes","soul_sand_valley","warped_forest"))output.accept(BiomeTreasureBox.create(biome,"lava",false));
                for(String biome:List.of("end_barrens","end_highlands","end_midlands","small_end_islands","the_end"))output.accept(BiomeTreasureBox.create(biome,"void",false));
                for(String biome:ModItems.BIOME_BALLS.keySet())
                    if(!List.of("basalt_deltas","crimson_forest","nether_wastes","soul_sand_valley","warped_forest","end_barrens","end_highlands","end_midlands","small_end_islands","the_end").contains(biome))
                        output.accept(BiomeTreasureBox.create(biome,"water",true));
            }).build());
    }
    public static void blueprints(List<ItemStack> entries){blueprints=entries.stream().map(ItemStack::copy).toList();}
}
