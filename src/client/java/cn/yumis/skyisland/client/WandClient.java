package cn.yumis.skyisland.client;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.RarityColors;
import cn.yumis.skyisland.network.WandPayloads;
import cn.yumis.skyisland.wand.*;
import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.*;

public final class WandClient {
    private static JsonObject snapshot=new JsonObject();
    private static List<BlockPos> preview=List.of(),undo=List.of();
    private static long clientTicks,lastUse=-10,lastSync=-20,contextRevision=-1,revision=-1;
    private static net.minecraft.client.player.LocalPlayer observedPlayer;
    private static String observedDimension="",snapshotDimension="";
    private static Item observedItem;
    private static int observedSlot=-1,observedHand=-1;
    private static boolean needsSync;
    static WandOptions options=WandOptions.DEFAULT;
    private static KeyMapping settings;
    private WandClient() {}
    public static void register(KeyMapping.Category category) {
        settings=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yumis_sky_island.wand_settings",InputConstants.KEY_V,category));
        ClientPlayNetworking.registerGlobalReceiver(WandPayloads.Snapshot.TYPE,(payload,context)->{
            observe(context.client());
            if(!payload.dimension().equals(observedDimension)||payload.contextRevision()<contextRevision||payload.revision()<=revision)return;
            snapshotDimension=payload.dimension();contextRevision=payload.contextRevision();revision=payload.revision();needsSync=false;
            snapshot=JsonParser.parseString(payload.json()).getAsJsonObject();
            preview=positions("positions");undo=positions("undo");
            if(snapshot.has("options"))options=cn.yumis.skyisland.config.GameConfig.JSON.fromJson(snapshot.get("options"),WandOptions.class);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{
            snapshot=new JsonObject();preview=List.of();undo=List.of();options=WandOptions.DEFAULT;
            clientTicks=0;lastUse=-10;lastSync=-20;contextRevision=-1;revision=-1;observedPlayer=null;
            observedDimension="";snapshotDimension="";observedItem=null;observedSlot=-1;observedHand=-1;needsSync=false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            clientTicks++;observe(client);
            if(needsSync&&client.player!=null&&!ConstructionWands.held(client.player).isEmpty()&&clientTicks-lastSync>=20)send(3);
            while(settings.consumeClick())if(client.player!=null&&client.gui.screen()==null&&!ConstructionWands.held(client.player).isEmpty())open();
        });
        ItemTooltipCallback.EVENT.register((stack,context,flag,lines)->tooltip(stack,lines));
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null||mc.gui.screen()!=null||ConstructionWands.held(mc.player).isEmpty()||!active()
                    ||context.levelState().cameraRenderState==null)return;
            boolean reversing=mc.player.isShiftKeyDown()&&control();List<BlockPos> positions=reversing?undo:preview;
            if(positions.isEmpty())return;
            Vec3 camera=context.levelState().cameraRenderState.pos;if(camera==null)return;
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),RenderTypes.linesTranslucent(),(pose,vertices)->{
                for(BlockPos pos:positions)for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++) {
                    float x=(float)(pos.getX()-camera.x)+(axis==0?0:a),y=(float)(pos.getY()-camera.y)+(axis==1?0:axis==0?a:b),z=(float)(pos.getZ()-camera.z)+(axis==2?0:b);
                    float dx=axis==0?1:0,dy=axis==1?1:0,dz=axis==2?1:0;
                    vertices.addVertex(pose,x,y,z).setColor(reversing?110:115,reversing?245:195,reversing?150:255,220).setNormal(pose,dx,dy,dz).setLineWidth(1.5f);
                    vertices.addVertex(pose,x+dx,y+dy,z+dz).setColor(reversing?110:115,reversing?245:195,reversing?150:255,220).setNormal(pose,dx,dy,dz).setLineWidth(1.5f);
                }
            });
        });
    }
    private static List<BlockPos> positions(String key) {
        if(!snapshot.has(key))return List.of();List<BlockPos> positions=new ArrayList<>();
        for(JsonElement entry:snapshot.getAsJsonArray(key)){var xyz=entry.getAsJsonArray();positions.add(new BlockPos(xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt()));}
        return List.copyOf(positions);
    }
    private static void observe(Minecraft mc) {
        var player=mc.player;String dimension=player==null?"":player.level().dimension().identifier().toString();
        ItemStack held=player==null?ItemStack.EMPTY:ConstructionWands.held(player);
        int hand=held.isEmpty()?-1:held==player.getMainHandItem()?0:1,slot=hand==0?player.getInventory().getSelectedSlot():-1;
        if(observedPlayer!=player||!observedDimension.equals(dimension)||observedItem!=held.getItem()||observedSlot!=slot||observedHand!=hand) {
            observedPlayer=player;observedDimension=dimension;observedItem=held.getItem();observedSlot=slot;observedHand=hand;
            snapshot=new JsonObject();preview=List.of();undo=List.of();snapshotDimension="";needsSync=true;lastSync=clientTicks-20;
            options=held.isEmpty()?WandOptions.DEFAULT:WandOptions.read(held);
        }
    }
    static boolean active(){
        Minecraft mc=Minecraft.getInstance();
        return mc.player==observedPlayer&&mc.player!=null&&snapshotDimension.equals(mc.player.level().dimension().identifier().toString())
                &&snapshot.has("active")&&snapshot.get("active").getAsBoolean();
    }
    private static boolean control(){var mc=Minecraft.getInstance();return InputConstants.isKeyDown(mc.getWindow(),InputConstants.KEY_LCONTROL);}
    private static void open(){Minecraft.getInstance().gui.setScreen(new WandSettingsScreen());}
    public static boolean use() {
        Minecraft mc=Minecraft.getInstance();
        observe(mc);
        if(mc.player==null||mc.gui.screen()!=null||ConstructionWands.held(mc.player).isEmpty())return false;
        if(mc.player.isShiftKeyDown()&&!control()&&(mc.hitResult==null||mc.hitResult.getType()==HitResult.Type.MISS)){open();return true;}
        if(!active()) {if(clientTicks-lastSync>=4)send(3);mc.player.sendOverlayMessage(Component.literal("建造杖仅在空岛世界使用，等待服务端规则同步"));return true;}
        if(clientTicks-lastUse<4)return true;lastUse=clientTicks;
        if(mc.player.isShiftKeyDown()&&control())send(1);
        else if(mc.hitResult!=null&&mc.hitResult.getType()==HitResult.Type.BLOCK)send(0);
        return true;
    }
    public static boolean scroll(double amount) {
        var mc=Minecraft.getInstance();if(amount==0||mc.player==null||mc.gui.screen()!=null||!mc.player.isShiftKeyDown()||ConstructionWands.held(mc.player).isEmpty())return false;
        options=new WandOptions(Math.floorMod(options.direction()+(amount>0?1:-1),5),options.matching(),options.copyFacing(),options.random());
        send(2);mc.player.sendOverlayMessage(Component.literal("建造方向："+WandOptions.DIRECTIONS[options.direction()]));return true;
    }
    static void send(int action) {
        var mc=Minecraft.getInstance();if(mc.player==null||!ClientPlayNetworking.canSend(WandPayloads.Request.TYPE))return;
        if(action!=3&&!active()){if(clientTicks-lastSync>=4)send(3);return;}
        if(action==3)lastSync=clientTicks;
        ClientPlayNetworking.send(new WandPayloads.Request(action,options.direction(),options.matching(),options.copyFacing(),options.random(),
                mc.player.level().dimension().identifier().toString(),contextRevision));
    }
    private static void tooltip(ItemStack stack,List<Component> lines) {
        int tier=ConstructionWands.specialTier(stack);if(tier<0)return;
        int color=RarityColors.color(SkyIslandClient.CATALOG.rarity(stack))&0xFFFFFF;
        if(!lines.isEmpty())lines.set(0,lines.getFirst().copy().withStyle(s->s.withColor(color)));
        if(SkyIslandClient.CATALOG.products.isEmpty()&&SkyIslandClient.CATALOG.itemRarities.isEmpty())
            lines.add(Component.literal("种类稀有度："+RarityColors.name(ConstructionWands.rarity(tier))).withStyle(s->s.withColor(color)));
        if(ConstructionWands.isWand(stack)) {
            int cap=snapshot.has("limits")?snapshot.getAsJsonArray("limits").get(tier).getAsInt():ConstructionWands.TIERS[tier];
            lines.add(Component.literal("每次最多新放置 "+cap+" 块 · 无耐久 · 仅空岛").withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal("右键表面扩建；副手方块优先指定材料").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Shift＋滚轮切方向；"+settings.getTranslatedKeyMessage().getString()+"／Shift＋右键空气设置").withStyle(ChatFormatting.GRAY));
            int depth=snapshot.has("undoDepth")?snapshot.get("undoDepth").getAsInt():3;
            lines.add(Component.literal("Shift＋左Ctrl＋右键撤销（靠近上次位置3格内）").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("保留最近"+depth+"次，断线清空；变化位置跳过，满包掉脚边").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal("消耗背包／随身潜影盒／收纳袋的普通建材").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("跳过品质、命名、附魔及带内容材料；仅单格非容器").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal("床、门、双高植物不可用；双台阶扣2，雪按层扣").withStyle(ChatFormatting.DARK_GRAY));
            WandOptions o=WandOptions.read(stack);
            lines.add(Component.literal("方向："+WandOptions.DIRECTIONS[o.direction()]+" · 匹配："+WandOptions.MATCHES[o.matching()]).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(tier==0?"常驻商店购买；不可制作或出售":"仅由上一档杖＋对应升级图纸锻造获得").withStyle(ChatFormatting.YELLOW));
            if(tier<5)lines.add(Component.literal("下一档：锻造台装备槽放杖，材料槽放图纸；模板槽留空").withStyle(ChatFormatting.GRAY));
        }else {
            lines.add(Component.literal("将 "+ConstructionWands.TIERS[tier-1]+" 档杖升级为 "+ConstructionWands.TIERS[tier]+" 档").withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal("锻造台：模板槽留空，装备槽放原杖，材料槽放本图纸").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("消耗原杖与1张图纸；不可跳级，保留杖设置及名称").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("每日／隐藏商店供货；不可制作或出售").withStyle(ChatFormatting.YELLOW));
        }
    }
}
