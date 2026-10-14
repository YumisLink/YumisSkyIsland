package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin({ResultSlot.class, FurnaceResultSlot.class})
public abstract class ProductionResultMixin {
    @Shadow(remap=false) @Final private Player player;
    @Shadow(remap=false) private int removeCount;
    @Inject(method="checkTakeAchievements", at=@At("HEAD"))
    private void take(ItemStack stack,CallbackInfo ci) {
        if(removeCount>0&&player instanceof ServerPlayer p){var rt=SkyServer.get(p.level().getServer());if(rt==null)return;
            Slot slot=(Slot)(Object)this;
            rt.progression.takeProduction(slot,p,slot instanceof FurnaceResultSlot?"smelt":"craft",stack.copy(),removeCount);
            rt.professions.take(slot,p,removeCount);
        }
    }
}
