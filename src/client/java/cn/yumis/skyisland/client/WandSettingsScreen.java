package cn.yumis.skyisland.client;

import cn.yumis.skyisland.wand.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A held-item settings screen, independent of the I control panel. */
public final class WandSettingsScreen extends Screen {
    public WandSettingsScreen(){super(Component.literal("建造杖设置"));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float delta){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        int left=(width-290)/2,top=Math.max(12,(height-192)/2);WandOptions o=WandClient.options;
        g.fill(0,0,width,height,0xA0000000);g.fill(left-12,top-10,left+302,top+184,UiTheme.PANEL);
        g.text(font,"建造杖设置",left,top,0xFFE4F5FF,false);
        String[] labels={"方向："+WandOptions.DIRECTIONS[o.direction()],"承托匹配："+WandOptions.MATCHES[o.matching()],
                "朝向："+(o.copyFacing()?"复制承托方块":"按玩家朝向"),"随机选材："+(o.random()?"开启":"关闭")};
        for(int i=0;i<4;i++){int y=top+25+i*29;g.fill(left,y,left+290,y+24,UiTheme.PANEL);g.text(font,labels[i]+"  ›",left+9,y+8,0xFFE0EDF5,false);}
        g.text(font,"左键切换，即时保存到持有的杖；Esc返回",left,top+153,0xFFB0CADB,false);
        if(!WandClient.active())g.text(font,"仅空岛可使用，当前设置需服务端确认",left,top+168,0xFFFFCB8C,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        int left=(width-290)/2,top=Math.max(12,(height-192)/2);
        if(event.button()!=0||event.x()<left||event.x()>=left+290)return super.mouseClicked(event,doubleClick);
        int row=(int)((event.y()-top-25)/29);if(event.y()<top+25||row<0||row>=4)return super.mouseClicked(event,doubleClick);
        WandOptions o=WandClient.options;
        WandClient.options=switch(row){
            case 0->new WandOptions((o.direction()+1)%5,o.matching(),o.copyFacing(),o.random());
            case 1->new WandOptions(o.direction(),(o.matching()+1)%3,o.copyFacing(),o.random());
            case 2->new WandOptions(o.direction(),o.matching(),!o.copyFacing(),o.random());
            default->new WandOptions(o.direction(),o.matching(),o.copyFacing(),!o.random());};
        WandClient.send(2);return true;
    }
}
