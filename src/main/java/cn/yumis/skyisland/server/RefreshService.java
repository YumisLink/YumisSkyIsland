package cn.yumis.skyisland.server;

import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.core.ClaimGrid;
import cn.yumis.skyisland.data.IslandState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Loaded palettes are counted under a tick budget; unloaded data uses the native async storage reader, without generation. */
public final class RefreshService implements AutoCloseable {
    private final SkyServer runtime;
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("yumis-refresh").factory());
    private final Set<Long> known = new LinkedHashSet<>();
    private final Map<Long,Set<Long>> knownBySlot=new HashMap<>();
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private final Set<UUID> enumerating = new HashSet<>();
    private final Set<UUID> paused = new HashSet<>();
    private int roundRobin, inflight;
    private final class Job {
        final UUID id;
        final Set<Long> scanned = new HashSet<>();
        final Map<String, Long> delta = new HashMap<>();
        final Set<Long> chunkKeys=new HashSet<>();
        final Queue<Long> newChunks=new ArrayDeque<>();
        Work work;
        long started=System.nanoTime();
        int startCursor;
        boolean notified;
        net.minecraft.server.level.ServerPlayer requester;
        Job(IslandState.Party p) { id = p.id; scanned.addAll(p.refresh.scanned); delta.putAll(p.refresh.delta);chunkKeys.addAll(p.refresh.chunks);for(long key:knownBySlot.getOrDefault(p.slot,Set.of()))addChunk(key); }
        void addChunk(long key){if(chunkKeys.add(key))newChunks.add(key);}
    }
    private static final class Work {
        long key; int section; boolean loading, touched, completed;
        LevelChunk live;
        List<Map<String, Long>> disk;
        Map<String, Long> counts = new HashMap<>(), changes = new HashMap<>();
    }
    public RefreshService(SkyServer runtime) { this.runtime = runtime; for(var p:runtime.state().parties.values())if(p.refresh!=null)paused.add(p.id); }
    public void loaded(LevelChunk chunk) { long key=chunk.getPos().pack();known.add(key);var slot=ClaimGrid.slotAt(chunk.getPos().getMinBlockX(),chunk.getPos().getMinBlockZ());if(slot.isPresent())knownBySlot.computeIfAbsent(slot.getAsLong(),_->new HashSet<>()).add(key);var party=runtime.claim(chunk.getPos().getWorldPosition());if(party!=null&&jobs.containsKey(party.id))jobs.get(party.id).addChunk(key); }
    public List<Long> knownChunks() { return new ArrayList<>(known); }
    public boolean isRunning(UUID partyId) {
        var party = runtime.state().parties.get(partyId);
        return enumerating.contains(partyId) || jobs.containsKey(partyId) || party != null && party.refresh != null;
    }
    public boolean isPaused(UUID partyId){return paused.contains(partyId);}
    public void changed(IslandState.Party party, String group, long amount, BlockPos pos) {
        if (group == null || party.refresh == null) return;
        long changedKey=ChunkPos.pack(pos.getX()>>4,pos.getZ()>>4);
        if(paused.contains(party.id)){
            UUID id=party.id,scan=party.refresh.jobId;long epoch=party.overworldEpoch;
            runtime.deferred(()->{var current=runtime.state().parties.get(id);if(current==null||current.overworldEpoch!=epoch||current.refresh==null||!current.refresh.jobId.equals(scan)||!current.refresh.scanned.contains(changedKey))return;var active=jobs.get(id);if(active!=null&&!paused.contains(id))active.delta.merge(group,amount,Long::sum);else current.refresh.delta.merge(group,amount,Long::sum);});
            return;
        }
        Job job = jobs.computeIfAbsent(party.id, _ -> new Job(party)); long key = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
        if (job.scanned.contains(key)) job.delta.merge(group, amount, Long::sum);
        else if (job.work != null && job.work.key == key) {
            Work work = job.work;
            if (work.loading) work.touched = true;
            if (!work.loading && work.live == null && work.disk != null || work.live != null && ((pos.getY() - work.live.getMinY()) >> 4) < work.section) work.changes.merge(group, amount, Long::sum);
        }
    }
    public void request(net.minecraft.server.level.ServerPlayer player) {
        IslandState.Party party = runtime.parties.requireMember(player);
        if(party.refresh!=null&&paused.remove(party.id)){var job=new Job(party);job.startCursor=party.refresh.cursor;job.requester=player;jobs.put(party.id,job);notifyStart(job);return;}
        start(party,player,false);
    }
    private void start(IslandState.Party party,net.minecraft.server.level.ServerPlayer player,boolean initial) {
        if (party.resetInProgress) throw new IllegalStateException("重开期间不能刷新");
        initial=initial||party.initialScanEpoch!=party.overworldEpoch;
        final boolean firstScan=initial;
        if (party.refresh != null || !enumerating.add(party.id)) { if(player!=null)SkyServer.tell(player, "刷新任务已在进行，输入 /is level 查看进度"); return; }
        if(player!=null)SkyServer.tell(player,"正在开始加载，请等候；正在读取区块索引和扫描速度");
        List<Long> inMemory = new ArrayList<>(known); ClaimGrid.Region region = ClaimGrid.region(party.slot);
        long epoch = party.overworldEpoch;
        Path folder = DimensionType.getStorageFolder(runtime.server.overworld().dimension(), runtime.server.getWorldPath(LevelResource.ROOT)).resolve("region");
        io.execute(() -> {
            try {
                List<Long> chunks = existing(folder, region, inMemory);
                runtime.server.execute(() -> runtime.deferred(() -> {
                    var current = runtime.state().parties.get(party.id);
                    if (runtime.closing || current == null || current.resetInProgress || current.overworldEpoch != epoch) { enumerating.remove(party.id); return; }
                    runtime.change(player, s -> { var p = s.parties.get(party.id); p.refresh = new IslandState.Refresh(); p.refresh.chunks = chunks; p.refresh.initial=firstScan; }, () -> { enumerating.remove(party.id);paused.remove(party.id);var job=new Job(runtime.state().parties.get(party.id));job.requester=player;jobs.put(party.id,job);notifyStart(job); });
                }));
            } catch (IOException e) { runtime.server.execute(() -> { enumerating.remove(party.id); if(player!=null)SkyServer.tell(player, "读取区块索引失败，旧等级保留"); }); }
        });
    }
    /** Read-only location headers. Boundary chunks are included and cropped during counting. */
    public static List<Long> existing(Path folder, ClaimGrid.Region claim, Collection<Long> inMemory) throws IOException {
        Set<Long> keys = new TreeSet<>();
        for (long key : inMemory) if (intersects(ChunkPos.unpack(key), claim)) keys.add(key);
        if (Files.isDirectory(folder)) try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "r.*.*.mca")) {
            for (Path file : files) {
                String[] parts = file.getFileName().toString().split("\\."); if (parts.length != 4) continue;
                int rx, rz; try { rx = Integer.parseInt(parts[1]); rz = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { continue; }
                long x = (long)rx * 512, z = (long)rz * 512;
                if (x >= claim.maxX() || x + 512 <= claim.minX() || z >= claim.maxZ() || z + 512 <= claim.minZ()) continue;
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                    if (channel.size() < 8192) continue;
                    ByteBuffer buffer = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN);
                    while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
                    if (buffer.hasRemaining()) continue; buffer.flip();
                    for (int i = 0; i < 1024; i++) {
                        int location = buffer.getInt(), sector = location >>> 8, count = location & 255;
                        ChunkPos pos = new ChunkPos(rx * 32 + (i & 31), rz * 32 + (i >> 5));
                        if (sector >= 2 && count > 0 && (long)sector * 4096 + 5 <= channel.size() && intersects(pos, claim)) keys.add(pos.pack());
                    }
                }
            }
        }
        return new ArrayList<>(keys);
    }
    private static boolean intersects(ChunkPos chunk, ClaimGrid.Region region) { return chunk.getMinBlockX() < region.maxX() && chunk.getMinBlockX() + 16 > region.minX() && chunk.getMinBlockZ() < region.maxZ() && chunk.getMinBlockZ() + 16 > region.minZ(); }
    public void tick() {
        if (!runtime.store.available()) return;
        for (var p : runtime.state().parties.values()) if(p.starterReady&&!p.resetInProgress&&p.refresh==null&&p.initialScanEpoch!=p.overworldEpoch&&!enumerating.contains(p.id)){start(p,null,true);break;}
        for (var p : runtime.state().parties.values()) if (p.refresh != null && !p.resetInProgress && !paused.contains(p.id)) jobs.computeIfAbsent(p.id, _ -> new Job(p));
        jobs.entrySet().removeIf(e->{boolean done=runtime.state().parties.get(e.getKey())==null||runtime.state().parties.get(e.getKey()).refresh==null;if(done)release(e.getValue().work);return done;});
        if (jobs.isEmpty()) return;
        JsonSettings settings = new JsonSettings(runtime.config);
        boolean slow=runtime.server.getAverageTickTimeNanos()>40_000_000L;
        long deadline = System.nanoTime() + settings.micros * (slow?500L:1000L); int sections = 0, requests = 0;
        List<Job> queue = new ArrayList<>(jobs.values());
        for (int attempts = 0; attempts < queue.size() && System.nanoTime() < deadline && sections < settings.sections && runtime.store.available(); attempts++) {
            Job job = queue.get(Math.floorMod(roundRobin++, queue.size())); IslandState.Party party = runtime.state().parties.get(job.id);
            if (party.refresh == null || party.resetInProgress) continue;
            while(!job.newChunks.isEmpty()&&System.nanoTime()<deadline)party.refresh.chunks.add(job.newChunks.remove());
            if (party.refresh.cursor >= party.refresh.chunks.size()) { finish(job); continue; }
            if (job.work == null) {
                // Sustained load reduces reads to one opportunity per second instead of starving the job forever.
                if (slow && runtime.tick % 20 != 0 || requests >= settings.requests || inflight >= settings.concurrent) continue;
                job.work = new Work(); job.work.key = party.refresh.chunks.get(party.refresh.cursor);
                ChunkPos chunk = ChunkPos.unpack(job.work.key); job.work.live = runtime.server.overworld().getChunkSource().getChunkNow(chunk.x(), chunk.z());
                if (job.work.live == null) { requests++; read(job, ClaimGrid.region(party.slot)); continue; }
                runtime.server.overworld().getChunkSource().addTicketWithRadius(WorldService.REFRESH_LOAD,chunk,0);
            }
            Work w = job.work; if (w.loading || w.completed) continue;
            int total = w.live == null ? w.disk.size() : w.live.getSections().length;
            while (w.section < total && sections < settings.sections && System.nanoTime() < deadline) {
                if (w.live != null && w.live.getSection(w.section).hasOnlyAir() || w.live == null && w.disk.get(w.section).isEmpty()) { w.section++; continue; }
                if (w.live != null) countLive(w, ClaimGrid.region(party.slot));
                else w.disk.get(w.section).forEach((k, v) -> w.counts.merge(k, v, Long::sum));
                w.section++; sections++;
            }
            if (w.section == total) checkpoint(job);
        }
    }
    private void notifyStart(Job job) {
        if(job.notified||job.requester==null)return;
        var p=runtime.state().parties.get(job.id);
        int completed=p.refresh.cursor-job.startCursor;
        double speed=completed>0?completed/Math.max(0.001,(System.nanoTime()-job.started)/1_000_000_000.0):p.scanChunksPerSecond;
        if(speed<=0&&p.refresh.chunks.size()>p.refresh.cursor)return;
        long seconds=speed<=0?0:(long)Math.ceil((p.refresh.chunks.size()-p.refresh.cursor)/speed);
        SkyServer.tell(job.requester,"正在开始加载，请等候，预计等待 "+seconds/60+"分"+seconds%60+"秒（"+p.refresh.chunks.size()+"个区块，按扫描速度估算）");job.notified=true;
    }
    private record JsonSettings(long micros, int sections, int requests, int concurrent) {
        JsonSettings(GameConfig c) { this(c.object("islands/refresh.json").get("globalComputeBudgetMicrosPerTick").getAsLong(), c.object("islands/refresh.json").get("maxSectionsPerTick").getAsInt(), c.object("islands/refresh.json").get("maxNewChunkRequestsPerTick").getAsInt(), c.object("islands/refresh.json").get("maxConcurrentChunkRequests").getAsInt()); }
    }
    private void read(Job job, ClaimGrid.Region region) {
        Work w = job.work; w.loading = true; w.touched = false; inflight++;
        runtime.server.overworld().getChunkSource().chunkMap.read(ChunkPos.unpack(w.key)).thenApplyAsync(tag -> tag.map(t -> diskCounts(t, ChunkPos.unpack(w.key), region)).orElse(List.of()), io).whenComplete((counts, error) -> runtime.server.execute(() -> {
            inflight--; if (job.work != w || jobs.get(job.id) != job || runtime.closing) return;
            if (error != null) { jobs.remove(job.id); runtime.deferred(() -> runtime.change(null, s -> s.parties.get(job.id).refresh = null, () -> {})); return; }
            w.live = runtime.server.overworld().getChunkSource().getChunkNow(ChunkPos.getX(w.key), ChunkPos.getZ(w.key));
            if(w.live!=null)runtime.server.overworld().getChunkSource().addTicketWithRadius(WorldService.REFRESH_LOAD,w.live.getPos(),0);
            if (w.live == null && w.touched) { w.loading = false; read(job, region); return; }
            w.disk = counts; w.loading = false;
        }));
    }
    private void countLive(Work work, ClaimGrid.Region region) {
        LevelChunkSection section = work.live.getSection(work.section); ChunkPos pos = work.live.getPos();
        if (section.hasOnlyAir()) return;
        boolean full = region.contains(pos.getMinBlockX(), pos.getMinBlockZ()) && region.contains(pos.getMinBlockX() + 15, pos.getMinBlockZ() + 15);
        if (full) section.getStates().count((state, count) -> add(work.counts, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), count));
        else for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) if (region.contains(pos.getMinBlockX() + x, pos.getMinBlockZ() + z)) for (int y = 0; y < 16; y++) add(work.counts, BuiltInRegistries.BLOCK.getKey(section.getBlockState(x,y,z).getBlock()).toString(), 1);
    }
    private void add(Map<String, Long> counts, String block, long amount) { var b = runtime.config.blocks.get(block); if (b != null && b.points() > 0) counts.merge(b.group(), amount, Long::sum); }
    private List<Map<String, Long>> diskCounts(CompoundTag tag, ChunkPos pos, ClaimGrid.Region region) {
        List<Map<String, Long>> sections = new ArrayList<>();
        for (CompoundTag section : tag.getListOrEmpty("sections").compoundStream().toList()) {
            Map<String, Long> counts = new HashMap<>(); CompoundTag states = section.getCompoundOrEmpty("block_states"); ListTag palette = states.getListOrEmpty("palette");
            int size = palette.size(); if (size == 0) { sections.add(counts); continue; }
            String[] names = new String[size]; for (int i = 0; i < size; i++) names[i] = palette.getCompoundOrEmpty(i).getStringOr("Name", "minecraft:air");
            if(Arrays.stream(names).noneMatch(name->{var b=runtime.config.blocks.get(name);return b!=null&&b.points()>0;})){sections.add(counts);continue;}
            if(size==1 && region.contains(pos.getMinBlockX(),pos.getMinBlockZ()) && region.contains(pos.getMinBlockX()+15,pos.getMinBlockZ()+15)){add(counts,names[0],4096);sections.add(counts);continue;}
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(size - 1)), perLong = 64 / bits; long mask = (1L << bits) - 1;
            long[] data = states.getLongArray("data").orElse(new long[0]);
            for (int i = 0; i < 4096; i++) {
                int x = i & 15, z = i >> 4 & 15; if (!region.contains(pos.getMinBlockX() + x, pos.getMinBlockZ() + z)) continue;
                int id = size == 1 ? 0 : (int)((data[i / perLong] >>> ((i % perLong) * bits)) & mask);
                if (id >= size) throw new IllegalArgumentException("Corrupt block palette"); add(counts, names[id], 1);
            }
            sections.add(counts);
        }
        return sections;
    }
    private void checkpoint(Job job) {
        Work w = job.work; w.completed = true; job.scanned.add(w.key);
        w.changes.forEach((k,v) -> w.counts.merge(k,v,Long::sum));
        runtime.change(null, s -> {
            var refresh = s.parties.get(job.id).refresh; w.counts.forEach((k,v) -> refresh.counts.merge(k,v,Long::sum));
            refresh.cursor++; refresh.scanned.add(w.key); refresh.delta = new LinkedHashMap<>(job.delta);
        }, () -> {release(job.work);job.work = null;notifyStart(job);});
    }
    private void finish(Job job) {
        notifyStart(job);
        runtime.change(null, s -> {
            var p = s.parties.get(job.id); Map<String,Long> counts = new LinkedHashMap<>(p.refresh.counts);
            job.delta.forEach((k,v) -> counts.merge(k,v,Long::sum)); counts.replaceAll((_,v) -> Math.max(0,v)); p.blockCounts = counts;
            if(p.refresh.initial)p.initialScanEpoch=p.overworldEpoch;
            p.scanChunksPerSecond=Math.max(0.001,(p.refresh.cursor-job.startCursor)/Math.max(0.001,(System.nanoTime()-job.started)/1_000_000_000.0));
            p.refresh = null;runtime.updateScore(p);
        }, () -> { jobs.remove(job.id);paused.remove(job.id); for (var player : runtime.server.getPlayerList().getPlayers()) if (runtime.parties.active(player.getUUID()) != null && runtime.parties.active(player.getUUID()).id.equals(job.id)) SkyServer.tell(player, "测试结束，刷新完成，空岛 " + runtime.level(runtime.state().parties.get(job.id)) + " 级"); });
    }
    public void checkpointDeltas() { jobs.forEach((id,job) -> { var p = runtime.state().parties.get(id); if (p != null && p.refresh != null) p.refresh.delta = new LinkedHashMap<>(job.delta); }); }
    private void release(Work work){if(work!=null&&work.live!=null)runtime.server.overworld().getChunkSource().removeTicketWithRadius(WorldService.REFRESH_LOAD,work.live.getPos(),0);}
    @Override public void close() { io.shutdown();jobs.values().forEach(j->release(j.work)); }
}
