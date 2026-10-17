package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.FruitText;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.network.ShopPayloads;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Personal profession stores and the server's effective experience catalog. */
public final class ProfessionScreen extends Screen {
    private static final List<String> IDS=List.of("blacksmith","chef","farmer","hunter","miner","fisherman");
    private static final List<String> NAMES=List.of("铁匠","厨师","农夫","猎人","矿工","渔夫");
    private final ClientCatalog model;
    private int tab;
    private int fishingScroll;
    private int guideScroll;
    private boolean guideMode;
    private static final int FISHING_TOP=169;
    public ProfessionScreen(ClientCatalog model){super(Component.literal("职业商店"));this.model=model;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float delta){}
    private static boolean inside(double x,double y,int l,int t,int w,int h){return x>=l&&x<l+w&&y>=t&&y<t+h;}
    private int tabWidth(){return Math.max(32,Math.min(100,(width-18)/IDS.size()-4));}
    private String selected(){return IDS.get(tab);}
    private BigInteger coins(){
        var all=model.state.getAsJsonObject("professionCoins");
        return all==null||!all.has(selected())?BigInteger.ZERO:new BigInteger(all.get(selected()).getAsString());
    }
    private long price(String key){
        var row=model.state.getAsJsonObject("professionShop");
        return row==null||!row.has(key)?0:row.get(key).getAsLong();
    }
    private boolean unlocked(String key){
        var list=model.state.getAsJsonArray("unlockedAccessories");
        if(list==null)return false;
        for(var entry:list)if(entry.getAsString().equals(key))return true;
        return false;
    }
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int w,boolean active,boolean hover){
        g.fill(x,y,x+w,y+25,active?UiTheme.CARD:hover?UiTheme.CARD:UiTheme.CARD);
        g.text(font,label,x+7,y+8,active?0xFFE1FFF4:0xFFD7E0E8,false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xB0000000);
        g.fill(6,6,width-6,36,UiTheme.CARD);
        g.text(font,"YUMIS · 职业",15,17,0xFFF3F5F7,false);
        int tw=tabWidth();
        for(int i=0;i<IDS.size();i++)button(g,NAMES.get(i),8+i*(tw+4),46,tw,i==tab,inside(mx,my,8+i*(tw+4),46,tw,25));
        g.fill(8,82,width-8,height-12,UiTheme.CARD);
        var progress=model.state.getAsJsonObject("professionProgress");
        var entry=progress==null?null:progress.getAsJsonObject(selected());
        String level=entry==null?"0":""+entry.get("level").getAsInt();
        String xp=entry==null?"0/0":entry.get("xp").getAsString()+"/"+entry.get("next").getAsString();
        g.text(font,NAMES.get(tab)+"等级 " +level+" · 经验 " +xp,20,97,0xFFE4F5FF,false);
        g.text(font,"个人职业积分："+coins(),20,116,0xFFFFDD74,false);
        button(g,"商店",20,137,72,!guideMode,inside(mx,my,20,137,72,25));
        button(g,"职业典籍",96,137,94,guideMode,inside(mx,my,96,137,94,25));
        if(guideMode)guide(g);
        else if(tab<IDS.size()-1){
            ItemStack fruit=new ItemStack(ModItems.FRUITS.get(selected()));
            g.fill(19,173,width-19,238,UiTheme.CARD);g.item(fruit,30,188);
            g.text(font,NAMES.get(tab)+"果实",59,184,0xFFF0E8B0,false);
            g.text(font,FruitText.line(selected(),used()),59,202,0xFFC9D5DD,false);
            g.text(font,"已使用 "+used()+"/"+Math.clamp(model.state.getAsJsonObject("professionShop").get("fruitUseCap").getAsInt(),1,100),59,218,0xFFC9D5DD,false);
            button(g,"购买 · "+price("fruitPrice")+" 职业积分",Math.max(20,width-235),197,215,false,inside(mx,my,Math.max(20,width-235),197,215,25));
        } else {
            fishing(g,mx,my);
        }
        if(!model.lastError.isBlank())g.text(font,model.lastError,20,height-34,0xFFFFAAAA,false);
    }
    private JsonArray guideRows(){
        JsonObject all=model.state.getAsJsonObject("professionGuide");
        return all==null||!all.has(selected())?new JsonArray():all.getAsJsonArray(selected());
    }
    private int guideMax(){return Math.max(0,guideRows().size()*25-(height-222));}
    private void guide(GuiGraphicsExtractor g){
        g.text(font,"品质参考等级不限制获得经验；高出10～29级经验减半，30级及以上仅得1点。",20,169,0xFFB8D9D7,false);
        int top=190,bottom=height-24;guideScroll=Math.clamp(guideScroll,0,guideMax());
        g.enableScissor(18,top,width-18,bottom);
        JsonArray rows=guideRows();
        for(int i=0;i<rows.size();i++){
            int y=top+i*25-guideScroll;if(y+25<top||y>=bottom)continue;
            JsonObject row=rows.get(i).getAsJsonObject();String id=row.get("id").getAsString();
            if(i%2==0)g.fill(20,y,width-20,y+24,0x66383838);
            String name=id;
            try{Identifier key=Identifier.parse(id);if(BuiltInRegistries.ITEM.containsKey(key)){
                ItemStack item=new ItemStack(BuiltInRegistries.ITEM.getValue(key));g.item(item,24,y+4);name=item.getHoverName().getString();
            }}catch(IllegalArgumentException ignored){}
            g.text(font,name,46,y+3,0xFFE2EDF0,false);
            g.text(font,row.get("source").getAsString()+" · 参考"+row.get("itemLevel").getAsInt()+"级 · 基础"+row.get("baseXp").getAsLong()+"经验",46,y+14,0xFFC0D2D5,false);
        }
        g.disableScissor();
    }
    private int used(){var all=model.state.getAsJsonObject("consumedFruits");return all==null||!all.has(selected())?0:all.get(selected()).getAsInt();}
    private static String number(double value){return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private static String signed(double value){return (value>=0?"+":"")+number(value);}
    private List<FormattedCharSequence> fishingLines(){
        List<String> lines=new ArrayList<>();
        var power=model.state.getAsJsonObject("fishingPower");
        if(power!=null){
            lines.add("基础值："+number(power.get("base").getAsDouble()));
            for(var element:power.getAsJsonArray("sources")){
                var source=element.getAsJsonObject();
                double amount=source.get("amount").getAsDouble();
                String value=switch(source.get("operation").getAsString()){
                    case "ADD_MULTIPLIED_BASE" -> signed(amount*100)+"%（基础加成）";
                    case "ADD_MULTIPLIED_TOTAL" -> signed(amount*100)+"%（总值倍率）";
                    default -> signed(amount);
                };
                lines.add(source.get("name").getAsString()+"："+value);
            }
        }
        List<FormattedCharSequence> wrapped=new ArrayList<>();
        for(String line:lines)wrapped.addAll(font.split(Component.literal(line),Math.max(40,width-64)));
        return wrapped;
    }
    private int fishingBottom(){return height-(model.lastError.isBlank()?32:52);}
    private int fishingStatsHeight(){return 40+fishingLines().size()*13;}
    private boolean compactBait(){return width<520;}
    private int baitHeight(){return compactBait()?88:65;}
    private int baitButtonX(){return compactBait()?28:Math.max(20,width-235);}
    private int baitButtonWidth(){return compactBait()?width-56:215;}
    private int baitButtonY(int y){return y+(compactBait()?54:24);}
    private int maximumFishingScroll(){return Math.max(0,fishingStatsHeight()+2*baitHeight()+24-(fishingBottom()-FISHING_TOP));}
    private int firstBaitY(){return FISHING_TOP-fishingScroll+fishingStatsHeight()+12;}
    private void fishing(GuiGraphicsExtractor g,int mx,int my){
        int maximum=maximumFishingScroll();
        fishingScroll=Math.clamp(fishingScroll,0,maximum);
        int y=FISHING_TOP-fishingScroll;
        g.enableScissor(18,FISHING_TOP,width-18,fishingBottom());
        g.fill(19,y,width-19,y+fishingStatsHeight(),UiTheme.CARD);
        var power=model.state.getAsJsonObject("fishingPower");
        String total=power==null?"渔力数据同步中…":"当前总渔力："+number(power.get("total").getAsDouble())+"（上限 "+number(power.get("maximum").getAsDouble())+"）";
        g.text(font,total,28,y+11,0xFF9EEACD,false);
        int lineY=y+31;
        for(var line:fishingLines()){g.text(font,line,28,lineY,0xFFCCDDE4,false);lineY+=13;}
        int baitY=firstBaitY();
        bait(g,mx,my,baitY,"lava_bait","熔岩鱼饵","拥有后可在岩浆钓鱼",price("lavaBaitPrice"));
        bait(g,mx,my,baitY+baitHeight()+12,"void_bait","虚空鱼饵","拥有后可在虚空钓鱼",price("voidBaitPrice"));
        g.disableScissor();
        if(maximum>0){
            int viewport=fishingBottom()-FISHING_TOP;
            int thumb=Math.max(18,viewport*viewport/(viewport+maximum));
            int thumbY=FISHING_TOP+(viewport-thumb)*fishingScroll/maximum;
            g.fill(width-14,FISHING_TOP,width-11,fishingBottom(),0x55333333);
            g.fill(width-14,thumbY,width-11,thumbY+thumb,0xB9AAAAAA);
            g.text(font,"滚轮查看渔力来源和鱼饵",20,height-24,0xFFA7BEC0,false);
        }
    }
    private void bait(GuiGraphicsExtractor g,int mx,int my,int y,String id,String name,String description,long price){
        g.fill(19,y,width-19,y+baitHeight(),UiTheme.CARD);g.item(new ItemStack(Items.FISHING_ROD),30,y+15);
        g.text(font,name,59,y+13,0xFFF0E8B0,false);g.text(font,description,59,y+32,0xFFC9D5DD,false);
        int x=baitButtonX(),buttonY=baitButtonY(y),w=baitButtonWidth();
        button(g,unlocked(id)?"已永久解锁":"解锁 · "+price+" 职业积分",x,buttonY,w,unlocked(id),inside(mx,my,x,buttonY,w,25));
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        int tw=tabWidth();
        for(int i=0;i<IDS.size();i++)if(inside(e.x(),e.y(),8+i*(tw+4),46,tw,25)){tab=i;fishingScroll=0;guideScroll=0;model.lastError="";return true;}
        if(inside(e.x(),e.y(),20,137,72,25)){guideMode=false;return true;}
        if(inside(e.x(),e.y(),96,137,94,25)){guideMode=true;return true;}
        if(guideMode)return super.mouseClicked(e,doubleClick);
        int x=Math.max(20,width-235);
        if(e.button()==0 && tab<IDS.size()-1 && inside(e.x(),e.y(),x,197,215,25))purchase(selected()+":fruit",1);
        else if(e.button()==0 && tab==IDS.size()-1 && e.y()>=FISHING_TOP && e.y()<fishingBottom()){
            fishingScroll=Math.clamp(fishingScroll,0,maximumFishingScroll());
            int baitY=firstBaitY(),w=baitButtonWidth();
            if(inside(e.x(),e.y(),baitButtonX(),baitButtonY(baitY),w,25) && !unlocked("lava_bait"))purchase("fisherman:lava_bait",1);
            else if(inside(e.x(),e.y(),baitButtonX(),baitButtonY(baitY+baitHeight()+12),w,25) && !unlocked("void_bait"))purchase("fisherman:void_bait",1);
            else return super.mouseClicked(e,doubleClick);
        }
        else return super.mouseClicked(e,doubleClick);
        return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        if(guideMode&&inside(x,y,18,190,width-36,height-214)){guideScroll=Math.clamp(guideScroll-(int)Math.signum(sy)*50,0,guideMax());return true;}
        if(tab!=IDS.size()-1||guideMode||!inside(x,y,8,82,width-16,height-94))return super.mouseScrolled(x,y,sx,sy);
        fishingScroll=Math.clamp(fishingScroll-(int)Math.signum(sy)*27,0,maximumFishingScroll());
        return true;
    }
    private void purchase(String product,int quantity){
        model.lastError="";
        if(ClientPlayNetworking.canSend(ShopPayloads.ProfessionPurchase.TYPE))ClientPlayNetworking.send(new ShopPayloads.ProfessionPurchase(product,quantity,model.state.get("professionShopRevision").getAsLong()));
    }
}
