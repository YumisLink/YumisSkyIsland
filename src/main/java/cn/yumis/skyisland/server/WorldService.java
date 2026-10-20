package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.ClaimGrid;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.effect.ModEffects;
import cn.yumis.skyisland.world.VoidWorlds;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.Container;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class WorldService implements AutoCloseable {
    public static final TicketType LOAD = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("yumis_sky_island", "island_load"), new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    public static final TicketType RESET_LOAD = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("yumis_sky_island", "reset_load"), new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    public static final TicketType REFRESH_LOAD = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("yumis_sky_island", "refresh_hold"), new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    public static void registerTickets() { }
    private record Area(ServerLevel level, ChunkPos chunk, int radius) {}
    private final Map<Area, Integer> tickets = new HashMap<>();
    private final Set<UUID> generating = new HashSet<>();
    private static final class Transfer {
        final Entity entity; final ServerLevel source; final UUID party; final long epoch;
        final List<ServerPlayer> players; final Map<UUID,UUID> activeParties=new HashMap<>(); final int started;
        BlockPos portalEntry;
        Transfer(Entity entity,IslandState.Party party,List<ServerPlayer> players,int started) {
            this.entity=entity;this.source=(ServerLevel)entity.level();this.party=party==null?null:party.id;
            this.epoch=party==null?0:party.overworldEpoch;this.players=players;this.started=started;
        }
    }
    private final Map<UUID,Transfer> pending=new HashMap<>();
    private final Map<UUID,Integer> rescueRetry=new HashMap<>();
    public final Set<UUID> waitingPlayers = new HashSet<>();
    private final SkyServer runtime;
    private boolean lobbyLoading;
    public WorldService(SkyServer runtime) { this.runtime = runtime; }
    private static List<ServerPlayer> players(Entity entity) {
        List<ServerPlayer> result=new ArrayList<>();if(entity instanceof ServerPlayer p)result.add(p);
        for(Entity passenger:entity.getIndirectPassengers())if(passenger instanceof ServerPlayer p)result.add(p);
        return result;
    }
    private Transfer begin(Entity entity,IslandState.Party party) {
        List<ServerPlayer> players=players(entity);
        Transfer previous=pending.get(entity.getUUID());if(previous!=null)finish(previous);
        for(ServerPlayer player:players){previous=pending.get(player.getUUID());if(previous!=null)finish(previous);}
        Transfer transfer=new Transfer(entity,party,players,runtime.server.getTickCount());
        pending.put(entity.getUUID(),transfer);
        for(ServerPlayer player:players) {
            var active=runtime.parties.active(player.getUUID());transfer.activeParties.put(player.getUUID(),active==null?null:active.id);
            pending.put(player.getUUID(),transfer);waitingPlayers.add(player.getUUID());
        }
        return transfer;
    }
    private boolean valid(Transfer transfer) {
        if(runtime.closing||pending.get(transfer.entity.getUUID())!=transfer||transfer.entity.isRemoved()
                ||!transfer.entity.isAlive()||transfer.entity.level()!=transfer.source)return false;
        if(transfer.party!=null) {
            var party=runtime.state().parties.get(transfer.party);
            if(party==null||party.resetInProgress||party.overworldEpoch!=transfer.epoch)return false;
        }
        for(ServerPlayer player:transfer.players) {
            var active=runtime.parties.active(player.getUUID());
            if(runtime.server.getPlayerList().getPlayer(player.getUUID())!=player||!player.isAlive()||player.level()!=transfer.source
                    ||!Objects.equals(transfer.activeParties.get(player.getUUID()),active==null?null:active.id))return false;
        }
        if(transfer.portalEntry!=null&&!touchingPortal(transfer,transfer.portalEntry))return false;
        return true;
    }
    private void finish(Transfer transfer) {
        pending.remove(transfer.entity.getUUID(),transfer);
        for(ServerPlayer player:transfer.players)if(pending.remove(player.getUUID(),transfer))waitingPlayers.remove(player.getUUID());
        // The root player's map entry was removed above, too.
        if(transfer.entity instanceof ServerPlayer p&&!pending.containsKey(p.getUUID()))waitingPlayers.remove(p.getUUID());
    }
    private void fail(Transfer transfer,String message) {
        if(pending.get(transfer.entity.getUUID())!=transfer)return;
        for(ServerPlayer player:transfer.players)if(runtime.server.getPlayerList().getPlayer(player.getUUID())==player)SkyServer.tell(player,message);
        transfer.entity.setPortalCooldown(40);finish(transfer);
    }
    public void disconnected(UUID player) {
        Transfer transfer=pending.get(player);if(transfer!=null)finish(transfer);rescueRetry.remove(player);
    }
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
            runtime.deferred(() -> runtime.change(null, SyncImpact.none(), s -> s.lobbyReady = true, () -> lobbyLoading = false));
        }, () -> lobbyLoading = false);
    }
    public void lobby(ServerPlayer player) {
        Transfer transfer=begin(player,null);
        load(runtime.server.overworld(), lobbyPoint(), 1, () -> {
            if(valid(transfer)){initializeLobby();teleport(player,runtime.server.overworld(),lobbyPoint());}finish(transfer);
        }, () -> finish(transfer));
    }
    public void home(ServerPlayer player, IslandState.Party party) {
        if (party == null || party.resetInProgress) { SkyServer.tell(player, "岛正在重开，请稍后返回"); return; }
        if (!party.starterReady) { if(party.members.contains(player.getUUID()))starter(player,party);else SkyServer.tell(player,"对方初始岛还没有生成完成");return; }
        BlockPos point = new BlockPos((int) party.homeX, party.homeY, (int) party.homeZ);
        long epoch = party.overworldEpoch;
        Transfer transfer=begin(player,party);
        load(runtime.server.overworld(), point, 0, () -> {
            if(!valid(transfer)){finish(transfer);return;}
            var current = runtime.state().parties.get(party.id);
            if (current == null || current.resetInProgress || current.overworldEpoch != epoch || !current.starterReady) {
                SkyServer.tell(player, "岛状态已变化，请稍后重新回岛");
            } else if(current.homeX!=point.getX()||current.homeY!=point.getY()||current.homeZ!=point.getZ()) {
                finish(transfer);home(player,current);return;
            } else if (safe(runtime.server.overworld(), point)) {
                teleport(player, runtime.server.overworld(), point);
                if(runtime.parties.active(player.getUUID())!=null && runtime.parties.active(player.getUUID()).id.equals(party.id))player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, point, 0, 0), true), false);
            } else SkyServer.tell(player, "回家点已不安全；修复落脚地面后 /is sethome，或由岛主 /is reset 重开");
            finish(transfer);
        }, () -> finish(transfer));
    }
    private void starter(ServerPlayer owner, IslandState.Party party) {
        if (!generating.add(party.id)) { SkyServer.tell(owner, "正在生成初始岛"); return; }
        ClaimGrid.Region region = ClaimGrid.region(party.slot); int cx = (int) region.centerX(), cz = (int) region.centerZ();
        BlockPos home = new BlockPos(cx - 1, 81, cz - 1);Transfer transfer=begin(owner,party);
        long epoch = party.overworldEpoch;
        Runnable finished = () -> { generating.remove(party.id); finish(transfer); };
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
            BlockPos supplies = new BlockPos(cx - 2, 81, cz - 2);
            world.setBlock(supplies, Blocks.CHEST.defaultBlockState(), 3);
            if (world.getBlockEntity(supplies) instanceof Container chest) {
                chest.setItem(0, new ItemStack(Items.ICE, 3));
                chest.setItem(1, new ItemStack(Items.LAVA_BUCKET));
            }
            runtime.deferred(() -> {
                var ready = runtime.state().parties.get(party.id);
                if (ready == null || ready.resetInProgress || ready.overworldEpoch != epoch) { finished.run(); return; }
                runtime.change(owner, SyncImpact.none(), s -> {
                IslandState.Party p = s.parties.get(party.id); p.starterReady = true; p.homeX = home.getX(); p.homeY = home.getY(); p.homeZ = home.getZ();
                runtime.progression.initialize(p);
            }, () -> {
                boolean mayTeleport=valid(transfer);
                finished.run();
                var p = runtime.state().parties.get(party.id);
                var active = runtime.parties.active(owner.getUUID());
                if (!mayTeleport||p == null || p.resetInProgress || p.overworldEpoch != epoch || active == null || !active.id.equals(p.id) || runtime.server.getPlayerList().getPlayer(owner.getUUID()) != owner) return;
                teleport(owner, world, home);
                owner.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, home, 0, 0), true), false);
            });
            });
        }, finished);
    }
    public void setHome(ServerPlayer player) {
        IslandState.Party party = runtime.parties.requireMember(player); BlockPos point = player.blockPosition();
        if (!player.level().dimension().equals(Level.OVERWORLD) || runtime.claim(point) != party || !safe(player.level(), point)) throw new IllegalArgumentException("回家点必须在本队主世界区域内且有安全地面");
        runtime.change(player, SyncImpact.none(), s -> { var p = s.parties.get(party.id); p.homeX = point.getX(); p.homeY = point.getY(); p.homeZ = point.getZ(); }, () -> SkyServer.tell(player, "已设置回家点"));
    }
    public static boolean safe(ServerLevel level, BlockPos point) { return level.getBlockState(point).isAir() && level.getBlockState(point.above()).isAir() && level.getBlockState(point.below()).isFaceSturdy(level, point.below(), Direction.UP); }
    public static void teleport(ServerPlayer player, ServerLevel level, BlockPos point) {
        player.stopRiding(); player.teleportTo(level, point.getX() + .5, point.getY(), point.getZ() + .5, Set.of(), player.getYRot(), player.getXRot(), true); player.resetFallDistance();
    }
    /** Programmatic routing used by internal callers; actual portal blocks supply their entry position. */
    public TeleportTransition portal(ServerLevel source,Entity entity,String kind) {
        return portal(source,entity,entity.portalProcess==null?null:entity.portalProcess.getEntryPosition(),kind);
    }
    public TeleportTransition portal(ServerLevel source,Entity entity,BlockPos entry,String kind) {
        if(pending.containsKey(entity.getUUID())||!entity.isAlive()||entity.level()!=source)return null;
        UUID origin=Protection.entityParty(runtime,entity);
        IslandState.Party party=entity instanceof ServerPlayer p?runtime.parties.active(p.getUUID()):origin==null?null:runtime.state().parties.get(origin);
        if(party==null||party.resetInProgress)return null;
        for(ServerPlayer player:players(entity))if(runtime.parties.active(player.getUUID())!=party)return null;
        ResourceKey<Level> dimension=kind.equals("nether")?(source.dimension().equals(Level.NETHER)?Level.OVERWORLD:Level.NETHER)
                :kind.equals("gateway")?Level.END:source.dimension().equals(Level.END)?Level.OVERWORLD:Level.END;
        ServerLevel target=runtime.server.getLevel(dimension);if(target==null)return null;
        BlockPos sourceEntry=entry==null?null:entry.immutable();
        Transfer transfer=begin(entity,party);
        if(kind.equals("nether"))transfer.portalEntry=sourceEntry;
        if(kind.equals("nether")&&dimension.equals(Level.OVERWORLD))returnFromNether(transfer);
        else if(kind.equals("nether")) {
            BlockPos point=fixedPoint(party);
            load(target,point,1,()->{
                if(!portalValid(transfer,sourceEntry,kind)){finish(transfer);return;}
                BlockPos landing=ensureNetherPortal(target,party,entity,point);
                if(landing==null){fail(transfer,"地狱固定返程门的位置被占用或没有安全出口；请清理本队落点附近后重试");return;}
                if(source.dimension().equals(Level.OVERWORLD)&&sourceEntry!=null&&!transfer.players.isEmpty())
                    rememberEntrance(transfer,sourceEntry,()->load(target,point,1,()->{
                        if(!portalValid(transfer,sourceEntry,kind)){finish(transfer);return;}
                        BlockPos exit=ensureNetherPortal(target,party,entity,point);
                        if(exit==null)fail(transfer,"地狱返程门在等待期间发生变化，请重试");else move(transfer,target,exit,false);
                    },()->fail(transfer,"地狱落点加载失败，请稍后重试")));
                else move(transfer,target,landing,false);
            },()->fail(transfer,"地狱落点加载失败，请稍后重试"));
        } else if(dimension.equals(Level.OVERWORLD))homeFallback(transfer);
        else {
            BlockPos point=fixedPoint(party);
            load(target,point,1,()->{
                if(!valid(transfer)){finish(transfer);return;}
                if(!safe(target,point))for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                    target.setBlock(point.offset(x,-1,z),Blocks.OBSIDIAN.defaultBlockState(),3);
                    target.setBlock(point.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);
                    target.setBlock(point.offset(x,1,z),Blocks.AIR.defaultBlockState(),3);
                }
                if(safe(target,point))move(transfer,target,point,false);else fail(transfer,"末地落点不安全，请稍后重试");
            },()->fail(transfer,"目标区域加载失败，请稍后重试"));
        }
        return null; // Keep vanilla from searching or creating an exit while loading.
    }
    private static BlockPos fixedPoint(IslandState.Party party) {
        ClaimGrid.Region region=ClaimGrid.region(party.slot);return new BlockPos(region.centerX()+8,81,region.centerZ());
    }
    private static Direction.Axis portalAxis(ServerLevel level,BlockPos pos) {
        BlockState state=level.getBlockState(pos);return state.is(Blocks.NETHER_PORTAL)?state.getValue(NetherPortalBlock.AXIS):null;
    }
    private static BlockUtil.FoundRectangle portalRectangle(ServerLevel level,BlockPos pos,Direction.Axis axis) {
        if(axis==null||portalAxis(level,pos)!=axis||PortalShape.findPortalShape(level,pos,PortalShape::isComplete,axis).isEmpty())return null;
        return BlockUtil.getLargestRectangleAround(pos,axis,21,Direction.Axis.Y,21,
                next->level.hasChunkAt(next)&&portalAxis(level,next)==axis);
    }
    private boolean portalValid(Transfer transfer,BlockPos entry,String kind) {
        if(!valid(transfer))return false;
        if(entry==null||!kind.equals("nether"))return true;
        return touchingPortal(transfer,entry);
    }
    private static boolean touchingPortal(Transfer transfer,BlockPos entry) {
        var rectangle=portalRectangle(transfer.source,entry,portalAxis(transfer.source,entry));
        if(rectangle==null)return false;
        Direction.Axis axis=portalAxis(transfer.source,entry);
        BlockPos end=rectangle.minCorner.relative(Direction.get(Direction.AxisDirection.POSITIVE,axis),rectangle.axis1Size-1).above(rectangle.axis2Size-1);
        return transfer.entity.getBoundingBox().intersects(new AABB(rectangle.minCorner.getX(),rectangle.minCorner.getY(),rectangle.minCorner.getZ(),
                end.getX()+1,end.getY()+1,end.getZ()+1).inflate(.1));
    }
    private static boolean safeFor(ServerLevel level,Entity entity,BlockPos point) {
        if(!level.isInWorldBounds(point)||!level.hasChunkAt(point)||!level.getWorldBorder().isWithinBounds(point)
                ||!safe(level,point)||!level.getFluidState(point.below()).isEmpty())return false;
        BlockState floor=level.getBlockState(point.below());
        if(floor.is(Blocks.MAGMA_BLOCK)||floor.is(Blocks.CAMPFIRE)||floor.is(Blocks.SOUL_CAMPFIRE)||floor.is(Blocks.CACTUS))return false;
        return level.noCollision(entity,entity.getDimensions(entity.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(point)));
    }
    private BlockPos portalLanding(ServerLevel level,Entity entity,BlockPos portal,Transfer transfer) {
        Direction.Axis axis=portalAxis(level,portal);var rectangle=portalRectangle(level,portal,axis);if(rectangle==null)return null;
        Direction width=Direction.get(Direction.AxisDirection.POSITIVE,axis),normal=axis==Direction.Axis.X?Direction.SOUTH:Direction.EAST;
        List<BlockPos> candidates=new ArrayList<>();
        for(int i=0;i<rectangle.axis1Size;i++)for(int side:new int[]{1,-1})candidates.add(rectangle.minCorner.relative(width,i).relative(normal,side));
        candidates.sort(Comparator.comparingDouble(pos->pos.distSqr(portal.atY(rectangle.minCorner.getY()))));
        for(BlockPos point:candidates)if(safeFor(level,entity,point)
                &&(transfer==null||transfer.players.stream().allMatch(player->Protection.canBuild(level,player,point))))return point;
        return null;
    }
    /** A small fixed structure; preflight everything and never erase a player's block. */
    private BlockPos ensureNetherPortal(ServerLevel level,IslandState.Party party,Entity entity,BlockPos point) {
        BlockPos inside=point.north();
        if(portalAxis(level,inside)!=null)return portalLanding(level,entity,inside,null);
        Map<BlockPos,BlockState> additions=new LinkedHashMap<>();
        for(int x=-1;x<=2;x++)for(int z=-2;z<=1;z++) {
            BlockPos pos=point.offset(x,-1,z);BlockState state=level.getBlockState(pos);
            if(!portalBuildable(level,party,pos)||!state.getFluidState().isEmpty())return null;
            if(state.isAir())additions.put(pos,Blocks.OBSIDIAN.defaultBlockState());
            else if(!state.isFaceSturdy(level,pos,Direction.UP))return null;
        }
        for(int x=-1;x<=2;x++)for(int y=-1;y<=3;y++) {
            BlockPos pos=inside.offset(x,y,0);BlockState state=level.getBlockState(pos);
            if(!portalBuildable(level,party,pos))return null;
            boolean frame=x==-1||x==2||y==-1||y==3;
            if(frame) { if(!state.isAir()&&!state.is(Blocks.OBSIDIAN))return null;if(state.isAir())additions.put(pos,Blocks.OBSIDIAN.defaultBlockState()); }
            else if(!state.isAir()&&!(state.is(Blocks.NETHER_PORTAL)&&state.getValue(NetherPortalBlock.AXIS)==Direction.Axis.X))return null;
        }
        for(int x=0;x<=1;x++)for(int y=0;y<2;y++)if(!level.getBlockState(point.offset(x,y,0)).isAir())return null;
        if(!level.noCollision(entity,entity.getDimensions(entity.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(point))))return null;
        List<BlockPos> placed=new ArrayList<>();
        for(var addition:additions.entrySet()) {
            if(!level.setBlock(addition.getKey(),addition.getValue(),Block.UPDATE_ALL)) { rollbackPortal(level,placed);return null; }
            placed.add(addition.getKey());
        }
        var shape=PortalShape.findEmptyPortalShape(level,inside,Direction.Axis.X);
        if(shape.isEmpty()){rollbackPortal(level,placed);return null;}
        shape.get().createPortalBlocks(level);
        return portalRectangle(level,inside,Direction.Axis.X)!=null&&safeFor(level,entity,point)?point:null;
    }
    private boolean portalBuildable(ServerLevel level,IslandState.Party party,BlockPos pos) {
        return level.isInWorldBounds(pos)&&level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)
                &&runtime.claim(pos)==runtime.state().parties.get(party.id)&&!level.getBlockState(pos).hasBlockEntity()
                &&!cn.yumis.skyisland.prefab.PrefabService.locked(level,pos);
    }
    private static void rollbackPortal(ServerLevel level,List<BlockPos> placed) {
        for(BlockPos pos:placed)if(level.getBlockState(pos).is(Blocks.OBSIDIAN))level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
    }
    private void rememberEntrance(Transfer transfer,BlockPos entry,Runnable after) {
        if(!portalValid(transfer,entry,"nether")){finish(transfer);return;}
        if(runtime.store.failure()!=null){fail(transfer,"入口位置保存失败，暂不传送，请联系管理员");return;}
        if(!runtime.store.available()){runtime.deferred(()->rememberEntrance(transfer,entry,after));return;}
        IslandState.Party sourceParty=runtime.claim(entry);Direction.Axis axis=portalAxis(transfer.source,entry);
        if(transfer.players.stream().anyMatch(player->!Protection.canBuild(transfer.source,player,entry))) { fail(transfer,"你没有使用这个入口领地的权限");return; }
        runtime.store.commit(state->{
            for(ServerPlayer player:transfer.players) {
                IslandState.NetherReturnPortal saved=new IslandState.NetherReturnPortal();saved.partyId=transfer.party;saved.overworldEpoch=transfer.epoch;
                saved.sourcePartyId=sourceParty==null?null:sourceParty.id;saved.sourceOverworldEpoch=sourceParty==null?0:sourceParty.overworldEpoch;
                saved.x=entry.getX();saved.y=entry.getY();saved.z=entry.getZ();saved.axis=axis.getName();
                PartyService.person(state,player).netherReturnPortal=saved;
            }
        },()->{if(valid(transfer))after.run();else finish(transfer);},error->{
            YumisSkyIsland.LOGGER.error("地狱入口记录保存失败",error);fail(transfer,"入口位置保存失败，暂不传送，请联系管理员");
        });
    }
    private IslandState.NetherReturnPortal returnRecord(ServerPlayer player,Transfer transfer) {
        var person=runtime.state().players.get(player.getUUID());var record=person==null?null:person.netherReturnPortal;
        if(record==null||!Objects.equals(record.partyId,transfer.party)||record.overworldEpoch!=transfer.epoch)return null;
        if(record.sourcePartyId!=null) {
            var source=runtime.state().parties.get(record.sourcePartyId);
            if(source==null||source.resetInProgress||source.overworldEpoch!=record.sourceOverworldEpoch)return null;
        }
        return record;
    }
    private static boolean sameEntrance(IslandState.NetherReturnPortal first,IslandState.NetherReturnPortal second) {
        if(first==null||second==null)return first==second;
        return first.x==second.x&&first.y==second.y&&first.z==second.z&&Objects.equals(first.axis,second.axis)
                &&Objects.equals(first.sourcePartyId,second.sourcePartyId)&&first.sourceOverworldEpoch==second.sourceOverworldEpoch;
    }
    private void returnFromNether(Transfer transfer) {
        if(!valid(transfer)){finish(transfer);return;}
        var record=transfer.players.isEmpty()?null:returnRecord(transfer.players.getFirst(),transfer);
        if(transfer.players.stream().anyMatch(player->!sameEntrance(record,returnRecord(player,transfer)))) {
            fail(transfer,"乘客记录的主世界入口不同，请先下坐骑再分别通过返程门");return;
        }
        if(record==null){homeFallback(transfer);return;}
        BlockPos entry=new BlockPos(record.x,record.y,record.z);ServerLevel target=runtime.server.overworld();
        load(target,entry,2,()->{
            if(!valid(transfer)){finish(transfer);return;}
            boolean stillRecorded=transfer.players.stream().allMatch(player->sameEntrance(record,returnRecord(player,transfer))&&Protection.canBuild(target,player,entry));
            Direction.Axis axis="x".equals(record.axis)?Direction.Axis.X:"z".equals(record.axis)?Direction.Axis.Z:null;
            BlockPos landing=stillRecorded&&portalAxis(target,entry)==axis?portalLanding(target,transfer.entity,entry,transfer):null;
            if(landing==null)homeFallback(transfer);else move(transfer,target,landing,false);
        },()->homeFallback(transfer));
    }
    private void homeFallback(Transfer transfer) {
        if(!valid(transfer)){finish(transfer);return;}
        IslandState.Party party=runtime.state().parties.get(transfer.party);
        if(party==null){fail(transfer,"没有可返回的空岛");return;}
        BlockPos point=new BlockPos((int)party.homeX,party.homeY,(int)party.homeZ);ServerLevel target=runtime.server.overworld();
        load(target,point,0,()->{
            if(!valid(transfer)){finish(transfer);return;}
            var current=runtime.state().parties.get(transfer.party);
            if(current.homeX!=point.getX()||current.homeY!=point.getY()||current.homeZ!=point.getZ()){homeFallback(transfer);return;}
            if(safeFor(target,transfer.entity,point)&&transfer.players.stream().allMatch(player->Protection.canBuild(target,player,point)))move(transfer,target,point,false);
            else fail(transfer,"原地狱门已失效，当前回家点也不安全；请修复回家点后重试");
        },()->fail(transfer,"主世界回家点加载失败，请稍后重试"));
    }
    private void move(Transfer transfer,ServerLevel target,BlockPos point,boolean rescue) {
        if(!valid(transfer)){finish(transfer);return;}
        Entity moved=transfer.entity.teleport(new TeleportTransition(target,Vec3.atBottomCenterOf(point),Vec3.ZERO,
                transfer.entity.getYRot(),transfer.entity.getXRot(),TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
        if(moved!=null) {
            moved.setDeltaMovement(Vec3.ZERO);moved.resetFallDistance();moved.portalProcess=null;moved.setPortalCooldown();
            for(ServerPlayer player:players(moved)) {
                player.setDeltaMovement(Vec3.ZERO);player.resetFallDistance();player.portalProcess=null;player.setPortalCooldown();
                if(rescue)player.addEffect(new MobEffectInstance(ModEffects.CLOUD_STEP,300,0,false,false,true));
            }
        }
        finish(transfer);
    }
    public void rescueFromVoid(ServerPlayer player) {
        if(!player.isAlive()||player.getY()>=-100||!VoidWorlds.isSky(player.level())
                ||!player.level().dimension().equals(Level.NETHER)&&!player.level().dimension().equals(Level.END)
                ||pending.containsKey(player.getUUID())||runtime.server.getTickCount()<rescueRetry.getOrDefault(player.getUUID(),0))return;
        IslandState.Party party=runtime.parties.active(player.getUUID());
        if(party==null||party.resetInProgress||!party.starterReady){lobby(player);return;}
        player.stopRiding();Transfer transfer=begin(player,party);rescueRetry.put(player.getUUID(),runtime.server.getTickCount()+20);
        rescueAtHome(transfer);
    }
    private void rescueAtHome(Transfer transfer) {
        if(!valid(transfer)||transfer.entity.getY()>=-100){finish(transfer);return;}
        var party=runtime.state().parties.get(transfer.party);BlockPos point=new BlockPos((int)party.homeX,325,(int)party.homeZ);
        ServerLevel target=runtime.server.overworld();
        load(target,point,0,()->{
            if(!valid(transfer)||transfer.entity.getY()>=-100){finish(transfer);return;}
            var current=runtime.state().parties.get(transfer.party);
            if(current.homeX!=point.getX()||current.homeZ!=point.getZ()){rescueAtHome(transfer);return;}
            if(!target.getWorldBorder().isWithinBounds(point)){fail(transfer,"回家点超出世界边界，虚空救援暂时无法完成");return;}
            move(transfer,target,point,true);
        },()->fail(transfer,"虚空救援区域加载失败，正在等待重试"));
    }
    public void tick() {
        if(!runtime.state().lobbyReady&&!lobbyLoading)initializeLobby();
        for(Transfer transfer:new HashSet<>(pending.values())) {
            if(!valid(transfer))finish(transfer);
            else if(runtime.store.failure()!=null||runtime.server.getTickCount()-transfer.started>600)fail(transfer,"传送等待超时或存档写入失败，请稍后重试");
        }
    }
    @Override public void close() {
        for(Transfer transfer:new HashSet<>(pending.values()))finish(transfer);
        rescueRetry.clear();
        for(Area area:tickets.keySet())area.level().getChunkSource().removeTicketWithRadius(LOAD,area.chunk(),area.radius());tickets.clear();
    }
}
