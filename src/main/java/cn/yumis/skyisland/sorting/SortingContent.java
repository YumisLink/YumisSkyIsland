package cn.yumis.skyisland.sorting;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.server.SkyServer;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class SortingContent {
    public static SorterBlock BLOCK;
    public static BlockEntityType<SorterEntity> ENTITY;
    public static MenuType<SorterMenu> MENU;
    public static final AttachmentType<String> CHEST_ID=AttachmentRegistry.createPersistent(YumisSkyIsland.id("sorting_chest_id"),Codec.STRING);
    public static final AttachmentType<Long> RECEIPT=AttachmentRegistry.create(YumisSkyIsland.id("sorting_receipt"),b->b.initializer(()->0L).persistent(Codec.LONG));
    private SortingContent() {}
    public static void register(){
        var id=YumisSkyIsland.id("sorter");
        BLOCK=Registry.register(BuiltInRegistries.BLOCK,id,new SorterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .setId(ResourceKey.create(Registries.BLOCK,id)).strength(3.5F).pushReaction(PushReaction.BLOCK)));
        Registry.register(BuiltInRegistries.ITEM,id,new BlockItem(BLOCK,new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id)).useBlockDescriptionPrefix()));
        ENTITY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id,FabricBlockEntityTypeBuilder.create(SorterEntity::new,BLOCK).build());
        MENU=Registry.register(BuiltInRegistries.MENU,id,new ExtendedMenuType<>(SorterMenu::new,BlockPos.STREAM_CODEC));
        PayloadTypeRegistry.serverboundPlay().register(SorterPayloads.Operation.TYPE,SorterPayloads.Operation.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SorterPayloads.Snapshot.TYPE,SorterPayloads.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SorterPayloads.Operation.TYPE,(payload,ctx)->{
            var rt=SkyServer.get(ctx.server());if(rt!=null)rt.sorting.operation(ctx.player(),payload);
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(hand!=InteractionHand.MAIN_HAND||!(player instanceof ServerPlayer sp))return InteractionResult.PASS;
            var rt=SkyServer.get(sp.level().getServer());return rt!=null?rt.sorting.select(sp,hit.getBlockPos()):InteractionResult.PASS;
        });
    }
}
