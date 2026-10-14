package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.ControlPanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ShopToastMixin {
    @Inject(method="extractRenderState", at=@At("HEAD"), cancellable=true)
    private void shop(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        var screen=Minecraft.getInstance().gui.screen();
        if (screen instanceof ControlPanelScreen) ci.cancel();
    }
}
