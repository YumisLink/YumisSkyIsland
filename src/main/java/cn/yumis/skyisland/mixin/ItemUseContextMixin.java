package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ItemStack.class)
public abstract class ItemUseContextMixin{
    @Inject(method="useOn",at=@At("HEAD"))private void before(UseOnContext context,CallbackInfoReturnable<InteractionResult> ci){Protection.pushPlayer(context.getLevel(),context.getClickedPos(),context.getPlayer());}
    @Inject(method="useOn",at=@At("RETURN"))private void after(UseOnContext context,CallbackInfoReturnable<InteractionResult> ci){Protection.pop(context.getLevel());}
}
