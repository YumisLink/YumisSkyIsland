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
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Server quotes are bound to the exact selection shown next to the real inventory. */
public final class SalePanel {
    private final ControlPanelScreen parent;
    private net.minecraft.client.gui.Font font;
    private int width,height;
    private final ClientCatalog model;
    private final Map<Integer,Integer> selected=new TreeMap<>();
    private int ticks, lastSent=-10, scroll, focused=-1;
    private boolean dirty, confirming;
    public SalePanel(ControlPanelScreen parent){this.parent=parent;this.model=parent.model();}
    private String selection(){var values=new ArrayList<String>();selected.forEach((s,n)->{if(n>0)values.add(s+":"+n);});return String.join(",",values);}
    private void send(String action,String product){ClientPlayNetworking.send(new ShopPayloads.Request(action,product,0));}
    public void tick(){ticks++;if(dirty&&ticks-lastSent>=5){model.lastError="";send("sale_preview",selection());dirty=false;lastSent=ticks;}}
    public void updated(){confirming=false;if(model.sale.get("selection").getAsString().isEmpty()&&!selection().isEmpty()){selected.clear();focused=-1;dirty=false;}}
    public void failed(){confirming=false;dirty=false;}
    private int cell(){return Math.max(20,Math.min(38,Math.min((width-24)/9,(height-190)/4)));}
    private int left(){return (width-9*cell())/2;}
    private int bottom(){return 39+4*cell();}
    private static boolean inside(double x,double y,int a,int b,int w,int h){return x>=a&&x<a+w&&y>=b&&y<b+h;}
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int color,int max){g.text(font,font.plainSubstrByWidth(s,Math.max(0,max)),x,y,color,false);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int w,boolean active){g.fill(x,y,x+w,y+22,active?UiTheme.BUTTON:UiTheme.DISABLED);text(g,label,x+5,y+7,active?0xFFFFFFFF:0xFF888888,w-10);}
    public void render(GuiGraphicsExtractor g,int mx,int my,float d){
        font=parent.uiFont();width=parent.contentWidth();height=parent.height-70;
        g.enableScissor(parent.contentLeft(),70,parent.width-8,parent.height-5);
        g.pose().pushMatrix();g.pose().translate(parent.contentLeft(),70);
        draw(g,mx-parent.contentLeft(),my-70,d);
        g.pose().popMatrix();g.disableScissor();
    }
    private void draw(GuiGraphicsExtractor g,int mx,int my,float d){
        g.fill(0,0,width,height,UiTheme.PANEL);
        text(g,model.sale.has("hand")&&model.sale.get("hand").getAsBoolean()?"出售手持 · 请先选择物品":"出售背包 · 请先选择物品",12,12,0xFFFFFFFF,width-24);
        ItemStack hovered=ItemStack.EMPTY;
        if(model.sale.has("inventory"))for(var e:model.sale.getAsJsonArray("inventory")){
            var row=e.getAsJsonObject();int slot=row.get("slot").getAsInt(),x=left()+slot%9*cell(),y=35+slot/9*cell();
            var stack=model.decode(row.get("stack"));boolean allowed=row.get("allowed").getAsBoolean();
            g.fill(x,y,x+cell()-2,y+cell()-2,selected.containsKey(slot)?UiTheme.SELECTED:allowed?UiTheme.CARD:0xBB191919);
            if(!stack.isEmpty()){g.item(stack,x+3,y+2);text(g,Integer.toString(selected.getOrDefault(slot,stack.getCount())),x+cell()-15,y+13,selected.containsKey(slot)?0xFF9DE5C7:0xFFFFFFFF,15);}
            if(inside(mx,my,x,y,cell()-2,cell()-2))hovered=stack;
        }
        int tools=bottom()+2;
        button(g,"-1",12,tools,35,focused>=0);button(g,"+1",51,tools,35,focused>=0);button(g,"整堆",90,tools,45,focused>=0);
        button(g,"清空",139,tools,45,!selected.isEmpty());
        text(g,"点击选择/取消，选中后调数量",190,tools+7,0xFFACB8C2,width-200);
        int start=tools+29,available=Math.max(1,(height-65-start)/12);
        var lines=model.sale.has("lines")?model.sale.getAsJsonArray("lines"):new JsonArray();
        scroll=Math.clamp(scroll,0,Math.max(0,lines.size()-available));
        for(int i=scroll;i<Math.min(lines.size(),scroll+available);i++){
            var row=lines.get(i).getAsJsonObject();
            String rarity=model.rarity(model.decode(model.sale.getAsJsonArray("inventory").get(row.get("slot").getAsInt()).getAsJsonObject().get("stack")));
            String name=model.decode(model.sale.getAsJsonArray("inventory").get(row.get("slot").getAsInt()).getAsJsonObject().get("stack")).getHoverName().getString();
            text(g,name+" ×"+row.get("quantity").getAsInt()+"  基础"+ClientCatalog.decimal(Rational.parse(row.get("unit").getAsString()))+"  本行"+Money.format(row.get("coins").getAsLong()),12,start+(i-scroll)*12,RarityColors.color(rarity),width-24);
        }
        boolean quoted=!dirty&&model.sale.has("selection")&&selection().equals(model.sale.get("selection").getAsString());
        long coins=quoted&&model.sale.has("coins")?model.sale.get("coins").getAsLong():0;
        text(g,quoted?"合计获得："+Money.format(coins)+"（整笔取整）":"正在更新报价…",12,height-55,0xFFFFDC78,width-24);
        if(!model.lastError.isEmpty())text(g,model.lastError,12,height-42,0xFFFF8888,width-24);
        button(g,confirming?"正在出售…":"确认出售",12,height-27,105,quoted&&coins>0&&!confirming);
        button(g,"取消出售",123,height-27,105,true);
        if(!hovered.isEmpty()){
            String label=hovered.getHoverName().getString()+" · "+RarityColors.name(model.rarity(hovered));
            int w=Math.min(width-16,font.width(label)+12),x=Math.clamp(mx+8,8,width-w-8),y=Math.clamp(my+16,8,height-32);
            g.fill(x,y,x+w,y+20,UiTheme.PANEL);text(g,label,x+5,y+6,RarityColors.color(model.rarity(hovered)),w-10);
        }
    }
    public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(confirming)return true;double x=e.x()-parent.contentLeft(),y=e.y()-70;int tools=bottom()+2;
        if(inside(x,y,12,height-27,105,22)){
            if(!dirty&&model.sale.has("selection")&&selection().equals(model.sale.get("selection").getAsString())&&model.sale.get("coins").getAsLong()>0){
                String token=model.sale.get("token").getAsString();
                long coins=model.sale.get("coins").getAsLong();
                parent.confirm("确认出售所选物品，获得 "+Money.format(coins)+"？",()->{confirming=true;send("sale_confirm",token);});
            }return true;
        }
        if(inside(x,y,123,height-27,105,22)){onClose();return true;}
        if(inside(x,y,139,tools,45,22)){selected.clear();dirty=true;return true;}
        if(focused>=0&&model.sale.has("inventory")){
            var row=model.sale.getAsJsonArray("inventory").get(focused).getAsJsonObject();int max=model.decode(row.get("stack")).getCount(),count=selected.getOrDefault(focused,0);
            if(inside(x,y,12,tools,35,22))count=Math.max(0,count-1);
            else if(inside(x,y,51,tools,35,22))count=Math.min(max,count+1);
            else if(inside(x,y,90,tools,45,22))count=max;else count=-1;
            if(count>=0){if(count==0)selected.remove(focused);else selected.put(focused,count);dirty=true;return true;}
        }
        if(model.sale.has("inventory"))for(var value:model.sale.getAsJsonArray("inventory")){
            var row=value.getAsJsonObject();int slot=row.get("slot").getAsInt();
            if(inside(x,y,left()+slot%9*cell(),35+slot/9*cell(),cell()-2,cell()-2)&&row.get("allowed").getAsBoolean()){
                focused=slot;if(selected.containsKey(slot))selected.remove(slot);else selected.put(slot,model.decode(row.get("stack")).getCount());dirty=true;return true;
            }
        }
        return true;
    }
    public boolean mouseScrolled(double x,double y,double sx,double sy){scroll-=Math.signum(sy);return true;}
    public void onClose(){parent.cancelSale(true);}
}
