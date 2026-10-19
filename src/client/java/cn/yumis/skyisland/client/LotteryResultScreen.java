package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.RarityColors;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Centered draw result. One prize stacks icon over name; several use rows of five. */
public final class LotteryResultScreen extends Screen {
    private final Screen parent;
    private final List<Prize> prizes;
    public LotteryResultScreen(Screen parent,JsonObject result,ClientCatalog model){
        super(Component.literal("祈愿结果"));this.parent=parent;prizes=read(result,model);
    }
    public Screen parent(){return parent;}
    private static List<Prize> read(JsonObject result,ClientCatalog model){
        List<Prize> rows=new ArrayList<>();
        if(result.has("prizes")&&result.get("prizes").isJsonArray())for(var element:result.getAsJsonArray("prizes")){
            JsonObject row=element.getAsJsonObject();
            if(!row.has("rewardStack"))continue;
            rows.add(new Prize(model.decode(row.get("rewardStack")),row.has("rarity")?row.get("rarity").getAsString():"common",row.has("pity")&&row.get("pity").getAsBoolean()));
        }
        if(rows.isEmpty()&&result.has("rewardStack"))rows.add(new Prize(model.decode(result.get("rewardStack")),result.has("rarity")?result.get("rarity").getAsString():"common",false));
        return rows;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float delta){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xC0000000);
        if(prizes.isEmpty()){g.text(font,"没有可显示的结果",(width-font.width("没有可显示的结果"))/2,height/2,0xFFDDDDDD,false);return;}
        if(prizes.size()==1)drawSingle(g);else drawGrid(g);
    }
    private void drawSingle(GuiGraphicsExtractor g){
        Prize prize=prizes.getFirst();int panelW=220,panelH=168,x=(width-panelW)/2,y=(height-panelH)/2;
        g.fill(x,y,x+panelW,y+panelH,UiTheme.PANEL);
        QualityFrames.draw(g,prize.stack,x,y,panelW,panelH);
        g.pose().pushMatrix();g.pose().translate(x+86,y+28);g.pose().scale(3f,3f);g.item(prize.stack,0,0);g.pose().popMatrix();
        String name=font.plainSubstrByWidth(prize.stack.getHoverName().getString(),panelW-24);
        g.text(font,name,x+(panelW-font.width(name))/2,y+92,RarityColors.color(prize.rarity),false);
        if(prize.pity){String mark="保底";g.text(font,mark,x+(panelW-font.width(mark))/2,y+108,0xFFFFAA00,false);}
        confirm(g,x+(panelW-72)/2,y+panelH-32);
    }
    private void drawGrid(GuiGraphicsExtractor g){
        int columns=Math.min(5,prizes.size()),rows=(prizes.size()+columns-1)/columns,cell=86;
        int panelW=columns*cell+24,panelH=rows*cell+56,x=(width-panelW)/2,y=Math.max(12,(height-panelH)/2);
        g.fill(x,y,x+panelW,y+panelH,UiTheme.PANEL);
        for(int i=0;i<prizes.size();i++){
            Prize prize=prizes.get(i);int cx=x+12+(i%columns)*cell,cy=y+12+(i/columns)*cell;
            QualityFrames.draw(g,prize.stack,cx,cy,cell-2,cell-2);
            g.pose().pushMatrix();g.pose().translate(cx+(cell-32)/2f,cy+4);g.pose().scale(2f,2f);g.item(prize.stack,0,0);g.pose().popMatrix();
            String name=font.plainSubstrByWidth(prize.stack.getHoverName().getString(),cell-8);
            g.text(font,name,cx+(cell-font.width(name))/2,cy+42,RarityColors.color(prize.rarity),false);
            if(prize.pity){String mark="保底";g.text(font,mark,cx+(cell-font.width(mark))/2,cy+56,0xFFFFAA00,false);}
        }
        confirm(g,x+(panelW-72)/2,y+panelH-32);
    }
    private void confirm(GuiGraphicsExtractor g,int x,int y){
        g.fill(x,y,x+72,y+22,UiTheme.BUTTON);String label="确认";g.text(font,label,x+(72-font.width(label))/2,y+7,0xFFF1F1F1,false);
    }
    private boolean confirmHit(double mx,double my){
        int x,y,w,h;
        if(prizes.size()<=1){int panelW=220,panelH=168;x=(width-panelW)/2+(panelW-72)/2;y=(height-panelH)/2+panelH-32;}
        else {int columns=Math.min(5,prizes.size()),rows=(prizes.size()+columns-1)/columns,cell=86,panelW=columns*cell+24,panelH=rows*cell+56;x=(width-panelW)/2+(panelW-72)/2;y=Math.max(12,(height-panelH)/2)+panelH-32;}
        w=72;h=22;return mx>=x&&mx<x+w&&my>=y&&my<y+h;
    }
    private boolean insidePanel(double mx,double my){
        int x,y,w,h;
        if(prizes.size()<=1){w=220;h=168;x=(width-w)/2;y=(height-h)/2;}
        else {int columns=Math.min(5,prizes.size()),rows=(prizes.size()+columns-1)/columns,cell=86;w=columns*cell+24;h=rows*cell+56;x=(width-w)/2;y=Math.max(12,(height-h)/2);}
        return mx>=x&&mx<x+w&&my>=y&&my<y+h;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()!=0)return super.mouseClicked(event,doubleClick);
        if(confirmHit(event.x(),event.y())||!insidePanel(event.x(),event.y())){onClose();return true;}
        return true;
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.gui.setScreen(parent);}
    private record Prize(ItemStack stack,String rarity,boolean pity) {}
}
