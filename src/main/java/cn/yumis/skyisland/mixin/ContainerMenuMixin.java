package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {
    @Inject(method="clicked", at=@At("HEAD"), cancellable=true)
    private void click(int slot,int button,ContainerInput input,Player player,CallbackInfo ci) {
        if (!(player instanceof ServerPlayer p)) return; SkyServer rt = SkyServer.get(p.level().getServer()); if (rt == null) return;
        if (rt.inventoryLocks.contains(p.getUUID()) || !((AbstractContainerMenu)(Object)this).stillValid(p)) { ci.cancel(); return; }
        if (input != ContainerInput.CLONE) rt.professions.prepare((AbstractContainerMenu)(Object)this,slot,p);
        if (!Protection.menuAllowed((AbstractContainerMenu)(Object)this,p)) ci.cancel();
    }
    @Inject(method="clicked",at=@At("RETURN"))
    private void afterClick(int slot,int button,ContainerInput input,Player player,CallbackInfo ci){if(player instanceof ServerPlayer p){var rt=SkyServer.get(p.level().getServer());if(rt!=null)rt.professions.afterClick((AbstractContainerMenu)(Object)this);}}
}
