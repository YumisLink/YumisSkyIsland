package cn.yumis.skyisland.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class BlueprintPositionScreen extends Screen {
    private record Action(String label,Runnable run) {}
    private int step=1;
    private boolean viewMode;
    private final List<Action> actions=new ArrayList<>();
    public BlueprintPositionScreen(){super(Component.literal("图纸位置"));}
    public boolean viewMode(){return viewMode;}
    private void toggleView(){
        viewMode=!viewMode;
        if(viewMode)minecraft.mouseHandler.grabMouse();
        else{minecraft.mouseHandler.releaseMouse();releaseMovement();}
    }
    private void releaseMovement(){
        for(KeyMapping key:List.of(minecraft.options.keyUp,minecraft.options.keyDown,minecraft.options.keyLeft,minecraft.options.keyRight,minecraft.options.keyJump,minecraft.options.keyShift,minecraft.options.keySprint))key.setDown(false);
    }
    private void updateMovement(){
        if(!viewMode)return;
        for(KeyMapping key:List.of(minecraft.options.keyUp,minecraft.options.keyDown,minecraft.options.keyLeft,minecraft.options.keyRight,minecraft.options.keyJump,minecraft.options.keyShift,minecraft.options.keySprint)){
            var bound=InputConstants.getKey(key.saveString());
            key.setDown(bound.getType()==InputConstants.Type.KEYSYM&&InputConstants.isKeyDown(minecraft.getWindow(),bound.getValue()));
        }
    }
    @Override public void tick(){updateMovement();}
    @Override public void removed(){if(viewMode){viewMode=false;minecraft.mouseHandler.releaseMouse();releaseMovement();}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){}
    private void move(int x,int y,int z){JsonObject request=new JsonObject();request.addProperty("action","preview_move");request.addProperty("dx",x*step);request.addProperty("dy",y*step);request.addProperty("dz",z*step);PrefabClient.send(request);}
    private void buttons(){actions.clear();actions.add(new Action("西 ←",()->move(-1,0,0)));actions.add(new Action("东 →",()->move(1,0,0)));actions.add(new Action("北 ↑",()->move(0,0,-1)));actions.add(new Action("南 ↓",()->move(0,0,1)));actions.add(new Action("向上",()->move(0,1,0)));actions.add(new Action("向下",()->move(0,-1,0)));
        actions.add(new Action("步长："+step+"格",()->step=step==1?5:1));actions.add(new Action(PrefabClient.preview.has("rotationCount")&&PrefabClient.preview.get("rotationCount").getAsInt()==1?"方向锁定"+PrefabClient.preview.get("rotation").getAsInt()*90+"°":"旋转90°",()->PrefabClient.send("preview_rotate")));
        actions.add(new Action("替换："+(PrefabClient.preview.has("replace")&&PrefabClient.preview.get("replace").getAsBoolean()?"开启":"关闭"),()->PrefabClient.send("preview_replace")));
        actions.add(new Action("查看："+(PrefabClient.layer<0?"全部层":"第"+(PrefabClient.layer+1)+"层"),()->PrefabClient.layer=PrefabClient.layer<0?0:-1));
        actions.add(new Action("楼层＋",()->PrefabClient.layer=Math.min(PrefabClient.preview.get("height").getAsInt()-1,PrefabClient.layer+1)));actions.add(new Action("楼层－",()->PrefabClient.layer=Math.max(-1,PrefabClient.layer-1)));
        actions.add(new Action("保存位置到图纸",()->PrefabClient.send("preview_save")));actions.add(new Action("取消预览",()->{PrefabClient.visible=false;PrefabClient.send("preview_cancel");onClose();}));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        buttons();int left=8,top=8;g.fill(left,top,left+224,Math.min(height-8,top+300),UiTheme.PANEL);var p=PrefabClient.preview;
        g.text(font,p.has("name")?p.get("name").getAsString():"图纸位置",left+10,top+9,0xFFE5F7FF,false);
        if(p.has("origin"))g.text(font,"位置 "+p.getAsJsonArray("origin"),left+10,top+25,0xFFBDD6E8,false);
        g.text(font,viewMode?"查看模式：WASD/跳跃移动，右键返回操作":"右键切换查看模式；Esc关闭，G重开",left+10,top+40,0xFF94B3C8,false);
        int rowTop=top+58;
        for(int i=0;i<actions.size();i++){int x=left+9+(i%2)*104,y=rowTop+(i/2)*24;g.fill(x,y,x+99,y+20,viewMode?UiTheme.CARD:UiTheme.PANEL);g.text(font,actions.get(i).label,x+5,y+6,viewMode?0xFF94A4AD:0xFFE7F5FF,false);}
        String error=p.has("error")?p.get("error").getAsString():"等待服务端预览";int y=rowTop+175;
        for(var line:font.split(Component.literal(error.isBlank()?"确认后将位置写入图纸，再装入大炮。":error),204)){g.text(font,line,left+10,y,error.isBlank()?0xFF99DEB6:0xFFFF9595,false);y+=11;}
        if(p.has("notes"))for(var line:font.split(Component.literal(p.get("notes").getAsString()),204)){if(y>height-20)break;g.text(font,line,left+10,y,0xFFC1CEDA,false);y+=11;}
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()==1){toggleView();return true;}
        if(viewMode)return true;
        buttons();if(event.button()==0){int column=(int)((event.x()-17)/104),row=(int)((event.y()-66)/24);if(event.x()>=17&&event.x()<220&&event.y()>=66&&column>=0&&column<2&&row>=0&&row*2+column<actions.size()){actions.get(row*2+column).run.run();return true;}}
        return super.mouseClicked(event,doubleClick);
    }
}
