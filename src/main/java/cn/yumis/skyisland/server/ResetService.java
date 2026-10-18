package cn.yumis.skyisland.server;
import cn.yumis.skyisland.core.ClaimGrid;
import cn.yumis.skyisland.mixin.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.concurrent.*;
public final class ResetService implements AutoCloseable {
    private record Confirmation(String code,long expires) {}
    private static final class Work { UUID party; boolean loading,saving; LevelChunk chunk; int cursor; }
    private final SkyServer runtime;
    private final Map<UUID,Confirmation> confirmations = new HashMap<>();
    private final Set<UUID> enumerating = new HashSet<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("yumis-reset-index").factory());
    private Work current;
    public ResetService(SkyServer runtime) { this.runtime = runtime; }
    public String propose(ServerPlayer owner) {
        runtime.parties.requireOwner(owner); String code = Integer.toString(100000 + owner.getRandom().nextInt(900000));
        confirmations.put(owner.getUUID(),new Confirmation(code,runtime.state().effectiveTicks + 1200));
        SkyServer.tell(owner,"重开会删除本队主世界、"+cn.yumis.skyisland.core.Money.currencyName()+"、岛等级、队伍主线、死亡次数、悬赏格、售出历史和首获记录。背包、个人职业、个人支线与职业任务、地狱、末地、当日库存与售出额度保留。60秒内输入 /is reset confirm " + code);
        return code;
    }
    public void confirm(ServerPlayer owner,String code) {
        var party = runtime.parties.requireOwner(owner); Confirmation token = confirmations.remove(owner.getUUID());
        if (token == null || token.expires < runtime.state().effectiveTicks || !token.code.equals(code)) throw new IllegalArgumentException("确认码无效或已过期，请重新 /is reset");
        if (party.resetInProgress) throw new IllegalArgumentException("已有重开任务");
        if(runtime.sorting.transactions.busy()||runtime.sorting.transactions.recovering())throw new IllegalArgumentException("分类器事务正在保存或恢复，请稍后重开");
        if(runtime.residents.transactions.busy()||runtime.residents.transactions.recovering())throw new IllegalArgumentException("居民物资事务正在保存或恢复，请稍后重开");
        runtime.change(owner,s -> {
            var p = s.parties.get(party.id); p.resetInProgress = true; p.resetChunks = null; p.resetCursor = 0; p.overworldEpoch++; p.coins = 0; p.refresh = null; p.blockCounts.clear(); p.islandDeaths.clear(); p.starterReady = false;
            ProgressionService.reset(p);
            cn.yumis.skyisland.npc.ResidentService.reset(p);
            s.sorters.values().removeIf(device->device.party.equals(p.id)&&device.dimension.equals("minecraft:overworld"));
            s.boxes.entrySet().removeIf(e -> e.getValue().party.equals(p.id) && e.getValue().dimension.equals("minecraft:overworld"));
        }, () -> {
            for (var player : runtime.server.getPlayerList().getPlayers()) if (player.level().dimension().equals(Level.OVERWORLD) && runtime.claim(player.blockPosition()) != null && runtime.claim(player.blockPosition()).id.equals(party.id)) runtime.worlds.lobby(player);
            SkyServer.tell(owner,"主世界重开已排队；任务可在重启后继续");
        });
    }
    public void tick() {
        if (!runtime.store.available()) return;
        var party = runtime.state().parties.values().stream().filter(p -> p.resetInProgress).findFirst().orElse(null); if (party == null) return;
        if (party.resetChunks == null) {
            if (!enumerating.add(party.id)) return;
            var world = runtime.server.overworld(); var dimension = DimensionType.getStorageFolder(world.dimension(),runtime.server.getWorldPath(LevelResource.ROOT));
            var folder=dimension.resolve("region");var entityFolder=dimension.resolve("entities");
            var memory = runtime.refresh.knownChunks(); var claim = ClaimGrid.region(party.slot);
            for(var entity:world.getAllEntities())if(!(entity instanceof Player)&&claim.contains(entity.blockPosition().getX(),entity.blockPosition().getZ()))memory.add(ChunkPos.containing(entity.blockPosition()).pack());
            io.execute(() -> {
                try {
                    var indexed=new TreeSet<>(RefreshService.existing(folder,claim,memory));indexed.addAll(RefreshService.existing(entityFolder,claim,List.of()));
                    List<Long> chunks = new ArrayList<>(indexed);
                    runtime.server.execute(() -> runtime.deferred(() -> runtime.change(null,s -> s.parties.get(party.id).resetChunks = chunks, () -> enumerating.remove(party.id))));
                } catch (Exception e) { runtime.server.execute(() -> { enumerating.remove(party.id); cn.yumis.skyisland.YumisSkyIsland.LOGGER.error("重开区块索引失败，保留任务等待重试",e); }); }
            }); return;
        }
        if (party.resetCursor >= party.resetChunks.size()) {
            runtime.change(null,s -> { var p = s.parties.get(party.id); p.resetInProgress = false; p.resetChunks = null; p.resetCursor = 0; p.blockCounts.clear(); }, () -> {
                current = null; ServerPlayer owner = runtime.server.getPlayerList().getPlayer(party.owner); if (owner != null) runtime.worlds.home(owner,runtime.state().parties.get(party.id));
            }); return;
        }
        if (current == null) {
            current = new Work(); current.party = party.id; current.loading = true; Work work = current; ChunkPos pos = ChunkPos.unpack(party.resetChunks.get(party.resetCursor));
            var cache=runtime.server.overworld().getChunkSource();
            var entities=((ServerLevelEntitiesAccessor)runtime.server.overworld()).yumis$entities();
            var storage=((EntityStorageAccessor)((EntityManagerStorageAccessor)entities).yumis$storage()).yumis$region();
            work.chunk=cache.getChunkNow(pos.x(),pos.z());
            if(work.chunk!=null){work.loading=false;cache.addTicketWithRadius(WorldService.RESET_LOAD,pos,0);((EntityManagerStorageAccessor)entities).yumis$load(pos.pack());}
            else cache.chunkMap.read(pos).thenCombineAsync(storage.read(pos),(tag,entityTag)->(tag.isEmpty()||emptyTerrain(tag.get()))&&(entityTag.isEmpty()||entityTag.get().getListOrEmpty("Entities").isEmpty()),io).whenComplete((empty,error)->runtime.server.execute(()->{
                if(runtime.closing||current!=work)return;
                if(error!=null){current=null;cn.yumis.skyisland.YumisSkyIsland.LOGGER.error("重开读取失败，保留当前进度",error);return;}
                if(empty && cache.getChunkNow(pos.x(),pos.z())==null && !entities.areEntitiesLoaded(pos.pack())) {
                    // Skipping empty records changes no world data. A crash may safely repeat these reads.
                    runtime.deferred(()->{var p=runtime.state().parties.get(work.party);p.resetCursor++;current=null;if(p.resetCursor%32==0)runtime.change(null,_->{},()->{});});
                } else runtime.worlds.load(runtime.server.overworld(),new BlockPos(pos.getMinBlockX(),80,pos.getMinBlockZ()),0,()->{
                    work.chunk=cache.getChunkNow(pos.x(),pos.z());work.loading=false;
                    if(work.chunk==null)current=null;else {cache.addTicketWithRadius(WorldService.RESET_LOAD,pos,0);((EntityManagerStorageAccessor)entities).yumis$load(pos.pack());}
                },()->current=null);
            }));
            return;
        }
        Work work = current; if (work.loading || work.saving || work.chunk == null) return;
        var manager=((ServerLevelEntitiesAccessor)runtime.server.overworld()).yumis$entities();
        if(!manager.areEntitiesLoaded(work.chunk.getPos().pack()))return;
        var chunk = work.chunk; var claim = ClaimGrid.region(party.slot); int total = chunk.getHeight() * 256;
        long deadline = System.nanoTime() + 1_000_000;
        while (work.cursor < total && System.nanoTime() < deadline) {
            int section = work.cursor >> 12;
            if (chunk.getSection(section).hasOnlyAir()) { work.cursor = (section + 1) << 12; continue; }
            int index = work.cursor++, x = index & 15, z = index >> 4 & 15, y = (index >> 8) + chunk.getMinY();
            BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX()+x,y,chunk.getPos().getMinBlockZ()+z);
            if (claim.contains(pos.getX(),pos.getZ()) && !chunk.getBlockState(pos).isAir()) runtime.server.overworld().setBlock(pos,Blocks.AIR.defaultBlockState(),2|32);
        }
        if (work.cursor == total) {
            var pos = chunk.getPos();
            ((EntityManagerStorageAccessor)manager).yumis$sections().getExistingSectionsInChunk(pos.pack()).flatMap(section->section.getEntities()).filter(e -> !(e instanceof Player) && claim.contains(e.blockPosition().getX(),e.blockPosition().getZ())).toList().forEach(net.minecraft.world.entity.Entity::discard);
            work.saving=true;
            var data=net.minecraft.world.level.chunk.storage.SerializableChunkData.copyOf(runtime.server.overworld(),chunk).write();
            var storage=runtime.server.overworld().getChunkSource().chunkMap;
            var entityStorage=((EntityStorageAccessor)((EntityManagerStorageAccessor)manager).yumis$storage()).yumis$region();
            var terrainSaved=storage.write(pos,data).thenCompose(_->storage.synchronize(true));
            var entitiesSaved=entityStorage.write(pos,(CompoundTag)null).thenCompose(_->entityStorage.synchronize(true));
            CompletableFuture.allOf(terrainSaved,entitiesSaved).whenComplete((ignored,error)->runtime.server.execute(()->{
                if(error!=null){work.saving=false;cn.yumis.skyisland.YumisSkyIsland.LOGGER.error("重开区块写入失败，保留当前进度",error);return;}
                runtime.deferred(()->runtime.change(null,s -> s.parties.get(party.id).resetCursor++, () -> { runtime.server.overworld().getChunkSource().removeTicketWithRadius(WorldService.RESET_LOAD,pos,0); current = null; }));
            }));
        }
    }
    /** Empty saved terrain needs no full chunk load. Separate entity storage is checked before skipping. */
    private static boolean emptyTerrain(CompoundTag tag) {
        if(!tag.getListOrEmpty("entities").isEmpty() || !tag.getListOrEmpty("block_entities").isEmpty())return false;
        for(var section:tag.getListOrEmpty("sections").compoundStream().toList())
            for(var entry:section.getCompoundOrEmpty("block_states").getListOrEmpty("palette").compoundStream().toList())
                if(!entry.getStringOr("Name","minecraft:air").equals("minecraft:air"))return false;
        return true;
    }
    @Override public void close() { io.shutdown(); if (current != null && current.chunk != null) runtime.server.overworld().getChunkSource().removeTicketWithRadius(WorldService.RESET_LOAD,current.chunk.getPos(),0); }
}
