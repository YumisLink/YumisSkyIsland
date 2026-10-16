package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.BlueprintPositionScreen;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Mouse capture normally closes the screen; the blueprint view keeps it visible. */
@Mixin(MouseHandler.class)
public abstract class BlueprintMouseMixin {
    @Redirect(method="grabMouse",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void yumis$keepBlueprintScreen(Gui gui,Screen requested){
        if(gui.screen() instanceof BlueprintPositionScreen screen&&screen.viewMode())return;
        gui.setScreen(requested);
    }
}
