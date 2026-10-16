package cn.yumis.skyisland.client.mixin;

import cn.yumis.skyisland.client.WandClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The custom request replaces vanilla use, so Ctrl+right click cannot also construct/open a block. */
@Mixin(Minecraft.class)
public abstract class WandUseMixin {
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void wandUse(CallbackInfo callback){if(WandClient.use())callback.cancel();}
}
