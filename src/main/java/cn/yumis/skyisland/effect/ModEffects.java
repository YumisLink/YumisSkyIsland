package cn.yumis.skyisland.effect;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.attribute.ModAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class ModEffects {
    public static final Holder<MobEffect> CLOUD_STEP = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, YumisSkyIsland.id("cloud_step"), new CloudStepEffect());
    public static final Holder<MobEffect> VULNERABILITY = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, YumisSkyIsland.id("vulnerability"), new MobEffect(MobEffectCategory.HARMFUL, 0xC45A6A) {});
    public static final Holder<MobEffect> BLOOD_MOON_RALLY = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, YumisSkyIsland.id("blood_moon_rally"), new MobEffect(MobEffectCategory.BENEFICIAL, 0xE24B4B) {});
    public static final Holder<MobEffect> BLOOD_MOON_FRENZY = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, YumisSkyIsland.id("blood_moon_frenzy"), new MobEffect(MobEffectCategory.HARMFUL, 0x8E1D1D) {});
    /** Marker only. Absorption points are applied directly so one level stays one point. */
    public static final Holder<MobEffect> FOOD_GUARD = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, YumisSkyIsland.id("food_guard"), new MobEffect(MobEffectCategory.BENEFICIAL, 0xF2D16B) {});

    private ModEffects() {}
    public static void register() {
        pace(BLOOD_MOON_RALLY.value(), "rally");
        pace(BLOOD_MOON_FRENZY.value(), "frenzy");
        BLOOD_MOON_FRENZY.value().addAttributeModifier(Attributes.MAX_HEALTH, YumisSkyIsland.id("frenzy_health"), 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void pace(MobEffect effect, String name) {
        effect.addAttributeModifier(Attributes.ATTACK_DAMAGE, YumisSkyIsland.id(name + "_attack"), 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        effect.addAttributeModifier(Attributes.MOVEMENT_SPEED, YumisSkyIsland.id(name + "_move"), 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        effect.addAttributeModifier(Attributes.ATTACK_SPEED, YumisSkyIsland.id(name + "_haste"), 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        effect.addAttributeModifier(ModAttributes.LIFESTEAL, YumisSkyIsland.id(name + "_lifesteal"), 10, AttributeModifier.Operation.ADD_VALUE);
    }

    private static final class CloudStepEffect extends MobEffect {
        private CloudStepEffect() { super(MobEffectCategory.BENEFICIAL, 0xB9E4F4); }
    }
}
