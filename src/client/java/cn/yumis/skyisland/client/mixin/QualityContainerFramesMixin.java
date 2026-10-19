package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.QualityFrames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public abstract class QualityContainerFramesMixin {
    // Use the actual rendered stack: quick-craft previews can differ from Slot.getItem().
    @WrapOperation(method={"extractSlot", "extractFloatingItem"}, at=@At(value="INVOKE",
        target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void frameBeforeDecorations(GuiGraphicsExtractor graphics, Font font, ItemStack stack,
                                       int x, int y, String count, Operation<Void> original) {
        QualityFrames.draw(graphics, stack, x - 1, y - 1, 18, 18);
        original.call(graphics, font, stack, x, y, count);
    }
}
