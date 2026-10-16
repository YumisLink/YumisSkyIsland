package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.WandClient;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Adapted from ConstructionWand's MouseMixin, Copyright (c) 2021 ThetaDev, MIT. */
@Mixin(MouseHandler.class)
public abstract class WandScrollMixin {
    @Shadow @Final private Minecraft minecraft;
    @Redirect(method="onScroll",at=@At(value="INVOKE",target="Lnet/minecraft/client/ScrollWheelHandler;getNextScrollWheelSelection(DII)I"))
    private int wandScroll(double wheel,int selected,int limit) {
        return WandClient.scroll(wheel)&&minecraft.player!=null?minecraft.player.getInventory().getSelectedSlot():ScrollWheelHandler.getNextScrollWheelSelection(wheel,selected,limit);
    }
}
