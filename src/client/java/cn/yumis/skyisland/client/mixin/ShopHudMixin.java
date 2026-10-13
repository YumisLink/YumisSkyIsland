package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.ShopScreen;
import cn.yumis.skyisland.client.SaleScreen;
import cn.yumis.skyisland.client.TaskScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The shop supplies its own wallet and controls. Gameplay HUD returns when it closes. */
@Mixin(Hud.class)
public abstract class ShopHudMixin {
    @Inject(method="extractRenderState", at=@At("HEAD"), cancellable=true)
    private void shop(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        var screen=Minecraft.getInstance().gui.screen();
        if (screen instanceof ShopScreen || screen instanceof SaleScreen || screen instanceof TaskScreen) ci.cancel();
    }
}
