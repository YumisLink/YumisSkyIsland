package cn.yumis.skyisland;
import cn.yumis.skyisland.attribute.ModAttributes;
import cn.yumis.skyisland.block.ModBlocks;
import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.effect.ModEffects;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.network.ShopPayloads;
import cn.yumis.skyisland.network.TaskPayloads;
import cn.yumis.skyisland.network.LotteryPayloads;
import cn.yumis.skyisland.server.*;
import cn.yumis.skyisland.world.VoidWorlds;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.Level;
import org.slf4j.*;
public final class YumisSkyIsland implements ModInitializer {
    public static final String MOD_ID = "yumis_sky_island";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static GameConfig CONFIG;
    public static final AttachmentType<Long> TRANSACTION_SEQUENCE = AttachmentRegistry.create(id("transaction_sequence"),b -> b.initializer(() -> 0L).persistent(Codec.LONG).copyOnDeath());
    public static final AttachmentType<String> ENTITY_PARTY = AttachmentRegistry.create(id("entity_party"),b -> b.initializer(() -> "").persistent(Codec.STRING));
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID,path); }
    @Override public void onInitialize() {
        try { CONFIG = new GameConfig(); } catch (Exception e) { throw new IllegalStateException("空岛配置加载失败，请检查 config/yumis_sky_island",e); }
        ModBlocks.register(); ModItems.register(); cn.yumis.skyisland.npc.ResidentContent.register(); cn.yumis.skyisland.wand.ConstructionWands.register(); VoidWorlds.register(); WorldService.registerTickets();
        cn.yumis.skyisland.prefab.PrefabItems.register(); cn.yumis.skyisland.prefab.PrefabBlocks.register(); cn.yumis.skyisland.item.SkyCreativeTabs.register(); cn.yumis.skyisland.prefab.PrefabService.register();
        cn.yumis.skyisland.wand.ConstructionWandService.register();
        ModAttributes.register(); ModEffects.register(); CloudLeapService.register();
        PayloadTypeRegistry.serverboundPlay().register(ShopPayloads.Request.TYPE,ShopPayloads.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ShopPayloads.Purchase.TYPE,ShopPayloads.Purchase.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ShopPayloads.ProfessionPurchase.TYPE,ShopPayloads.ProfessionPurchase.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(cn.yumis.skyisland.network.ConfigEditPayloads.Request.TYPE,cn.yumis.skyisland.network.ConfigEditPayloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(cn.yumis.skyisland.network.ConfigEditPayloads.Snapshot.TYPE,cn.yumis.skyisland.network.ConfigEditPayloads.Snapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShopPayloads.Snapshot.TYPE,ShopPayloads.Snapshot.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TaskPayloads.Request.TYPE,TaskPayloads.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LotteryPayloads.Operation.TYPE,LotteryPayloads.Operation.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TaskPayloads.Snapshot.TYPE,TaskPayloads.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ShopPayloads.Request.TYPE,(payload,context) -> { var rt = SkyServer.get(context.server()); if (rt != null) rt.request(context.player(),payload); });
        ServerPlayNetworking.registerGlobalReceiver(ShopPayloads.Purchase.TYPE,(payload,context) -> { var rt = SkyServer.get(context.server()); if (rt != null) rt.purchase(context.player(),payload); });
        ServerPlayNetworking.registerGlobalReceiver(ShopPayloads.ProfessionPurchase.TYPE,(payload,context) -> { var rt=SkyServer.get(context.server());if(rt!=null)rt.purchaseProfession(context.player(),payload); });
        ServerPlayNetworking.registerGlobalReceiver(cn.yumis.skyisland.network.ConfigEditPayloads.Request.TYPE,(payload,context)->{var rt=SkyServer.get(context.server());if(rt!=null)rt.configEditor.request(context.player(),payload);});
        ServerPlayNetworking.registerGlobalReceiver(TaskPayloads.Request.TYPE,(payload,context) -> { var rt = SkyServer.get(context.server()); if (rt != null) rt.questEditor.request(context.player(),payload); });
        ServerPlayNetworking.registerGlobalReceiver(LotteryPayloads.Operation.TYPE,(payload,context) -> { var rt = SkyServer.get(context.server()); if (rt != null) rt.operateLottery(context.player(),payload); });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> SkyCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { try { SkyServer.start(server,CONFIG); } catch (Exception e) { throw new IllegalStateException("空岛存档加载失败",e); } });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { var rt = SkyServer.get(server); if (rt != null) try { rt.close(); } catch (Exception e) { LOGGER.error("空岛关闭保存失败",e); } });
        ServerTickEvents.END_SERVER_TICK.register(server -> { var rt = SkyServer.get(server); if (rt != null) rt.tick(); });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> { var rt = SkyServer.get(server); if (rt != null) rt.joined(handler.player); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> { var rt = SkyServer.get(server); if (rt != null) {rt.residents.disconnected(handler.player);rt.prefabs.disconnected(handler.player.getUUID());rt.disconnected(handler.player);} });
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk,newlyGenerated) -> { var rt = SkyServer.get(world.getServer()); if (rt != null) { rt.residents.loaded(world,chunk);rt.prefabs.loaded(world,chunk); ModItems.applyBiomeOverride(rt,world,chunk); if(world.dimension().equals(Level.OVERWORLD))rt.refresh.loaded(chunk); } });
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{var rt=SkyServer.get(world.getServer());if(rt!=null)rt.residents.unloaded(world,chunk);});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{var rt=SkyServer.get(world.getServer());if(rt!=null&&entity instanceof cn.yumis.skyisland.npc.ResidentEntity resident)rt.residents.entityUnloaded(resident);});
        ServerEntityEvents.ENTITY_LOAD.register((entity,world) -> {
            var rt = SkyServer.get(world.getServer()); var target = (AttachmentTarget)entity;
            if(rt!=null&&entity instanceof cn.yumis.skyisland.npc.ResidentEntity resident){rt.residents.entityLoaded(resident);return;}
            if(entity instanceof net.minecraft.world.entity.Mob mob&&entity instanceof net.minecraft.world.entity.monster.Enemy)((cn.yumis.skyisland.mixin.MobTargetAccessor)mob).yumis$targets().addGoal(3,new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(mob,cn.yumis.skyisland.npc.ResidentEntity.class,true));
            if(rt!=null&&entity instanceof net.minecraft.world.entity.LivingEntity living)rt.bloodMoon.apply(living);
            if (rt != null && !target.hasAttached(ENTITY_PARTY)) {
                java.util.UUID origin=Protection.currentOrigin(rt);
                if(entity instanceof net.minecraft.world.entity.projectile.Projectile projectile && projectile.getOwner()!=null)origin=Protection.entityParty(rt,projectile.getOwner());
                if(origin==null){var claim=rt.claim(entity.blockPosition());if(claim!=null)origin=claim.id;}
                target.setAttached(ENTITY_PARTY,origin==null?"":origin.toString());
            }
        });
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,be) -> !(world instanceof ServerLevel level) || Protection.canBuild(level,player,pos));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(entity instanceof cn.yumis.skyisland.npc.ResidentEntity)return;
            AccessoryService.defeated(entity,source);
            if(entity instanceof net.minecraft.server.level.ServerPlayer dead){var rt=SkyServer.get(dead.level().getServer());if(rt!=null)rt.playerDied(dead);}
            if(entity instanceof net.minecraft.world.entity.Mob&&source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player){var rt=SkyServer.get(player.level().getServer());if(rt!=null){String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();rt.progression.action(player,"kill",id,1,"","");rt.professions.hunterKill(player,id);}}
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,finalDamage,blocked)->{if(!blocked&&finalDamage>0)AccessoryService.damaged(entity,source);});
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->{
            newPlayer.getFoodData().setFoodLevel(CONFIG.object("economy.toml").getAsJsonObject("death").get("respawnFoodLevel").getAsInt());
            var runtime=SkyServer.get(newPlayer.level().getServer());
            if(runtime!=null){
                var person=runtime.state().players.get(newPlayer.getUUID());
                ModItems.applyFruitBonuses(newPlayer,person);
                AccessoryService.apply(newPlayer,person);
                FishingService.applyLevel(runtime,newPlayer);
                ModItems.disconnected(newPlayer.getUUID());
                ModItems.refreshFlight(newPlayer,person);
            }
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit) -> world instanceof ServerLevel level && !Protection.canBuild(level,player,hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND)return InteractionResult.PASS;
            boolean permit=player.getMainHandItem().is(cn.yumis.skyisland.npc.ResidentContent.PERMIT);
            if(world.isClientSide())return permit?InteractionResult.SUCCESS:InteractionResult.PASS;
            if(player instanceof net.minecraft.server.level.ServerPlayer online&&world instanceof ServerLevel level){
                var rt=SkyServer.get(level.getServer());
                if(permit&&rt==null){SkyServer.tell(online,"居住许可证仅可在空岛使用");return InteractionResult.FAIL;}
                if(rt!=null&&(permit||rt.residents.editing(online))){
                    try{rt.residents.mark(online,hit.getBlockPos(),hand);}catch(IllegalArgumentException|IllegalStateException error){SkyServer.tell(online,error.getMessage());}
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit) -> {
            if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND||!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)||!(world instanceof ServerLevel level))return InteractionResult.PASS;
            var rt=SkyServer.get(level.getServer());
            return rt!=null&&VoidWorlds.isSky(level)&&rt.veinMining.harvestCrop(serverPlayer,level,hit.getBlockPos())?InteractionResult.SUCCESS:InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player,world,hand,pos,face) -> world instanceof ServerLevel level && !Protection.canBuild(level,player,pos) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit) -> {
            if (entity instanceof Villager && !CONFIG.object("server_rules.toml").get("villagerTradingEnabled").getAsBoolean() || entity instanceof WanderingTrader && !CONFIG.object("server_rules.toml").get("wanderingTraderTradingEnabled").getAsBoolean()) return InteractionResult.FAIL;
            return world instanceof ServerLevel level && !Protection.canBuild(level,player,entity.blockPosition()) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit) -> world instanceof ServerLevel level && !Protection.canBuild(level,player,entity.blockPosition()) ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}
