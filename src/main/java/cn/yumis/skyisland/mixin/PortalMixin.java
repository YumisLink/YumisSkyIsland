package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({NetherPortalBlock.class, EndPortalBlock.class, EndGatewayBlock.class})
public abstract class PortalMixin {
    @Inject(method="getPortalDestination", at=@At("HEAD"), cancellable=true)
    private void route(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> ci) {
        SkyServer runtime = SkyServer.get(level.getServer());
        if (runtime != null) ci.setReturnValue(runtime.worlds.portal(level, entity, (Object)this instanceof NetherPortalBlock ? "nether" : (Object)this instanceof EndGatewayBlock ? "gateway" : "end"));
    }
}
