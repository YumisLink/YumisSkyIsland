package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.BowHits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Vulnerability and bow accessories scale the amount before armor. */
@Mixin(LivingEntity.class)
public abstract class CombatAdjustMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float yumis$adjustIncoming(float amount, ServerLevel level, DamageSource source, float original) {
        return BowHits.adjust((LivingEntity) (Object) this, source, amount);
    }
}
