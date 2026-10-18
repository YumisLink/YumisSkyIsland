package cn.yumis.skyisland.client;
import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.block.ModBlocks;
import cn.yumis.skyisland.core.Money;
import cn.yumis.skyisland.network.ShopPayloads;
import cn.yumis.skyisland.network.TaskPayloads;
import cn.yumis.skyisland.network.CloudLeapPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.blockentity.*;
public final class SkyIslandClient implements ClientModInitializer {
    public static ClientCatalog CATALOG;
    @Override public void onInitializeClient() {
        if(FabricLoader.getInstance().isModLoaded("jei"))
            YumisSkyIsland.LOGGER.info("JEI Fabric 插件入口数量：{}",FabricLoader.getInstance().getEntrypointContainers("jei_mod_plugin",Object.class).size());
        CATALOG = new ClientCatalog();
        net.minecraft.client.renderer.entity.EntityRenderers.register(cn.yumis.skyisland.npc.ResidentContent.TYPE,ResidentRenderer::new);
        VeinPreviewRenderer.register(CATALOG);
        BlockEntityRenderers.register(ModBlocks.SHIPPING_TYPE,ChestRenderer::new);
        var category = KeyMapping.Category.register(YumisSkyIsland.id("sky_island"));
        WandClient.register(category);
        PrefabClient.register(category);
        SorterScreen.register();
        QuiverClient.register();
        KeyMapping shop = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.shop",InputConstants.KEY_I,category));
        KeyMapping professions = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.professions",InputConstants.KEY_P,category));
        KeyMapping accessories = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.accessories",InputConstants.KEY_O,category));
        KeyMapping enderChest = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.ender_chest",InputConstants.KEY_L,category));
        KeyMapping configuration=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.configuration",org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET,category));
        ConfigurationHotkey.bind(configuration);
        ClientTickEvents.END_CLIENT_TICK.register(client->{while(configuration.consumeClick()){} });
        ClientLifecycleEvents.CLIENT_STARTED.register(DefaultKeyConflictResolver::resolve);
        KeyMapping vein = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.vein",InputConstants.KEY_GRAVE,category));
        KeyMapping holdAttack = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.hold_attack",InputConstants.KEY_EQUALS,category));
        final boolean[] holdAttackEnabled={false};
        final boolean[] holdAttackVeinEnabled={false};
        final boolean[] veinHeld={false};
        KeyMapping cloudLeap = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.cloud_leap",InputConstants.KEY_C,category));
        ClientPlayNetworking.registerGlobalReceiver(ShopPayloads.Snapshot.TYPE,(payload,context) -> CATALOG.receive(payload.json()));
        ClientPlayNetworking.registerGlobalReceiver(TaskPayloads.Snapshot.TYPE,(payload,context) -> CATALOG.receiveTasks(payload.json()));
        ClientPlayNetworking.registerGlobalReceiver(cn.yumis.skyisland.network.ConfigEditPayloads.Snapshot.TYPE,(payload,context)->CATALOG.receiveConfig(payload.json()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> {
            if(holdAttackEnabled[0])client.options.keyAttack.setDown(false);
            holdAttackEnabled[0]=false;
            holdAttackVeinEnabled[0]=false;
            veinHeld[0]=false;
            CATALOG.clearConnection();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> { while (shop.consumeClick()) if (client.player != null && ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE)) ClientPlayNetworking.send(new ShopPayloads.Request("open","",0)); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> { while (professions.consumeClick()) if (client.player != null && ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE)) ClientPlayNetworking.send(new ShopPayloads.Request("profession_open","",0)); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (accessories.consumeClick()) if (client.player != null && ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE)) ClientPlayNetworking.send(new ShopPayloads.Request("accessory_open","",0));
            while (enderChest.consumeClick()) if (client.player != null && client.gui.screen() == null && !client.player.isSpectator() && ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE)) ClientPlayNetworking.send(new ShopPayloads.Request("ender_chest","",0));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var window=client.getWindow();
            boolean playing=client.player!=null&&client.gui.screen()==null;
            boolean veinKeyHeld=playing&&vein.isDown();
            boolean modifiers=InputConstants.isKeyDown(window,InputConstants.KEY_LCONTROL)||InputConstants.isKeyDown(window,InputConstants.KEY_RCONTROL);
            modifiers&=InputConstants.isKeyDown(window,InputConstants.KEY_LALT)||InputConstants.isKeyDown(window,InputConstants.KEY_RALT);
            while(holdAttack.consumeClick()) if(modifiers && playing){
                holdAttackEnabled[0]=!holdAttackEnabled[0];
                holdAttackVeinEnabled[0]=holdAttackEnabled[0]&&veinKeyHeld;
                if(!holdAttackEnabled[0])client.options.keyAttack.setDown(false);
                client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(holdAttackEnabled[0]?(holdAttackVeinEnabled[0]?"自动挖掘：开（连锁采集）":"自动挖掘：开"):"自动挖掘：关"));
            }
            // Keep a chain-key press for this auto-mining session, even after it is released.
            if(holdAttackEnabled[0]&&veinKeyHeld&&!holdAttackVeinEnabled[0]){
                holdAttackVeinEnabled[0]=true;
                client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("自动挖掘：开（连锁采集）"));
            }
            boolean next=playing&&(veinKeyHeld||holdAttackEnabled[0]&&holdAttackVeinEnabled[0]);
            if(next!=veinHeld[0]){veinHeld[0]=next;CATALOG.veinPreview=java.util.List.of();if(ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE))ClientPlayNetworking.send(new ShopPayloads.Request("vein","",next?1:0));}
            if(client.player==null){holdAttackEnabled[0]=false;holdAttackVeinEnabled[0]=false;return;}
            HammerRefill.tick(client);
            if(!holdAttackEnabled[0])return;
            if(client.gui.screen()!=null){client.options.keyAttack.setDown(false);return;}
            client.options.keyAttack.setDown(true);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cloudLeap.consumeClick()) {
                if (client.player != null && client.gui.screen() == null && !client.isPaused()
                        && ClientPlayNetworking.canSend(CloudLeapPayload.TYPE)) {
                    ClientPlayNetworking.send(new CloudLeapPayload(client.options.keyJump.isDown()));
                }
            }
        });
        ItemTooltipCallback.EVENT.register((stack,context,flag,lines) -> CATALOG.tooltip(stack,lines));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,YumisSkyIsland.id("coins"),(graphics,delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                    && CATALOG.state.has("bloodMoon") && CATALOG.state.get("bloodMoon").getAsBoolean())
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x28FF2020);
            if (!CATALOG.state.has("wallet")) return; String text = Money.format(CATALOG.wallet());
            int x = graphics.guiWidth()-mc.font.width(text)-12,y = graphics.guiHeight()-28;
            graphics.fill(x-6,y-4,graphics.guiWidth()-6,y+13,0x98000000); graphics.text(mc.font,text,x,y,0xFFFFDD74,true);
            if (CATALOG.state.has("partyId") && !CATALOG.state.get("partyId").getAsString().isEmpty()) {
                String island = "岛屿等级 " + CATALOG.level();
                int ix = graphics.guiWidth() - mc.font.width(island) - 12;
                graphics.fill(ix-6,y-24,graphics.guiWidth()-6,y-7,0x98000000);
                graphics.text(mc.font,island,ix,y-20,0xFFE6F5FF,true);
            }
            if (CATALOG.state.has("flightRemainingMs")) {
                long remaining = Math.max(0L,CATALOG.state.get("flightRemainingMs").getAsLong()-(System.nanoTime()-CATALOG.flightSnapshotClientTime)/1_000_000L);
                if (remaining > 0) {
                    long seconds = (remaining + 999) / 1000;
                    String flight = "飞行剩余 " + (seconds / 60) + ":" + String.format(java.util.Locale.ROOT,"%02d",seconds % 60);
                    graphics.text(mc.font, flight, (graphics.guiWidth()-mc.font.width(flight))/2, graphics.guiHeight()-69, 0xFF97E8FF, true);
                }
            }
            CATALOG.coinGains.render(graphics,y);
            CATALOG.events.render(graphics);
        });
    }
}
