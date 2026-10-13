package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.network.ShopPayloads;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/** I menu task hub: the next team quest and a scrollable exchange board. */
public final class TaskScreen extends Screen {
    private final ClientCatalog model;
    private int mode,scroll;
    private String action="",id="",confirmation="";
    private String confirmationParty="";
    private int count;
    public TaskScreen(ClientCatalog model){super(Component.literal("任务"));this.model=model;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){}
    private JsonObject data(){return model.state.has("progression")?model.state.getAsJsonObject("progression"):new JsonObject();}
    private static boolean inside(double x,double y,int a,int b,int w,int h){return x>=a&&x<a+w&&y>=b&&y<b+h;}
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int c,int w){g.text(font,font.plainSubstrByWidth(s,Math.max(0,w)),x,y,c,false);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int w){g.fill(x,y,x+w,y+22,0xCC354D51);text(g,label,x+5,y+7,0xFFFFFFFF,w-10);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
        g.fill(0,0,width,height,0xFF101419);text(g,"全队任务 · 最高岛等级 "+model.level()+" · "+Money.format(model.wallet()),12,12,0xFFFFFFFF,width-24);
        button(g,"成长任务",12,32,86);button(g,"悬赏",102,32,65);button(g,"返回商店",width-98,32,86);
        var data=data();
        if(data.entrySet().isEmpty()){text(g,"请先输入 /is 创建或加入空岛",12,72,0xFFBBC6CE,width-24);}
        else if(mode==0){
            if(!data.has("quest")||data.get("quest").isJsonNull())text(g,"全部已配置任务已完成",12,72,0xFF9DE5C7,width-24);
            else{
                var q=data.getAsJsonObject("quest");int y=70;
                text(g,q.get("name").getAsString(),12,y,0xFFFFDC78,width-24);y+=17;
                for(var line:font.split(Component.literal(q.get("description").getAsString()),width-24)){g.text(font,line,12,y,0xFFDDE6EA,false);y+=12;}
                for(var requirement:q.getAsJsonArray("requirements")){
                    var r=requirement.getAsJsonObject();var template=model.decode(r.get("stack"));int have=0;
                    if(Minecraft.getInstance().player!=null)for(int i=0;i<36;i++){var stack=Minecraft.getInstance().player.getInventory().getItem(i);if(cn.yumis.skyisland.shop.ItemEconomy.safeToSell(stack)&&cn.yumis.skyisland.shop.ItemEconomy.matchesCanonical(stack,template,r.getAsJsonObject("canonical").getAsJsonObject("componentPatch")))have+=stack.getCount();}
                    text(g,"提交 "+r.get("name").getAsString()+" ×"+r.get("quantity").getAsInt()+"（背包 "+have+"）",12,y,RarityColors.color(model.rarity(template)),width-24);y+=14;
                }
                for(var e:q.getAsJsonArray("rewardItems")){var stack=model.decode(e);g.item(stack,12,y);text(g,"奖励："+stack.getHoverName().getString()+" ×"+stack.getCount(),34,y+5,RarityColors.color(model.rarity(stack)),width-48);y+=24;}
                if(q.get("rewardCoins").getAsLong()>0){text(g,"奖励："+Money.format(q.get("rewardCoins").getAsLong()),12,y,0xFFFFDC78,width-24);y+=14;}
                for(var feature:q.getAsJsonArray("unlockFeatures")){text(g,"解锁："+featureName(feature.getAsString()),12,y,0xFF9DE5C7,width-24);y+=14;}
                button(g,"提交材料并领取奖励",12,height-34,155);
            }
        }else if(data.has("offers")){
            text(g,"格数 "+data.get("slots").getAsInt()+"/"+data.get("maximumSlots").getAsInt()+" · 每10级免费1格",12,63,0xFF9DE5C7,width-24);
            button(g,data.get("slots").getAsInt()>=data.get("maximumSlots").getAsInt()?"格数已满":"购格 "+Money.format(data.get("slotCost").getAsLong()),12,77,Math.min(230,width-24));
            var offers=data.getAsJsonArray("offers");int visible=Math.max(1,(height-140)/55);scroll=Math.clamp(scroll,0,Math.max(0,offers.size()-visible));
            for(int i=scroll;i<Math.min(offers.size(),scroll+visible);i++){
                var b=offers.get(i).getAsJsonObject();int y=105+(i-scroll)*55;g.fill(10,y,width-10,y+51,0xBB252B32);
                if(!b.has("material")||b.get("material").isJsonNull()){text(g,"格 "+(i+1)+" · 暂无合格悬赏；请先卖出普通材料",16,y+18,0xFFBBC6CE,width-32);continue;}
                var stack=model.decode(b.get("rewardStack"));g.item(stack,16,y+5);
                text(g,b.get("rewardName").getAsString()+" ×1",39,y+7,RarityColors.color(model.rarity(stack)),width-128);
                text(g,"提交 "+b.get("materialName").getAsString()+" ×"+b.get("quantity").getAsInt(),16,y+24,0xFFDDE6EA,width-130);
                long ticks=Math.max(0,b.get("expiresAt").getAsLong()-model.state.get("effectiveTicks").getAsLong());
                text(g,"更替 "+ticks/24000+"日 "+ticks%24000/20/60+"分（报价已锁定）",16,y+38,0xFFADB9C3,width-130);button(g,"兑换",width-83,y+14,60);
            }
            if(!data.get("bountyAllowed").getAsBoolean())text(g,"先完成悬赏功能解锁任务",12,height-24,0xFFFF8888,width-24);
            else if(!data.get("specialAllowed").getAsBoolean())text(g,"先完成每日/隐藏物资解锁任务",12,height-24,0xFFFF8888,width-24);
        }
        if(!model.lastError.isEmpty())text(g,model.lastError,12,height-49,0xFFFF8888,width-24);
        if(!action.isEmpty()){
            int w=Math.min(360,width-24),h=104,x=(width-w)/2,y=(height-h)/2;g.fill(0,0,width,height,0xAA000000);g.fill(x,y,x+w,y+h,0xFF1C2930);
            int yy=y+10;for(var line:font.split(Component.literal(confirmation),w-20)){g.text(font,line,x+10,yy,0xFFDDE6EA,false);yy+=12;}
            button(g,"确认",x+10,y+h-30,80);button(g,"取消",x+w-90,y+h-30,80);
        }
    }
    private static String featureName(String key){return switch(key){case "bounty.use"->"悬赏";case "shop.special"->"每日/隐藏商店";case "automation.shipping"->"出货箱";default->key;};}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        double x=e.x(),y=e.y();var data=data();
        if(!action.isEmpty()){int w=Math.min(360,width-24),dx=(width-w)/2,dy=(height-104)/2;if(inside(x,y,dx+10,dy+74,80,22)){model.lastError="";ClientPlayNetworking.send(new ShopPayloads.Request(action,confirmationParty+"|"+id,count));action="";}else if(inside(x,y,dx+w-90,dy+74,80,22))action="";return true;}
        confirmationParty=model.state.has("partyId")?model.state.get("partyId").getAsString():"";
        if(inside(x,y,12,32,86,22)){mode=0;return true;}if(inside(x,y,102,32,65,22)){mode=1;return true;}if(inside(x,y,width-98,32,86,22)){onClose();return true;}
        if(mode==0&&data.has("quest")&&!data.get("quest").isJsonNull()&&inside(x,y,12,height-34,155,22)){var q=data.getAsJsonObject("quest");action="quest_claim";id=q.get("id").getAsString()+"@"+model.state.get("islandEpoch").getAsLong();count=0;confirmation="确认提交任务材料并领取："+q.get("description").getAsString();return true;}
        if(mode==1&&data.has("offers")){
            if(inside(x,y,12,77,Math.min(230,width-24),22)&&data.get("slots").getAsInt()<data.get("maximumSlots").getAsInt()){action="bounty_slot";id=Long.toString(model.state.get("islandEpoch").getAsLong());count=data.get("paidSlots").getAsInt();confirmation="确认花费 "+Money.format(data.get("slotCost").getAsLong())+"，为全队增加1个永久悬赏格？重开会重置。";return true;}
            var offers=data.getAsJsonArray("offers");int visible=Math.max(1,(height-140)/55);
            for(int i=scroll;i<Math.min(offers.size(),scroll+visible);i++)if(inside(x,y,width-83,119+(i-scroll)*55,60,22)){var b=offers.get(i).getAsJsonObject();if(!b.has("material")||b.get("material").isJsonNull())return true;action="bounty_claim";id=b.get("slot").getAsInt()+":"+b.get("generation").getAsLong()+":"+model.state.get("islandEpoch").getAsLong();count=0;confirmation="提交 "+b.get("materialName").getAsString()+" ×"+b.get("quantity").getAsInt()+"，获得 "+b.get("rewardName").getAsString()+" ×1。完成后立刻生成新悬赏。";return true;}
        }
        return super.mouseClicked(e,twice);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(action.isEmpty())scroll-=Math.signum(sy);return true;}
    @Override public void onClose(){if(!action.isEmpty()){action="";return;}Minecraft.getInstance().gui.setScreen(new ShopScreen(model));}
}
