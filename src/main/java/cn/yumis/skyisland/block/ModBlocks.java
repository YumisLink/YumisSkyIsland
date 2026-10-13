package cn.yumis.skyisland.block;
import cn.yumis.skyisland.YumisSkyIsland;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
public final class ModBlocks {
    public static ShippingChestBlock SHIPPING_CHEST;
    public static BlockEntityType<ShippingChestEntity> SHIPPING_TYPE;
    public static void register() {
        Identifier id = YumisSkyIsland.id("shipping_chest");
        SHIPPING_CHEST = Registry.register(BuiltInRegistries.BLOCK, id, new ShippingChestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST).setId(ResourceKey.create(Registries.BLOCK, id)).pushReaction(PushReaction.BLOCK)));
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(SHIPPING_CHEST, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
        SHIPPING_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, FabricBlockEntityTypeBuilder.create(ShippingChestEntity::new, SHIPPING_CHEST).build());
    }
}
