package cn.yumis.skyisland.client;
import net.minecraft.client.*;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Handle actual press edges, including taps shorter than a tick and GUI-induced key resets. */
public final class ConfigurationHotkey {
    private static KeyMapping mapping;
    private static int heldKey=-1,heldScan=-1;
    private static boolean handled;
    public static void bind(KeyMapping value){mapping=value;}
    public static boolean key(int action,KeyEvent event){
        if(action==GLFW.GLFW_RELEASE){if(event.key()==heldKey&&event.scancode()==heldScan){heldKey=-1;heldScan=-1;handled=false;}return false;}
        if(action==GLFW.GLFW_REPEAT&&event.key()==heldKey&&event.scancode()==heldScan&&handled)return true;
        if(action!=GLFW.GLFW_PRESS||mapping==null||!mapping.matches(event)||heldKey!=-1)return false;
        heldKey=event.key();heldScan=event.scancode();
        var client=Minecraft.getInstance();
        boolean control=(event.modifiers()&GLFW.GLFW_MOD_CONTROL)!=0||com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow(),GLFW.GLFW_KEY_LEFT_CONTROL)||com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow(),GLFW.GLFW_KEY_RIGHT_CONTROL);
        if(client.player==null||client.gui.screen()!=null||!control)return false;
        var model=SkyIslandClient.CATALOG;if(model==null)return false;
        if(model.editing()){model.configWanted=true;model.configMessage="";model.editConfig("open",new com.google.gson.JsonObject());}
        else model.events.receive("请先使用 /is admin edit on 开启管理员编辑模式",0xFFFFAD9F);
        handled=true;return true;
    }
    private ConfigurationHotkey(){}
}
