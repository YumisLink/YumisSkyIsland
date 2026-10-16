package cn.yumis.skyisland.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shared confirmation path for every control-panel action. */
final class ConfirmDialog {
    private String message="";
    private Runnable action;
    boolean active(){return action!=null;}
    void open(String text,Runnable next){message=text;action=next;}
    void cancel(){message="";action=null;}
    void confirm(){Runnable next=action;if(next==null)return;cancel();next.run();}
    void render(GuiGraphicsExtractor graphics,ControlPanelScreen screen){
        if(!active())return;
        int width=screen.width,height=screen.height,w=Math.min(420,width-24),h=Math.min(150,height-24),x=(width-w)/2,y=(height-h)/2;
        graphics.fill(0,0,width,height,0xAA000000);graphics.fill(x,y,x+w,y+h,UiTheme.PANEL);
        int yy=y+12;for(var line:screen.uiFont().split(Component.literal(message),w-24)){graphics.text(screen.uiFont(),line,x+12,yy,0xFFDDE6EA,false);yy+=12;}
        screen.actionButton(graphics,"确认",x+12,y+h-30,80,true);screen.actionButton(graphics,"取消",x+w-92,y+h-30,80,true);
    }
    boolean click(ControlPanelScreen screen,double mouseX,double mouseY){
        if(!active())return false;
        int w=Math.min(420,screen.width-24),h=Math.min(150,screen.height-24),x=(screen.width-w)/2,y=(screen.height-h)/2;
        if(ControlPanelScreen.hit(mouseX,mouseY,x+12,y+h-30,80,22))confirm();
        else if(ControlPanelScreen.hit(mouseX,mouseY,x+w-92,y+h-30,80,22))cancel();
        return true;
    }
    boolean key(int key){if(!active())return false;if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)confirm();else if(key==GLFW.GLFW_KEY_ESCAPE)cancel();return true;}
}
