package cn.yumis.skyisland.mixin;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.decoration.*;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({LivingEntity.class,Player.class,ServerPlayer.class,VehicleEntity.class,BlockAttachedEntity.class,ItemFrame.class,ArmorStand.class,EnderMan.class})
public abstract class EntityDamageMixin {
    @Inject(method="hurtServer",at=@At("HEAD"),cancellable=true)
    private void damage(ServerLevel level,DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){if(!Protection.damage(level,(Entity)(Object)this,source))ci.setReturnValue(false);}
}
