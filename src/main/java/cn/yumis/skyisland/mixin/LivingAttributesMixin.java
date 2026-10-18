package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.attribute.ModAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every living entity can carry lifesteal. Players inherit it from this builder. */
@Mixin(LivingEntity.class)
public abstract class LivingAttributesMixin {
    @Inject(method = "createLivingAttributes", at = @At("RETURN"), cancellable = true)
    private static void yumis$lifesteal(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        cir.setReturnValue(cir.getReturnValue().add(ModAttributes.LIFESTEAL));
    }
}
