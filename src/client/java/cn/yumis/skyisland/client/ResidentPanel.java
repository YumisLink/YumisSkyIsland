package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.ResidentRules;
import cn.yumis.skyisland.network.ResidentPayloads;
import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.*;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/** All settlement actions stay in the same control-panel tab, including the account field. */
public final class ResidentPanel {
    private record Hit(int x,int y,int width,int height,Runnable action){}
    private final ControlPanelScreen parent;
    private final List<Hit> hits=new ArrayList<>();
    private String selected="",account="";
    private boolean focused;
    private int scroll,total,fieldX,fieldY,fieldWidth;
    public ResidentPanel(ControlPanelScreen parent){this.parent=parent;}
    public void select(String id){selected=id;focused=false;account="";scroll=0;}
    private JsonObject data(){return parent.model().residents();}
    private JsonArray rows(String name){return data().has(name)?data().getAsJsonArray(name):new JsonArray();}
    private static String value(JsonObject row,String name){return row.has(name)?row.get(name).getAsString():"";}
    private String stacks(JsonArray rows){List<String> names=new ArrayList<>();for(var value:rows){var row=value.getAsJsonObject();String item=row.get("item").getAsString();names.add((item.equals("#planks")?"任意木板":parent.model().productName(item))+"×"+row.get("count").getAsInt());}return String.join("、",names);}
    private void line(GuiGraphicsExtractor graphics,String text,int x,int y,int color,int width){parent.textLine(graphics,text,x,y,color,width);}
    private void button(GuiGraphicsExtractor graphics,String label,int x,int y,int width,boolean enabled,Runnable action){parent.actionButton(graphics,label,x,y,width,enabled);if(enabled&&y+22>70&&y<parent.height-54)hits.add(new Hit(x,y,width,22,action));}
    private void request(String action,JsonObject body){var state=parent.model().state;if(!state.has("partyId")||state.get("partyId").getAsString().isBlank())return;body.addProperty("action",action);body.addProperty("party",state.get("partyId").getAsString());body.addProperty("islandEpoch",state.get("islandEpoch").getAsLong());body.addProperty("revision",parent.model().residentRevision());if(parent.model().sync.ready()&&ClientPlayNetworking.canSend(ResidentPayloads.Request.TYPE))ClientPlayNetworking.send(new ResidentPayloads.Request(body.toString()));}
    private void action(String action){request(action,new JsonObject());}
    private void residentAction(String action,String resident){JsonObject body=new JsonObject();body.addProperty("resident",resident);request(action,body);}
    public void render(GuiGraphicsExtractor graphics,int mx,int my){
        hits.clear();fieldY=-1000;int width=parent.width-32,x=16;var model=parent.model();var npc=data();if(!npc.has("capacity")){line(graphics,"请先创建或加入空岛队伍",x,90,0xFFD3D5D7,width);return;}
        graphics.fill(8,68,parent.width-8,parent.height-52,UiTheme.PANEL);graphics.enableScissor(10,70,parent.width-10,parent.height-54);int y=78-scroll;
        line(graphics,"村落声望 "+npc.get("reputation").getAsLong()+" · 房屋 "+rows("houses").size()+"/"+npc.get("capacity").getAsInt()+" · 居民 "+rows("residents").size(),x,y,0xFFBAE5D9,width);y+=20;
        boolean first=!npc.get("starterClaimed").getAsBoolean();button(graphics,first?"领取免费许可证":"首张许可证已领取",x,y,140,first,()->action("starter"));y+=28;line(graphics,"每日04:00空房入住率 "+npc.get("arrivalBps").getAsInt()/100.0+"% · 目标职业占 "+npc.get("targetBps").getAsInt()/100.0+"%",x,y,0xFFBBC2CB,width);y+=24;
        if(npc.has("draft")){
            JsonObject draft=npc.getAsJsonObject("draft");line(graphics,draft.get("editing").getAsBoolean()?"房屋补登记草稿":"新房屋登记草稿",x,y,0xFFFFDE94,width);y+=18;
            line(graphics,"已标记 "+draft.get("markers").getAsInt()+"格 · 床 "+yes(draft,"bed")+" · 消耗箱 "+yes(draft,"supply")+" · 产出箱 "+yes(draft,"output"),x,y,0xFFD3D5D7,width);y+=18;
            line(graphics,"目标："+ResidentRules.name(value(draft,"target"))+"；点击已解锁职业切换",x,y,0xFFAEDBCC,width);y+=18;
            int col=0,columns=Math.max(2,width/104);for(var element:rows("jobs")){var job=element.getAsJsonObject();int bx=x+col%columns*104,by=y+col/columns*27;String id=value(job,"id");button(graphics,(id.equals(value(draft,"target"))?"✓ ":"")+value(job,"name"),bx,by,100,job.get("unlocked").getAsBoolean(),()->{JsonObject body=new JsonObject();body.addProperty("target",id);request("draft_target",body);});col++;}y+=(col+columns-1)/columns*27+5;
            button(graphics,"下一箱：消耗",x,y,100,true,()->box("supply"));button(graphics,"下一箱：产出",x+106,y,100,true,()->box("output"));y+=28;
            button(graphics,"在屋内提交登记",x,y,120,true,()->action("draft_submit"));button(graphics,"取消草稿",x+126,y,90,true,()->action("draft_cancel"));button(graphics,"返回世界标记",x+222,y,Math.min(112,Math.max(64,width-222)),true,parent::onClose);y+=36;
        }else{line(graphics,"手持许可证逐一右键设施，回屋内Shift＋右键空气提交。",x,y,0xFFAEB8C3,width);y+=26;}
        JsonObject chosen=null;for(var element:rows("residents")){JsonObject row=element.getAsJsonObject();if(value(row,"id").equals(selected))chosen=row;}
        if(chosen!=null){String id=value(chosen,"id");line(graphics,"管理："+ResidentRules.name(value(chosen,"job"))+" · "+(value(chosen,"account").isBlank()?"未绑定账号":value(chosen,"account")),x,y,0xFF9DDDE1,width);y+=20;
            fieldX=x;fieldY=y;fieldWidth=Math.max(110,Math.min(190,width-110));graphics.fill(x,y,x+fieldWidth,y+22,focused?UiTheme.SELECTED:UiTheme.CARD);line(graphics,account.isBlank()&&!focused?"输入正版账号名":account+(focused?"_":""),x+5,y+7,0xFFE2E6ED,fieldWidth-10);
            button(graphics,"绑定皮肤",x+fieldWidth+6,y,90,!account.isBlank(),()->{JsonObject body=new JsonObject();body.addProperty("resident",id);body.addProperty("account",account);request("skin",body);focused=false;});y+=28;
            button(graphics,"刷新原账号皮肤",x,y,130,!value(chosen,"account").isBlank(),()->residentAction("skin_refresh",id));button(graphics,"驱逐居民",x+136,y,90,true,()->parent.confirm("驱逐该居民？房屋和箱内物品会保留。",()->residentAction("evict",id)));y+=30;
            var care=npc.getAsJsonObject("care").getAsJsonObject(value(chosen,"job"));y+=parent.wrappedText(graphics,"每日消耗：一餐食物＋"+stacks(care.getAsJsonArray("supplies")),x,y,0xFFB7C7CC,width)+6;y+=parent.wrappedText(graphics,"产出："+(care.get("random").getAsBoolean()?"随机一项 ":"")+stacks(care.getAsJsonArray("outputs")),x,y,0xFFB7C7CC,width)+6;if(care.get("extraEvery").getAsInt()>0){line(graphics,"每成功生产"+care.get("extraEvery").getAsInt()+"批追加 "+stacks(care.getAsJsonArray("extra")),x,y,0xFFB7C7CC,width);y+=18;}
            line(graphics,"迁居：在下方空房卡片点击“迁入此房”。",x,y,0xFFB7C7CC,width);y+=28;
        }
        line(graphics,"居民 · 同类专家增益不叠，普通居民逐人＋1%售价",x,y,0xFFE6C77D,width);y+=20;
        for(var element:rows("residents")){JsonObject row=element.getAsJsonObject();String id=value(row,"id"),name=value(row,"account");graphics.fill(x,y,x+width,y+65,id.equals(selected)?UiTheme.SELECTED:UiTheme.CARD);line(graphics,ResidentRules.name(value(row,"job"))+" · "+(name.isBlank()?"未绑定账号":name),x+6,y+7,row.get("active").getAsBoolean()?0xFF9AE1B6:0xFFE1E4E9,width-95);line(graphics,value(row,"message"),x+6,y+24,0xFFB3BEC8,width-95);line(graphics,"累计产出批次 "+row.get("batches").getAsLong(),x+6,y+43,0xFF9EB4BC,width-95);button(graphics,"管理",x+width-76,y+22,70,true,()->{selected=id;account=name;focused=false;scroll=0;});y+=72;}
        line(graphics,"房屋 · 两箱独占；破坏关键设施或漏口会取消登记",x,y,0xFFE6C77D,width);y+=20;
        for(var element:rows("houses")){JsonObject row=element.getAsJsonObject();String id=value(row,"id");graphics.fill(x,y,x+width,y+79,UiTheme.CARD);line(graphics,"房屋 "+id.substring(0,8)+" · "+row.get("air").getAsInt()+"格空气 · 目标"+ResidentRules.name(value(row,"target")),x+6,y+7,0xFFDCE2E9,width-12);line(graphics,houseStatus(value(row,"status"))+" · 床 "+BlockPos.of(row.get("bed").getAsLong()).toShortString(),x+6,y+24,0xFFAEC2C7,width-12);button(graphics,"补登记",x+6,y+47,86,true,()->{JsonObject body=new JsonObject();body.addProperty("house",id);request("house_edit",body);});if(chosen!=null&&!row.get("occupied").getAsBoolean())button(graphics,"迁入此房",x+98,y+47,92,value(row,"status").equals("valid"),()->{JsonObject body=new JsonObject();body.addProperty("house",id);body.addProperty("resident",selected);request("relocate",body);});y+=86;}
        line(graphics,"居民委托 · 每日最多"+npc.get("commissionLimit").getAsInt()+"份 · 交付＋"+npc.get("commissionReward").getAsInt()+"声望",x,y,0xFFE6C77D,width);y+=20;
        for(var element:rows("commissions")){var row=element.getAsJsonObject();boolean complete=row.get("completed").getAsBoolean();String id=value(row,"id");graphics.fill(x,y,x+width,y+53,UiTheme.CARD);line(graphics,model.productName(value(row,"item"))+" ×"+row.get("count").getAsInt(),x+6,y+8,0xFFE2E7EB,width-98);line(graphics,complete?"已完成":"次日过期；提交时扣除材料",x+6,y+29,0xFFB7C5CF,width-98);button(graphics,complete?"已完成":"交付",x+width-80,y+15,74,!complete,()->{JsonObject body=new JsonObject();body.addProperty("commission",id);request("commission",body);});y+=60;}
        line(graphics,"工作站与解锁",x,y,0xFFE6C77D,width);y+=20;
        for(var element:rows("jobs")){var row=element.getAsJsonObject();line(graphics,value(row,"name")+"："+model.productName(value(row,"station"))+" · 岛"+row.get("level").getAsInt()+"级／声望"+row.get("reputation").getAsLong(),x,y,row.get("unlocked").getAsBoolean()?0xFFAFD9C4:0xFF8C969F,width);y+=17;}
        total=y+scroll-78;graphics.disableScissor();scroll=Math.clamp(scroll,0,Math.max(0,total-(parent.height-138)));line(graphics,"滚轮浏览 · 房屋"+npc.get("minAir").getAsInt()+"～"+npc.get("maxAir").getAsInt()+"空气格 · 正式队员可管理",16,parent.height-24,0xFF9CAFB9,parent.width-32);
    }
    private static String yes(JsonObject row,String key){return row.get(key).getAsBoolean()?"✓":"待登记";}
    private static String houseStatus(String status){return switch(status){case "valid"->"有效";case "paused"->"暂停：需补登记或点灯";case "unloaded"->"区块未加载";default->"正在复核";};}
    private void box(String role){JsonObject body=new JsonObject();body.addProperty("role",role);request("draft_box",body);}
    public boolean clicked(MouseButtonEvent event){if(event.y()<70||event.y()>=parent.height-54)return false;focused=ControlPanelScreen.hit(event.x(),event.y(),fieldX,fieldY,fieldWidth,22);if(focused)return true;for(Hit hit:hits)if(ControlPanelScreen.hit(event.x(),event.y(),hit.x,hit.y,hit.width,hit.height)){hit.action.run();return true;}return true;}
    public boolean scrolled(double amount){scroll=Math.clamp(scroll-(int)(amount*34),0,Math.max(0,total-(parent.height-138)));return true;}
    public boolean key(KeyEvent event){if(!focused)return false;if(event.key()==GLFW.GLFW_KEY_ESCAPE){focused=false;return true;}if(event.key()==GLFW.GLFW_KEY_BACKSPACE&&!account.isEmpty())account=account.substring(0,account.length()-1);return true;}
    public boolean typed(CharacterEvent event){if(!focused)return false;String c=event.codepointAsString();if(account.length()<16&&c.matches("[A-Za-z0-9_]"))account+=c;return true;}
}
