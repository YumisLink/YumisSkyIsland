package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.LotteryNames;
import cn.yumis.skyisland.network.LotteryPayloads;
import com.google.gson.*;
import java.math.BigInteger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Inline single-pool view. Public snapshots contain no prize catalog or probabilities. */
public final class LotteryPanel {
    private final ClientCatalog model;
    private int scroll;
    public LotteryPanel(ClientCatalog model){this.model=model;}
    private JsonObject data(){return model.state.has("lottery")?model.state.getAsJsonObject("lottery"):new JsonObject();}
    private boolean flag(String key){return data().has(key)&&data().get(key).getAsBoolean();}
    private int number(String key,int fallback){return data().has(key)?data().get(key).getAsInt():fallback;}
    private int level(){var p=Minecraft.getInstance().player;return p==null?0:p.experienceLevel;}
    private int left(int width){return Math.max(14,(width-640)/2);}
    private int maxScroll(int height){return Math.max(0,288-Math.max(0,height-157));}
    private boolean available(String action){
        if(model.lotteryBusy||!flag("enabled"))return false;
        return switch(action){case "draw"->flag("canDraw")&&number("tickets",0)>0;case "exchange_xp"->level()>=number("experienceCost",30);
            default->data().has("tokens")&&data().get("tokens").getAsBigInteger().compareTo(BigInteger.valueOf(number("tokenCost",160)))>=0;};
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
        g.fill(x,y+86,x+w,y+152,UiTheme.CARD);
        g.text(font,"距史诗保底 "+number("epicRemaining",10)+" 抽"+(flag("epicOwed")?" · 下次固定史诗":""),x+10,y+96,0xFFB99AE9,false);
        g.text(font,"距传说保底 "+number("legendaryRemaining",50)+" 抽",x+10,y+114,0xFFFFCE7F,false);
        g.text(font,flag("featuredGuaranteed")?"下一件传说必为当时UP":"保底进度跟随个人保存",x+10,y+132,0xFFCCCCCC,false);
        String drawLabel=model.lotteryBusy?"结算中…":!flag("canDraw")?"尚未解锁或奖池未就绪":number("tickets",0)==0?"需要1个"+LotteryNames.ticket():LotteryNames.draw()+"一次 · 消耗1个"+LotteryNames.ticket();
        button(g,font,x,y+164,w,drawLabel,available("draw"));
        button(g,font,x,y+208,w,number("experienceCost",30)+"级经验 → 1个"+LotteryNames.ticket(),available("exchange_xp"));
        button(g,font,x,y+252,w,number("tokenCost",160)+LotteryNames.token()+" → 1个"+LotteryNames.ticket(),available("exchange_tokens"));
        g.disableScissor();
        String footer="每次获得1件物品 · 滚轮查看兑换";
        if(!model.lastLotteryResult.isEmpty()&&model.lastLotteryResult.has("rewardStack"))footer="上次获得："+model.decode(model.lastLotteryResult.get("rewardStack")).getHoverName().getString()+" ×1";
        if(data().has("problem")&&!data().get("problem").getAsString().isBlank())footer=data().get("problem").getAsString();
        g.text(font,font.plainSubstrByWidth(footer,w),x,height-27,0xFFBBBBBB,false);
    }
    private void button(GuiGraphicsExtractor g,Font font,int x,int y,int w,String label,boolean enabled){g.fill(x,y,x+w,y+30,enabled?UiTheme.BUTTON:UiTheme.DISABLED);g.text(font,font.plainSubstrByWidth(label,w-16),x+8,y+11,enabled?0xFFF1F1F1:0xFF999999,false);}
    public boolean click(double mx,double my,int width,int height){
        if(my<111||my>=height-46)return false;
        int x=left(width),w=width-2*x;if(mx<x||mx>=x+w)return false;
        String[] actions={"draw","exchange_xp","exchange_tokens"};int[] offsets={164,208,252};
        for(int i=0;i<actions.length;i++)if(my>=111-scroll+offsets[i]&&my<141-scroll+offsets[i]){
            if(available(actions[i])&&ClientPlayNetworking.canSend(LotteryPayloads.Operation.TYPE)){
                model.lotteryBusy=true;model.lotteryRequestSequence=data().get("sequence").getAsLong();model.lastError="";
                ClientPlayNetworking.send(new LotteryPayloads.Operation(actions[i],model.lotteryRequestSequence,data().get("rulesRevision").getAsLong(),data().get("context").getAsString()));
            }return true;
        }return false;
    }
    public void scroll(double delta,int height){scroll=Math.clamp(scroll-(int)Math.signum(delta)*28,0,maxScroll(height));}
}
