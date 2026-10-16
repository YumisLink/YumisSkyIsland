package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smithing results use an ItemCombiner result slot, not the crafting ResultSlot. */
@Mixin(SmithingMenu.class)
public abstract class SmithingResultMixin {
    @Inject(method="createResult",at=@At("RETURN"))
    private void lottery$result(CallbackInfo ci){
        SmithingMenu menu=(SmithingMenu)(Object)this;
        cn.yumis.skyisland.shop.LotteryItemRarity.inherit(menu.getSlot(1).getItem(),menu.getSlot(menu.getResultSlot()).getItem());
    }
    @Inject(method="onTake",at=@At("HEAD"))
    private void yumis$take(Player player,ItemStack stack,CallbackInfo ci){
        if(!(player instanceof ServerPlayer serverPlayer)||stack.isEmpty())return;
        SkyServer runtime=SkyServer.get(serverPlayer.level().getServer());if(runtime==null)return;
        SmithingMenu menu=(SmithingMenu)(Object)this;
        var slot=menu.getSlot(menu.getResultSlot());
        runtime.progression.takeProduction(slot,serverPlayer,"craft",stack.copy(),stack.getCount());
        runtime.professions.take(slot,serverPlayer,stack.getCount());
    }
}
