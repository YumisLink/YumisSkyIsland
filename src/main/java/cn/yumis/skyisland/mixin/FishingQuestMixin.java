package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.advancements.triggers.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FishingRodHookedTrigger.class)
public abstract class FishingQuestMixin {
    @Inject(method="trigger",at=@At("TAIL"))
    private void caught(ServerPlayer player,ItemStack rod,FishingHook hook,Collection<ItemStack> loot,CallbackInfo ci){
        if(hook.getHookedIn()==null&&!loot.isEmpty()){var rt=SkyServer.get(player.level().getServer());if(rt!=null)rt.progression.fished(player,loot);}
    }
}
