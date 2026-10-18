package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.BowHits;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Splash uses the damage just applied to the struck entity. */
@Mixin(AbstractArrow.class)
public abstract class ArrowSplashMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void yumis$clearHit(EntityHitResult hit, CallbackInfo ci) { BowHits.begin(); }

    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void yumis$splash(EntityHitResult hit, CallbackInfo ci) { BowHits.splash((AbstractArrow) (Object) this, hit); }
}
