package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.QualityFrames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class QualityHotbarFramesMixin {
    // Both hotbar and offhand use this path, after the pickup-bounce pose has been restored.
    @WrapOperation(method="extractSlot", at=@At(value="INVOKE",
        target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
    private void frameBeforeDecorations(GuiGraphicsExtractor graphics, Font font, ItemStack stack,
                                       int x, int y, Operation<Void> original) {
        QualityFrames.draw(graphics, stack, x - 2, y - 2, 20, 20);
        original.call(graphics, font, stack, x, y);
    }
}
