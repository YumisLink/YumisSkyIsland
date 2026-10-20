package cn.yumis.skyisland.client;

import cn.yumis.skyisland.network.ShopPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Route actual press edges once; two mappings sharing '=' must not compete for vanilla's click queue. */
public final class UtilityHotkeys {
    private static KeyMapping trash, attack;
    private static int attackToggles;
    private static final Set<Integer> handled = new HashSet<>();
    private UtilityHotkeys() {}
    public static void bind(KeyMapping trashKey, KeyMapping attackKey) { trash = trashKey; attack = attackKey; }
    public static void reset() { handled.clear(); attackToggles = 0; }
    public static boolean consumeAttackToggle() { if (attackToggles == 0) return false; attackToggles--; return true; }

    public static boolean key(int action, KeyEvent event) {
        if (action == GLFW.GLFW_RELEASE) { handled.remove(event.key()); return false; }
        if (handled.contains(event.key())) return true;
        if (action != GLFW.GLFW_PRESS || trash == null || attack == null) return false;
        var client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() != null || client.isPaused()) return false;
        var window = client.getWindow();
        boolean control = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
            || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean alt = (event.modifiers() & GLFW.GLFW_MOD_ALT) != 0
            || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
        if (!control) return false;
        if (alt && attack.matches(event)) attackToggles++;
        else if (!alt && trash.matches(event) && ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE))
            ClientPlayNetworking.send(new ShopPayloads.Request("auto_trash", "", 0));
        else return false;
        handled.add(event.key());
        return true;
    }
}
