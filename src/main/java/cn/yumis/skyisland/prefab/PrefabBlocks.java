package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.YumisSkyIsland;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class PrefabBlocks {
    public static BlueprintCannonBlock CANNON;
    public static BlockEntityType<BlueprintCannonEntity> CANNON_TYPE;
    public static MenuType<BlueprintCannonMenu> CANNON_MENU;
    private PrefabBlocks() {}
    public static void register() {
        var id=YumisSkyIsland.id("blueprint_cannon");
        CANNON=Registry.register(BuiltInRegistries.BLOCK,id,new BlueprintCannonBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
            .setId(ResourceKey.create(Registries.BLOCK,id)).strength(3.5F).pushReaction(PushReaction.BLOCK)));
        Registry.register(BuiltInRegistries.ITEM,id,new BlockItem(CANNON,new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id)).useBlockDescriptionPrefix()));
        CANNON_TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id,FabricBlockEntityTypeBuilder.create(BlueprintCannonEntity::new,CANNON).build());
        CANNON_MENU=Registry.register(BuiltInRegistries.MENU,id,new ExtendedMenuType<>(BlueprintCannonMenu::new,BlockPos.STREAM_CODEC));
    }
}
