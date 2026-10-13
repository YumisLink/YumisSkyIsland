package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.YumisSkyIsland;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {
    @Shadow @Final private Merchant trader;
    private boolean disabled() { return YumisSkyIsland.CONFIG != null && (trader instanceof Villager && !YumisSkyIsland.CONFIG.object("server_rules.json").get("villagerTradingEnabled").getAsBoolean() || trader instanceof WanderingTrader && !YumisSkyIsland.CONFIG.object("server_rules.json").get("wanderingTraderTradingEnabled").getAsBoolean()); }
    @Inject(method="stillValid",at=@At("HEAD"),cancellable=true) private void valid(Player p,CallbackInfoReturnable<Boolean> ci) { if (disabled()) ci.setReturnValue(false); }
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true) private void shift(Player p,int slot,CallbackInfoReturnable<ItemStack> ci) { if (disabled()) ci.setReturnValue(ItemStack.EMPTY); }
}
