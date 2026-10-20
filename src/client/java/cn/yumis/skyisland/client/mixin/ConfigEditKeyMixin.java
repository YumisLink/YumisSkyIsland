package cn.yumis.skyisland.client.mixin;
import cn.yumis.skyisland.client.ConfigurationHotkey;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardHandler.class)
public abstract class ConfigEditKeyMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void configuration(long window,int action,KeyEvent event,CallbackInfo info){if(window==net.minecraft.client.Minecraft.getInstance().getWindow().handle()&&(ConfigurationHotkey.key(action,event)||cn.yumis.skyisland.client.UtilityHotkeys.key(action,event)))info.cancel();}
}
