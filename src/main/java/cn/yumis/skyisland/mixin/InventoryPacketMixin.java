package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class InventoryPacketMixin {
    @Shadow public ServerPlayer player;
    @Inject(method={"handlePlayerAction","handleUseItem","handleUseItemOn","handleSetCarriedItem","handleContainerClick","handleContainerClose","handleContainerButtonClick","handleSetCreativeModeSlot"},at=@At("HEAD"),cancellable=true)
    private void locked(CallbackInfo ci) { var rt = SkyServer.get(player.level().getServer()); if (rt != null && rt.inventoryLocks.contains(player.getUUID())) ci.cancel(); }
}
