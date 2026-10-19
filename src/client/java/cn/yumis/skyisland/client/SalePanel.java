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
    private record Tool(String label,int x,int y,int w,int action,boolean active){}
    private final List<Tool> tools=new ArrayList<>();
    private int toolBottom;
    private int maxScroll;
    private int confirmWidth(){return Math.max(60,Math.min(105,(width-30)/2));}
    public SalePanel(ControlPanelScreen parent){this.parent=parent;this.model=parent.model();}
    private String selection(){var values=new ArrayList<String>();selected.forEach((s,n)->{if(n>0)values.add(s+":"+n);});return String.join(",",values);}
    private boolean inventorySale(){return !(model.sale.has("hand")&&model.sale.get("hand").getAsBoolean());}
    private boolean anyAllowed(){
        if(!model.sale.has("inventory"))return false;
        for(var entry:model.sale.getAsJsonArray("inventory"))if(entry.getAsJsonObject().get("allowed").getAsBoolean()&&model.decode(entry.getAsJsonObject().get("stack")).getCount()>0)return true;
        return false;
    }
    private void selectAll(){
        selected.clear();
        if(model.sale.has("inventory"))for(var entry:model.sale.getAsJsonArray("inventory")){
            var row=entry.getAsJsonObject();
            int count=model.decode(row.get("stack")).getCount();
            if(row.get("allowed").getAsBoolean()&&count>0)selected.put(row.get("slot").getAsInt(),count);
        }
        dirty=true;
    }
    private void send(String action,String product){ClientPlayNetworking.send(new ShopPayloads.Request(action,product,0));}
    public void selectProfession(){
        selected.clear();focused=-1;
        if(model.sale.has("inventory"))for(var value:model.sale.getAsJsonArray("inventory")){
            var row=value.getAsJsonObject();var stack=model.decode(row.get("stack"));
            if(!stack.isEmpty()&&row.get("allowed").getAsBoolean()&&model.professionSaleItems.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))selected.put(row.get("slot").getAsInt(),stack.getCount());
        }dirty=true;
    }
    private void layoutTools(){
        tools.clear();int x=12,y=bottom()+2;String[] labels={"-1","+1","整堆","清空","全部可售","副职业产品"};int[] widths={30,30,40,40,76,92};
        for(int i=0;i<(inventorySale()?6:4);i++){
            if(x+widths[i]>width-10){x=12;y+=24;}
            boolean active=i<3?focused>=0:i==3?!selected.isEmpty():i==4?anyAllowed():anyAllowed()&&!model.professionSaleItems.isEmpty();
            tools.add(new Tool(labels[i],x,y,widths[i],i,active));x+=widths[i]+4;
        }toolBottom=y+24;
    }
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
        ItemStack hovered=ItemStack.EMPTY;String hoveredReason="";
        g.enableScissor(0,30,width,height-65);g.pose().pushMatrix();g.pose().translate(0,-scroll);
        if(model.sale.has("inventory"))for(var e:model.sale.getAsJsonArray("inventory")){
            var row=e.getAsJsonObject();int slot=row.get("slot").getAsInt(),x=left()+slot%9*cell(),y=35+slot/9*cell();
            var stack=model.decode(row.get("stack"));boolean allowed=row.get("allowed").getAsBoolean();
            g.fill(x,y,x+cell()-2,y+cell()-2,selected.containsKey(slot)?UiTheme.SELECTED:allowed?UiTheme.CARD:0xBB191919);
            QualityFrames.draw(g,stack,x,y,cell()-2,cell()-2);
            if(!stack.isEmpty()){g.item(stack,x+3,y+2);text(g,Integer.toString(selected.getOrDefault(slot,stack.getCount())),x+cell()-15,y+13,selected.containsKey(slot)?0xFF9DE5C7:0xFFFFFFFF,15);}
            if(my>=30&&my<height-65&&inside(mx,my+scroll,x,y,cell()-2,cell()-2)){hovered=stack;hoveredReason=row.has("reason")?row.get("reason").getAsString():"";}
        }
        layoutTools();for(var tool:tools)button(g,tool.label(),tool.x(),tool.y(),tool.w(),tool.active());
        int start=toolBottom+3;
        var lines=model.sale.has("lines")?model.sale.getAsJsonArray("lines"):new JsonArray();
        for(int i=0;i<lines.size();i++){
            var row=lines.get(i).getAsJsonObject();
            String rarity=model.rarity(model.decode(model.sale.getAsJsonArray("inventory").get(row.get("slot").getAsInt()).getAsJsonObject().get("stack")));
            String name=model.decode(model.sale.getAsJsonArray("inventory").get(row.get("slot").getAsInt()).getAsJsonObject().get("stack")).getHoverName().getString();
            text(g,name+" ×"+row.get("quantity").getAsInt()+"  基础"+ClientCatalog.decimal(Rational.parse(row.get("unit").getAsString()))+"  本行"+Money.format(row.get("coins").getAsLong()),12,start+i*12,RarityColors.color(rarity),width-24);
        }
        maxScroll=Math.max(0,start+lines.size()*12+5-(height-65));scroll=Math.clamp(scroll,0,maxScroll);
        g.pose().popMatrix();g.disableScissor();
        if(maxScroll>0){int rail=Math.max(8,height-99),thumb=Math.max(8,rail*rail/(rail+maxScroll)),yy=32+(rail-thumb)*scroll/maxScroll;g.fill(width-6,32,width-3,height-67,UiTheme.CARD);g.fill(width-6,yy,width-3,yy+thumb,0xFF8FA9AD);}
        boolean quoted=!dirty&&model.sale.has("selection")&&selection().equals(model.sale.get("selection").getAsString());
        long coins=quoted&&model.sale.has("coins")?model.sale.get("coins").getAsLong():0;
        text(g,quoted?"合计获得："+Money.format(coins)+"（整笔取整）":"正在更新报价…",12,height-55,0xFFFFDC78,width-24);
        if(!model.lastError.isEmpty())text(g,model.lastError,12,height-42,0xFFFF8888,width-24);
        button(g,confirming?"正在出售…":"确认出售",12,height-27,confirmWidth(),quoted&&coins>0&&!confirming);
        button(g,"取消出售",18+confirmWidth(),height-27,confirmWidth(),true);
        if(!hovered.isEmpty()){
            String label=hovered.getHoverName().getString()+" · "+RarityColors.name(model.rarity(hovered));
            int w=Math.min(width-16,font.width(label)+12),x=Math.clamp(mx+8,8,width-w-8),y=Math.clamp(my+16,8,height-32);
            g.fill(x,y,x+w,y+20,UiTheme.PANEL);text(g,label,x+5,y+6,RarityColors.color(model.rarity(hovered)),w-10);
            if(!hoveredReason.isEmpty()){g.fill(8,height-80,width-8,height-60,UiTheme.PANEL);text(g,hoveredReason,12,height-74,0xFFFFAD9F,width-24);}
        }
    }
    public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(confirming)return true;double x=e.x()-parent.contentLeft(),y=e.y()-70;
        if(inside(x,y,12,height-27,confirmWidth(),22)){
            if(!dirty&&model.sale.has("selection")&&selection().equals(model.sale.get("selection").getAsString())&&model.sale.get("coins").getAsLong()>0){
                String token=model.sale.get("token").getAsString();
                long coins=model.sale.get("coins").getAsLong();
                parent.confirm("确认出售所选物品，获得 "+Money.format(coins)+"？",()->{confirming=true;send("sale_confirm",token);});
            }return true;
        }
        if(inside(x,y,18+confirmWidth(),height-27,confirmWidth(),22)){onClose();return true;}
        if(y<30||y>=height-65)return true;y+=scroll;
        for(var tool:tools)if(inside(x,y,tool.x(),tool.y(),tool.w(),22)){
            if(!tool.active())return true;
            if(tool.action()==3){selected.clear();dirty=true;}else if(tool.action()==4)selectAll();else if(tool.action()==5)selectProfession();
            else{var row=model.sale.getAsJsonArray("inventory").get(focused).getAsJsonObject();int max=model.decode(row.get("stack")).getCount(),count=selected.getOrDefault(focused,0);count=tool.action()==0?Math.max(0,count-1):tool.action()==1?Math.min(max,count+1):max;if(count==0)selected.remove(focused);else selected.put(focused,count);dirty=true;}return true;
        }
        if(model.sale.has("inventory"))for(var value:model.sale.getAsJsonArray("inventory")){
            var row=value.getAsJsonObject();int slot=row.get("slot").getAsInt();
            if(inside(x,y,left()+slot%9*cell(),35+slot/9*cell(),cell()-2,cell()-2)&&row.get("allowed").getAsBoolean()){
                focused=slot;if(selected.containsKey(slot))selected.remove(slot);else selected.put(slot,model.decode(row.get("stack")).getCount());dirty=true;return true;
            }
        }
        return true;
    }
    public boolean mouseScrolled(double x,double y,double sx,double sy){scroll=Math.clamp(scroll-(int)Math.round(sy*24),0,maxScroll);return true;}
    public void onClose(){parent.cancelSale(true);}
}
