package cn.yumis.skyisland;
import cn.yumis.skyisland.block.ModBlocks;
import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.effect.ModEffects;
import cn.yumis.skyisland.network.ShopPayloads;
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
        ModBlocks.register(); VoidWorlds.register(); WorldService.registerTickets();
        ModEffects.register(); CloudLeapService.register();
        PayloadTypeRegistry.serverboundPlay().register(ShopPayloads.Request.TYPE,ShopPayloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShopPayloads.Snapshot.TYPE,ShopPayloads.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ShopPayloads.Request.TYPE,(payload,context) -> { var rt = SkyServer.get(context.server()); if (rt != null) rt.request(context.player(),payload); });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> SkyCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { try { SkyServer.start(server,CONFIG); } catch (Exception e) { throw new IllegalStateException("空岛存档加载失败",e); } });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { var rt = SkyServer.get(server); if (rt != null) try { rt.close(); } catch (Exception e) { LOGGER.error("空岛关闭保存失败",e); } });
        ServerTickEvents.END_SERVER_TICK.register(server -> { var rt = SkyServer.get(server); if (rt != null) rt.tick(); });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> { var rt = SkyServer.get(server); if (rt != null) rt.joined(handler.player); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> { var rt = SkyServer.get(server); if (rt != null) rt.disconnected(handler.player); });
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk,newlyGenerated) -> { var rt = SkyServer.get(world.getServer()); if (rt != null && world.dimension().equals(Level.OVERWORLD)) rt.refresh.loaded(chunk); });
        ServerEntityEvents.ENTITY_LOAD.register((entity,world) -> {
            var rt = SkyServer.get(world.getServer()); var target = (AttachmentTarget)entity;
            if (rt != null && !target.hasAttached(ENTITY_PARTY)) {
                java.util.UUID origin=Protection.currentOrigin(rt);
                if(entity instanceof net.minecraft.world.entity.projectile.Projectile projectile && projectile.getOwner()!=null)origin=Protection.entityParty(rt,projectile.getOwner());
                if(origin==null){var claim=rt.claim(entity.blockPosition());if(claim!=null)origin=claim.id;}
                target.setAttached(ENTITY_PARTY,origin==null?"":origin.toString());
            }
        });
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,be) -> !(world instanceof ServerLevel level) || Protection.canBuild(level,player,pos));
        UseBlockCallback.EVENT.register((player,world,hand,hit) -> world instanceof ServerLevel level && !Protection.canBuild(level,player,hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player,world,hand,pos,face) -> world instanceof ServerLevel level && !Protection.canBuild(level,player,pos) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit) -> {
            if (entity instanceof Villager && !CONFIG.object("server_rules.json").get("villagerTradingEnabled").getAsBoolean() || entity instanceof WanderingTrader && !CONFIG.object("server_rules.json").get("wanderingTraderTradingEnabled").getAsBoolean()) return InteractionResult.FAIL;
            return world instanceof ServerLevel level && !Protection.canBuild(level,player,entity.blockPosition()) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit) -> world instanceof ServerLevel level && !Protection.canBuild(level,player,entity.blockPosition()) ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}
