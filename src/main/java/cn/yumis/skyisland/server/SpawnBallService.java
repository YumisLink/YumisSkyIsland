package cn.yumis.skyisland.server;

import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.shop.TradeService;
import cn.yumis.skyisland.world.VoidWorlds;
import com.google.gson.JsonArray;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;

/** Persistent chunk policies, queried only by the vanilla natural-spawn path. No tick or chunk tickets. */
public final class SpawnBallService {
    public record Kind(String id, String name, boolean water) {
        public String itemId() { return "yumis_sky_island:spawn_ball_" + id; }
        public EntityType<?> type() { return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(id)); }
    }
    public static final List<Kind> KINDS = List.of(
        new Kind("zombie", "僵尸", false), new Kind("skeleton", "骷髅", false),
        new Kind("spider", "蜘蛛", false), new Kind("creeper", "爬行者", false),
        new Kind("cave_spider", "洞穴蜘蛛", false), new Kind("slime", "史莱姆", false),
        new Kind("drowned", "溺尸", true), new Kind("magma_cube", "岩浆怪", false),
        new Kind("zombified_piglin", "僵尸猪灵", false), new Kind("enderman", "末影人", false),
        new Kind("blaze", "烈焰人", false), new Kind("witch", "女巫", false),
        new Kind("guardian", "守护者", true), new Kind("wither_skeleton", "凋零骷髅", false));
    private record Policy(UUID party, Kind kind, SpawnerData data) {}
    private final SkyServer runtime;
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Policy>> index = new HashMap<>();
    private final Map<Kind, SpawnerData> spawners = new HashMap<>();
    private final Set<SpawnerData> overrides = Collections.newSetFromMap(new IdentityHashMap<>());

    public SpawnBallService(SkyServer runtime) {
        this.runtime = runtime;
        for (Kind kind : KINDS) spawners.put(kind, new SpawnerData(kind.type(), 1, 4));
        overrides.addAll(spawners.values());
        rebuild();
    }
    public boolean enabled() { return runtime.config.object("gameplay/spawn_balls.toml").get("enabled").getAsBoolean(); }
    public static String key(ServerLevel level, BlockPos pos) { return level.dimension().identifier() + "|" + ChunkPos.containing(pos).pack(); }

    /** Startup and successful policy mutations only. A commit failure never publishes an index. */
    public void rebuild() {
        index.clear();
        for (ServerLevel level : runtime.server.getAllLevels()) {
            if (!VoidWorlds.isSky(level)) continue;
            var chunks = new Long2ObjectOpenHashMap<Policy>();
            String prefix = level.dimension().identifier() + "|";
            for (var party : runtime.state().parties.values()) for (var entry : party.spawnOverrides.entrySet()) {
                if (!entry.getKey().startsWith(prefix)) continue;
                Kind kind = KINDS.stream().filter(k -> k.id().equals(entry.getValue())).findFirst().orElse(null);
                if (kind == null) continue;
                try { chunks.put(Long.parseLong(entry.getKey().substring(prefix.length())), new Policy(party.id, kind, spawners.get(kind))); }
                catch (NumberFormatException ignored) { /* Invalid saved keys never load or affect world chunks. */ }
            }
            index.put(level.dimension(), chunks);
        }
    }
    private Policy policy(ServerLevel level, BlockPos pos) {
        if (!enabled()) return null;
        var chunks = index.get(level.dimension());
        if (chunks == null) return null;
        Policy found = chunks.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        if (found == null) return null;
        var party = runtime.state().parties.get(found.party());
        return party == null || party.resetInProgress && level.dimension().equals(Level.OVERWORLD) ? null : found;
    }
    public static SpawnBallService get(ServerLevel level) {
        var runtime = SkyServer.get(level.getServer());
        return runtime == null ? null : runtime.spawnBalls;
    }
    public SpawnerData target(ServerLevel level, BlockPos pos) {
        Policy policy = policy(level, pos);
        return policy == null ? null : policy.data();
    }
    public boolean owns(SpawnerData data) { return overrides.contains(data); }
    public boolean matches(ServerLevel level, Mob mob) {
        var data = target(level, mob.blockPosition());
        return data != null && data.type() == mob.getType();
    }
    /** Keep physical habitat and dimension-specific hostile darkness, bypass only species gates. */
    public boolean positionAllowed(ServerLevel level, BlockPos pos, EntityType<?> type, double distanceSquared) {
        Policy policy = policy(level, pos);
        if (policy == null || policy.data().type() != type || level.getDifficulty() == Difficulty.PEACEFUL
                || !type.canSummon() || !type.isEnabled(level.enabledFeatures())
                || (!type.canSpawnFarFromPlayer() && distanceSquared > (double)type.getCategory().getDespawnDistance() * type.getCategory().getDespawnDistance())) return false;
        // Every examined block must already be loaded; collision queries must not request neighbours.
        var box = type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (!loaded(level, box) || !habitat(level, pos, policy.kind(), type) || !level.noCollision(box)) return false;
        return Monster.isDarkEnoughToSpawn(level, pos, level.getRandom());
    }
    private static boolean habitat(ServerLevel level, BlockPos pos, Kind kind, EntityType<?> type) {
        if (kind.water()) return level.getFluidState(pos).is(FluidTags.WATER)
            && level.getFluidState(pos.above()).is(FluidTags.WATER);
        return level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type)
            && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty();
    }
    public boolean spaceAllowed(ServerLevel level, Mob mob) {
        Policy policy = policy(level, mob.blockPosition());
        return policy != null && policy.data().type() == mob.getType() && loaded(level, mob.getBoundingBox())
            && level.noCollision(mob) && level.isUnobstructed(mob)
            && (policy.kind().water() || !level.containsAnyLiquid(mob.getBoundingBox()));
    }
    private static boolean loaded(ServerLevel level, net.minecraft.world.phys.AABB box) {
        int minX = net.minecraft.util.Mth.floor(box.minX) >> 4, maxX = net.minecraft.util.Mth.floor(box.maxX) >> 4;
        int minZ = net.minecraft.util.Mth.floor(box.minZ) >> 4, maxZ = net.minecraft.util.Mth.floor(box.maxZ) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private IslandState.Party requireLocation(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !VoidWorlds.isSky(level)) throw new IllegalArgumentException("刷怪球仅限本队空岛维度使用");
        var party = runtime.parties.requireMember(player);
        if (!party.members.contains(player.getUUID())) throw new IllegalArgumentException("只有本队正式成员可以管理刷怪球");
        var claim = runtime.claim(player.blockPosition());
        if (claim == null || !claim.id.equals(party.id)) throw new IllegalArgumentException("只能管理本队领地区块的刷怪球");
        if (party.resetInProgress) throw new IllegalStateException("重开期间暂停刷怪球操作");
        return party;
    }
    public void info(ServerPlayer player) {
        var party = requireLocation(player);
        String id = party.spawnOverrides.get(key((ServerLevel)player.level(), player.blockPosition()));
        var kind = KINDS.stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
        SkyServer.tell(player, "当前区块 " + ChunkPos.containing(player.blockPosition()) + "：" + (kind == null ? "原版自然刷怪" : kind.name() + "刷怪球" + (enabled() ? "（永久生效）" : "（配置已暂停）")));
    }
    public void clear(ServerPlayer player) {
        var party = requireLocation(player);
        runtime.trades.requireInventoryReady(player);
        String key = key((ServerLevel)player.level(), player.blockPosition());
        if (!party.spawnOverrides.containsKey(key)) { SkyServer.tell(player, "当前区块没有刷怪球效果"); return; }
        long epoch = party.overworldEpoch;
        if (!runtime.tryChange(player, SyncImpact.none(), state -> {
            requireContext(state, player.getUUID(), party.id, epoch).spawnOverrides.remove(key);
        }, () -> { rebuild(); SkyServer.tell(player, "当前区块已恢复原版自然刷怪，不返还刷怪球"); }, error -> {}))
            throw new IllegalStateException("数据正在保存，请稍后重试");
    }
    public void use(ServerPlayer player, InteractionHand hand, Kind kind) {
        if (!enabled()) throw new IllegalStateException("刷怪球功能已关闭，已有效果保留并暂停");
        var party = requireLocation(player);
        runtime.trades.requireInventoryReady(player);
        String key = key((ServerLevel)player.level(), player.blockPosition());
        if (kind.id().equals(party.spawnOverrides.get(key))) { SkyServer.tell(player, "当前区块已是" + kind.name() + "，未消耗刷怪球"); return; }
        int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot();
        Item item = ModItems.SPAWN_BALLS.get(kind.id());
        ItemStack held = player.getInventory().getItem(slot);
        if (!held.is(item) || held.isEmpty()) throw new IllegalArgumentException("手持刷怪球已变化");
        JsonArray inventory = runtime.economy.capture(player.getInventory());
        ItemStack remaining = held.copy(); remaining.shrink(1);
        inventory.set(slot, runtime.economy.encode(remaining));
        long epoch = party.overworldEpoch;
        if (!runtime.trades.commitInventory(player, SyncImpact.none(), new TradeService.InventoryChange(inventory, TradeService.ExperienceChange.none(), null),
            state -> requireContext(state, player.getUUID(), party.id, epoch).spawnOverrides.put(key, kind.id()),
            this::rebuild, current -> SkyServer.tell(current, "当前16×16区块已永久改为" + kind.name() + "自然刷怪；/is spawnball info 查看，/is spawnball clear 清除")))
            throw new IllegalStateException("数据正在保存，请稍后重试");
    }
    private static IslandState.Party requireContext(IslandState state, UUID player, UUID partyId, long epoch) {
        var person = state.players.get(player); var party = state.parties.get(partyId);
        if (person == null || !partyId.equals(person.activeParty) || party == null || !party.members.contains(player)
                || party.overworldEpoch != epoch || party.resetInProgress) throw new IllegalStateException("队伍或岛屿已变化，请重新操作");
        return party;
    }
}
