package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla experience APIs cannot invalidate a receipt's baseline; recovery has a scoped bypass. */
@Mixin(ServerPlayer.class)
public abstract class PlayerExperienceLockMixin {
    @Unique private boolean yumis$experienceLocked(){
        ServerPlayer player=(ServerPlayer)(Object)this;var runtime=SkyServer.get(player.level().getServer());
        if(runtime==null||!runtime.trades.blocksExperienceMutation(player))return false;
        SkyServer.tell(player,"背包事务正在保存，请稍后修改经验");return true;
    }
    @Inject(method={"giveExperiencePoints","giveExperienceLevels","setExperiencePoints","setExperienceLevels"},at=@At("HEAD"),cancellable=true)
    private void experience(int amount,CallbackInfo ci){if(yumis$experienceLocked())ci.cancel();}
    @Inject(method="onEnchantmentPerformed",at=@At("HEAD"),cancellable=true)
    private void enchanted(ItemStack stack,int levels,CallbackInfo ci){if(yumis$experienceLocked())ci.cancel();}
}
