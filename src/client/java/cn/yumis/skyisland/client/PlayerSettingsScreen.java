package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.VeinMiningRules;
import cn.yumis.skyisland.network.ShopPayloads;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Personal settings opened from the control panel; gameplay limits are confirmed by the server. */
public final class PlayerSettingsScreen extends Screen {
    private final ControlPanelScreen parent;
    private final ClientCatalog model;
    private EditBox limit;
    private boolean busy;
    private String message="";
    private int left,top,panelWidth;

    public PlayerSettingsScreen(ControlPanelScreen parent,ClientCatalog model){super(Component.literal("设置"));this.parent=parent;this.model=model;}
    @Override public boolean isPauseScreen(){return false;}
    private JsonObject settings(){return model.state.has("veinMining")?model.state.getAsJsonObject("veinMining"):new JsonObject();}
    private int value(String key,int fallback){var data=settings();return data.has(key)?data.get(key).getAsInt():fallback;}
    @Override protected void init(){
        String draft=limit==null?Integer.toString(value("limit",VeinMiningRules.MAX_LIMIT)):limit.getValue();
        panelWidth=Math.min(420,width-24);left=(width-panelWidth)/2;top=Math.max(8,(height-224)/2);
        limit=new EditBox(font,left+12,top+89,panelWidth-104,20,Component.literal("启用的最大连锁"));
        limit.setMaxLength(3);limit.setValue(draft);limit.setFocused(true);
    }
    private void text(GuiGraphicsExtractor g,String text,int x,int y,int color){g.text(font,font.plainSubstrByWidth(text,panelWidth-24),x,y,color,false);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int w,boolean selected,boolean active){
        g.fill(x,y,x+w,y+22,!active?UiTheme.DISABLED:selected?UiTheme.SELECTED:UiTheme.BUTTON);
        g.text(font,label,x+6,y+7,active?0xFFE9EBEE:0xFF777F88,false);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiTheme.OVERLAY);g.fill(left,top,left+panelWidth,top+224,UiTheme.PANEL);
        text(g,"设置 · 连锁采集",left+12,top+12,0xFFE4F5FF);
        var data=settings();boolean hammer=data.has("chainHammer")&&data.get("chainHammer").getAsBoolean();
        text(g,"矿工等级 "+value("minerLevel",0)+" · 连锁锤"+(hammer?"已启用":"未启用"),left+12,top+34,0xFFB9D7D0);
        text(g,"当前可用上限 "+value("unlocked",8)+" · 实际启用 "+value("effective",8),left+12,top+50,0xFF9DE5C7);
        text(g,"启用的最大连锁",left+12,top+73,0xFFE5EAED);
        limit.active=!busy;limit.extractRenderState(g,mx,my,delta);
        button(g,"跟随上限",left+panelWidth-84,top+88,72,false,!busy);
        text(g,"可设1～128；实际采集不会超过当前可用上限。",left+12,top+115,0xFFB5C1C9);
        text(g,"数量包含起始方块；矿工每5级增加1，上限128。",left+12,top+130,0xFFB5C1C9);
        text(g,"界面大小",left+12,top+155,0xFFE5EAED);
        String[] labels={"小","中","大"},values={"small","medium","large"};
        for(int i=0;i<3;i++)button(g,labels[i],left+80+i*34,top+149,30,model.uiSize.equals(values[i]),true);
        text(g,message,left+12,top+177,message.startsWith("已保存")?0xFF8BE0B2:0xFFFFAD9F);
        button(g,busy?"正在保存…":"保存并生效",left+12,top+192,108,false,!busy&&model.state.has("veinMining"));
        button(g,"返回",left+panelWidth-68,top+192,56,false,true);
    }
    private void save(){
        if(busy||!model.state.has("veinMining"))return;
        try{
            int requested=VeinMiningRules.checkedLimit(Integer.parseInt(limit.getValue()));
            if(!ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE)){message="连接已断开，请重新连接后保存";return;}
            busy=true;message="";ClientPlayNetworking.send(new ShopPayloads.Request("vein_limit","",requested));
        }catch(IllegalArgumentException invalid){message="请填写1～128的整数";}
    }
    public void saved(JsonObject data){busy=false;limit.setValue(data.get("limit").getAsString());message="已保存，实际启用 "+data.get("effective").getAsInt()+" 个方块";}
    public void failed(String error){busy=false;message=error;}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()!=0)return super.mouseClicked(event,doubleClick);
        double x=event.x(),y=event.y();
        if(ControlPanelScreen.hit(x,y,left+panelWidth-68,top+192,56,22)){onClose();return true;}
        String[] sizes={"small","medium","large"};
        for(int i=0;i<3;i++)if(ControlPanelScreen.hit(x,y,left+80+i*34,top+149,30,22)){model.uiSize=sizes[i];model.save();return true;}
        if(busy)return true;
        if(ControlPanelScreen.hit(x,y,left+12,top+192,108,22)){save();return true;}
        if(ControlPanelScreen.hit(x,y,left+panelWidth-84,top+88,72,22)){limit.setValue(Integer.toString(VeinMiningRules.MAX_LIMIT));return true;}
        limit.setFocused(ControlPanelScreen.hit(x,y,limit.getX(),limit.getY(),limit.getWidth(),20));
        if(limit.isFocused())limit.mouseClicked(event,doubleClick);
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(event.key()==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
        if(event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_KP_ENTER){save();return true;}
        return !busy&&limit.isFocused()?limit.keyPressed(event):super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event){return !busy&&limit.isFocused()?limit.charTyped(event):super.charTyped(event);}
    @Override public boolean preeditUpdated(PreeditEvent event){return !busy&&limit.isFocused()?limit.preeditUpdated(event):super.preeditUpdated(event);}
    @Override public void onClose(){model.save();minecraft.gui.setScreen(parent);}
}
