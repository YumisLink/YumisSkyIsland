package cn.yumis.skyisland.client;

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
    private final LotteryPanel lottery;
    private String hovered;
    public AccessoryScreen(ClientCatalog model){super(Component.literal("饰品与"+cn.yumis.skyisland.core.LotteryNames.draw()));this.model=model;this.lottery=new LotteryPanel(model);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float delta){}
    private boolean contains(String field,String id){
        var entries=model.state.getAsJsonArray(field);if(entries==null)return false;
        for(var entry:entries)if(entry.getAsString().equals(id))return true;
        return false;
    }
    private int pending(){return model.state.has("pendingAccessoryDraws")?model.state.get("pendingAccessoryDraws").getAsInt():0;}
    private int cell(){return Math.max(49,Math.min(82,(width-28)/5));}
    private int left(){return (width-cell()*5)/2;}
    private int viewportRows(){return Math.max(1,(height-178)/67);}
    private static boolean inside(double x,double y,int a,int b,int w,int h){return x>=a&&x<a+w&&y>=b&&y<b+h;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        g.fill(7,7,width-7,76,UiTheme.PANEL);
        g.text(font,"YUMIS · 饰品与"+cn.yumis.skyisland.core.LotteryNames.draw(),17,19,0xFFF5F8FA,false);
        String[] names={"饰品",cn.yumis.skyisland.core.LotteryNames.draw()};
        for(int t=0;t<2;t++){int x=17+t*100;g.fill(x,43,x+94,68,tab==t?UiTheme.SELECTED:UiTheme.BUTTON);if(tab==t)g.fill(x,66,x+94,68,0xFF79D5B9);g.text(font,names[t],x+10,51,0xFFEEEEEE,false);}
        if(tab==1){lottery.render(g,font,width,height,mx,my);if(!model.lastError.isBlank())g.text(font,model.lastError,17,height-43,0xFFFFAAAA,false);return;}
        g.text(font,"已激活 "+activeCount()+"/"+slots()+"    待领取 "+pending(),17,87,0xFFC9E8DB,false);
        g.fill(width-118,80,width-15,107,pending()>0?UiTheme.BUTTON:UiTheme.DISABLED);
        g.text(font,"随机领取",width-104,89,pending()>0?0xFFE2FFF4:0xFFADB2B7,false);
        List<String> ids=new ArrayList<>(AccessoryService.CATALOG.keySet());
        scroll=Math.clamp(scroll,0,Math.max(0,5-viewportRows()));
        hovered=null;
        int x0=left(),size=cell();
        for(int i=0;i<ids.size();i++){
            int row=i/5-scroll;if(row<0||row>=viewportRows())continue;
            int x=x0+(i%5)*size,y=116+row*67;
            String id=ids.get(i);var entry=AccessoryService.CATALOG.get(id);
            boolean owned=contains("unlockedAccessories",id),active=contains("activeAccessories",id);
            g.fill(x+2,y,x+size-3,y+62,active?UiTheme.SELECTED:owned?UiTheme.CARD:UiTheme.DISABLED);
            if(active)g.fill(x+2,y+60,x+size-3,y+62,0xFF83E1A5);
            ItemStack icon=new ItemStack(ModItems.ACCESSORY_ICONS.get(id));
            g.pose().pushMatrix();g.pose().translate(x+(size-32)/2,y+5);g.item(icon,0,0);g.pose().popMatrix();
            String label=entry.name();if(font.width(label)>size-8)label=font.plainSubstrByWidth(label,size-15)+"…";
            g.text(font,label,x+4,y+38,owned?0xFFE9F4EE:0xFF818A91,false);
            if(inside(mx,my,x+2,y,size-5,62))hovered=id;
        }
        g.text(font,"左键开启／关闭；鱼饵拥有后自动生效",17,height-27,0xFFC4CED0,false);
        if(hovered!=null){
            var entry=AccessoryService.CATALOG.get(hovered);int w=Math.min(width-20,Math.max(165,font.width(entry.effect())+20));
            int x=Math.clamp(mx+8,8,width-w-8),y=Math.clamp(my+8,8,height-46);
            g.fill(x,y,x+w,y+35,UiTheme.PANEL);g.text(font,entry.name(),x+8,y+6,0xFFF2E5AA,false);
            g.text(font,entry.effect(),x+8,y+19,0xFFD6E3EA,false);
        }
        if(!model.lastError.isBlank())g.text(font,model.lastError,17,height-43,0xFFFFAAAA,false);
    }
    private int activeCount(){var entries=model.state.getAsJsonArray("activeAccessories");return entries==null?0:entries.size();}
    private int slots(){return model.state.has("accessorySlots")?model.state.get("accessorySlots").getAsInt():3;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(e.button()!=0)return super.mouseClicked(e,doubleClick);
        for(int t=0;t<2;t++)if(inside(e.x(),e.y(),17+t*100,43,94,25)){tab=t;model.lastError="";return true;}
        if(tab==1)return lottery.click(e.x(),e.y(),width,height)||super.mouseClicked(e,doubleClick);
        if(inside(e.x(),e.y(),width-118,80,103,27)){
            if(pending()>0)send("accessory_claim","");return true;
        }
        int size=cell(),x0=left();List<String> ids=new ArrayList<>(AccessoryService.CATALOG.keySet());
        for(int i=0;i<ids.size();i++){
            int row=i/5-scroll;if(row<0||row>=viewportRows())continue;
            int x=x0+(i%5)*size,y=116+row*67;
            if(inside(e.x(),e.y(),x+2,y,size-5,62)){
                String id=ids.get(i);if(contains("unlockedAccessories",id))send("accessory_toggle",id);return true;
            }
        }
        return super.mouseClicked(e,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(tab==1)lottery.scroll(sy,height);else scroll=Math.clamp(scroll-(int)Math.signum(sy),0,Math.max(0,5-viewportRows()));return true;}
    private void send(String action,String product){model.lastError="";if(ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE))ClientPlayNetworking.send(new ShopPayloads.Request(action,product,0));}
}
