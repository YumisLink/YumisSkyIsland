package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.BloodAccessories;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Modify only this hit's armor input; toughness and vanilla armor/enchantment processing stay intact. */
@Mixin(LivingEntity.class)
public abstract class SharkArmorMixin {
    @ModifyArg(method="getDamageAfterArmorAbsorb", at=@At(value="INVOKE",
            target="Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"), index=3)
    private float yumis$sharkArmor(LivingEntity victim, float damage, DamageSource source, float armor, float toughness) {
        return BloodAccessories.armor(source, armor);
    }
}
