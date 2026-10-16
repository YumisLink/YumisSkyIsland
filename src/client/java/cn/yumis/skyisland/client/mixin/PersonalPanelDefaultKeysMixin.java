package cn.yumis.skyisland.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Resetting controls should keep the two vanilla panel shortcuts unassigned. */
@Mixin(Options.class)
public abstract class PersonalPanelDefaultKeysMixin {
    @ModifyArgs(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;<init>(Ljava/lang/String;ILnet/minecraft/client/KeyMapping$Category;)V"))
    private void yumis$panelDefaults(Args args) {
        String name=args.get(0);
        if(name.equals("key.friends")||name.equals("key.socialInteractions"))args.set(1,InputConstants.UNKNOWN.getValue());
    }
}
