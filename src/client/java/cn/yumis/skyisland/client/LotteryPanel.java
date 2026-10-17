package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.LotteryNames;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.network.LotteryPayloads;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Inline single-pool view. Public snapshots contain no prize catalog or probabilities. */
public final class LotteryPanel {
    private static final int CONTENT=348,PITY_Y=86,BUTTON_Y=164,EXCHANGE_Y=206,CELL=72,ROW=24;
    private static final int GOLD=0xFFFFAA00;
    private final ClientCatalog model;
    private int scroll;
    public LotteryPanel(ClientCatalog model){this.model=model;}
    private JsonObject data(){return model.state.has("lottery")?model.state.getAsJsonObject("lottery"):new JsonObject();}
    private boolean flag(String key){return data().has(key)&&data().get(key).getAsBoolean();}
    private int number(String key,int fallback){return data().has(key)?data().get(key).getAsInt():fallback;}
    private int level(){var p=Minecraft.getInstance().player;return p==null?0:p.experienceLevel;}
    private int left(int width){return Math.max(14,(width-640)/2);}
    private int maxScroll(int height){return Math.max(0,CONTENT-Math.max(0,height-157));}
    private boolean available(String action){
        if(model.lotteryBusy||!flag("enabled"))return false;
        return switch(action){
            case "draw"->flag("canDraw")&&number("tickets",0)>0;
            case "draw_10"->flag("canDraw")&&number("tickets",0)>=10;
            case "exchange_xp"->level()>=number("experienceCost",30);
            default->data().has("tokens")&&data().get("tokens").getAsBigInteger().compareTo(BigInteger.valueOf(number("tokenCost",160)))>=0;
        };
    }
    public void render(GuiGraphicsExtractor g,Font font,int width,int height,int mx,int my){
        int x=left(width),w=width-x*2;scroll=Math.clamp(scroll,0,maxScroll(height));int y=111-scroll;
        String resources=LotteryNames.token()+" "+(data().has("tokens")?data().get("tokens").getAsString():"0")+" · "+LotteryNames.ticket()+" "+number("tickets",0)+" · 附魔等级 "+level();
        g.text(font,font.plainSubstrByWidth(resources,w),x,86,0xFFF1DFAC,false);
        g.enableScissor(x,111,width-x,height-46);
        g.fill(x,y,x+w,y+78,UiTheme.CARD);
        if(data().has("upStack")){
            var stack=model.decode(data().get("upStack"));g.item(stack,x+10,y+13);
            g.text(font,"今日UP",x+38,y+10,0xFFFFD789,false);
            g.text(font,font.plainSubstrByWidth(stack.getHoverName().getString(),w-48),x+38,y+29,0xFFFFC16F,false);
        }else g.text(font,"今日UP · 达到解锁等级后显示",x+10,y+17,0xFFCCCCCC,false);
        g.text(font,"最低传说等级 "+number("unlockLevel",75)+" · 当前岛等级 "+number("islandLevel",0),x+10,y+56,0xFFCCCCCC,false);
        int pityW=Math.max(1,w/2);
        g.fill(x,y+PITY_Y,x+pityW,y+PITY_Y+66,UiTheme.CARD);
        g.text(font,font.plainSubstrByWidth("距史诗保底 "+number("epicRemaining",10)+" 抽"+(flag("epicOwed")?" · 下次固定史诗":""),pityW-16),x+8,y+PITY_Y+10,0xFFB99AE9,false);
        g.text(font,font.plainSubstrByWidth("距传说保底 "+number("legendaryRemaining",50)+" 抽",pityW-16),x+8,y+PITY_Y+28,0xFFFFCE7F,false);
        g.text(font,font.plainSubstrByWidth(flag("featuredGuaranteed")?"下一件传说必为当时UP":"保底进度跟随个人保存",pityW-16),x+8,y+PITY_Y+46,0xFFCCCCCC,false);
        int buttonW=Math.max(40,(w-8)/2);
        button(g,font,x,y+BUTTON_Y,buttonW,model.lotteryBusy?"结算中…":LotteryNames.draw()+"1次",available("draw"),false);
        button(g,font,x+buttonW+8,y+BUTTON_Y,buttonW,model.lotteryBusy?"结算中…":LotteryNames.draw()+"10次",available("draw_10"),true);
        int cellX=x,cellY=y+EXCHANGE_Y;
        g.fill(cellX,cellY,cellX+CELL,cellY+CELL,UiTheme.CARD);
        ItemStack ticket=new ItemStack(ModItems.LOTTERY_TICKET);
        g.pose().pushMatrix();g.pose().translate(cellX+20,cellY+8);g.pose().scale(2f,2f);g.item(ticket,0,0);g.pose().popMatrix();
        g.text(font,font.plainSubstrByWidth(ticket.getHoverName().getString(),CELL-8),cellX+4,cellY+CELL-14,0xFFF6E7B2,false);
        int boxW=Math.min(w,280),boxY=cellY+CELL+6;
        g.fill(x,boxY,x+boxW,boxY+ROW*2,UiTheme.CARD);
        material(g,font,x,boxY,boxW,"exchange_xp",number("experienceCost",30)+"级经验",mx,my);
        material(g,font,x,boxY+ROW,boxW,"exchange_tokens",number("tokenCost",160)+LotteryNames.token(),mx,my);
        g.disableScissor();
        String footer="每次获得1件物品 · 点下方材料兑换";
        if(data().has("problem")&&!data().get("problem").getAsString().isBlank())footer=data().get("problem").getAsString();
        else if(!flag("canDraw"))footer="尚未解锁或奖池未就绪";
        else if(number("tickets",0)<=0)footer="需要1个"+LotteryNames.ticket();
        else if(!model.lastLotteryResult.isEmpty()&&model.lastLotteryResult.has("prizes")&&model.lastLotteryResult.get("prizes").isJsonArray()&&model.lastLotteryResult.getAsJsonArray("prizes").size()>1)
            footer="上次获得："+model.lastLotteryResult.getAsJsonArray("prizes").size()+" 件";
        else if(!model.lastLotteryResult.isEmpty()&&model.lastLotteryResult.has("rewardStack"))footer="上次获得："+model.decode(model.lastLotteryResult.get("rewardStack")).getHoverName().getString()+" ×1";
        g.text(font,font.plainSubstrByWidth(footer,w),x,height-27,0xFFBBBBBB,false);
    }
    private void material(GuiGraphicsExtractor g,Font font,int x,int y,int w,String action,String label,int mx,int my){
        boolean enabled=available(action),hot=enabled&&mx>=x&&mx<x+w&&my>=y&&my<y+ROW;
        if(hot)g.fill(x,y,x+w,y+ROW,UiTheme.HOVER);
        g.text(font,font.plainSubstrByWidth(label,w-16),x+8,y+8,enabled?0xFFF1F1F1:0xFF999999,false);
    }
    private void button(GuiGraphicsExtractor g,Font font,int x,int y,int w,String label,boolean enabled,boolean gold){
        g.fill(x,y,x+w,y+30,enabled?UiTheme.BUTTON:UiTheme.DISABLED);
        if(gold){g.fill(x,y,x+w,y+1,GOLD);g.fill(x,y+29,x+w,y+30,GOLD);g.fill(x,y,x+1,y+30,GOLD);g.fill(x+w-1,y,x+w,y+30,GOLD);}
        g.text(font,font.plainSubstrByWidth(label,w-16),x+8,y+11,enabled?0xFFF1F1F1:0xFF999999,false);
    }
    public boolean click(double mx,double my,int width,int height){
        if(my<111||my>=height-46)return false;
        int x=left(width),w=width-2*x;if(mx<x||mx>=x+w)return false;
        int buttonW=Math.max(40,(w-8)/2),top=111-scroll;
        if(hit(mx,my,x,top+BUTTON_Y,buttonW,30))return send("draw");
        if(hit(mx,my,x+buttonW+8,top+BUTTON_Y,buttonW,30))return send("draw_10");
        int boxW=Math.min(w,280),boxY=top+EXCHANGE_Y+CELL+6;
        if(hit(mx,my,x,boxY,boxW,ROW))return send("exchange_xp");
        if(hit(mx,my,x,boxY+ROW,boxW,ROW))return send("exchange_tokens");
        return false;
    }
    private boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private boolean send(String action){
        if(available(action)&&data().has("sequence")&&ClientPlayNetworking.canSend(LotteryPayloads.Operation.TYPE)){
            model.lotteryBusy=true;model.lotteryRequestSequence=data().get("sequence").getAsLong();model.lastError="";
            ClientPlayNetworking.send(new LotteryPayloads.Operation(action,model.lotteryRequestSequence,data().get("rulesRevision").getAsLong(),data().get("context").getAsString()));
        }
        return true;
    }
    public void scroll(double delta,int height){scroll=Math.clamp(scroll-(int)Math.signum(delta)*28,0,maxScroll(height));}
}
