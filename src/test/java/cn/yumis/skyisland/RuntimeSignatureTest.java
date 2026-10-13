package cn.yumis.skyisland;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import static org.junit.jupiter.api.Assertions.*;
/** Fabric Loader JUnit transforms these real 26.2 classes; invalid required injections fail this test. */
class RuntimeSignatureTest {
    @Test void requiredMixinTargetsLoadOnTheActualVersion() throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        assertTrue(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("yumis_sky_island"),"Test must load the mod's Mixin configuration");
        assertTrue(cn.yumis.skyisland.world.VoidMarker.class.isAssignableFrom(net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.class),"Void marker Mixin must be applied");
        for(String type:new String[]{
            "net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator","net.minecraft.world.level.chunk.ChunkGenerator",
            "net.minecraft.world.level.block.NetherPortalBlock","net.minecraft.world.level.block.EndPortalBlock","net.minecraft.world.level.block.EndGatewayBlock",
            "net.minecraft.world.level.ServerExplosion","net.minecraft.world.level.block.entity.HopperBlockEntity",
            "net.minecraft.world.level.block.piston.PistonBaseBlock","net.minecraft.world.level.material.FlowingFluid",
            "net.minecraft.world.inventory.MerchantMenu","net.minecraft.world.inventory.StonecutterMenu$2",
            "net.minecraft.world.inventory.ResultSlot","net.minecraft.world.inventory.FurnaceResultSlot",
            "net.minecraft.server.level.ServerPlayerGameMode","net.minecraft.server.network.ServerGamePacketListenerImpl",
            "net.minecraft.server.players.PlayerList","net.minecraft.world.CompoundContainer",
            "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal","net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal",
            "net.minecraft.world.entity.item.FallingBlockEntity","net.minecraft.world.entity.vehicle.VehicleEntity",
            "net.minecraft.world.entity.decoration.ItemFrame","net.minecraft.world.entity.decoration.ArmorStand","net.minecraft.world.entity.decoration.BlockAttachedEntity"
        })assertNotNull(Class.forName(type,false,getClass().getClassLoader()));
        assertEquals("26.2",SharedConstants.getCurrentVersion().name());
    }
}
