package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.network.ShopPayloads;
import com.google.gson.JsonObject;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Local sale preferences share the existing scrollable personal settings page. */
public final class PlayerSettingsScreen extends Screen {
    private record Hit(int x,int y,int w,Runnable action){}
    private final ControlPanelScreen parent;
    private final ClientCatalog model;
    private final List<Hit> hits=new ArrayList<>();
    private EditBox limit,search;
    private boolean busy;
    private String message="";
    private int left,top,panelWidth,panelHeight,scroll,maxScroll,bodyTop,bodyBottom;
    public PlayerSettingsScreen(ControlPanelScreen parent,ClientCatalog model){super(Component.literal("设置"));this.parent=parent;this.model=model;}
    @Override public boolean isPauseScreen(){return false;}
    private JsonObject settings(){return model.state.has("veinMining")?model.state.getAsJsonObject("veinMining"):new JsonObject();}
    private int value(String key,int fallback){var data=settings();return data.has(key)?data.get(key).getAsInt():fallback;}
    @Override protected void init(){
        String draft=limit==null?Integer.toString(value("limit",VeinMiningRules.MAX_LIMIT)):limit.getValue(),query=search==null?"":search.getValue();
        panelWidth=Math.min(520,width-16);panelHeight=Math.min(560,height-16);left=(width-panelWidth)/2;top=(height-panelHeight)/2;bodyTop=top+30;bodyBottom=top+panelHeight-34;
        limit=new EditBox(font,0,0,Math.max(60,panelWidth-120),20,Component.literal("启用的最大连锁"));limit.setMaxLength(3);limit.setValue(draft);
        search=new EditBox(font,0,0,panelWidth-24,20,Component.literal("搜索物品名或ID"));search.setHint(Component.literal("搜索物品名 / ID（添加或移除）"));search.setMaxLength(100);search.setValue(query);
        search.setResponder(v->scroll=Math.min(scroll,180));
    }
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int color){text(g,s,x,y,color,panelWidth-24);}
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int color,int max){g.text(font,font.plainSubstrByWidth(s,Math.max(0,max)),x,y,color,false);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int w,boolean active,Runnable action){
        g.fill(x,y,x+w,y+22,active?UiTheme.BUTTON:UiTheme.DISABLED);g.text(font,font.plainSubstrByWidth(label,w-10),x+5,y+7,active?0xFFE9EBEE:0xFF777F88,false);
        if(active)hits.add(new Hit(x,y,w,action));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        hits.clear();g.fill(0,0,width,height,UiTheme.OVERLAY);g.fill(left,top,left+panelWidth,top+panelHeight,UiTheme.PANEL);text(g,"设置",left+12,top+11,0xFFE4F5FF);
        g.enableScissor(left,bodyTop,left+panelWidth,bodyBottom);int x=left+12,y=bodyTop-scroll;
        text(g,"矿工 "+value("minerLevel",0)+"级 · 当前连锁上限 "+value("unlocked",8)+" · 实际 "+value("effective",8),x,y,0xFF9DE5C7);
        text(g,"启用的最大连锁（1～128）",x,y+18,0xFFE5EAED);limit.setX(x);limit.setY(y+32);limit.active=!busy;limit.extractRenderState(g,mx,my,delta);
        button(g,"跟随上限",left+panelWidth-92,y+31,80,!busy,()->limit.setValue("128"));
        button(g,"副职业品质彩框："+(model.professionQualityFrames?"开启":"关闭"),x,y+60,panelWidth-24,true,()->{model.professionQualityFrames=!model.professionQualityFrames;model.save();message="已保存本机彩框设置";});
        text(g,"界面大小",x,y+91,0xFFE5EAED);String[] labels={"小","中","大"},sizes={"small","medium","large"};
        for(int i=0;i<3;i++){String size=sizes[i];button(g,labels[i]+(model.uiSize.equals(size)?" ✓":""),x+80+i*48,y+85,44,true,()->{model.uiSize=size;model.save();});}
        text(g,"副职业产品出售名单 · "+model.professionSaleItems.size()+"项",x,y+119,0xFF9DE5C7);
        text(g,"本机共用，包含全部品质；增删立即保存。",x,y+135,0xFFB5C1C9);
        button(g,"恢复默认46项",x,y+150,120,true,()->{model.professionSaleItems.clear();model.professionSaleItems.addAll(ProfessionSaleItems.DEFAULTS);model.save();message="已恢复默认出售名单";});
        search.setX(x);search.setY(y+178);search.setWidth(panelWidth-24);search.extractRenderState(g,mx,my,delta);int rowY=y+204;
        if(!search.getValue().isBlank()){
            Set<String> shown=new HashSet<>();
            for(var item:ItemPickerCatalog.items())if(shown.add(item.id())&&ItemPickerCatalog.contains(search.getValue(),item.name(),item.id())){
                if(rowY>bodyTop-24&&rowY<bodyBottom){g.item(item.stack(),x,rowY+3);text(g,item.name(),x+21,rowY+3,0xFFE7EBEF,panelWidth-110);text(g,item.id(),x+21,rowY+13,0xFF8D9DA8,panelWidth-110);boolean added=model.professionSaleItems.contains(item.id());button(g,added?"移除":"添加",left+panelWidth-66,rowY,54,true,()->{if(!model.professionSaleItems.remove(item.id()))model.professionSaleItems.add(item.id());model.save();message="已保存本机出售名单";});}rowY+=26;
            }
        }else for(String id:new ArrayList<>(model.professionSaleItems)){
            var key=Identifier.tryParse(id);ItemStack stack=key!=null&&BuiltInRegistries.ITEM.containsKey(key)?BuiltInRegistries.ITEM.getValue(key).getDefaultInstance():ItemStack.EMPTY;
            if(rowY>bodyTop-24&&rowY<bodyBottom){if(!stack.isEmpty())g.item(stack,x,rowY+3);text(g,stack.isEmpty()?id:stack.getHoverName().getString(),x+21,rowY+3,0xFFE7EBEF,panelWidth-110);text(g,id,x+21,rowY+13,0xFF8D9DA8,panelWidth-110);button(g,"移除",left+panelWidth-66,rowY,54,true,()->{model.professionSaleItems.remove(id);model.save();message="已保存本机出售名单";});}rowY+=26;
        }
        maxScroll=Math.max(0,rowY+scroll-bodyBottom+8);scroll=Math.clamp(scroll,0,maxScroll);g.disableScissor();hits.removeIf(hit->hit.y()+22<=bodyTop||hit.y()>=bodyBottom);
        if(maxScroll>0){int rail=Math.max(8,bodyBottom-bodyTop),thumb=Math.max(8,rail*rail/(rail+maxScroll)),yy=bodyTop+(rail-thumb)*scroll/maxScroll;g.fill(left+panelWidth-5,bodyTop,left+panelWidth-2,bodyBottom,UiTheme.CARD);g.fill(left+panelWidth-5,yy,left+panelWidth-2,yy+thumb,0xFF8FA9AD);}
        if(!message.isBlank())text(g,message,left+90,top+11,0xFF9DE5C7,panelWidth-102);
        button(g,busy?"正在保存…":"保存连锁设置",left+12,top+panelHeight-27,120,!busy&&model.sync.ready()&&model.state.has("veinMining"),this::save);
        button(g,"返回",left+panelWidth-68,top+panelHeight-27,56,true,this::onClose);
    }
    private void save(){
        if(busy||!model.sync.ready()||!model.state.has("veinMining"))return;
        try{int requested=VeinMiningRules.checkedLimit(Integer.parseInt(limit.getValue()));if(!ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE)){message="连接已断开，请重连后保存";return;}busy=true;message="";ClientPlayNetworking.send(new ShopPayloads.Request("vein_limit","",requested));}
        catch(IllegalArgumentException invalid){message="连锁数量须为1～128的整数";}
    }
    public void refreshed(JsonObject data){if(limit!=null&&!limit.isFocused()&&!busy)limit.setValue(data.get("limit").getAsString());}
    public void saved(JsonObject data){busy=false;limit.setValue(data.get("limit").getAsString());message="已保存连锁数量";}
    public void failed(String error){busy=false;message=error;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(e.button()!=0)return super.mouseClicked(e,twice);
        for(int i=hits.size()-1;i>=0;i--){var hit=hits.get(i);boolean visible=hit.y()>=bodyBottom||e.y()>=bodyTop&&e.y()<bodyBottom;if(visible&&ControlPanelScreen.hit(e.x(),e.y(),hit.x(),hit.y(),hit.w(),22)){hit.action().run();return true;}}
        limit.setFocused(e.y()>=bodyTop&&e.y()<bodyBottom&&ControlPanelScreen.hit(e.x(),e.y(),limit.getX(),limit.getY(),limit.getWidth(),20));
        search.setFocused(e.y()>=bodyTop&&e.y()<bodyBottom&&ControlPanelScreen.hit(e.x(),e.y(),search.getX(),search.getY(),search.getWidth(),20));
        if(limit.isFocused())limit.mouseClicked(e,twice);if(search.isFocused())search.mouseClicked(e,twice);return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){scroll=Math.clamp(scroll-(int)Math.round(sy*30),0,maxScroll);return true;}
    @Override public boolean keyPressed(KeyEvent e){if(e.key()==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}if(limit.isFocused()&&!busy)return limit.keyPressed(e);if(search.isFocused())return search.keyPressed(e);return super.keyPressed(e);}
    @Override public boolean charTyped(CharacterEvent e){return limit.isFocused()&&!busy?limit.charTyped(e):search.isFocused()?search.charTyped(e):super.charTyped(e);}
    @Override public boolean preeditUpdated(PreeditEvent e){return limit.isFocused()&&!busy?limit.preeditUpdated(e):search.isFocused()?search.preeditUpdated(e):super.preeditUpdated(e);}
    @Override public void onClose(){model.save();minecraft.gui.setScreen(parent);}
}
