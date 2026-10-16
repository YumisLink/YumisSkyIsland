package cn.yumis.skyisland.npc;
import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.network.ResidentPayloads;
import cn.yumis.skyisland.server.SkyServer;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import com.mojang.serialization.Codec;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class ResidentContent {
    public static Item PERMIT;
    public static EntityType<ResidentEntity> TYPE;
    public static AttachmentType<Map<String,Long>> RECEIPTS;
    private ResidentContent() {}
    public static void register(){
        var item=YumisSkyIsland.id("housing_permit");PERMIT=Registry.register(BuiltInRegistries.ITEM,item,new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,item)).stacksTo(1)){
            @Override public InteractionResult useOn(UseOnContext context){
                if(context.getPlayer() instanceof ServerPlayer player){var rt=SkyServer.get(player.level().getServer());if(rt==null){SkyServer.tell(player,"居住许可证仅可在空岛使用");return InteractionResult.FAIL;}try{rt.residents.mark(player,context.getClickedPos(),context.getHand());}catch(IllegalArgumentException|IllegalStateException error){SkyServer.tell(player,error.getMessage());}}
                return InteractionResult.SUCCESS;
            }
            @Override public InteractionResult use(Level level,net.minecraft.world.entity.player.Player user,InteractionHand hand){
                if(user instanceof ServerPlayer player){var rt=SkyServer.get(player.level().getServer());if(rt==null)return InteractionResult.FAIL;try{if(player.isShiftKeyDown())rt.residents.submit(player,hand);else rt.residents.open(player,null);}catch(IllegalArgumentException|IllegalStateException error){SkyServer.tell(player,error.getMessage());}}
                return InteractionResult.SUCCESS;
            }
        });
        var entity=YumisSkyIsland.id("resident");TYPE=Registry.register(BuiltInRegistries.ENTITY_TYPE,entity,EntityType.Builder.of(ResidentEntity::new,MobCategory.CREATURE).sized(0.6F,1.8F).eyeHeight(1.62F).clientTrackingRange(8).updateInterval(3).noLootTable().build(ResourceKey.create(Registries.ENTITY_TYPE,entity)));
        FabricDefaultAttributeRegistry.register(TYPE,Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,0.25).add(Attributes.FOLLOW_RANGE,24));
        RECEIPTS=AttachmentRegistry.create(YumisSkyIsland.id("resident_receipts"),builder->builder.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING,Codec.LONG)));
        PayloadTypeRegistry.serverboundPlay().register(ResidentPayloads.Request.TYPE,ResidentPayloads.Request.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ResidentPayloads.Request.TYPE,(payload,context)->{var rt=SkyServer.get(context.server());if(rt!=null)rt.residents.request(context.player(),payload.json());});
    }
}
