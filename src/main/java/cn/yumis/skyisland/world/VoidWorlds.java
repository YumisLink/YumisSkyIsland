package cn.yumis.skyisland.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

public final class VoidWorlds {
    public static final MapCodec<NoiseBasedChunkGenerator> CODEC = NoiseBasedChunkGenerator.CODEC.xmap(g -> { ((VoidMarker) (Object) g).yumis$markVoid(); return g; }, g -> g);
    public static void register() { Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath("yumis_sky_island", "void_noise"), CODEC); }
    public static boolean isSky(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof VoidMarker marker && marker.yumis$isVoid();
    }
}
