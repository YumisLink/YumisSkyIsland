package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.data.*;
import cn.yumis.skyisland.shop.*;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.world.*;
import cn.yumis.skyisland.network.ShopPayloads;
import com.google.gson.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.effect.MobEffectInstance;
import cn.yumis.skyisland.effect.ModEffects;
import net.minecraft.world.phys.Vec3;
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
    public final cn.yumis.skyisland.npc.ResidentService residents;
    public final RefreshService refresh;
    public final ProfessionService professions;
    public final ProfessionShopService professionShop;
    public final AccessoryService accessories;
    public final LotteryService lottery;
    public final BloodMoonService bloodMoon;
    public final VeinMiningService veinMining;
    public final cn.yumis.skyisland.prefab.PrefabService prefabs;
    public final cn.yumis.skyisland.sorting.SortingService sorting;
    public final ResetService resets;
    public final ProgressionService progression;
    public final QuestEditorService questEditor;
    public final AdminEditSession editors;
    public final ConfigEditorService configEditor;
    public long rulesRevision, priceRevision, professionShopRevision, lotteryRulesRevision, scoringRevision;
    private final Queue<Runnable> deferred = new ArrayDeque<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Map<UUID, String> lastSnapshots = new HashMap<>();
    private final Map<UUID, Queue<String>> catalogQueue = new HashMap<>();
    private final Set<UUID> catalogSent = new HashSet<>();
    private final Map<UUID,Long> priceEpochSent = new HashMap<>();
    private final Map<UUID,Float> lastChefHealth = new HashMap<>();
    private final Map<UUID,Integer> lastChefDamageTick = new HashMap<>();
    public final Set<UUID> inventoryLocks = new HashSet<>();
    public int tick;
    public boolean closing;
    private long lastWorldTime;
    private SkyServer(MinecraftServer server, GameConfig config) throws IOException {
        this.server = server; this.config = config;config.validateScoringRegistry();
        store = new DurableStore(server.getWorldPath(LevelResource.ROOT).resolve("yumis_sky_island"), server::execute);
        state().schemaVersion=3;
        state().worldSeed = server.overworld().getSeed();
        lastWorldTime=server.overworld().getGameTime();
        if(state().lastMarketDay<0)state().lastMarketDay=marketDay();
        residents = new cn.yumis.skyisland.npc.ResidentService(this);
        prefabs = new cn.yumis.skyisland.prefab.PrefabService(this);
        economy = new ItemEconomy(config, server.registryAccess(), state().worldSeed);
        economy.residentModifiers(residents::saleBps,residents::buyBps);
        economy.epoch(epoch());
        parties = new PartyService(this); worlds = new WorldService(this); trades = new TradeService(this);
        refresh = new RefreshService(this); professions = new ProfessionService(this); professionShop=new ProfessionShopService(this); accessories=new AccessoryService(this);bloodMoon=new BloodMoonService(this);veinMining=new VeinMiningService(this);
        sorting = new cn.yumis.skyisland.sorting.SortingService(this);
        resets = new ResetService(this);
        progression = new ProgressionService(this);
        lottery = new LotteryService(this);
        editors = new AdminEditSession(this);questEditor = new QuestEditorService(this);configEditor=new ConfigEditorService(this);
        if(!state().parties.isEmpty())change(null,s->{for(var p:s.parties.values()){
            updateScore(s,p);
            if(!p.resetInProgress)progression.initialize(p);
        }},()->{});
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
        tryChange(actor, edit, after, error -> {});
    }
    public boolean tryChange(ServerPlayer actor, Consumer<IslandState> edit, Runnable after, Consumer<Throwable> failed) {
        if (closing) return false;
        if (!store.available()) { if (actor != null) tell(actor, store.failure() == null ? "数据正在保存，请稍后重试" : "数据写入失败，交易已停止，请联系管理员"); return false; }
        Map<UUID,JsonObject> changes=new LinkedHashMap<>();
        store.commit(next->{
            Map<UUID,Long> before=new HashMap<>();next.parties.forEach((id,p)->before.put(id,p.coins));edit.accept(next);
            next.parties.forEach((id,p)->{long old=before.getOrDefault(id,p.coins);if(p.coins!=old){JsonObject data=new JsonObject();data.addProperty("partyId",id.toString());data.addProperty("sequence",next.sequence+1);data.addProperty("amount",Math.subtractExact(p.coins,old));changes.put(id,data);}});
        }, () -> {
            changes.forEach((id,data)->partyEvent(id,"coin_change",data));
            drain();after.run();syncAll();
        }, e -> { YumisSkyIsland.LOGGER.error("空岛持久化失败", e); if (actor != null && server.getPlayerList().getPlayer(actor.getUUID()) == actor) tell(actor, "保存失败，操作未开放继续执行"); failed.accept(e); });
        return true;
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
        if(bloodMoon.active()){
            String text=player.getGameProfile().name()+"在血月期间死亡，不扣除"+Money.currencyName()+"，之后的死亡费用也不提高。";
            for(var online:server.getPlayerList().getPlayers()){
                var person=state().players.get(online.getUUID());
                if(location.members.contains(online.getUUID())&&person!=null&&location.id.equals(person.activeParty))tell(online,text);
            }
            return;
        }
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
            GameConfig.BlockValue b = config.blockValue(e.getKey());
            if (b != null) result = result.add(BlockScorer.scaledPoints(Math.max(0, e.getValue()), b.points(), config.scoreTiers));
        }
        return result;
    }
    public int level(IslandState.Party party) { return party.highestIslandLevel; }
    public void updateScore(IslandState state, IslandState.Party party) {
        int before=party.highestIslandLevel;
        party.cachedPoints=calculatePoints(party).toString();
        party.highestIslandLevel=BlockScorer.level(points(party));
        AccessoryService.awardMilestones(state,party,before,party.highestIslandLevel);
        PersonalTokens.levels(state,party,before,party.highestIslandLevel,lottery.levelReward());
    }
    public void blockChanged(ServerLevel world, BlockPos pos, BlockState old, BlockState next) {
        sorting.changed(world,pos,old,next);
        if (!world.dimension().equals(Level.OVERWORLD) || old.equals(next)) return;
        residents.changed(world,pos,old,next);
        IslandState.Party claim = claim(pos); if (claim == null || claim.resetInProgress) return;
        if (!next.isAir()) refresh.recordPlacement(claim, pos);
        String before = BuiltInRegistries.BLOCK.getKey(old.getBlock()).toString(), after = BuiltInRegistries.BLOCK.getKey(next.getBlock()).toString();
        GameConfig.BlockValue beforeValue = config.blockValue(before), afterValue = config.blockValue(after);
        GameConfig.BlockValue a = beforeValue != null && beforeValue.points() > 0 ? beforeValue : null, b = afterValue != null && afterValue.points() > 0 ? afterValue : null;
        if(a==null&&b==null)return;
        if (a != null && b != null && a.group().equals(b.group())) return;
        UUID id = claim.id;
        long islandEpoch=claim.overworldEpoch;
        boolean playerPlacement=b!=null&&Protection.playerPlacement(world,pos,after);
        long scoringRevision=this.scoringRevision;
        if (a != null) refresh.changed(claim, a.group(), -1, pos);
        if (b != null) refresh.changed(claim, b.group(), 1, pos);
        deferred(() -> {
            IslandState.Party p=state().parties.get(id);if(p==null||p.resetInProgress||p.overworldEpoch!=islandEpoch)return;
            long prior=b==null?0:p.blockCounts.getOrDefault(b.group(),0L);adjust(p,a,-1);adjust(p,b,1);
            p.cachedPoints=calculatePoints(p).toString();long count=p.blockCounts.getOrDefault(after,0L);
            Runnable notice=()->{var current=state().parties.get(id);if(playerPlacement&&scoringRevision==this.scoringRevision&&current!=null&&current.overworldEpoch==islandEpoch)notifyScoreTier(current,after,prior,count);};
            if(BlockScorer.level(points(p))!=p.highestIslandLevel){
                refresh.checkpointDeltas();change(null,s->updateScore(s,s.parties.get(id)),notice);
            }else notice.run();
        });
    }
    private void notifyScoreTier(IslandState.Party party,String block,long before,long after){
        var rules=config.scoring;long base=rules.base(block);if(!rules.notices()||base<=5)return;
        for(int index=0;index<rules.tiers().size()-1;index++){
            var tier=rules.tiers().get(index);long threshold=tier.upToCount();if(before>=threshold||after<threshold)continue;
            int next=rules.tiers().get(index+1).multiplierBps();
            var registered=BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(block));
            String template=rules.message().replace("{threshold}",Long.toString(threshold)).replace("{nextPoints}",Long.toString(BlockScorer.perBlock(base,next))).replace("{nextMultiplier}",java.math.BigDecimal.valueOf(next,2).stripTrailingZeros().toPlainString());
            var message=Component.empty();String[] fragments=template.split(java.util.regex.Pattern.quote("{block}"),-1);for(int part=0;part<fragments.length;part++){if(part>0)message.append(registered.getName());message.append(fragments[part]);}
            for(var online:server.getPlayerList().getPlayers()){var person=state().players.get(online.getUUID());if(party.members.contains(online.getUUID())&&person!=null&&party.id.equals(person.activeParty))tell(online,message);}
        }
    }
    public void configurationChanged(String file,Runnable acknowledged){
        rulesRevision++;
        if(file.equals("products/profession_shop.toml"))professionShopRevision++;
        if(file.equals(LotteryService.FILE)){lotteryRulesRevision++;lottery.reload();}
        if(file.equals("economy.toml"))Money.currencyName(config.object(file).get("genshinMode").getAsBoolean()?"摩拉":"金币");
        Runnable published=()->{invalidateCatalogs();syncAll();acknowledged.run();};
        if(file.equals("islands/scoring.toml")){
            scoringRevision++;
            config.publishScoring(cn.yumis.skyisland.config.ScoringSettings.parse(config.object(file)));
            change(null,s->{for(var party:s.parties.values())updateScore(s,party);},published);
        }else published.run();
    }
    public void invalidateCatalogs(){catalogQueue.clear();catalogSent.clear();priceEpochSent.clear();for(var online:server.getPlayerList().getPlayers())sendCatalog(online);}
    private void adjust(IslandState.Party p, GameConfig.BlockValue b, long amount) {
        if (b == null) return;
        p.blockCounts.compute(b.group(), (_, n) -> Math.max(0, (n == null ? 0 : n) + amount));
    }
    public void tick() {
        tick++;long worldTime=server.overworld().getGameTime();if(worldTime>lastWorldTime)deferred(()->state().effectiveTicks++);lastWorldTime=worldTime;
        long today=marketDay();
        if(today>state().lastMarketDay && store.available())change(null,s->{s.lastMarketDay=today;s.marketRound++;residents.captureOnline(s,s.marketRound);},()->{
            for(ServerPlayer online:server.getPlayerList().getPlayers())tell(online,"早上了，每日刷新了。");
        });
        drain(); economy.epoch(epoch());
        residents.tick(); trades.tick(); refresh.tick(); resets.tick(); worlds.tick(); progression.tick(); lottery.tick(); bloodMoon.tick(); veinMining.tick(); prefabs.tick(); sorting.tick(); Protection.clear();
        if (store.available()) {
            for (IslandState.Party party : state().parties.values()) {
                if (party.marketEpoch != epoch()) { trades.roll(party); break; }
            }
        }
        if (tick % 200 == 0 && store.available()) { refresh.checkpointDeltas(); change(null, AccessoryService::flushStudyRemainder, () -> {}); }
        if (tick % 20 == 0) syncAll();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModItems.refreshFlight(player, state().players.get(player.getUUID()));
            if (tick % 100 == 0) refreshAttributes(player);
            AccessoryService.tick(player);
            if (tick % 20 == 0) {
                UUID chefId=player.getUUID();
                float health=player.getHealth();
                lastChefDamageTick.putIfAbsent(chefId,tick);
                if (health < lastChefHealth.getOrDefault(chefId, health)) lastChefDamageTick.put(chefId,tick);
                IslandState.Person chef=state().players.get(chefId);
                int fruits=chef==null?0:Math.clamp(chef.consumedFruits.getOrDefault("chef",0),0,100);
                if (fruits>0 && tick-lastChefDamageTick.getOrDefault(chefId,tick)>=200 && health>0 && health<player.getMaxHealth())
                    player.heal((float)(fruits*0.01));
                lastChefHealth.put(chefId,player.getHealth());
            }
            if (player.isAlive() && player.getY() < -100 && VoidWorlds.isSky(player.level())) {
                if (!player.level().dimension().equals(Level.OVERWORLD)) {
                    worlds.rescueFromVoid(player);
                    continue;
                }
                player.stopRiding();
                player.teleportTo(player.level(), player.getX(), 325, player.getZ(), Set.of(), player.getYRot(), player.getXRot(), true);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                player.addEffect(new MobEffectInstance(ModEffects.CLOUD_STEP, 300, 0, false, false, true));
            }
            Queue<String> pages = catalogQueue.get(player.getUUID());
            if (pages != null && ServerPlayNetworking.canSend(player, ShopPayloads.Snapshot.TYPE)) {
                for (int i = 0; i < 2 && !pages.isEmpty(); i++) ServerPlayNetworking.send(player, new ShopPayloads.Snapshot(pages.remove()));
                if (pages.isEmpty()) { catalogQueue.remove(player.getUUID()); catalogSent.add(player.getUUID()); }
            }
        }
    }
    public void joined(ServerPlayer player) {
        trades.recover(player);
        ModItems.refreshFlight(player, state().players.get(player.getUUID()));
        refreshAttributes(player);
        lastChefHealth.put(player.getUUID(),player.getHealth());lastChefDamageTick.put(player.getUUID(),tick);
        if (parties.active(player.getUUID()) == null) worlds.lobby(player);
        sendCatalog(player); sendState(player);
        questEditor.sendEditor(player);
    }
    public static void openEnderChest(ServerPlayer player) {
        if (player.isSpectator() || player.containerMenu != player.inventoryMenu) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, user) -> ChestMenu.threeRows(id, inventory, player.getEnderChestInventory()), Component.translatable("container.enderchest")));
    }
    private void refreshAttributes(ServerPlayer player) {
        IslandState.Person person = state().players.get(player.getUUID());
        ModItems.applyFruitBonuses(player, person);
        AccessoryService.apply(player, person);
        FoodHealth.correctInflated(this, player);
        FoodHealth.apply(player, person);
        ProfessionAttributes.apply(this, player, person);
    }
    public void disconnected(ServerPlayer player) { sorting.disconnected(player.getUUID()); worlds.disconnected(player.getUUID()); ModItems.disconnected(player.getUUID()); veinMining.disconnect(player); lastChefHealth.remove(player.getUUID());lastChefDamageTick.remove(player.getUUID()); trades.forget(player); questEditor.forget(player); catalogQueue.remove(player.getUUID()); catalogSent.remove(player.getUUID()); lastSnapshots.remove(player.getUUID()); priceEpochSent.remove(player.getUUID()); lastRequest.remove(player.getUUID()); }
    public void request(ServerPlayer player, ShopPayloads.Request request) {
        long now = state().effectiveTicks;
        if (now - lastRequest.getOrDefault(player.getUUID(), -100L) < 4 && !Set.of("sale_preview","sale_confirm","sale_cancel","quest_claim","bounty_claim","bounty_slot","vein").contains(request.action())) return;
        lastRequest.put(player.getUUID(), now);
        try {
            switch (request.action()) {
                case "open" -> { if(player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty())player.closeContainer();sendCatalog(player); sendState(player); send(player, "open", new JsonObject()); }
                case "profession_open" -> { if(player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty())player.closeContainer();sendState(player);send(player,"profession_open",new JsonObject()); }
                case "accessory_open" -> { if(player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty())player.closeContainer();sendState(player);send(player,"accessory_open",new JsonObject()); }
                case "accessory_claim" -> accessories.claim(player);
                case "accessory_toggle" -> accessories.toggle(player,request.product());
                case "accessory_craft" -> accessories.craft(player,request.product());
                case "vein" -> veinMining.setHeld(player,request.quantity()!=0);
                case "buy" -> throw new IllegalArgumentException("请使用带商店和轮次校验的新版购买请求");
                case "sell_hand" -> trades.sell(player, true, false);
                case "sell_inventory" -> trades.sell(player, false, false);
                case "sale_cancel" -> trades.forget(player);
                case "sale_preview" -> trades.preview(player,request.product(),false);
                case "sale_confirm" -> trades.confirmSale(player,request.product());
                case "quest_claim" -> throw new IllegalArgumentException("请使用带任务修订号的新版领取请求");
                case "bounty_claim" -> progression.claimBounty(player,progressionProduct(player,request));
                case "bounty_slot" -> progression.buySlot(player,request.quantity(),Long.parseLong(progressionProduct(player,request)));
                case "profession_buy" -> throw new IllegalArgumentException("职业商店规则已更新，请使用带版本的购买请求");
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) { tell(player, e.getMessage()); JsonObject error=new JsonObject();error.addProperty("action",request.action());error.addProperty("message",e.getMessage());send(player,"request_error",error); if(request.action().equals("sale_preview")&&store.available())try{trades.preview(player,"",false);}catch(IllegalArgumentException|IllegalStateException ignored){} }
    }
    public void purchase(ServerPlayer player, ShopPayloads.Purchase purchase) {
        try {
            if(purchase.priceRevision()!=priceRevision)throw new IllegalArgumentException("商品价格已更新，请等待资料同步后重新确认");
            var party=parties.requireMember(player);if(purchase.npcRevision()!=residents.effectVersion(party))throw new IllegalArgumentException("居民价格增益已变化，请重新确认购买");
            trades.buy(player,purchase.product(),purchase.quantity(),purchase.shop(),purchase.marketEpoch(),purchase.party(),purchase.islandEpoch());
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            tell(player,e.getMessage());
            JsonObject error = new JsonObject(); error.addProperty("action","buy"); error.addProperty("message",e.getMessage());
            send(player,"request_error",error); sendState(player);
        }
    }
    public void purchaseProfession(ServerPlayer player,ShopPayloads.ProfessionPurchase request){
        try{if(request.rulesRevision()!=professionShopRevision)throw new IllegalArgumentException("职业商店价格已更新，请重新确认");professionShop.buy(player,request.product(),request.quantity());}
        catch(IllegalArgumentException|IllegalStateException|ArithmeticException error){tell(player,error.getMessage());JsonObject data=new JsonObject();data.addProperty("action","profession_buy");data.addProperty("message",error.getMessage());send(player,"request_error",data);sendState(player);}
    }
    public void operateLottery(ServerPlayer player,cn.yumis.skyisland.network.LotteryPayloads.Operation request) {
        try { lottery.operate(player,request); }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            tell(player, e.getMessage());
            JsonObject error = new JsonObject(); error.addProperty("action", "lottery_operation"); error.addProperty("message", e.getMessage());
            send(player, "request_error", error); sendState(player);
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
            row.addProperty("base", p.baseCoins());row.addProperty("buyBase",p.buyBaseCoins()); row.addProperty("buyMultiplierBps",p.buyMultiplierBps()); row.addProperty("rarity", p.rarity()); row.addProperty("level", p.level()); row.addProperty("buy", economy.canBuy(e)); row.addProperty("sell", p.canSell()); row.addProperty("category", p.id().startsWith("yumis_sky_island:biome_ball_")?"biome_ball":p.category());
            row.addProperty("firstAcquisitionPremium",economy.starter(e));
            var unit = new GameConfig.QuotaUnit(p.canonical().get("itemId").getAsString(), Rational.ONE);
            row.addProperty("quotaGroup", unit.group()); row.addProperty("quotaUnits", unit.units().toString()); entries.add(row);
            if (entries.size() == 64) { pages.add(catalogPage(entries, page++)); entries = new JsonArray(); }
        }
        if (!entries.isEmpty()||pages.isEmpty()) pages.add(catalogPage(entries, page));
        JsonObject rules = new JsonObject(); rules.add("scoring", config.object("islands/scoring.toml")); rules.add("enchantments", config.object("shop/enchantments.toml")); rules.add("quality", config.object("products/quality.toml")); rules.add("quotas",config.object("shop/sale_quotas.toml").get("tiers"));
        rules.addProperty("saleDivisor",config.saleDivisor);rules.addProperty("minimumBaseCoins",config.minimumBaseCoins);
        rules.addProperty("genshinMode",config.object("economy.toml").get("genshinMode").getAsBoolean());
        rules.addProperty("regularBuyMultiplierBps",config.regularBuyMultiplierBps);
        rules.addProperty("prefabAmmunitionPrice",prefabs.rules.ammunitionPrice());
        rules.addProperty("prefabAmmunitionCredits",prefabs.rules.ammunitionCredits());
        JsonArray creativeBlueprints=new JsonArray();for(String id:prefabs.catalog.registeredBlueprints())creativeBlueprints.add(id);
        rules.add("creativeBlueprints",creativeBlueprints);
        rules.add("itemRarities",config.object("shop/rarities.toml").get("itemRarities"));
        rules.addProperty("priceRevision",priceRevision);
        JsonObject packet = new JsonObject(); packet.addProperty("kind", "rules");packet.addProperty("revision",rulesRevision); packet.add("data", rules); pages.add(packet.toString()); catalogQueue.put(player.getUUID(), pages);
    }
    private String catalogPage(JsonArray rows, int page) {
        JsonObject packet = new JsonObject(); packet.addProperty("kind", "catalog");packet.addProperty("revision",rulesRevision); packet.addProperty("page", page); packet.add("data", rows); return packet.toString();
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
        json.add("residents",residents.snapshot(player,party));
        json.addProperty("admin",Protection.admin(player));
        json.addProperty("taskEditing",questEditor.editing(player));
        json.addProperty("adminEditing",editors.enabled(player));json.addProperty("professionShopRevision",professionShopRevision);
        json.addProperty("hidden", party != null && party.hidden); json.add("stock", GameConfig.JSON.toJsonTree(party == null ? Map.of() : party.dailyStock));
        json.add("quotas", GameConfig.JSON.toJsonTree(party == null ? Map.of() : party.quota)); json.add("counts", GameConfig.JSON.toJsonTree(party == null ? Map.of() : party.blockCounts));
        json.add("acquiredStarters",GameConfig.JSON.toJsonTree(party == null ? Set.of() : party.acquiredStarterIds));
        if(party!=null)json.add("progression",progression.snapshot(party,state().players.get(player.getUUID())));
        else if(Protection.admin(player))json.add("progression",progression.preview());
        if (!Objects.equals(priceEpochSent.get(player.getUUID()),epoch())) {
            JsonObject market = new JsonObject(); for (var e : economy.entries.keySet()) market.addProperty(e, economy.marketBps(e)); send(player,"prices",market); priceEpochSent.put(player.getUUID(),epoch());
        }
        IslandState.Person person = state().players.get(player.getUUID()); json.add("professions", GameConfig.JSON.toJsonTree(person == null ? Map.of() : person.professionXp));
        json.add("professionCoins", GameConfig.JSON.toJsonTree(person == null ? Map.of() : person.professionCoins));
        json.add("consumedFruits",GameConfig.JSON.toJsonTree(person==null?Map.of():person.consumedFruits));
        json.add("foodEaten",GameConfig.JSON.toJsonTree(person==null||person.foodEaten==null?Map.of():person.foodEaten));
        json.add("foodHealth",GameConfig.JSON.toJsonTree(person==null||person.foodHealth==null?Map.of():person.foodHealth));
        json.add("unlockedAccessories",GameConfig.JSON.toJsonTree(person==null?Set.of():person.unlockedAccessories));
        json.add("activeAccessories",GameConfig.JSON.toJsonTree(person==null?Set.of():person.activeAccessories));
        json.addProperty("pendingAccessoryDraws",person==null?0:person.pendingAccessoryDraws);
        json.add("lottery",lottery.snapshot(player));
        json.addProperty("accessorySlots",person==null?3:Math.clamp(person.accessorySlots,3,AccessoryService.CATALOG.size()));
        JsonObject professionProgress=new JsonObject();
        for(String profession:List.of("blacksmith","chef","farmer","hunter","miner","battle_master","fisherman")){
            long[] xpCurve=ProfessionService.curve(config.object("products/professions.toml"),profession);
            long xpBase=xpCurve[0], xpGrowth=xpCurve[1], xpLater=xpCurve[2];
            int professionLevel=professions.level(person,profession);
            BigInteger total=person==null?BigInteger.ZERO:person.professionXp.getOrDefault(profession,BigInteger.ZERO);
            JsonObject row=new JsonObject();row.addProperty("level",professionLevel);
            row.addProperty("xp",total.subtract(ProfessionRules.threshold(professionLevel,xpBase,xpGrowth,xpLater)).toString());
            row.addProperty("next",ProfessionRules.required(professionLevel,xpBase,xpGrowth,xpLater).toString());
            professionProgress.add(profession,row);
        }
        json.add("professionProgress",professionProgress);
        json.add("fishingPower",FishingService.snapshot(this,player));
        json.addProperty("fishingAccessoryBoxBps",BloodAccessories.chance(this,"accessoryBoxBps",2000));
        json.addProperty("fishingTreasureEggBps",config.object("gameplay/blood_moon.toml").get("treasureEggBps").getAsInt());
        JsonObject guide=new JsonObject();
        JsonObject professionConfig=config.object("products/professions.toml");
        JsonObject craftXp=professionConfig.getAsJsonObject("successfulCraftXp");
        JsonObject goods=config.object("products/goods.toml").getAsJsonObject("items");
        for(var entry:goods.entrySet()){
            JsonObject good=entry.getValue().getAsJsonObject();String profession=good.get("profession").getAsString();
            if(!QuestCatalog.PROFESSIONS.contains(profession))continue;
            JsonArray rows=guide.has(profession)?guide.getAsJsonArray(profession):new JsonArray();
            JsonObject row=new JsonObject();row.addProperty("id",entry.getKey());
            row.addProperty("source",profession.equals("blacksmith")&&entry.getKey().startsWith("minecraft:netherite_")?"锻造台领取":"手动制作领取");
            row.addProperty("itemLevel",good.get("itemLevel").getAsInt());
            row.addProperty("baseXp",good.has("baseXp")?good.get("baseXp").getAsLong():craftXp.get(profession).getAsLong());
            rows.add(row);guide.add(profession,rows);
        }
        JsonObject sources=professionConfig.getAsJsonObject("sourceItems");
        if(sources!=null)for(var entry:sources.entrySet()){
            JsonObject source=entry.getValue().getAsJsonObject();String profession=source.get("profession").getAsString();
            if(!profession.equals("farmer")&&!profession.equals("fisherman"))continue;
            JsonArray rows=guide.has(profession)?guide.getAsJsonArray(profession):new JsonArray();
            boolean noHarvestXp=profession.equals("farmer")&&Set.of("minecraft:pumpkin","minecraft:sugar_cane","minecraft:melon_slice").contains(entry.getKey());
            JsonObject row=new JsonObject();row.addProperty("id",entry.getKey());row.addProperty("source",profession.equals("fisherman")?"真实成功获钓":noHarvestXp?"收获不计经验（保留品质）":"本人成熟收获掉落");
            row.addProperty("itemLevel",source.get("itemLevel").getAsInt());
            row.addProperty("baseXp",noHarvestXp?0:profession.equals("fisherman")?professionConfig.get("successfulFishingXp").getAsLong():craftXp.get(profession).getAsLong());
            rows.add(row);guide.add(profession,rows);
        }
        JsonObject hunterMobs=professionConfig.getAsJsonObject("hunterMobs");
        if(hunterMobs!=null){JsonArray rows=guide.has("hunter")?guide.getAsJsonArray("hunter"):new JsonArray();
            for(var entry:hunterMobs.entrySet()){
                JsonObject row=new JsonObject();row.addProperty("id",entry.getKey());row.addProperty("source","本人有效击杀");
                row.addProperty("itemLevel",entry.getValue().getAsJsonObject().get("itemLevel").getAsInt());
                row.addProperty("baseXp",professionConfig.get("successfulHunterKillXp").getAsLong());rows.add(row);
            }guide.add("hunter",rows);
        }
        JsonArray fishingGuide=guide.has("fisherman")?guide.getAsJsonArray("fisherman"):new JsonArray();
        JsonObject fishingRow=new JsonObject();fishingRow.addProperty("id","yumis_sky_island:biome_treasure_box");fishingRow.addProperty("source","真实钓获（固定经验）");
        fishingRow.addProperty("itemLevel",0);fishingRow.addProperty("baseXp",12);fishingGuide.add(fishingRow);guide.add("fisherman",fishingGuide);
        JsonArray minerGuide=new JsonArray();
        for(String[] mined:new String[][]{{"minecraft:iron_pickaxe","稿子挖掘任意方块"},{"yumis_sky_island:iron_hammer","锤子挖掘任意方块"}}){
            JsonObject row=new JsonObject();row.addProperty("id",mined[0]);row.addProperty("source",mined[1]);row.addProperty("itemLevel",0);row.addProperty("baseXp",1);minerGuide.add(row);
        }
        guide.add("miner",minerGuide);
        JsonArray battleGuide=new JsonArray();
        JsonObject battleRow=new JsonObject();battleRow.addProperty("id","minecraft:iron_sword");battleRow.addProperty("source","对玩家或敌对生物造成或受到的最终伤害");
        battleRow.addProperty("itemLevel",0);battleRow.addProperty("baseXp",1);battleGuide.add(battleRow);guide.add("battle_master",battleGuide);
        json.add("professionGuide",guide);
        json.add("professionShop",config.object("products/profession_shop.toml"));
        json.addProperty("bloodMoon",state().bloodMoonActive);
        json.addProperty("flightRemainingMs", person == null ? 0 : Math.max(0L, person.flightExpiryEpochMs - System.currentTimeMillis()));
        String serialized = json.toString(); if (serialized.equals(lastSnapshots.put(player.getUUID(), serialized))) return;
        send(player, "state", json);
    }
    @Override public void close() throws IOException {
        sorting.close();residents.close();prefabs.close();
        // Finish accepted resident deaths, unloads and forced operation callbacks before closing mutations.
        do {store.finishPending();if(store.failure()!=null)throw new IOException("关闭时无法保存队伍状态",store.failure());drain();}while(!deferred.isEmpty());
        store.finishPending();closing=true;refresh.checkpointDeltas();resets.close();refresh.close();worlds.close();store.close();instance=null;
    }
}
