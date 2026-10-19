package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.QualityFrames;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class QualityFrameClockMixin {
    @Inject(method="<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;II)V", at=@At("RETURN"))
    private void beginFrame(CallbackInfo ci) { QualityFrames.beginFrame(); }
}
