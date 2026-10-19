package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.Money;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** One embedded, scrollable registry catalog and inline economic editor. */
public final class ProductAddPanel {
    private record Hit(int x,int y,int w,int h,Runnable action,boolean footer){}
    private record Row(String product,String id,String name,ItemStack stack,ItemPickerCatalog.Item item){}
    private final ControlPanelScreen parent;
    private final ClientCatalog model;
    private final EditBox search;
    private final LinkedHashMap<String,EditBox> fields=new LinkedHashMap<>();
    private final List<Hit> hits=new ArrayList<>();
    private List<Row> rows=List.of();
    private JsonObject snapshot;
    private int filter,scroll,maxScroll,catalogRevision=-1,bodyTop,bodyBottom,formHeight,ticks,lastPreview;
    private boolean applying,pendingPreview;
    private String mode="buy_sell",rarity="common";
    private boolean enabled=true;
    public ProductAddPanel(ControlPanelScreen parent){
        this.parent=parent;model=parent.model();search=box("搜索物品名 / ID",120);search.setHint(Component.literal("搜索物品名 / ID"));
        for(String key:List.of("buyBaseValueCoins","baseValueCoins","unlockLevel","dailyStock","buildFee"))fields.put(key,box(key,19));
        search.setResponder(v->{if(!applying)scroll=snapshot!=null&&snapshot.has("product")?formHeight:0;});
        for(var box:fields.values())box.setResponder(v->{if(!applying)pendingPreview=true;});
        model.priceBusy=true;model.configMessage="";model.editConfig("item_open",new JsonObject());
    }
    private EditBox box(String label,int length){var box=new EditBox(parent.uiFont(),0,0,120,20,Component.literal(label));box.setMaxLength(length);return box;}
    public void opened(JsonObject data){
        snapshot=data;model.priceBusy=false;applying=true;
        if(data.has("fields")){
            var values=data.getAsJsonObject("fields");
            fields.forEach((key,box)->box.setValue(values.has(key)?values.get(key).getAsString():""));
            mode=values.get("tradeMode").getAsString();rarity=values.get("rarity").getAsString();enabled=values.get("tradeEnabled").getAsBoolean();
        }
        applying=false;pendingPreview=false;
    }
    public void preview(JsonObject data){opened(data);}
    public void tick(){ticks++;if(pendingPreview&&!model.priceBusy&&snapshot!=null&&snapshot.has("product")&&ticks-lastPreview>=8){
        try{JsonObject body=body();pendingPreview=false;lastPreview=ticks;model.priceBusy=true;model.editConfig("item_preview",body);}catch(IllegalArgumentException incomplete){/* Keep incomplete numeric drafts editable. */}
    }}
    public void scroll(double amount){scroll=Math.clamp(scroll-(int)Math.round(amount*32),0,maxScroll);}
    private void rebuild(){
        if(catalogRevision==model.revision)return;catalogRevision=model.revision;
        var result=new ArrayList<Row>();Map<net.minecraft.world.item.Item,List<ClientCatalog.Product>> byItem=new HashMap<>();
        for(var p:model.products.values()){result.add(new Row(p.id(),p.canonical().get("itemId").getAsString(),p.name(),p.stack(),null));byItem.computeIfAbsent(p.stack().getItem(),k->new ArrayList<>()).add(p);}
        for(var item:ItemPickerCatalog.items()){
            boolean defined=false;for(var p:byItem.getOrDefault(item.stack().getItem(),List.of()))if(ItemStack.isSameItemSameComponents(p.stack(),item.stack())){defined=true;break;}
            if(!defined)result.add(new Row(null,item.id(),item.name(),item.stack(),item));
        }
        result.sort(Comparator.comparing(Row::name).thenComparing(Row::id).thenComparing(r->r.product()==null?"":r.product()));rows=List.copyOf(result);
    }
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int color,int max){parent.textLine(g,s,x,y,color,max);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int w,boolean active,Runnable action,boolean footer){
        parent.actionButton(g,label,x,y,w,active);if(active)hits.add(new Hit(x,y,w,22,action,footer));
    }
    public void render(GuiGraphicsExtractor g,int mx,int my,float delta){
        rebuild();hits.clear();int x=parent.contentLeft(),w=parent.contentWidth();
        text(g,"物品管理 · "+rows.size()+"项",x,76,0xFF9DE5C7,w);
        search.setX(x);search.setY(92);search.setWidth(w);search.active=!model.priceBusy;search.extractRenderState(g,mx,my,delta);
        String[] filters={"全部","已定价","未定价","已停用"};int bx=x,by=116;
        for(int i=0;i<4;i++){final int value=i;int bw=Math.min(72,(w-12)/4);button(g,(filter==i?"● ":"")+filters[i],bx,by,bw,!model.priceBusy,()->{filter=value;scroll=snapshot!=null&&snapshot.has("product")?formHeight:0;},true);bx+=bw+4;}
        bodyTop=142;bodyBottom=parent.height-62;int y=bodyTop-scroll;
        g.enableScissor(x,bodyTop,parent.width-8,bodyBottom);
        if(snapshot!=null&&snapshot.has("product")){int start=y;y=drawForm(g,x,y,w,mx,my,delta);formHeight=y-start;}else formHeight=0;
        int count=0;
        for(var row:rows){
            boolean defined=row.product()!=null,disabled=defined&&model.disabledProducts.contains(row.product());
            if(filter==1&&!defined||filter==2&&defined||filter==3&&!disabled)continue;
            if(!ItemPickerCatalog.contains(search.getValue(),row.name(),row.id())&&(row.product()==null||!row.product().contains(search.getValue())))continue;
            count++;if(y+34>bodyTop&&y<bodyBottom){
                g.fill(x,y,x+w,y+34,UiTheme.CARD);QualityFrames.draw(g,row.stack(),x,y,w,34);g.item(row.stack(),x+3,y+8);
                text(g,row.name(),x+24,y+4,disabled?0xFF889299:0xFFE7EBEF,w-28);
                text(g,row.product()==null?row.id():row.product(),x+24,y+15,0xFF94A5AF,w-28);
                var p=defined?model.products.get(row.product()):null;
                text(g,!defined?"未定价":disabled?"已停用 · 原配置保留":(row.stack().is(cn.yumis.skyisland.prefab.PrefabItems.AMMUNITION)?"固定购 "+Money.format(model.prefabAmmunitionPrice):"购基 "+Money.format(p.buyBase()))+" · 收估 "+Money.format(p.base()),x+24,y+25,0xFFB8C6C7,w-28);
                final Row chosen=row;hits.add(new Hit(x,y,w,34,()->choose(chosen),false));
            }y+=38;
        }
        if(count==0){text(g,"没有匹配物品",x,y+5,0xFFB5C1C9,w);y+=24;}
        maxScroll=Math.max(0,y+scroll-bodyBottom+8);scroll=Math.clamp(scroll,0,maxScroll);g.disableScissor();
        if(maxScroll>0){int rail=Math.max(8,bodyBottom-bodyTop),thumb=Math.max(8,rail*rail/(rail+maxScroll)),yy=bodyTop+(rail-thumb)*scroll/maxScroll;g.fill(x+w-3,bodyTop,x+w,bodyBottom,UiTheme.CARD);g.fill(x+w-3,yy,x+w,yy+thumb,0xFF8FA9AD);}
        text(g,model.configMessage,x,parent.height-51,model.configMessage.startsWith("已")?0xFF8BE0B2:0xFFFFAD9F,w);
        boolean selected=snapshot!=null&&snapshot.has("product");
        button(g,model.priceBusy?"正在处理…":selected&&snapshot.get("exists").getAsBoolean()?"保存修改":"加入物品配置",x,parent.height-31,112,selected&&!model.priceBusy,this::save,true);
        button(g,"返回",x+118,parent.height-31,Math.min(76,w-118),!model.priceBusy,parent::closeProductEditor,true);
    }
    private int drawForm(GuiGraphicsExtractor g,int x,int y,int w,int mx,int my,float delta){
        text(g,"当前："+model.decode(snapshot.get("stack")).getHoverName().getString(),x,y+2,0xFF9DE5C7,w);y+=17;
        text(g,snapshot.get("source").getAsString(),x,y,0xFF99ADB7,w);y+=17;
        boolean blueprint=snapshot.get("blueprint").getAsBoolean(),fixed=snapshot.get("fixedPurchase").getAsBoolean();
        Map<String,String> labels=new LinkedHashMap<>();labels.put("buyBaseValueCoins",fixed?"购买实际金额（固定）":"购入基价");labels.put("baseValueCoins","收购估值基准");labels.put("unlockLevel","解锁岛等级 0～100");labels.put("dailyStock","每日库存（-1不限）");if(blueprint)labels.put("buildFee","成品费（后续开工）");
        int columns=w>=390?2:1,fieldWidth=(w-(columns-1)*12)/columns,index=0,start=y;
        for(var value:labels.entrySet()){
            int xx=x+(index%columns)*(fieldWidth+12),yy=start+(index/columns)*42;
            text(g,value.getValue(),xx,yy,0xFFADB9C3,fieldWidth);
            var box=fields.get(value.getKey());box.setX(xx);box.setY(yy+13);box.setWidth(fieldWidth);box.active=!model.priceBusy;box.extractRenderState(g,mx,my,delta);index++;
        }y=start+((index+columns-1)/columns)*42;
        String modeLabel=switch(mode){case "buy_only"->"仅购买";case "sell_only"->"仅收购";default->"可买可卖";};
        int xx=x;String[] names={modeLabel,cn.yumis.skyisland.core.RarityColors.name(rarity),enabled?"交易：开启":"交易：停用"};
        for(int i=0;i<3;i++){
            int bw=Math.min(105,w);if(xx+bw>x+w){xx=x;y+=25;}final int button=i;
            button(g,names[i],xx,y,bw,!model.priceBusy,()->{if(button==0)mode=switch(mode){case "buy_sell"->"buy_only";case "buy_only"->"sell_only";default->"buy_sell";};else if(button==1)rarity=switch(rarity){case "common"->"rare";case "rare"->"epic";case "epic"->"legendary";default->"common";};else enabled=!enabled;pendingPreview=true;},false);xx+=bw+5;
        }y+=30;
        text(g,snapshot.get("previewContext").getAsString(),x,y,0xFF99ADB7,w);y+=13;
        text(g,"常驻 "+Money.format(snapshot.get("permanentPrice").getAsLong())+" · 每日/隐藏 "+Money.format(snapshot.get("specialPrice").getAsLong()),x,y,0xFFFFDC78,w);y+=13;
        text(g,"普通单件收购预估："+Money.format(snapshot.get("currentSale").getAsLong()),x,y,0xFF9DE5C7,w);y+=13;
        if(fixed){text(g,"每份 "+snapshot.get("ammunitionCredits").getAsInt()+" 次；仅完整弹药可回收",x,y,0xFF99ADB7,w);y+=13;}
        text(g,"每日库存改动下次04:00采用；今日不补货。",x,y,0xFF99ADB7,w);y+=21;
        return y;
    }
    private void choose(Row row){
        if(model.priceBusy)return;JsonObject body=new JsonObject();if(row.product()!=null)body.addProperty("product",row.product());else body.add("stack",encode(row.item()));
        search.setFocused(false);fields.values().forEach(box->box.setFocused(false));scroll=0;model.priceBusy=true;model.configMessage="";model.editConfig("item_open",body);
    }
    private JsonElement encode(ItemPickerCatalog.Item item){
        JsonObject stack=new JsonObject();stack.addProperty("id",item.id());stack.addProperty("count",1);stack.add("components",item.components().deepCopy());return stack;
    }
    private JsonObject body(){
        JsonObject body=new JsonObject();if(snapshot.get("exists").getAsBoolean())body.addProperty("product",snapshot.get("product").getAsString());else body.add("stack",snapshot.get("stack").deepCopy());
        for(String key:List.of("revision","digest","sourceDigests","prefabDigest"))body.add(key,snapshot.get(key).deepCopy());
        JsonObject changes=new JsonObject();
        for(var value:fields.entrySet()){
            if(value.getKey().equals("buildFee")&&!snapshot.get("blueprint").getAsBoolean())continue;
            try{long n=Long.parseLong(value.getValue().getValue());if(value.getKey().equals("unlockLevel")?n<0||n>100:value.getKey().equals("dailyStock")?n==0||n< -1||n>Integer.MAX_VALUE:n<1)throw new NumberFormatException();changes.addProperty(value.getKey(),n);}
            catch(NumberFormatException invalid){throw new IllegalArgumentException("金额须为正整数，等级0～100，库存正数或-1");}
        }
        changes.addProperty("tradeMode",mode);changes.addProperty("rarity",rarity);changes.addProperty("tradeEnabled",enabled);body.add("changes",changes);return body;
    }
    private void save(){if(model.priceBusy||snapshot==null||!snapshot.has("product"))return;try{var body=body();model.priceBusy=true;model.configMessage="";pendingPreview=false;model.editConfig("item_save",body);}catch(IllegalArgumentException invalid){model.configMessage=invalid.getMessage();}}
    public boolean click(MouseButtonEvent e,boolean twice){
        for(int i=hits.size()-1;i>=0;i--){var hit=hits.get(i);if((hit.footer()||e.y()>=bodyTop&&e.y()<bodyBottom)&&ControlPanelScreen.hit(e.x(),e.y(),hit.x(),hit.y(),hit.w(),hit.h())){hit.action().run();return true;}}
        search.setFocused(ControlPanelScreen.hit(e.x(),e.y(),search.getX(),search.getY(),search.getWidth(),20));if(search.isFocused())search.mouseClicked(e,twice);
        for(var box:fields.values()){box.setFocused(e.y()>=bodyTop&&e.y()<bodyBottom&&box.active&&ControlPanelScreen.hit(e.x(),e.y(),box.getX(),box.getY(),box.getWidth(),20));if(box.isFocused())box.mouseClicked(e,twice);}return true;
    }
    public boolean key(KeyEvent e){if(e.key()==GLFW.GLFW_KEY_ENTER&&!search.isFocused()){save();return true;}if(search.isFocused())return search.keyPressed(e);for(var box:fields.values())if(box.isFocused())return box.keyPressed(e);return true;}
    public boolean typed(CharacterEvent e){if(search.isFocused())return search.charTyped(e);for(var box:fields.values())if(box.isFocused())return box.charTyped(e);return true;}
    public boolean preedit(PreeditEvent e){if(search.isFocused())return search.preeditUpdated(e);for(var box:fields.values())if(box.isFocused())return box.preeditUpdated(e);return true;}
}
