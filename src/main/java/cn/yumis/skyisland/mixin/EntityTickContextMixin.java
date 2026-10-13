package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.*;
import cn.yumis.skyisland.core.ClaimGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerLevel.class)
public abstract class EntityTickContextMixin {
    private void begin(Entity entity){if(entity instanceof Player)return;var world=(ServerLevel)(Object)this;var rt=SkyServer.get(world.getServer());if(rt==null){Protection.push(world,entity.blockPosition());return;}var origin=Protection.entityParty(rt,entity);var party=origin==null?null:rt.state().parties.get(origin);BlockPos anchor=WorldService.lobbyPoint();if(party!=null){var claim=ClaimGrid.region(party.slot);anchor=new BlockPos(claim.centerX(),entity.blockPosition().getY(),claim.centerZ());}Protection.push(world,anchor);}
    @Inject(method="tickNonPassenger",at=@At("HEAD"))private void before(Entity entity,CallbackInfo ci){begin(entity);}
    @Inject(method="tickNonPassenger",at=@At("RETURN"))private void after(Entity entity,CallbackInfo ci){if(!(entity instanceof Player))Protection.pop((ServerLevel)(Object)this);}
    @Inject(method="tickPassenger",at=@At("HEAD"))private void beforePassenger(Entity vehicle,Entity passenger,CallbackInfo ci){begin(passenger);}
    @Inject(method="tickPassenger",at=@At("RETURN"))private void afterPassenger(Entity vehicle,Entity passenger,CallbackInfo ci){if(!(passenger instanceof Player))Protection.pop((ServerLevel)(Object)this);}
}
