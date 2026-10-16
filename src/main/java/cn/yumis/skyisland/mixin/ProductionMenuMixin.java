package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({CraftingMenu.class, InventoryMenu.class, StonecutterMenu.class, AbstractFurnaceMenu.class, ItemCombinerMenu.class})
public abstract class ProductionMenuMixin {
    @Inject(method="quickMoveStack", at=@At("HEAD"))
    private void prepare(Player player,int index,CallbackInfoReturnable<ItemStack> ci) { if (player instanceof ServerPlayer p) { var rt = SkyServer.get(p.level().getServer()); if (rt != null) rt.professions.prepare((AbstractContainerMenu)(Object)this,index,p); } }
    @Inject(method="quickMoveStack",at=@At("RETURN"))
    private void after(Player player,int index,CallbackInfoReturnable<ItemStack> ci){if(player instanceof ServerPlayer p){var rt=SkyServer.get(p.level().getServer());if(rt!=null)rt.professions.afterClick((AbstractContainerMenu)(Object)this);}}
}
