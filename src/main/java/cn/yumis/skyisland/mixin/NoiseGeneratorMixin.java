package cn.yumis.skyisland.mixin;

import cn.yumis.skyisland.world.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.concurrent.CompletableFuture;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseGeneratorMixin implements VoidMarker {
    @Unique private boolean yumis$void;
    public boolean yumis$isVoid() { return yumis$void; }
    public void yumis$markVoid() { yumis$void = true; }
    @Inject(method="codec", at=@At("HEAD"), cancellable=true)
    private void codec(CallbackInfoReturnable<MapCodec<? extends ChunkGenerator>> ci) { if (yumis$void) ci.setReturnValue(VoidWorlds.CODEC); }
    @Inject(method="fillFromNoise", at=@At("HEAD"), cancellable=true)
    private void fill(Blender b, RandomState r, StructureManager s, ChunkAccess c, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> ci) { if (yumis$void) ci.setReturnValue(CompletableFuture.completedFuture(c)); }
    @Inject(method="buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V", at=@At("HEAD"), cancellable=true)
    private void surface(WorldGenRegion w, StructureManager s, RandomState r, ChunkAccess c, CallbackInfo ci) { if (yumis$void) ci.cancel(); }
    @Inject(method="applyCarvers", at=@At("HEAD"), cancellable=true)
    private void carve(WorldGenRegion w, long seed, RandomState r, BiomeManager b, StructureManager s, ChunkAccess c, CallbackInfo ci) { if (yumis$void) ci.cancel(); }
    @Inject(method="spawnOriginalMobs", at=@At("HEAD"), cancellable=true)
    private void mobs(WorldGenRegion w, CallbackInfo ci) { if (yumis$void) ci.cancel(); }
    @Inject(method="getBaseHeight", at=@At("HEAD"), cancellable=true)
    private void height(int x, int z, Heightmap.Types t, LevelHeightAccessor h, RandomState r, CallbackInfoReturnable<Integer> ci) { if (yumis$void) ci.setReturnValue(h.getMinY()); }
    @Inject(method="getBaseColumn", at=@At("HEAD"), cancellable=true)
    private void column(int x, int z, LevelHeightAccessor h, RandomState r, CallbackInfoReturnable<NoiseColumn> ci) {
        if (yumis$void) { var states = new net.minecraft.world.level.block.state.BlockState[h.getHeight()]; java.util.Arrays.fill(states, Blocks.AIR.defaultBlockState()); ci.setReturnValue(new NoiseColumn(h.getMinY(), states)); }
    }
}
