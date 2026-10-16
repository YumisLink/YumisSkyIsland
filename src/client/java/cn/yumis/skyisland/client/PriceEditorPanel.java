package cn.yumis.skyisland.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import cn.yumis.skyisland.core.Money;

/** Purchase price form embedded in either shop; it cannot change sale value or supply. */
public final class PriceEditorPanel {
    private final ControlPanelScreen parent;
    private final ClientCatalog model;
    public final String product;
    private JsonObject snapshot;
    private EditBox price;
    private int scroll,maxScroll;
    public void scroll(double amount){scroll=Math.clamp(scroll-(int)Math.signum(amount)*35,0,maxScroll);}
    public PriceEditorPanel(ControlPanelScreen parent,String product){this.parent=parent;model=parent.model();this.product=product;JsonObject body=new JsonObject();body.addProperty("product",product);model.priceBusy=true;model.configMessage="";model.editConfig("price_open",body);}
    public void updated(JsonObject value){if(!product.equals(value.get("product").getAsString()))return;snapshot=value;price=new EditBox(parent.uiFont(),0,0,120,20,Component.literal("购入基价"));price.setMaxLength(19);price.setValue(value.get("buyBase").getAsString());price.setCursorPosition(0);price.setHighlightPos(0);}
    public void render(GuiGraphicsExtractor g,int mx,int my,float delta){
        int x=parent.contentLeft(),w=parent.contentWidth(),y=112-scroll;
        g.enableScissor(x,104,parent.width-8,parent.height-62);
        parent.textLine(g,"编辑全服购入基价",x,y,0xFF9DE5C7,w);
        if(snapshot==null){parent.textLine(g,model.configMessage.isEmpty()?"正在读取商品配置…":model.configMessage,x,y+25,0xFFB9C3CC,w);g.disableScissor();parent.actionButton(g,"返回商品",x,parent.height-31,90,true);return;}
        parent.textLine(g,snapshot.get("name").getAsString(),x,y+22,0xFFE7EBEF,w);
        int yy=y+41;for(var line:parent.uiFont().split(Component.literal(snapshot.get("message").getAsString()),w)){g.text(parent.uiFont(),line,x,yy,0xFFB5C1C9,false);yy+=11;}
        boolean editable=snapshot.get("editable").getAsBoolean();
        price.setX(x);price.setY(yy+9);price.setWidth(Math.min(220,w));price.active=editable&&!model.priceBusy;price.extractRenderState(g,mx,my,delta);
        yy+=40;
        var p=model.products.get(product);
        if(p!=null)try{
            long value=Long.parseLong(price.getValue());if(value<1)throw new NumberFormatException();
            var preview=new ClientCatalog.Product(p.id(),p.name(),p.stack(),p.canonical(),p.base(),value,p.buyMultiplierBps(),p.rarity(),p.level(),p.buy(),p.sell(),p.category(),p.quotaGroup(),p.units(),p.starter());
            parent.textLine(g,"普通店预览："+Money.format(model.price(preview,0)),x,yy,0xFFE9D095,w);
            parent.textLine(g,"每日／隐藏预览："+Money.format(model.price(preview,1)),x,yy+18,0xFFE9D095,w);
        }catch(RuntimeException error){parent.textLine(g,"请输入可成交的正整数基价",x,yy,0xFFFFAD9F,w);}
        maxScroll=Math.max(0,yy+scroll+38-(parent.height-62));scroll=Math.clamp(scroll,0,maxScroll);g.disableScissor();
        parent.textLine(g,model.configMessage,x,parent.height-51,model.configMessage.startsWith("已保存")?0xFF8BE0B2:0xFFFFAD9F,w);
        parent.actionButton(g,model.priceBusy?"正在保存…":"保存并生效",x,parent.height-31,108,editable&&!model.priceBusy);parent.actionButton(g,"返回",x+114,parent.height-31,Math.min(80,w-114),!model.priceBusy);
    }
    public boolean click(MouseButtonEvent e,boolean doubleClick){
        int x=parent.contentLeft();
        if(ControlPanelScreen.hit(e.x(),e.y(),x+(snapshot==null?0:114),parent.height-31,snapshot==null?90:80,22)&&!model.priceBusy){parent.closePriceEditor();return true;}
        if(snapshot==null)return true;
        if(ControlPanelScreen.hit(e.x(),e.y(),x,parent.height-31,108,22)&&snapshot.get("editable").getAsBoolean()&&!model.priceBusy){
            try{long value=Long.parseLong(price.getValue());if(value<1)throw new NumberFormatException();JsonObject body=new JsonObject();body.addProperty("product",product);body.add("revision",snapshot.get("revision"));body.add("digest",snapshot.get("digest"));body.addProperty("buyBase",value);model.priceBusy=true;model.configMessage="";model.editConfig("price_save",body);}catch(NumberFormatException error){model.configMessage="购入基价必须为正整数";}return true;
        }
        if(e.y()>=104&&e.y()<parent.height-62){price.mouseClicked(e,doubleClick);price.setFocused(ControlPanelScreen.hit(e.x(),e.y(),price.getX(),price.getY(),price.getWidth(),20));}else price.setFocused(false);return true;
    }
    public boolean key(KeyEvent event){return price!=null&&price.isFocused()&&price.keyPressed(event);}
    public boolean typed(CharacterEvent event){return price!=null&&price.isFocused()&&price.charTyped(event);}
    public boolean preedit(PreeditEvent event){return price!=null&&price.isFocused()&&price.preeditUpdated(event);}
}
