package cn.yumis.skyisland.client;

import cn.yumis.skyisland.core.AccessoryCraft;
import cn.yumis.skyisland.core.RarityColors;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.network.ShopPayloads;
import cn.yumis.skyisland.server.AccessoryService;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Immediate left-click toggles; active cells have a translucent green background. */
public final class AccessoryScreen extends Screen {
    private final ClientCatalog model;
    private int scroll;
    private int tab;
    public int syncTab(){return tab;}
    private String crafting;
    private final LotteryPanel lottery;
    private final PermanentBuffPanel buffs;
    private String hovered;
    private int rarityFilter;
    private boolean dragCell;
    private int frameOuterW, frameOuterH;
    private static final String[] RARITIES = {"", "common", "rare", "epic", "legendary"};
    private static final String[] RARITY_NAMES = {"全部", "普通", "稀有", "史诗", "传说"};
    public AccessoryScreen(ClientCatalog model){super(Component.literal("饰品与"+cn.yumis.skyisland.core.LotteryNames.draw()));this.model=model;this.lottery=new LotteryPanel(model);this.buffs=new PermanentBuffPanel(model);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float delta){}
    private boolean contains(String field,String id){
        var entries=model.state.getAsJsonArray(field);if(entries==null)return false;
        for(var entry:entries)if(entry.getAsString().equals(id))return true;
        return false;
    }
    private int pending(){return model.state.has("pendingAccessoryDraws")?model.state.get("pendingAccessoryDraws").getAsInt():0;}
    private float metric(){return "large".equals(model.uiSize)?1.2f:"small".equals(model.uiSize)?0.8f:1f;}
    private int cell(){return Math.max(32,Math.round(model.accessoryCellSize*metric()));}
    private int columns(){return Math.max(1,(width-28)/cell());}
    private int left(){int used=columns()*cell();return Math.max(14,(width-used)/2);}
    private int viewportRows(){return Math.max(1,(height-210)/67);}
    private List<String> shown(){
        List<String> ids=new ArrayList<>();
        String rarity=RARITIES[rarityFilter];
        for(var entry:AccessoryService.CATALOG.values())if(rarity.isEmpty()||rarity.equals(entry.rarity()))ids.add(entry.id());
        return ids;
    }
    private int rows(){int count=shown().size(),columns=columns();return count==0?0:(count+columns-1)/columns;}
    private int maxScroll(){return Math.max(0,rows()-viewportRows());}
    private int sliderX(){return width-150;}
    private static boolean inside(double x,double y,int a,int b,int w,int h){return x>=a&&x<a+w&&y>=b&&y<b+h;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        UiFrame frame=openFrame(g);
        try{draw(g,mx-frame.ox,my-frame.oy,delta);}
        finally{closeFrame(g);}
        model.sync.status(g,width);
    }
    private UiFrame openFrame(GuiGraphicsExtractor g){
        UiFrame frame=UiFrame.of(width,height,model.uiSize);
        frameOuterW=width;frameOuterH=height;width=frame.width;height=frame.height;
        if(g!=null){g.pose().pushMatrix();g.pose().translate(frame.ox,frame.oy);}
        return frame;
    }
    private void closeFrame(GuiGraphicsExtractor g){
        width=frameOuterW;height=frameOuterH;
        if(g!=null)g.pose().popMatrix();
    }
    private void draw(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        g.fill(7,7,width-7,76,UiTheme.PANEL);
        g.text(font,"YUMIS · 饰品与"+cn.yumis.skyisland.core.LotteryNames.draw(),17,19,0xFFF5F8FA,false);
        int tabW=Math.min(94,(width-34)/3);
        String[] names={"饰品",cn.yumis.skyisland.core.LotteryNames.draw(),model.genshinMode?"心之钢":"永久增益"};
        for(int t=0;t<3;t++){int x=17+t*(tabW+6);g.fill(x,43,x+tabW,68,tab==t?UiTheme.SELECTED:UiTheme.BUTTON);if(tab==t)g.fill(x,66,x+tabW,68,0xFF79D5B9);g.text(font,names[t],x+8,51,0xFFEEEEEE,false);}
        if(!model.sync.loaded())return;
        if(tab==1){lottery.render(g,font,width,height,mx,my);if(!model.lastError.isBlank())g.text(font,model.lastError,17,height-43,0xFFFFAAAA,false);return;}
        if(tab==2){buffs.render(g,font,width,height);return;}
        g.text(font,"已激活 "+activeCount()+"/"+slots()+"    待领取 "+pending(),17,87,0xFFC9E8DB,false);
        g.fill(width-118,80,width-15,107,pending()>0?UiTheme.BUTTON:UiTheme.DISABLED);
        g.text(font,"随机领取",width-104,89,pending()>0?0xFFE2FFF4:0xFFADB2B7,false);
        for(int i=0;i<RARITY_NAMES.length;i++){
            int x=17+i*46;
            int color=i==0?0xFFFFFFFF:RarityColors.color(RARITIES[i]);
            g.fill(x,112,x+42,132,rarityFilter==i?UiTheme.SELECTED:UiTheme.BUTTON);
            g.text(font,RARITY_NAMES[i],x+6,118,color,false);
        }
        g.text(font,"格子 "+model.accessoryCellSize,sliderX(),112,0xFFC4C7CD,false);
        g.fill(sliderX(),128,sliderX()+120,131,UiTheme.PANEL);
        int knob=sliderX()+(model.accessoryCellSize-ClientCatalog.MIN_CELL)*120/(ClientCatalog.MAX_CELL-ClientCatalog.MIN_CELL);
        g.fill(knob-2,124,knob+3,136,0xFF79D9C2);
        List<String> ids=shown();
        scroll=Math.clamp(scroll,0,maxScroll());
        hovered=null;
        int x0=left(),size=cell(),columns=columns();
        for(int i=0;i<ids.size();i++){
            int row=i/columns-scroll;if(row<0||row>=viewportRows())continue;
            int x=x0+(i%columns)*size,y=140+row*67;
            String id=ids.get(i);var entry=AccessoryService.CATALOG.get(id);
            boolean owned=contains("unlockedAccessories",id),active=contains("activeAccessories",id);
            g.fill(x+2,y,x+size-3,y+62,active?UiTheme.SELECTED:owned?UiTheme.CARD:UiTheme.DISABLED);
            if(active)g.fill(x+2,y+60,x+size-3,y+62,0xFF83E1A5);
            ItemStack icon=new ItemStack(ModItems.ACCESSORY_ICONS.get(id));
            g.pose().pushMatrix();g.pose().translate(x+(size-32)/2,y+5);g.item(icon,0,0);g.pose().popMatrix();
            String label=entry.name();if(font.width(label)>size-8)label=font.plainSubstrByWidth(label,size-15)+"…";
            g.text(font,label,x+4,y+38,owned?RarityColors.color(entry.rarity()):0xFF818A91,false);
            if(!owned&&AccessoryCraft.crafted(id))g.text(font,"合成",x+4,y+50,0xFF8FD0C4,false);
            if(inside(mx,my,x+2,y,size-5,62))hovered=id;
        }
        g.text(font,"左键开关；合成饰品先打开配方；鱼饵自动生效",17,height-27,0xFFC4CED0,false);
        if(crafting!=null&&(!AccessoryCraft.crafted(crafting)||contains("unlockedAccessories",crafting)))crafting=null;
        if(hovered!=null&&crafting==null){
            var entry=AccessoryService.CATALOG.get(hovered);int w=Math.min(width-20,Math.max(165,Math.min(320,font.width(entry.effect())+20)));
            var lines=font.split(Component.literal(entry.effect()),w-16);
            int h=25+lines.size()*13;
            int x=Math.clamp(mx+8,8,width-w-8),y=Math.clamp(my+8,8,Math.max(8,height-h-8));
            g.fill(x,y,x+w,y+h,UiTheme.PANEL);g.text(font,entry.name(),x+8,y+6,0xFFF2E5AA,false);
            for(int i=0;i<lines.size();i++)g.text(font,lines.get(i),x+8,y+19+i*13,0xFFD6E3EA,false);
        }
        if(!model.lastError.isBlank())g.text(font,model.lastError,17,height-43,0xFFFFAAAA,false);
        if(crafting!=null)renderCraft(g,mx,my);
    }
    private int activeCount(){var entries=model.state.getAsJsonArray("activeAccessories");return entries==null?0:entries.size();}
    private int slots(){return model.state.has("accessorySlots")?model.state.get("accessorySlots").getAsInt():3;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(model.sync.retryClick(e.y()))return true;
        UiFrame frame=openFrame(null);
        try{return click(frame.move(e),doubleClick);}
        finally{closeFrame(null);}
    }
    private boolean click(MouseButtonEvent e,boolean doubleClick){
        if(e.button()!=0)return super.mouseClicked(e,doubleClick);
        int tabW=Math.min(94,(width-34)/3);
        for(int t=0;t<3;t++)if(inside(e.x(),e.y(),17+t*(tabW+6),43,tabW,25)){tab=t;crafting=null;model.lastError="";return true;}
        if(tab==2)return true;
        if(tab==1)return lottery.click(e.x(),e.y(),width,height)||super.mouseClicked(e,doubleClick);
        if(inside(e.x(),e.y(),width-118,80,103,27)){
            if(pending()>0)send("accessory_claim","");return true;
        }
        for(int i=0;i<RARITY_NAMES.length;i++)if(inside(e.x(),e.y(),17+i*46,112,42,20)){rarityFilter=i;scroll=0;return true;}
        if(inside(e.x(),e.y(),sliderX()-4,122,128,16)){dragCell=true;accessorySlider(e.x());return true;}
        if(crafting!=null&&craftClick(e.x(),e.y()))return true;
        int size=cell(),x0=left(),columns=columns();List<String> ids=shown();
        for(int i=0;i<ids.size();i++){
            int row=i/columns-scroll;if(row<0||row>=viewportRows())continue;
            int x=x0+(i%columns)*size,y=140+row*67;
            if(inside(e.x(),e.y(),x+2,y,size-5,62)){
                String id=ids.get(i);
                if(!contains("unlockedAccessories",id)&&AccessoryCraft.crafted(id)){crafting=id;model.lastError="";return true;}
                crafting=null;if(contains("unlockedAccessories",id))send("accessory_toggle",id);return true;
            }
        }
        return super.mouseClicked(e,doubleClick);
    }
    private int[] craftBox(){
        List<String> materials=AccessoryCraft.materials(crafting);
        int w=240,h=86+materials.size()*16,x=(width-w)/2,y=(height-h)/2;
        return new int[]{x,y,w,h};
    }
    private void renderCraft(GuiGraphicsExtractor g,int mx,int my){
        int[] box=craftBox();int x=box[0],y=box[1],w=box[2],h=box[3];
        var entry=AccessoryService.CATALOG.get(crafting);
        List<String> materials=AccessoryCraft.materials(crafting);
        boolean ready=true;
        g.fill(x,y,x+w,y+h,UiTheme.PANEL);
        g.text(font,"合成 "+entry.name(),x+12,y+10,0xFFF2E5AA,false);
        String effect=entry.effect();if(font.width(effect)>w-24)effect=font.plainSubstrByWidth(effect,w-32)+"…";
        g.text(font,effect,x+12,y+24,0xFFD6E3EA,false);
        g.text(font,"需要已解锁，合成后仍然保留",x+12,y+40,0xFFC4CED0,false);
        for(int i=0;i<materials.size();i++){
            String material=materials.get(i);boolean owned=contains("unlockedAccessories",material);
            if(!owned)ready=false;
            g.text(font,AccessoryService.CATALOG.get(material).name()+"  "+(owned?"已拥有":"未解锁"),x+12,y+58+i*16,owned?0xFFB7E7C8:0xFFFFAAAA,false);
        }
        int by=y+h-26;g.fill(x+12,by,x+92,by+18,ready?UiTheme.BUTTON:UiTheme.DISABLED);
        g.text(font,"合成",x+36,by+5,ready?0xFFE2FFF4:0xFFADB2B7,false);
        if(inside(mx,my,x+w-52,y+8,40,14))g.text(font,"关闭",x+w-48,y+10,0xFFFFFFFF,false);
        else g.text(font,"关闭",x+w-48,y+10,0xFFC4CED0,false);
    }
    private boolean craftClick(double mx,double my){
        if(!AccessoryCraft.crafted(crafting)||contains("unlockedAccessories",crafting)){crafting=null;return false;}
        int[] box=craftBox();int x=box[0],y=box[1],w=box[2],h=box[3];
        if(!inside(mx,my,x,y,w,h)){crafting=null;return false;}
        if(inside(mx,my,x+w-52,y+8,40,14)){crafting=null;return true;}
        int by=y+h-26;
        if(inside(mx,my,x+12,by,80,18)&&AccessoryCraft.missing(unlocked(),crafting).isEmpty())send("accessory_craft",crafting);
        return true;
    }
    private java.util.Set<String> unlocked(){
        java.util.Set<String> owned=new java.util.LinkedHashSet<>();
        var entries=model.state.getAsJsonArray("unlockedAccessories");
        if(entries!=null)for(var entry:entries)owned.add(entry.getAsString());
        return owned;
    }
    private void accessorySlider(double x){model.accessoryCellSize=Math.clamp(ClientCatalog.MIN_CELL+(int)Math.round((x-sliderX())*(ClientCatalog.MAX_CELL-ClientCatalog.MIN_CELL)/120.0),ClientCatalog.MIN_CELL,ClientCatalog.MAX_CELL);scroll=0;}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        UiFrame frame=openFrame(null);
        try{if(dragCell){accessorySlider(frame.move(e).x());return true;}return super.mouseDragged(frame.move(e),dx,dy);}
        finally{closeFrame(null);}
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){if(dragCell){dragCell=false;model.save();return true;}return super.mouseReleased(e);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        UiFrame frame=openFrame(null);
        try{if(tab==1)lottery.scroll(sy,height);else if(tab==2)buffs.scroll(sy,height);else scroll=Math.clamp(scroll-(int)Math.signum(sy),0,maxScroll());return true;}
        finally{closeFrame(null);}
    }
    private void send(String action,String product){if(!model.sync.ready()){model.lastError="资料更新中，请稍后操作";return;}model.lastError="";if(ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE))ClientPlayNetworking.send(new ShopPayloads.Request(action,product,0));}
}
