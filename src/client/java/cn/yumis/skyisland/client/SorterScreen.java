package cn.yumis.skyisland.client;

import cn.yumis.skyisland.sorting.*;
import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class SorterScreen extends AbstractContainerScreen<SorterMenu> {
    private static JsonObject snapshot=new JsonObject();
    private JsonObject data=new JsonObject();
    private int page;
    private record Button(String action,String label,int x,int y,int width) {
        boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+18;}
    }
    public static void register(){
        MenuScreens.register(SortingContent.MENU,SorterScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(SorterPayloads.Snapshot.TYPE,(payload,context)->snapshot=JsonParser.parseString(payload.json()).getAsJsonObject());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->snapshot=new JsonObject());
    }
    public SorterScreen(SorterMenu menu,Inventory inventory,Component title){super(menu,inventory,title,SorterMenu.WIDTH,SorterMenu.HEIGHT);}
    private List<Button> buttons(){return List.of(new Button("source","选择来源",12,64,124),new Button("target","选择目标",144,64,124),
            new Button(data.has("paused")&&data.get("paused").getAsBoolean()?"resume":"pause",data.has("paused")&&data.get("paused").getAsBoolean()?"继续搬运":"暂停搬运",190,138,78),
            new Button("clear","清空绑定",190,162,78),new Button("previous","‹",190,104,18),new Button("next","›",250,104,18));}
    private String value(String key,String fallback){return data.has(key)?data.get(key).getAsString():fallback;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,0x88000000);}
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){}
    @Override public void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        if(snapshot.has("device")&&snapshot.has("position")&&snapshot.get("position").getAsLong()==menu.pos.asLong()
                &&minecraft.level!=null&&snapshot.get("dimension").getAsString().equals(minecraft.level.dimension().identifier().toString()))data=snapshot;
        g.fill(leftPos-5,topPos-5,leftPos+imageWidth+5,topPos+imageHeight+5,UiTheme.PANEL);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+22,UiTheme.CARD);
        text(g,"物品分类器",12,7,0xFFF0E6C5);
        text(g,"自动匹配 · 100格",172,7,0xFF9AA6AA);
        card(g,"A  来源箱",value("source","未选择"),12,0xFF87CFFC);
        card(g,"B  目标箱",value("target","未选择"),144,0xFFFFC17C);
        String status=value("status","正在同步设备信息");text(g,font.plainSubstrByWidth(status,256),12,88,0xFFBDC9CC);
        JsonArray samples=data.has("samples")?data.getAsJsonArray("samples"):new JsonArray();int pages=Math.max(1,(samples.size()+8)/9);page=Math.clamp(page,0,pages-1);
        for(int i=0;i<9;i++){
            int x=12+i*18,y=104;g.fill(leftPos+x-1,topPos+y-1,leftPos+x+17,topPos+y+17,0xFF454A4C);g.fill(leftPos+x,topPos+y,leftPos+x+16,topPos+y+16,UiTheme.CARD);
            int index=page*9+i;if(index<samples.size()){
                ItemStack item=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(samples.get(index).getAsString())));g.item(item,leftPos+x,topPos+y);
            }
        }
        text(g,(page+1)+"/"+pages,213,109,0xFFB4BDC0);
        text(g,"物品栏",12,126,0xFFB4BDC0);text(g,"B箱样本 · 只读",190,126,0xFF9AA6AA);
        // The actual Slot positions drive both item rendering/clicks and the slot borders.
        for(var slot:menu.slots){int x=leftPos+slot.x,y=topPos+slot.y;g.fill(x-1,y-1,x+17,y+17,0xFF596064);g.fill(x,y,x+16,y+16,UiTheme.CARD);}
        for(Button button:buttons()){
            boolean hover=button.contains(mouseX-leftPos,mouseY-topPos);g.fill(leftPos+button.x,topPos+button.y,leftPos+button.x+button.width,topPos+button.y+18,hover?UiTheme.HOVER:UiTheme.BUTTON);
            text(g,button.label,button.x+(button.width-font.width(button.label))/2,button.y+5,0xFFE0E4E1);
        }
        text(g,"距离",190,191,0xFF9AA6AA);text(g,data.has("distance")?String.format(Locale.ROOT,"%.1f / 100",data.get("distance").getAsDouble()):"— / 100",190,204,0xFFB7D5D8);
        super.extractContents(g,mouseX,mouseY,delta);
        int mx=mouseX-leftPos,my=mouseY-topPos;
        if(mx>=11&&mx<174&&my>=103&&my<121){
            int index=page*9+Math.max(0,(mx-12)/18);String tip="B箱现有种类，只读；在实际箱子中放入或取走样本";
            if(index<samples.size())tip=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(samples.get(index).getAsString()))).getHoverName().getString()+" · 按物品ID匹配，不消耗样本";
            tooltip(g,tip,mouseX,mouseY);
        }else if(mx>=12&&mx<268&&my>=86&&my<100)tooltip(g,status,mouseX,mouseY);
    }
    private void card(GuiGraphicsExtractor g,String name,String coordinates,int x,int color){
        g.fill(leftPos+x-3,topPos+26,leftPos+x+127,topPos+61,UiTheme.CARD);text(g,name,x,29,color);text(g,font.plainSubstrByWidth(coordinates,121),x,46,0xFFD2D7D6);
    }
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int color){g.text(font,s,leftPos+x,topPos+y,color,false);}
    private void tooltip(GuiGraphicsExtractor g,String value,int mouseX,int mouseY){
        var lines=font.split(Component.literal(value),Math.min(220,width-20));int boxWidth=Math.min(224,width-12),x=Math.clamp(mouseX+10,4,width-boxWidth-4),y=Math.clamp(mouseY+12,4,height-lines.size()*11-10);
        g.fill(x,y,x+boxWidth,y+lines.size()*11+8,UiTheme.TOOLTIP);int offset=4;for(var line:lines){g.text(font,line,x+4,y+offset,0xFFE8EEEA,false);offset+=11;}
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()==0)for(Button button:buttons())if(button.contains(event.x()-leftPos,event.y()-topPos)){
            if(button.action.equals("previous"))page=Math.max(0,page-1);else if(button.action.equals("next"))page++;
            else if(data.has("device")&&ClientPlayNetworking.canSend(SorterPayloads.Operation.TYPE))ClientPlayNetworking.send(new SorterPayloads.Operation(data.get("device").getAsString(),data.get("revision").getAsLong(),button.action));
            return true;
        }
        // Samples never become inventory slots or accept a cursor stack.
        if(event.x()>=leftPos+11&&event.x()<leftPos+175&&event.y()>=topPos+103&&event.y()<topPos+122)return true;
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(x>=leftPos+11&&x<leftPos+269&&y>=topPos+103&&y<topPos+123){page=Math.max(0,page+(vertical>0?-1:1));return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
}
