package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import cn.yumis.skyisland.server.StonecutTransferTracker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.minecraft.world.inventory.StonecutterMenu$2")
public abstract class StonecutResultMixin implements StonecutTransferTracker {
    @Unique private int yumis$transfer;
    @Unique private ItemStack yumis$output=ItemStack.EMPTY;
    @Override public void yumis$transferred(ItemStack output,int quantity){yumis$transfer=quantity;yumis$output=output;}
    @Inject(method="onTake", at=@At("HEAD"))
    private void take(Player player,ItemStack stack,CallbackInfo ci) {
        int quantity=yumis$transfer>0?yumis$transfer:stack.getCount();ItemStack output=yumis$transfer>0?yumis$output:stack.copy();yumis$transfer=0;yumis$output=ItemStack.EMPTY;
        if(player instanceof ServerPlayer p){var rt=SkyServer.get(p.level().getServer());if(rt!=null&&quantity>0){rt.progression.takeProduction((Slot)(Object)this,p,"craft",output,quantity);rt.professions.take((Slot)(Object)this,p,quantity);}}
    }
}
