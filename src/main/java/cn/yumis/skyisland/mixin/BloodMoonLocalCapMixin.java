package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.BloodMoonSpawnContext;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets="net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
public abstract class BloodMoonLocalCapMixin {
    @Redirect(method="canSpawn",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
    private int yumis$fivefoldLocalCap(MobCategory category){
        var blood=BloodMoonSpawnContext.current();
        return category.getMaxInstancesPerChunk()*(blood!=null&&category==MobCategory.MONSTER?blood.factor():1);
    }
}
