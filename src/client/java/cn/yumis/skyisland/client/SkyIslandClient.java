package cn.yumis.skyisland.client;
import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.block.ModBlocks;
import cn.yumis.skyisland.core.Money;
import cn.yumis.skyisland.network.ShopPayloads;
import cn.yumis.skyisland.network.TaskPayloads;
import cn.yumis.skyisland.network.CloudLeapPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.blockentity.*;
public final class SkyIslandClient implements ClientModInitializer {
    public static ClientCatalog CATALOG;
    @Override public void onInitializeClient() {
        CATALOG = new ClientCatalog();
        BlockEntityRenderers.register(ModBlocks.SHIPPING_TYPE,ChestRenderer::new);
        var category = KeyMapping.Category.register(YumisSkyIsland.id("sky_island"));
        KeyMapping shop = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.shop",InputConstants.KEY_I,category));
        KeyMapping cloudLeap = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.cloud_leap",InputConstants.KEY_C,category));
        ClientPlayNetworking.registerGlobalReceiver(ShopPayloads.Snapshot.TYPE,(payload,context) -> CATALOG.receive(payload.json()));
        ClientPlayNetworking.registerGlobalReceiver(TaskPayloads.Snapshot.TYPE,(payload,context) -> CATALOG.receiveTasks(payload.json()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> CATALOG.clearConnection());
        ClientTickEvents.END_CLIENT_TICK.register(client -> { while (shop.consumeClick()) if (client.player != null && ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE)) ClientPlayNetworking.send(new ShopPayloads.Request("open","",0)); });
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
            if (!CATALOG.state.has("wallet")) return; Minecraft mc = Minecraft.getInstance(); String text = Money.format(CATALOG.wallet());
            int x = graphics.guiWidth()-mc.font.width(text)-12,y = graphics.guiHeight()-28;
            graphics.fill(x-6,y-4,graphics.guiWidth()-6,y+13,0x98000000); graphics.text(mc.font,text,x,y,0xFFFFDD74,true);
            CATALOG.coinGains.render(graphics,y);
            CATALOG.events.render(graphics);
        });
    }
}
