package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.minecraft.world.inventory.StonecutterMenu$2")
public abstract class StonecutResultMixin {
    @Inject(method="onTake", at=@At("HEAD"))
    private void take(Player player,ItemStack stack,CallbackInfo ci) { if (player instanceof ServerPlayer p) { var rt = SkyServer.get(p.level().getServer()); if (rt != null) rt.professions.stonecutTake((Slot)(Object)this,p); } }
}
