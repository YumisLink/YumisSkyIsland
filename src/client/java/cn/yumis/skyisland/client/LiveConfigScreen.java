package cn.yumis.skyisland.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Standalone administrator form; configuration groups are peers, with explicit saves. */
public final class LiveConfigScreen extends Screen {
    private record Input(JsonObject spec,EditBox box) {}
    private final ClientCatalog model;
    private final List<Input> inputs=new ArrayList<>();
    private JsonObject draft=new JsonObject();
    private int scroll;
    private static final int LEFT=132,TOP=96,ROW=65;
    public LiveConfigScreen(ClientCatalog model){super(Component.literal("实时配置"));this.model=model;}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){if(inputs.isEmpty())updated();}
    public void updated(){
        if(font==null)return;inputs.clear();draft=new JsonObject();scroll=0;
        if(!model.configState.has("fields"))return;
        for(var value:model.configState.getAsJsonArray("fields")){
            JsonObject spec=value.getAsJsonObject();String key=spec.get("key").getAsString();draft.add(key,spec.get("value").deepCopy());
            EditBox box=null;
            if(!spec.get("kind").getAsString().equals("boolean")){
                box=new EditBox(font,0,0,100,20,Component.literal(spec.get("label").getAsString()));box.setMaxLength(spec.get("kind").getAsString().equals("text")?512:20);box.setValue(spec.get("value").getAsString());box.setCursorPosition(0);box.setHighlightPos(0);
            }
            inputs.add(new Input(spec,box));
        }
    }
    private void text(GuiGraphicsExtractor g,String text,int x,int y,int color,int max){g.text(font,font.plainSubstrByWidth(text,Math.max(0,max)),x,y,color,false);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int w,boolean selected,boolean active){g.fill(x,y,x+w,y+22,!active?UiTheme.DISABLED:selected?UiTheme.SELECTED:UiTheme.BUTTON);text(g,label,x+5,y+7,active?0xFFE9EBEE:0xFF777F88,w-10);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiTheme.OVERLAY);text(g,"管理员 · 实时配置",12,14,0xFFE4EEE9,width-24);
        if(!model.configState.has("fields"))return;
        int index=0;for(var value:model.configState.getAsJsonArray("groups")){var group=value.getAsJsonObject();button(g,group.get("name").getAsString(),8,38+index++*22,116,group.get("id").equals(model.configState.get("group")),!model.configBusy);}
        int w=width-LEFT-12;
        text(g,model.configState.get("name").getAsString(),LEFT,39,0xFF9DE5C7,w);
        int line=0;for(var wrapped:font.split(Component.literal(model.configState.get("help").getAsString()),w)){if(line>=3)break;g.text(font,wrapped,LEFT,56+line++*11,0xFFB5C1C9,false);}
        scroll=Math.clamp(scroll,0,Math.max(0,inputs.size()*ROW-(height-TOP-65)));
        g.enableScissor(LEFT,TOP,width-8,height-62);
        for(int i=0;i<inputs.size();i++){
            Input input=inputs.get(i);JsonObject spec=input.spec;int y=TOP+i*ROW-scroll;
            text(g,spec.get("label").getAsString(),LEFT,y+1,0xFFE5EAED,w);
            boolean bool=spec.get("kind").getAsString().equals("boolean");
            String note=spec.get("help").getAsString();if(!bool&&!spec.get("kind").getAsString().equals("text"))note=spec.get("minimum").getAsString()+"～"+spec.get("maximum").getAsString()+" · "+note;
            text(g,note,LEFT,y+14,0xFFAEBAC3,w);
            if(bool)button(g,draft.get(spec.get("key").getAsString()).getAsBoolean()?"开启":"关闭",LEFT,y+29,80,draft.get(spec.get("key").getAsString()).getAsBoolean(),!model.configBusy);
            else{input.box.setX(LEFT);input.box.setY(y+29);input.box.setWidth(w);input.box.active=!model.configBusy;input.box.visible=y+49>TOP&&y+29<height-62;if(input.box.visible)input.box.extractRenderState(g,mx,my,delta);}
        }
        g.disableScissor();
        text(g,model.configMessage,LEFT,height-54,model.configMessage.startsWith("已保存")?0xFF8BE0B2:0xFFFFAD9F,w);
        button(g,model.configBusy?"正在保存…":"保存并生效",LEFT,height-30,108,false,!model.configBusy);button(g,"返回",LEFT+114,height-30,56,false,true);
    }
    private void save(){
        try{
            for(var input:inputs)if(input.box!=null){String text=input.box.getValue();draft.add(input.spec.get("key").getAsString(),input.spec.get("kind").getAsString().equals("text")?new JsonPrimitive(text):new JsonPrimitive(new java.math.BigDecimal(text)));}
            JsonObject body=new JsonObject();body.add("group",model.configState.get("group"));body.add("revision",model.configState.get("revision"));body.add("digest",model.configState.get("digest"));body.add("changes",draft.deepCopy());model.configBusy=true;model.configMessage="";model.editConfig("save",body);
        }catch(RuntimeException error){model.configMessage="请填写有效整数，开关和文案按对应格式填写";}
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(ControlPanelScreen.hit(e.x(),e.y(),LEFT+114,height-30,56,22)){onClose();return true;}
        if(model.configBusy)return true;
        if(ControlPanelScreen.hit(e.x(),e.y(),LEFT,height-30,108,22)){save();return true;}
        int index=0;for(var value:model.configState.getAsJsonArray("groups")){var group=value.getAsJsonObject();if(ControlPanelScreen.hit(e.x(),e.y(),8,38+index++*22,116,22)){JsonObject body=new JsonObject();body.add("group",group.get("id"));model.editConfig("open",body);return true;}}
        for(var input:inputs)if(input.box!=null)input.box.setFocused(false);
        if(e.y()<TOP||e.y()>=height-62)return true;
        for(int i=0;i<inputs.size();i++){var input=inputs.get(i);int y=TOP+i*ROW-scroll+29;
            if(input.box!=null&&ControlPanelScreen.hit(e.x(),e.y(),LEFT,y,width-LEFT-12,20)){input.box.setFocused(true);input.box.mouseClicked(e,doubleClick);return true;}
            if(input.box==null&&ControlPanelScreen.hit(e.x(),e.y(),LEFT,y,80,22)){String key=input.spec.get("key").getAsString();draft.addProperty(key,!draft.get(key).getAsBoolean());return true;}
        }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}for(var input:inputs)if(input.box!=null&&input.box.isFocused())return input.box.keyPressed(event);return super.keyPressed(event);}
    @Override public boolean charTyped(CharacterEvent event){for(var input:inputs)if(input.box!=null&&input.box.isFocused())return input.box.charTyped(event);return super.charTyped(event);}
    @Override public boolean preeditUpdated(PreeditEvent event){for(var input:inputs)if(input.box!=null&&input.box.isFocused())return input.box.preeditUpdated(event);return super.preeditUpdated(event);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){scroll=Math.max(0,scroll-(int)Math.signum(sy)*ROW);return true;}
    @Override public void onClose(){model.configWanted=false;super.onClose();}
}
