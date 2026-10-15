package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.data.*;
import cn.yumis.skyisland.shop.*;
import cn.yumis.skyisland.world.*;
import cn.yumis.skyisland.network.ShopPayloads;
import com.google.gson.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;

public final class SkyServer implements AutoCloseable {
    private static SkyServer instance;
    public static SkyServer get(MinecraftServer server) { return instance != null && instance.server == server ? instance : null; }
    public static void start(MinecraftServer server, GameConfig config) throws IOException {
        if (!VoidWorlds.isSky(server.overworld())) { YumisSkyIsland.LOGGER.info("普通存档：未启用空岛领地，请新建 yumis_sky_island:sky_island 世界"); return; }
        instance = new SkyServer(server, config); instance.worlds.initializeLobby();
    }
    public final MinecraftServer server;
    public final GameConfig config;
    public final DurableStore store;
    public final PartyService parties;
    public final WorldService worlds;
    public final ItemEconomy economy;
    public final TradeService trades;
    public final RefreshService refresh;
    public final ProfessionService professions;
    public final ResetService resets;
    public final ProgressionService progression;
    public final QuestEditorService questEditor;
    private final Queue<Runnable> deferred = new ArrayDeque<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Map<UUID, String> lastSnapshots = new HashMap<>();
    private final Map<UUID, Queue<String>> catalogQueue = new HashMap<>();
    private final Set<UUID> catalogSent = new HashSet<>();
    private final Map<UUID,Long> priceEpochSent = new HashMap<>();
    public final Set<UUID> inventoryLocks = new HashSet<>();
    public int tick;
    public boolean closing;
    private long lastWorldTime;
    private SkyServer(MinecraftServer server, GameConfig config) throws IOException {
        this.server = server; this.config = config;
        store = new DurableStore(server.getWorldPath(LevelResource.ROOT).resolve("yumis_sky_island"), server::execute);
        state().schemaVersion=3;
        state().worldSeed = server.overworld().getSeed();
        lastWorldTime=server.overworld().getGameTime();
        if(state().lastMarketDay<0)state().lastMarketDay=marketDay();
        economy = new ItemEconomy(config, server.registryAccess(), state().worldSeed);
        economy.epoch(epoch());
        parties = new PartyService(this); worlds = new WorldService(this); trades = new TradeService(this);
        refresh = new RefreshService(this); professions = new ProfessionService(this);
        resets = new ResetService(this);
        progression = new ProgressionService(this);
        questEditor = new QuestEditorService(this);
        for (var p : state().parties.values()) {
            if (p.cachedPoints == null) { p.cachedPoints = calculatePoints(p).toString(); if(p.starterReady)p.initialScanEpoch=p.overworldEpoch; }
            p.highestIslandLevel=BlockScorer.level(points(p));
            if(!p.resetInProgress)progression.initialize(p);
        }
        economy.epoch(epoch());
        for (ServerLevel level : server.getAllLevels()) if (VoidWorlds.isSky(level) && level.dimension().equals(Level.END)) level.setDragonFight(null);
        if (economy.entries.size() != config.products.size()) YumisSkyIsland.LOGGER.warn("商品真实注册校验：{} / {}；无法创建的商品不会上架", economy.entries.size(), config.products.size());
    }
    public IslandState state() { return store.state(); }
    public long epoch() { return state().marketRound; }
    private long marketDay(){
        long anchor=config.object("shop/shops.toml").getAsJsonObject("market").get("refreshAtDayTick").getAsLong();
        return MarketClock.day(server.overworld().getOverworldClockTime(),anchor);
    }
    public static void tell(ServerPlayer player, String message) { player.sendSystemMessage(Component.literal("[空岛] " + message)); }
    public static void tell(ServerPlayer player, Component message) { player.sendSystemMessage(Component.literal("[空岛] ").append(message)); }
    public void change(ServerPlayer actor, Consumer<IslandState> edit, Runnable after) {
        if (closing) return;
        if (!store.available()) { if (actor != null) tell(actor, store.failure() == null ? "数据正在保存，请稍后重试" : "数据写入失败，交易已停止，请联系管理员"); return; }
        Map<UUID,JsonObject> changes=new LinkedHashMap<>();
        store.commit(next->{
            Map<UUID,Long> before=new HashMap<>();next.parties.forEach((id,p)->before.put(id,p.coins));edit.accept(next);
            next.parties.forEach((id,p)->{long old=before.getOrDefault(id,p.coins);if(p.coins!=old){JsonObject data=new JsonObject();data.addProperty("partyId",id.toString());data.addProperty("sequence",next.sequence+1);data.addProperty("amount",Math.subtractExact(p.coins,old));changes.put(id,data);}});
        }, () -> {
            changes.forEach((id,data)->partyEvent(id,"coin_change",data));
            drain();after.run();syncAll();
        }, e -> { YumisSkyIsland.LOGGER.error("空岛持久化失败", e); if (actor != null) tell(actor, "保存失败，操作未开放继续执行"); });
    }
    public void deferred(Runnable task) { if (store.available()) task.run(); else deferred.add(task); }
    public void partyEvent(UUID partyId,String kind,JsonObject data){
        var party=state().parties.get(partyId);if(party==null)return;
        for(var online:server.getPlayerList().getPlayers()){
            var person=state().players.get(online.getUUID());
            if(party.members.contains(online.getUUID())&&person!=null&&partyId.equals(person.activeParty))send(online,kind,data);
        }
    }
    public void playerDied(ServerPlayer player){
        if(player.level()!=server.overworld()||!VoidWorlds.isSky(server.overworld()))return;
        IslandState.Party location=claim(player.blockPosition());
        if(location==null||location.resetInProgress)return;
        JsonObject death=new JsonObject();
        change(null,s->{
            IslandState.Party party=s.parties.get(location.id);
            long count=Math.addExact(party.islandDeaths.getOrDefault(player.getUUID(),0L),1);
            JsonObject rules=config.object("economy.toml").getAsJsonObject("death");
            long cost=DeathRules.cost(count-1,rules.get("firstCost").getAsLong(),rules.get("additionalCostPerDeath").getAsLong());
            party.islandDeaths.put(player.getUUID(),count);
            party.coins=Math.subtractExact(party.coins,cost);
            death.addProperty("actor",player.getGameProfile().name());death.addProperty("cost",cost);death.addProperty("partyId",party.id.toString());
        },()->partyEvent(location.id,"death_notice",death));
    }
    private void drain() { while (store.available() && !deferred.isEmpty()) deferred.remove().run(); }
    public IslandState.Party claim(BlockPos pos) {
        var slot = ClaimGrid.slotAt(pos.getX(), pos.getZ());
        if (slot.isEmpty()) return null;
        return state().parties.values().stream().filter(p -> p.slot == slot.getAsLong()).findFirst().orElse(null);
    }
    public BigInteger points(IslandState.Party party) {
        return party.cachedPoints == null ? calculatePoints(party) : new BigInteger(party.cachedPoints);
    }
    private BigInteger calculatePoints(IslandState.Party party) {
        BigInteger result = BigInteger.ZERO;
        for (var e : party.blockCounts.entrySet()) {
            GameConfig.BlockValue b = config.blocks.get(e.getKey());
            if (b != null) result = result.add(BlockScorer.scaledPoints(Math.max(0, e.getValue()), b.points(), config.scoreTiers));
        }
        return result;
    }
    public int level(IslandState.Party party) { return party.highestIslandLevel; }
    public void updateScore(IslandState.Party party) {
        party.cachedPoints=calculatePoints(party).toString();
        party.highestIslandLevel=BlockScorer.level(points(party));
    }
    public void blockChanged(ServerLevel world, BlockPos pos, BlockState old, BlockState next) {
        if (!world.dimension().equals(Level.OVERWORLD) || old.equals(next)) return;
        IslandState.Party claim = claim(pos); if (claim == null || claim.resetInProgress) return;
        if (!next.isAir()) refresh.recordPlacement(claim, pos);
        String before = BuiltInRegistries.BLOCK.getKey(old.getBlock()).toString(), after = BuiltInRegistries.BLOCK.getKey(next.getBlock()).toString();
        GameConfig.BlockValue beforeValue = config.blocks.get(before), afterValue = config.blocks.get(after);
        GameConfig.BlockValue a = beforeValue != null && beforeValue.points() > 0 ? beforeValue : null, b = afterValue != null && afterValue.points() > 0 ? afterValue : null;
        if(a==null&&b==null)return;
        if (a != null && b != null && a.group().equals(b.group())) return;
        UUID id = claim.id;
        long islandEpoch=claim.overworldEpoch;
        if (a != null) refresh.changed(claim, a.group(), -1, pos);
        if (b != null) refresh.changed(claim, b.group(), 1, pos);
        deferred(() -> { IslandState.Party p = state().parties.get(id); if (p == null || p.resetInProgress || p.overworldEpoch!=islandEpoch) return; int previous=p.highestIslandLevel; adjust(p, a, -1); adjust(p, b, 1); updateScore(p); if(p.highestIslandLevel!=previous){refresh.checkpointDeltas();change(null,_->{},()->{});} });
    }
    private void adjust(IslandState.Party p, GameConfig.BlockValue b, long amount) {
        if (b == null) return;
        p.blockCounts.compute(b.group(), (_, n) -> Math.max(0, (n == null ? 0 : n) + amount));
    }
    public void tick() {
        tick++;long worldTime=server.overworld().getGameTime();if(worldTime>lastWorldTime)deferred(()->state().effectiveTicks++);lastWorldTime=worldTime;
        long today=marketDay();
        if(today>state().lastMarketDay && store.available())change(null,s->{s.lastMarketDay=today;s.marketRound++;},()->{
            for(ServerPlayer online:server.getPlayerList().getPlayers())tell(online,"早上了，每日刷新了。");
        });
        drain(); economy.epoch(epoch());
        trades.tick(); refresh.tick(); resets.tick(); worlds.tick(); progression.tick(); Protection.clear();
        if (store.available()) {
            for (IslandState.Party party : state().parties.values()) {
                if (party.marketEpoch != epoch()) { trades.roll(party); break; }
            }
        }
        if (tick % 200 == 0 && store.available()) { refresh.checkpointDeltas(); change(null, _ -> {}, () -> {}); }
        if (tick % 20 == 0) syncAll();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Queue<String> pages = catalogQueue.get(player.getUUID());
            if (pages != null && ServerPlayNetworking.canSend(player, ShopPayloads.Snapshot.TYPE)) {
                for (int i = 0; i < 2 && !pages.isEmpty(); i++) ServerPlayNetworking.send(player, new ShopPayloads.Snapshot(pages.remove()));
                if (pages.isEmpty()) { catalogQueue.remove(player.getUUID()); catalogSent.add(player.getUUID()); }
            }
        }
    }
    public void joined(ServerPlayer player) {
        trades.recover(player);
        if (parties.active(player.getUUID()) == null) worlds.lobby(player);
        sendCatalog(player); sendState(player);
        questEditor.sendEditor(player);
    }
    public void disconnected(ServerPlayer player) { trades.forget(player); questEditor.forget(player); catalogQueue.remove(player.getUUID()); catalogSent.remove(player.getUUID()); lastSnapshots.remove(player.getUUID()); priceEpochSent.remove(player.getUUID()); lastRequest.remove(player.getUUID()); }
    public void request(ServerPlayer player, ShopPayloads.Request request) {
        long now = state().effectiveTicks;
        if (now - lastRequest.getOrDefault(player.getUUID(), -100L) < 4 && !Set.of("sale_preview","sale_confirm","sale_cancel","quest_claim","bounty_claim","bounty_slot").contains(request.action())) return;
        lastRequest.put(player.getUUID(), now);
        try {
            switch (request.action()) {
                case "open" -> { if(player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty())player.closeContainer();sendCatalog(player); sendState(player); send(player, "open", new JsonObject()); }
                case "buy" -> throw new IllegalArgumentException("请使用带商店和轮次校验的新版购买请求");
                case "sell_hand" -> trades.sell(player, true, false);
                case "sell_inventory" -> trades.sell(player, false, false);
                case "sale_cancel" -> trades.forget(player);
                case "sale_preview" -> trades.preview(player,request.product(),false);
                case "sale_confirm" -> trades.confirmSale(player,request.product());
                case "quest_claim" -> throw new IllegalArgumentException("请使用带任务修订号的新版领取请求");
                case "bounty_claim" -> progression.claimBounty(player,progressionProduct(player,request));
                case "bounty_slot" -> progression.buySlot(player,request.quantity(),Long.parseLong(progressionProduct(player,request)));
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) { tell(player, e.getMessage()); JsonObject error=new JsonObject();error.addProperty("action",request.action());error.addProperty("message",e.getMessage());send(player,"request_error",error); if(request.action().equals("sale_preview")&&store.available())try{trades.preview(player,"",false);}catch(IllegalArgumentException|IllegalStateException ignored){} }
    }
    public void purchase(ServerPlayer player, ShopPayloads.Purchase purchase) {
        try {
            trades.buy(player,purchase.product(),purchase.quantity(),purchase.shop(),purchase.marketEpoch(),purchase.party(),purchase.islandEpoch());
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            tell(player,e.getMessage());
            JsonObject error = new JsonObject(); error.addProperty("action","buy"); error.addProperty("message",e.getMessage());
            send(player,"request_error",error); sendState(player);
        }
    }
    private String progressionProduct(ServerPlayer player,ShopPayloads.Request request){
        var p=parties.requireMember(player);int split=request.product().indexOf('|');
        if(split<0||!request.product().substring(0,split).equals(p.id.toString()))throw new IllegalArgumentException("队伍已变化，请重新确认");
        return request.product().substring(split+1);
    }
    public void send(ServerPlayer p, String kind, JsonElement data) {
        if (!ServerPlayNetworking.canSend(p, ShopPayloads.Snapshot.TYPE)) return;
        JsonObject root = new JsonObject(); root.addProperty("kind", kind); root.add("data", data);
        ServerPlayNetworking.send(p, new ShopPayloads.Snapshot(root.toString()));
    }
    public void openShop(ServerPlayer player) { if(player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty())player.closeContainer();sendState(player); send(player, "open", new JsonObject()); }
    public void sendCatalog(ServerPlayer player) {
        if (catalogQueue.containsKey(player.getUUID()) || catalogSent.contains(player.getUUID())) return;
        Queue<String> pages = new ArrayDeque<>(); JsonArray entries = new JsonArray(); int page = 0;
        for (ItemEconomy.Entry e : economy.entries.values()) {
            var p = e.product(); JsonObject row = new JsonObject(); row.addProperty("id", p.id()); row.addProperty("name", p.name()); row.add("stack", economy.encode(e.template())); row.add("canonical",p.canonical());
            row.addProperty("base", p.baseCoins());row.addProperty("buyBase",p.buyBaseCoins()); row.addProperty("buyMultiplierBps",p.buyMultiplierBps()); row.addProperty("rarity", p.rarity()); row.addProperty("level", p.level()); row.addProperty("buy", p.canBuy()); row.addProperty("sell", p.canSell()); row.addProperty("category", p.category());
            row.addProperty("firstAcquisitionPremium",economy.starter(e));
            var unit = new GameConfig.QuotaUnit(p.canonical().get("itemId").getAsString(), Rational.ONE);
            row.addProperty("quotaGroup", unit.group()); row.addProperty("quotaUnits", unit.units().toString()); entries.add(row);
            if (entries.size() == 64) { pages.add(catalogPage(entries, page++)); entries = new JsonArray(); }
        }
        if (!entries.isEmpty()) pages.add(catalogPage(entries, page));
        JsonObject rules = new JsonObject(); rules.add("scoring", config.object("islands/scoring.toml")); rules.add("enchantments", config.object("shop/enchantments.toml")); rules.add("quality", config.object("products/quality.toml")); rules.add("quotas",config.object("shop/sale_quotas.toml").get("tiers"));
        rules.addProperty("saleDivisor",config.saleDivisor);rules.addProperty("minimumBaseCoins",config.minimumBaseCoins);
        rules.addProperty("genshinMode",config.object("economy.toml").get("genshinMode").getAsBoolean());
        rules.addProperty("regularBuyMultiplierBps",config.regularBuyMultiplierBps);
        rules.add("itemRarities",config.object("shop/rarities.toml").get("itemRarities"));
        JsonObject packet = new JsonObject(); packet.addProperty("kind", "rules"); packet.add("data", rules); pages.add(packet.toString()); catalogQueue.put(player.getUUID(), pages);
    }
    private String catalogPage(JsonArray rows, int page) {
        JsonObject packet = new JsonObject(); packet.addProperty("kind", "catalog"); packet.addProperty("page", page); packet.add("data", rows); return packet.toString();
    }
    public void syncAll() { for (ServerPlayer p : server.getPlayerList().getPlayers()) sendState(p); }
    public void sendState(ServerPlayer player) {
        IslandState.Party party = parties.active(player.getUUID());
        JsonObject json = new JsonObject(); long period=24000,anchor=config.object("shop/shops.toml").getAsJsonObject("market").get("refreshAtDayTick").getAsLong();long left=Math.floorMod(anchor-server.overworld().getOverworldClockTime(),period);json.addProperty("epoch", epoch()); json.addProperty("ticksLeft", left==0?period:left);json.addProperty("effectiveTicks",state().effectiveTicks);
        json.addProperty("wallet", party == null ? 0 : party.coins); json.addProperty("level", party == null ? 0 : level(party)); json.addProperty("party", party == null ? "尚未加入空岛" : party.name);
        int islandLevel=party==null?0:level(party);
        BigInteger scaled=party==null?BigInteger.ZERO:points(party);
        json.addProperty("currentLevelScoreScaled",scaled.subtract(BlockScorer.threshold(islandLevel).multiply(BigInteger.valueOf(10000))).toString());
        json.addProperty("nextLevelScoreScaled",BlockScorer.required(islandLevel).multiply(BigInteger.valueOf(10000)).toString());
        json.addProperty("islandEpoch",party==null?0:party.overworldEpoch);
        json.addProperty("partyId",party==null?"":party.id.toString());
        json.addProperty("admin",Protection.admin(player));
        json.addProperty("taskEditing",questEditor.editing(player));
        json.addProperty("hidden", party != null && party.hidden); json.add("stock", GameConfig.JSON.toJsonTree(party == null ? Map.of() : party.dailyStock));
        json.add("quotas", GameConfig.JSON.toJsonTree(party == null ? Map.of() : party.quota)); json.add("counts", GameConfig.JSON.toJsonTree(party == null ? Map.of() : party.blockCounts));
        json.add("acquiredStarters",GameConfig.JSON.toJsonTree(party == null ? Set.of() : party.acquiredStarterIds));
        if(party!=null)json.add("progression",progression.snapshot(party,state().players.get(player.getUUID())));
        else if(Protection.admin(player))json.add("progression",progression.preview());
        if (!Objects.equals(priceEpochSent.get(player.getUUID()),epoch())) {
            JsonObject market = new JsonObject(); for (var e : economy.entries.keySet()) market.addProperty(e, economy.marketBps(e)); send(player,"prices",market); priceEpochSent.put(player.getUUID(),epoch());
        }
        IslandState.Person person = state().players.get(player.getUUID()); json.add("professions", GameConfig.JSON.toJsonTree(person == null ? Map.of() : person.professionXp));
        String serialized = json.toString(); if (serialized.equals(lastSnapshots.put(player.getUUID(), serialized))) return;
        send(player, "state", json);
    }
    @Override public void close() throws IOException { closing = true; store.finishPending(); drain(); refresh.checkpointDeltas(); resets.close(); refresh.close(); worlds.close(); store.close(); instance = null; }
}
