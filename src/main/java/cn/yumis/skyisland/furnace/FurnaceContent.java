package cn.yumis.skyisland.furnace;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.config.FurnaceSettings;
import java.util.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Seven ordinary smelting machines; registration and ticking do not depend on a sky-island runtime. */
public final class FurnaceContent {
    public static final Map<String,UpgradedFurnaceBlock> BLOCKS=new LinkedHashMap<>();
    public static BlockEntityType<UpgradedFurnaceEntity> ENTITY;
    private FurnaceContent() {}
    public static void register(){
        for(String tier:FurnaceSettings.TIERS){
            var id=YumisSkyIsland.id(tier+"_furnace");
            var block=Registry.register(BuiltInRegistries.BLOCK,id,new UpgradedFurnaceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE)
                .setId(ResourceKey.create(Registries.BLOCK,id))));
            BLOCKS.put(tier,block);
            Registry.register(BuiltInRegistries.ITEM,id,new BlockItem(block,new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id)).useBlockDescriptionPrefix()));
        }
        ENTITY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,YumisSkyIsland.id("upgraded_furnace"),
            FabricBlockEntityTypeBuilder.create(UpgradedFurnaceEntity::new,BLOCKS.values().toArray(UpgradedFurnaceBlock[]::new)).build());
    }
}
