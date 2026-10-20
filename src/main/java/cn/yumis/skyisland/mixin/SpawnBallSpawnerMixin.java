package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.server.SpawnBallService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the vanilla natural spawning call chain is intercepted. Entity creation and cap callbacks stay native. */
@Mixin(NaturalSpawner.class)
public abstract class SpawnBallSpawnerMixin {
    private static final String POSITION = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V";

    @Shadow private static Optional<SpawnerData> getRandomSpawnMobAt(ServerLevel level, StructureManager structures, ChunkGenerator generator, MobCategory category, RandomSource random, BlockPos pos) { throw new AssertionError(); }

    @Inject(method="getRandomSpawnMobAt", at=@At("HEAD"), cancellable=true)
    private static void yumis$choose(ServerLevel level, StructureManager structures, ChunkGenerator generator, MobCategory category, RandomSource random, BlockPos pos, CallbackInfoReturnable<Optional<SpawnerData>> cir) {
        if (category != MobCategory.MONSTER) return;
        var service = SpawnBallService.get(level);
        var data = service == null ? null : service.target(level, pos);
        if (data != null) cir.setReturnValue(Optional.of(data));
    }

    @WrapOperation(method=POSITION, at=@At(value="INVOKE", target="Lnet/minecraft/world/level/NaturalSpawner;isValidSpawnPostitionForType(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/MobSpawnSettings$SpawnerData;Lnet/minecraft/core/BlockPos$MutableBlockPos;D)Z"))
    private static boolean yumis$eachPosition(ServerLevel level, MobCategory category, StructureManager structures, ChunkGenerator generator, SpawnerData data, BlockPos.MutableBlockPos pos, double distance, Operation<Boolean> original,
            @Local LocalRef<SpawnerData> selected, @Local LocalRef<SpawnGroupData> group) {
        var service = category == MobCategory.MONSTER ? SpawnBallService.get(level) : null;
        if (service == null) return original.call(level, category, structures, generator, data, pos, distance);
        SpawnerData target = service.target(level, pos);
        // The native loop caches its first selection for a pack, but a pack can cross chunk borders.
        if (target == null && service.owns(data)) {
            target = getRandomSpawnMobAt(level, structures, generator, category, level.getRandom(), pos).orElse(null);
            if (target == null) { group.set(null); return false; }
        }
        if (target != null) {
            if (target.type() != data.type()) group.set(null);
            selected.set(target);
            data = target;
        }
        return original.call(level, category, structures, generator, data, pos, distance);
    }

    @Inject(method="isValidSpawnPostitionForType", at=@At("HEAD"), cancellable=true)
    private static void yumis$physicalRules(ServerLevel level, MobCategory category, StructureManager structures, ChunkGenerator generator, SpawnerData data, BlockPos.MutableBlockPos pos, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (category != MobCategory.MONSTER) return;
        var service = SpawnBallService.get(level);
        if (service != null && service.target(level, pos) != null) cir.setReturnValue(service.positionAllowed(level, pos, data.type(), distance));
    }

    @Inject(method="isValidPositionForMob", at=@At("HEAD"), cancellable=true)
    private static void yumis$entityRules(ServerLevel level, Mob mob, double distance, CallbackInfoReturnable<Boolean> cir) {
        var service = SpawnBallService.get(level);
        if (service == null || !service.matches(level, mob)) return;
        int limit = mob.getType().getCategory().getDespawnDistance();
        cir.setReturnValue((distance <= (double)limit * limit || !mob.removeWhenFarAway(distance)) && service.spaceAllowed(level, mob));
    }

    @WrapOperation(method=POSITION, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private static void yumis$finalSize(ServerLevel level, Entity entity, Operation<Void> original, @Share("spawnBallAccepted") LocalBooleanRef accepted) {
        // Only cubes can grow beyond their already-checked bounds during vanilla finalization.
        // Fixed-size species (including adult -> baby changes) do not need another collision query.
        var service = entity.getType() == EntityTypes.SLIME || entity.getType() == EntityTypes.MAGMA_CUBE ? SpawnBallService.get(level) : null;
        boolean valid = !(entity instanceof Mob mob) || service == null || !service.matches(level, mob) || service.spaceAllowed(level, mob);
        accepted.set(valid);
        if (valid) original.call(level, entity);
    }

    @WrapOperation(method=POSITION, at=@At(value="INVOKE", target="Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;run(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/chunk/ChunkAccess;)V"))
    private static void yumis$countAccepted(NaturalSpawner.AfterSpawnCallback callback, Mob mob, ChunkAccess chunk, Operation<Void> original, @Share("spawnBallAccepted") LocalBooleanRef accepted) {
        if (accepted.get()) original.call(callback, mob, chunk);
    }
}
