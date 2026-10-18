package cn.yumis.skyisland.client;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/** Embedded shop form for adding one registered item. It does not open another screen. */
public final class ProductAddPanel {
    private record Hit(int x,int y,int w,int h,Runnable action) {}
    private final ControlPanelScreen parent;
    private final ClientCatalog model;
    private final EditBox search,price,level,stock;
    private final List<Hit> hits=new ArrayList<>();
    private JsonObject snapshot;
    private ItemPickerCatalog.Item selected;
    private String mode="buy_sell",rarity="common";
    private int scroll,maxScroll;
    private boolean applying,priceTouched,levelTouched,rarityTouched;
    public ProductAddPanel(ControlPanelScreen parent){
        this.parent=parent;model=parent.model();
        search=box("物品",80);
        price=box("基价",19);price.setValue("100");
        level=box("解锁等级",3);level.setValue("0");
        stock=box("每日库存",11);stock.setValue("64");
        search.setResponder(this::searchChanged);
        price.setResponder(value->{if(!applying&&price.isFocused())priceTouched=true;});
        level.setResponder(value->{if(!applying&&level.isFocused())levelTouched=true;});
        model.priceBusy=true;model.configMessage="";
        model.editConfig("product_open",new JsonObject());
    }
    private EditBox box(String label,int maximum){
        EditBox result=new EditBox(parent.uiFont(),0,0,120,20,Component.literal(label));
        result.setMaxLength(maximum);result.setCursorPosition(0);result.setHighlightPos(0);return result;
    }
    public void opened(JsonObject value){
        if(value.has("saved")&&value.get("saved").getAsBoolean()){parent.closeProductEditor();return;}
        snapshot=value;
    }
    public void preview(JsonObject value){
        if(selected==null||!selected.id().equals(value.get("itemId").getAsString()))return;
        if(value.get("exists").getAsBoolean()){model.configMessage="这个物品已经在商店里";return;}
        applying=true;
        if(!priceTouched)price.setValue(value.get("suggestedBase").getAsString());
        if(!levelTouched)level.setValue(value.get("level").getAsString());
        if(!rarityTouched)rarity=value.get("rarity").getAsString();
        applying=false;
        model.configMessage="";
    }
    public void scroll(double amount){scroll=Math.clamp(scroll-(int)Math.signum(amount)*35,0,maxScroll);}
    public void render(GuiGraphicsExtractor g,int mx,int my,float delta){
        int x=parent.contentLeft(),w=parent.contentWidth(),y=112-scroll;
        hits.clear();
        g.enableScissor(x,104,parent.width-8,parent.height-62);
        parent.textLine(g,"新增商店物品",x,y,0xFF9DE5C7,w);
        if(snapshot==null){
            parent.textLine(g,model.configMessage.isEmpty()?"正在读取商店配置…":model.configMessage,x,y+24,0xFFB9C3CC,w);
            g.disableScissor();
            parent.actionButton(g,"返回商品",x,parent.height-31,90,true);
            hits.add(new Hit(x,parent.height-31,90,22,parent::closeProductEditor));
            return;
        }
        int yy=y+20;
        for(var line:parent.uiFont().split(Component.literal(snapshot.get("message").getAsString()),w)){g.text(parent.uiFont(),line,x,yy,0xFFB5C1C9,false);yy+=11;}
        parent.textLine(g,"搜索物品名 / ID",x,yy+6,0xFFADB9C3,w);
        field(g,search,x,yy+20,Math.min(280,w),mx,my,delta);
        if(selected!=null){
            g.item(selected.stack(),x,yy+46);
            parent.textLine(g,selected.name()+" · "+selected.id(),x+20,yy+50,0xFFE7EBEF,w-20);
        }
        int form=yy+(selected==null?48:72);
        parent.textLine(g,"基价（购入与收购）",x,form,0xFFADB9C3,w);
        field(g,price,x,form+14,Math.min(180,w),mx,my,delta);
        cycle(g,tradeLabel(),x,form+40,Math.min(150,w),()->mode=switch(mode){case "buy_sell"->"buy_only";case "buy_only"->"sell_only";default->"buy_sell";});
        cycle(g,rarityLabel(),x+Math.min(158,w/2),form+40,Math.min(110,Math.max(70,w/2-8)),()->{rarityTouched=true;rarity=switch(rarity){case "common"->"rare";case "rare"->"epic";case "epic"->"legendary";default->"common";};});
        parent.textLine(g,"解锁等级 0～100",x,form+70,0xFFADB9C3,140);
        field(g,level,x,form+84,90,mx,my,delta);
        parent.textLine(g,"每日库存，-1 为不限",x+110,form+70,0xFFADB9C3,w-110);
        field(g,stock,x+110,form+84,90,mx,my,delta);
        maxScroll=Math.max(0,form+scroll+130-(parent.height-62));
        scroll=Math.clamp(scroll,0,maxScroll);
        g.disableScissor();
        if(search.isFocused())drawSuggestions(g,mx,my,search.getX(),search.getY()+22,search.getWidth());
        parent.textLine(g,model.configMessage,x,parent.height-51,model.configMessage.startsWith("已")?0xFF8BE0B2:0xFFFFAD9F,w);
        parent.actionButton(g,model.priceBusy?"正在保存…":"加入商店",x,parent.height-31,108,snapshot!=null&&!model.priceBusy);
        hits.add(new Hit(x,parent.height-31,108,22,this::save));
        parent.actionButton(g,"返回",x+114,parent.height-31,Math.min(80,w-114),!model.priceBusy);
        hits.add(new Hit(x+114,parent.height-31,80,22,parent::closeProductEditor));
    }
    private void field(GuiGraphicsExtractor g,EditBox box,int x,int y,int w,int mx,int my,float delta){
        box.setX(x);box.setY(y);box.setWidth(Math.max(60,w));box.active=!model.priceBusy;
        box.extractRenderState(g,mx,my,delta);
    }
    private void cycle(GuiGraphicsExtractor g,String label,int x,int y,int w,Runnable action){
        parent.actionButton(g,label,x,y,w,!model.priceBusy);
        if(!model.priceBusy)hits.add(new Hit(x,y,w,22,action));
    }
    private void drawSuggestions(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w){
        var results=ItemPickerCatalog.items().stream().filter(item->!model.products.containsKey(item.id())&&plain(item)&&ItemPickerCatalog.contains(search.getValue(),item.name(),item.id())).limit(6).toList();
        int row=0;
        for(var item:results){
            int sy=y+row*22;
            if(sy>parent.height-70)break;
            boolean hot=ControlPanelScreen.hit(mx,my,x,sy,w,22);
            g.fill(x,sy,x+w,sy+22,hot?0xF02A3338:0xF0181C20);
            g.item(item.stack(),x+2,sy+3);
            parent.textLine(g,item.name(),x+22,sy+6,0xFFE7EBEF,w-26);
            hits.add(new Hit(x,sy,w,22,()->choose(item)));
            row++;
        }
    }
    private void choose(ItemPickerCatalog.Item item){
        selected=item;priceTouched=false;levelTouched=false;rarityTouched=false;applying=true;
        search.setValue(item.name());search.setFocused(false);applying=false;
        JsonObject body=new JsonObject();body.addProperty("itemId",item.id());
        model.configMessage="";model.editConfig("product_preview",body);
    }
    private void searchChanged(String value){
        if(applying)return;
        if(selected!=null&&!value.equals(selected.name())&&!value.equals(selected.id()))selected=null;
    }
    private String tradeLabel(){return switch(mode){case "buy_only"->"仅购买";case "sell_only"->"仅收购";default->"可买可卖";};}
    private String rarityLabel(){return switch(rarity){case "rare"->"稀有";case "epic"->"史诗";case "legendary"->"传说";default->"普通";};}
    private void save(){
        if(snapshot==null||model.priceBusy)return;
        String itemId=selected!=null?selected.id():search.getValue().trim();
        if(itemId.isBlank()){model.configMessage="请先选择物品";return;}
        if(model.products.containsKey(itemId)){model.configMessage="这个物品已经在商店里";return;}
        long base;int unlock;long inventory;
        try{
            base=Long.parseLong(price.getValue());unlock=Integer.parseInt(level.getValue());inventory=Long.parseLong(stock.getValue());
            if(base<1||unlock<0||unlock>100||inventory==0||inventory<-1)throw new NumberFormatException();
        }catch(NumberFormatException error){model.configMessage="基价为正整数，等级 0～100，库存为正整数或 -1";return;}
        JsonObject body=new JsonObject();
        body.addProperty("itemId",itemId);
        body.addProperty("name",selected!=null?selected.name():itemId);
        body.addProperty("shopCategory",selected==null?"other":category(selected));
        body.addProperty("buyBase",base);
        body.addProperty("tradeMode",mode);
        body.addProperty("rarity",rarity);
        body.addProperty("level",unlock);
        body.addProperty("dailyStock",inventory);
        body.add("revision",snapshot.get("revision"));
        body.add("digest",snapshot.get("digest"));
        body.add("levelsDigest",snapshot.get("levelsDigest"));
        body.add("raritiesDigest",snapshot.get("raritiesDigest"));
        body.add("levelsRevision",snapshot.get("levelsRevision"));
        body.add("raritiesRevision",snapshot.get("raritiesRevision"));
        model.priceBusy=true;model.configMessage="";model.editConfig("product_add",body);
    }
    private static boolean plain(ItemPickerCatalog.Item item){
        var registered=BuiltInRegistries.ITEM.getValue(Identifier.parse(item.id()));
        return ItemStack.isSameItemSameComponents(item.stack(),registered.getDefaultInstance());
    }
    private static String category(ItemPickerCatalog.Item item){
        for(CreativeModeTab tab:CreativeModeTabs.tabs()){
            if(tab.getType()!=CreativeModeTab.Type.CATEGORY)continue;
            for(var stack:tab.getDisplayItems())if(stack.is(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(item.id()))))
                return BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString();
        }
        return "other";
    }
    public boolean click(MouseButtonEvent event,boolean doubleClick){
        for(int i=hits.size()-1;i>=0;i--){
            Hit hit=hits.get(i);
            if(ControlPanelScreen.hit(event.x(),event.y(),hit.x,hit.y,hit.w,hit.h)){hit.action.run();return true;}
        }
        if(snapshot==null)return true;
        boolean field=false;
        for(EditBox box:List.of(search,price,level,stock)){
            boolean inside=ControlPanelScreen.hit(event.x(),event.y(),box.getX(),box.getY(),box.getWidth(),20);
            box.setFocused(inside);
            if(inside){box.mouseClicked(event,doubleClick);field=true;}
        }
        return field||event.y()>=104;
    }
    public boolean key(KeyEvent event){return focused()!=null&&focused().keyPressed(event);}
    public boolean typed(CharacterEvent event){return focused()!=null&&focused().charTyped(event);}
    public boolean preedit(PreeditEvent event){return focused()!=null&&focused().preeditUpdated(event);}
    private EditBox focused(){for(EditBox box:List.of(search,price,level,stock))if(box.isFocused())return box;return null;}
}
