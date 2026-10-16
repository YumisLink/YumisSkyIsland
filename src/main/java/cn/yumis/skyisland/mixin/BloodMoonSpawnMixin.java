package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.BloodMoonSpawnContext;
import cn.yumis.skyisland.server.SkyServer;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NaturalSpawner.class)
public abstract class BloodMoonSpawnMixin {
    @Inject(method="spawnForChunk",at=@At("HEAD"))
    private static void yumis$begin(ServerLevel level,LevelChunk chunk,NaturalSpawner.SpawnState state,List<MobCategory> categories,CallbackInfo ci){
        SkyServer runtime=SkyServer.get(level.getServer());
        if(runtime!=null&&runtime.bloodMoon.active()&&level==runtime.server.overworld())
            BloodMoonSpawnContext.begin(state,runtime.config.object("gameplay/blood_moon.toml").get("hostileSpawnMultiplier").getAsInt());
    }
    @Inject(method="spawnForChunk",at=@At("RETURN"))
    private static void yumis$end(ServerLevel level,LevelChunk chunk,NaturalSpawner.SpawnState state,List<MobCategory> categories,CallbackInfo ci){BloodMoonSpawnContext.end();}
    @Redirect(method="spawnForChunk",at=@At(value="INVOKE",target="Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private static Iterator<MobCategory> yumis$repeatMonsters(List<MobCategory> vanilla){
        // Let the normal per-category loop repeat its own cap checks. Carpet also
        // redirects spawnCategoryForChunk; redirecting that call here would crash.
        return BloodMoonSpawnContext.attempts(vanilla);
    }
}
