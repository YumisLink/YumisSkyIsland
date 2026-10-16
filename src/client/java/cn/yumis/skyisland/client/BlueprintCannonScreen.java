package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.Money;
import cn.yumis.skyisland.prefab.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class BlueprintCannonScreen extends AbstractContainerScreen<BlueprintCannonMenu> {
    private String confirmation="";
    private int materialScroll;
    public BlueprintCannonScreen(BlueprintCannonMenu menu,Inventory inventory,Component title){super(menu,inventory,title,364,232);inventoryLabelY=128;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){}
    @Override public void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(leftPos-7,topPos-7,leftPos+imageWidth+7,topPos+imageHeight+7,UiTheme.PANEL);
        g.text(font,"图纸",leftPos+17,topPos+14,0xFFAEDAFF,false);g.text(font,"弹药",leftPos+53,topPos+14,0xFFAEDAFF,false);g.text(font,"供料栏 · 支持相邻箱与一层潜影盒",leftPos+8,topPos+52,0xFFC6DCE8,false);
        for(var slot:menu.slots){g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF666666);g.fill(leftPos+slot.x+1,topPos+slot.y+1,leftPos+slot.x+15,topPos+slot.y+15,UiTheme.PANEL);}
        super.extractContents(g,mx,my,delta);JsonObject data=PrefabClient.cannon;int x=leftPos+178,y=topPos+8;
        g.text(font,"蓝图大炮",x,y,0xFFE8F8FF,false);y+=17;
        if(data.has("name")){g.text(font,data.get("name").getAsString(),x,y,0xFFFFE1A4,false);y+=14;}
        String status=data.has("status")?data.get("status").getAsString():"idle";
        String progress=data.has("progress")?data.get("progress").getAsInt()+" / "+data.get("total").getAsInt():data.has("total")?"预计 "+data.get("total").getAsInt()+" 方块":"先放入已定位图纸";
        g.text(font,progress,x,y,0xFFBED4DF,false);y+=13;
        g.text(font,"可用弹药 "+(data.has("availableFuel")?data.get("availableFuel").getAsLong():0),x,y,0xFFBED4DF,false);y+=13;
        if(data.has("interval")){g.text(font,"每 "+data.get("interval").getAsInt()+" tick 最多放置1块",x,y,0xFFBED4DF,false);y+=15;}
        if(!confirmation.isBlank()){
            String warning=confirmation.equals("terminate")?"终止后不退款，已建部分保留。":"开工消耗图纸；不能撤销，替换旧方块不返还。";
            if(confirmation.equals("start_paid")&&data.has("fee"))warning="成品费 "+Money.format(data.get("fee").getAsLong())+"。"+warning;
            for(var line:font.split(Component.literal(warning),172)){g.text(font,line,x,y,0xFFFFB2A4,false);y+=12;}
            button(g,"确认",x,topPos+130,80);button(g,"返回",x+87,topPos+130,80);
        }else{
            button(g,status.equals("idle")?"自备材料开工":"暂停",x,topPos+80,168);
            button(g,status.equals("idle")?"购买成品开工":"继续施工",x,topPos+105,168);
            button(g,"终止施工（不退款）",x,topPos+130,168);
        }
        y=topPos+158;String message=data.has("message")?data.get("message").getAsString():"";
        for(var line:font.split(Component.literal(message),172)){if(y>topPos+197)break;g.text(font,line,x,y,0xFFE5CDA8,false);y+=11;}
        if(data.has("materials")&&confirmation.isBlank()){
            var materials=data.getAsJsonArray("materials");g.text(font,"材料清单（滚轮查看）",leftPos+8,topPos+222,0xFFA8D8EC,false);
            int index=Math.min(materialScroll,Math.max(0,materials.size()-1));
            if(!materials.isEmpty()){var row=materials.get(index).getAsJsonObject();ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(row.get("item").getAsString())));g.text(font,stack.getHoverName().getString()+" × "+row.get("count").getAsLong(),x,topPos+211,0xFFBFDCC8,false);}
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){g.text(font,"物品栏",8,128,0xFFC6DCE8,false);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int w){g.fill(x,y,x+w,y+20,UiTheme.PANEL);g.text(font,label,x+5,y+6,0xFFE4F3FD,false);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()==0&&event.x()>=leftPos+178&&event.x()<leftPos+346){double y=event.y()-topPos;
            if(!confirmation.isBlank()&&y>=130&&y<150){if(event.x()<leftPos+258)PrefabClient.cannon(confirmation,menu.pos);confirmation="";return true;}
            String status=PrefabClient.cannon.has("status")?PrefabClient.cannon.get("status").getAsString():"idle";
            if(y>=80&&y<100){if(status.equals("idle"))confirmation="start_material";else PrefabClient.cannon("pause",menu.pos);return true;}
            if(y>=105&&y<125){if(status.equals("idle"))confirmation="start_paid";else PrefabClient.cannon("resume",menu.pos);return true;}
            if(y>=130&&y<150){confirmation="terminate";return true;}
        }return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){materialScroll=Math.max(0,materialScroll+(vertical>0?-1:1));return true;}
}
