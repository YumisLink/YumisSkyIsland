package cn.yumis.skyisland.effect;

import cn.yumis.skyisland.YumisSkyIsland;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class ModEffects {
    public static final Holder<MobEffect> CLOUD_STEP = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, YumisSkyIsland.id("cloud_step"), new CloudStepEffect());

    private ModEffects() {}
    public static void register() {}

    private static final class CloudStepEffect extends MobEffect {
        private CloudStepEffect() { super(MobEffectCategory.BENEFICIAL, 0xB9E4F4); }
    }
}
