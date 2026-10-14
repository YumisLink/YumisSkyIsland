package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.ClaimGrid;
import cn.yumis.skyisland.data.IslandState;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class WorldService implements AutoCloseable {
    public static final TicketType LOAD = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("yumis_sky_island", "island_load"), new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    public static final TicketType RESET_LOAD = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("yumis_sky_island", "reset_load"), new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    public static final TicketType REFRESH_LOAD = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("yumis_sky_island", "refresh_hold"), new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    public static void registerTickets() { }
    private record Area(ServerLevel level, ChunkPos chunk, int radius) {}
    private final Map<Area, Integer> tickets = new HashMap<>();
    private final Set<UUID> generating = new HashSet<>(), portals = new HashSet<>();
    public final Set<UUID> waitingPlayers = new HashSet<>();
    private final SkyServer runtime;
    private boolean lobbyLoading;
    public WorldService(SkyServer runtime) { this.runtime = runtime; }
    public void load(ServerLevel level, BlockPos pos, int radius, Runnable ready, Runnable failed) {
        if (runtime.closing) { failed.run(); return; }
        Area area = new Area(level, ChunkPos.containing(pos), radius);
        tickets.merge(area, 1, Integer::sum);
        level.getChunkSource().addTicketAndLoadWithRadius(LOAD, area.chunk(), radius).whenComplete((result, error) -> runtime.server.execute(() -> {
            try {
                if (runtime.closing || error != null || result instanceof ChunkResult<?> r && !r.isSuccess()) { if (error != null) YumisSkyIsland.LOGGER.error("加载岛区失败", error); failed.run(); }
                else ready.run();
            } finally {
                int left = tickets.getOrDefault(area, 1) - 1;
                if (left == 0) { tickets.remove(area); level.getChunkSource().removeTicketWithRadius(LOAD, area.chunk(), radius); }
                else tickets.put(area, left);
            }
        }));
    }
    public static BlockPos lobbyPoint() { return new BlockPos(ClaimGrid.LOBBY_X + 10, ClaimGrid.LOBBY_Y + 1, ClaimGrid.LOBBY_Z + 10); }
    public void initializeLobby() {
        var world = runtime.server.overworld();
        world.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, lobbyPoint(), 0, 0));
        if (runtime.state().lobbyReady || lobbyLoading) return;
        lobbyLoading = true;
        load(world, lobbyPoint(), 1, () -> {
            for (int x = 0; x < 20; x++) for (int z = 0; z < 20; z++) world.setBlock(new BlockPos(ClaimGrid.LOBBY_X + x, ClaimGrid.LOBBY_Y, ClaimGrid.LOBBY_Z + z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            runtime.deferred(() -> runtime.change(null, s -> s.lobbyReady = true, () -> lobbyLoading = false));
        }, () -> lobbyLoading = false);
    }
    public void lobby(ServerPlayer player) {
        waitingPlayers.add(player.getUUID());
        load(runtime.server.overworld(), lobbyPoint(), 1, () -> {
            initializeLobby(); teleport(player, runtime.server.overworld(), lobbyPoint()); waitingPlayers.remove(player.getUUID());
        }, () -> waitingPlayers.remove(player.getUUID()));
    }
    public void home(ServerPlayer player, IslandState.Party party) {
        if (party == null || party.resetInProgress) { SkyServer.tell(player, "岛正在重开，请稍后返回"); return; }
        if (!party.starterReady) { if(party.members.contains(player.getUUID()))starter(player,party);else SkyServer.tell(player,"对方初始岛还没有生成完成");return; }
        BlockPos point = new BlockPos((int) party.homeX, party.homeY, (int) party.homeZ);
        long epoch = party.overworldEpoch;
        waitingPlayers.add(player.getUUID());
        load(runtime.server.overworld(), point, 0, () -> {
            var current = runtime.state().parties.get(party.id);
            if (current == null || current.resetInProgress || current.overworldEpoch != epoch || !current.starterReady) {
                SkyServer.tell(player, "岛状态已变化，请稍后重新回岛");
            } else if (safe(runtime.server.overworld(), point)) {
                teleport(player, runtime.server.overworld(), point);
                if(runtime.parties.active(player.getUUID())!=null && runtime.parties.active(player.getUUID()).id.equals(party.id))player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, point, 0, 0), true), false);
            } else SkyServer.tell(player, "回家点已不安全；修复落脚地面后 /is sethome，或由岛主 /is reset 重开");
            waitingPlayers.remove(player.getUUID());
        }, () -> waitingPlayers.remove(player.getUUID()));
    }
    private void starter(ServerPlayer owner, IslandState.Party party) {
        if (!generating.add(party.id)) { SkyServer.tell(owner, "正在生成初始岛"); return; }
        ClaimGrid.Region region = ClaimGrid.region(party.slot); int cx = (int) region.centerX(), cz = (int) region.centerZ();
        BlockPos home = new BlockPos(cx - 1, 81, cz - 1); waitingPlayers.add(owner.getUUID());
        long epoch = party.overworldEpoch;
        Runnable finished = () -> { generating.remove(party.id); waitingPlayers.remove(owner.getUUID()); };
        load(runtime.server.overworld(), home, 1, () -> {
            var current = runtime.state().parties.get(party.id);
            if (current == null || current.resetInProgress || current.overworldEpoch != epoch || current.starterReady) { finished.run(); return; }
            ServerLevel world = runtime.server.overworld();
            for (int x = cx - 3; x < cx + 3; x++) for (int y = 75; y < 81; y++) for (int z = cz - 3; z < cz + 3; z++) world.setBlock(new BlockPos(x, y, z), (y == 80 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 3);
            int tx = cx + 1, tz = cz + 1;
            for (int y = 81; y <= 85; y++) world.setBlock(new BlockPos(tx, y, tz), Blocks.OAK_LOG.defaultBlockState(), 3);
            for (int y = 83; y <= 86; y++) {
                int radius = y == 86 ? 1 : 2;
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) if (!(x == 0 && z == 0 && y <= 85) && !(Math.abs(x) == radius && Math.abs(z) == radius))
                    world.setBlock(new BlockPos(tx + x, y, tz + z), Blocks.OAK_LEAVES.defaultBlockState(), 3);
            }
            runtime.deferred(() -> {
                var ready = runtime.state().parties.get(party.id);
                if (ready == null || ready.resetInProgress || ready.overworldEpoch != epoch) { finished.run(); return; }
                runtime.change(owner, s -> {
                IslandState.Party p = s.parties.get(party.id); p.starterReady = true; p.homeX = home.getX(); p.homeY = home.getY(); p.homeZ = home.getZ();
                runtime.progression.initialize(p);
            }, () -> {
                finished.run();
                var p = runtime.state().parties.get(party.id);
                var active = runtime.parties.active(owner.getUUID());
                if (p == null || p.resetInProgress || p.overworldEpoch != epoch || active == null || !active.id.equals(p.id) || runtime.server.getPlayerList().getPlayer(owner.getUUID()) != owner) return;
                teleport(owner, world, home);
                owner.getInventory().placeItemBackInInventory(new ItemStack(Items.WATER_BUCKET)); owner.getInventory().placeItemBackInInventory(new ItemStack(Items.LAVA_BUCKET));
                owner.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, home, 0, 0), true), false);
            });
            });
        }, finished);
    }
    public void setHome(ServerPlayer player) {
        IslandState.Party party = runtime.parties.requireMember(player); BlockPos point = player.blockPosition();
        if (!player.level().dimension().equals(Level.OVERWORLD) || runtime.claim(point) != party || !safe(player.level(), point)) throw new IllegalArgumentException("回家点必须在本队主世界区域内且有安全地面");
        runtime.change(player, s -> { var p = s.parties.get(party.id); p.homeX = point.getX(); p.homeY = point.getY(); p.homeZ = point.getZ(); }, () -> SkyServer.tell(player, "已设置回家点"));
    }
    public static boolean safe(ServerLevel level, BlockPos point) { return level.getBlockState(point).isAir() && level.getBlockState(point.above()).isAir() && level.getBlockState(point.below()).isFaceSturdy(level, point.below(), Direction.UP); }
    public static void teleport(ServerPlayer player, ServerLevel level, BlockPos point) {
        player.stopRiding(); player.teleportTo(level, point.getX() + .5, point.getY(), point.getZ() + .5, Set.of(), player.getYRot(), player.getXRot(), true); player.resetFallDistance();
    }
    public TeleportTransition portal(ServerLevel source, Entity entity, String kind) {
        if (!portals.add(entity.getUUID())) return null;
        java.util.UUID origin=Protection.entityParty(runtime,entity);
        IslandState.Party party = entity instanceof ServerPlayer p ? runtime.parties.active(p.getUUID()) : origin==null?null:runtime.state().parties.get(origin);
        if (party == null || party.resetInProgress) { portals.remove(entity.getUUID()); return null; }
        for (Entity rider : entity.getIndirectPassengers()) if (rider instanceof ServerPlayer p && runtime.parties.active(p.getUUID()) != party) { portals.remove(entity.getUUID()); return null; }
        ResourceKey<Level> dimension = kind.equals("nether") ? (source.dimension().equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER) : kind.equals("gateway") ? Level.END : source.dimension().equals(Level.END) ? Level.OVERWORLD : Level.END;
        ServerLevel target = runtime.server.getLevel(dimension);
        if (target == null) { portals.remove(entity.getUUID()); return null; }
        ClaimGrid.Region region = ClaimGrid.region(party.slot);
        BlockPos point = dimension.equals(Level.OVERWORLD) ? new BlockPos((int) party.homeX, party.homeY, (int) party.homeZ) : new BlockPos((int) region.centerX() + 8, 81, (int) region.centerZ());
        if (entity instanceof ServerPlayer p) waitingPlayers.add(p.getUUID());
        long epoch = party.overworldEpoch;
        load(target, point, 0, () -> {
            var current = runtime.state().parties.get(party.id);
            var active = entity instanceof ServerPlayer p ? runtime.parties.active(p.getUUID()) : current;
            if (current == null || current.resetInProgress || current.overworldEpoch != epoch || active == null || !active.id.equals(current.id)) {
                portals.remove(entity.getUUID()); waitingPlayers.remove(entity.getUUID()); return;
            }
            if (!dimension.equals(Level.OVERWORLD) && !safe(target, point)) {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { target.setBlock(point.offset(x, -1, z), Blocks.OBSIDIAN.defaultBlockState(), 3); target.setBlock(point.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3); target.setBlock(point.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3); }
            }
            if (!entity.isRemoved() && safe(target, point)) {
                Entity moved = entity.teleport(new TeleportTransition(target, Vec3.atBottomCenterOf(point), Vec3.ZERO, entity.getYRot(), entity.getXRot(), TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
                if (moved != null) { moved.resetFallDistance(); moved.setPortalCooldown(); }
            }
            portals.remove(entity.getUUID()); waitingPlayers.remove(entity.getUUID());
        }, () -> { portals.remove(entity.getUUID()); waitingPlayers.remove(entity.getUUID()); });
        return null; // Vanilla must not search/create an exit while the asynchronous load is pending.
    }
    public void tick() { if (!runtime.state().lobbyReady && !lobbyLoading) initializeLobby(); }
    @Override public void close() { for (Area area : tickets.keySet()) area.level().getChunkSource().removeTicketWithRadius(LOAD, area.chunk(), area.radius()); tickets.clear(); }
}
