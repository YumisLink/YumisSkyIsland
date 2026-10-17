package cn.yumis.skyisland.server;

import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.shop.ItemEconomy;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Team quests and independent, persisted exchange slots. All grants use inventory recovery. */
public final class ProgressionService {
    private final SkyServer rt;
    private final JsonObject rules;
    private QuestCatalog catalog;
    private record Production(ItemStack output,boolean repair) {}
    private final Map<net.minecraft.world.inventory.Slot,Production> production=new WeakHashMap<>();
    public void prepareProduction(net.minecraft.world.inventory.Slot slot){
        if(slot.getItem().isEmpty())return;
        boolean repair=slot.container instanceof net.minecraft.world.inventory.ResultContainer result&&result.getRecipeUsed()!=null&&result.getRecipeUsed().value() instanceof net.minecraft.world.item.crafting.RepairItemRecipe;
        production.put(slot,new Production(slot.getItem().copy(),repair));
    }
    public void takeProduction(net.minecraft.world.inventory.Slot slot,ServerPlayer player,String source,ItemStack fallback,int quantity){
        Production saved=production.remove(slot);if(saved!=null&&saved.repair())return;
        ItemStack output=saved==null?fallback:saved.output();if(!output.isEmpty())produced(player,source,output,quantity);
    }
    public ProgressionService(SkyServer rt) {
        this.rt = rt; rules = rt.config.object("tasks/bounties.toml");
        BountyRules.slotCost(rules.get("maximumSlots").getAsInt(), rules.get("firstSlotCost").getAsLong(), rules.get("subsequentSlotCostStep").getAsLong());
        long weightSum=0; for(String key:List.of("rare","epic","legendary")) { int w=rules.getAsJsonObject("rarityWeights").get(key).getAsInt(); if(w<0)throw new IllegalArgumentException("悬赏权重不能为负"); weightSum+=w; }
        if(weightSum==0)throw new IllegalArgumentException("悬赏权重不能全部为0");
        if (rules.get("initialSlots").getAsInt() < 1 || rules.get("maximumSlots").getAsInt() > 50 ||
            rules.get("maximumSlots").getAsInt() < rules.get("initialSlots").getAsInt() ||
            rules.get("levelsPerSlot").getAsInt() < 1 || rules.get("durationGameDays").getAsInt() < 1 ||
            rules.get("maximumMaterialQuantity").getAsInt() < 1 || rules.get("maximumMaterialQuantity").getAsInt() > 1536 ||
            rules.get("materialAttemptsPerReward").getAsInt() < 1 || rules.get("materialAttemptsPerReward").getAsInt() > 5)
            throw new IllegalArgumentException("悬赏配置范围无效");
        catalog = new QuestCatalog(rt.config.object("tasks/quests.toml"),rt.economy,rt.config.object("products/quality.toml"));
    }
    public QuestCatalog catalog() { return catalog; }
    public boolean allows(IslandState.Party p, String feature) { return p.unlockedFeatures.contains(feature); }
    public void saveDefinitions(JsonObject document) throws java.io.IOException {
        QuestCatalog validated = new QuestCatalog(document,rt.economy,rt.config.object("products/quality.toml"));
        rt.config.saveObject("tasks/quests.toml",document);
        catalog = validated;
    }
    public int slots(IslandState.Party p) {
        return Math.min(rules.get("maximumSlots").getAsInt(), rules.get("initialSlots").getAsInt() +
            p.highestIslandLevel / rules.get("levelsPerSlot").getAsInt() + p.paidBountySlots);
    }
    public long slotCost(IslandState.Party p) {
        return BountyRules.slotCost(p.paidBountySlots, rules.get("firstSlotCost").getAsLong(), rules.get("subsequentSlotCostStep").getAsLong());
    }
    private String context(IslandState.Party p) { return rt.epoch() + "/" + p.highestIslandLevel + "/" + p.soldProductIds.hashCode() + "/" + p.acquiredStarterIds.hashCode() + "/" + p.unlockedFeatures.hashCode(); }
    private boolean invalid(IslandState.Bounty b) {
        if (b.material == null) return false;
        var material = rt.economy.entries.get(b.material);
        var reward = rt.economy.entries.get(b.reward);
        return material == null || !material.product().canSell() || !material.product().rarity().equals("common") ||
            !rt.economy.canBuy(reward) || reward.product().rarity().equals("common") || reward.product().id().startsWith("yumis_sky_island:biome_ball_") ||
            b.rewardStack != null && !rt.economy.acquisitionAllowed(rt.economy.decode(b.rewardStack));
    }
    public void tick() {
        if (rt.tick % 20 != 0 || !rt.store.available()) return;
        for (var p : rt.state().parties.values()) {
            if (p.resetInProgress || !allows(p, "bounty.use")) continue;
            String context = context(p);
            if (p.bounties.size() != slots(p) || p.bounties.stream().anyMatch(b -> b.expiresAt <= rt.state().effectiveTicks || invalid(b) || b.material == null && !context.equals(b.attemptContext))) {
                rt.change(null, s -> maintain(s.parties.get(p.id)), () -> {}); return;
            }
        }
    }
    private void maintain(IslandState.Party p) {
        while (p.bounties.size() < slots(p)) { var b = new IslandState.Bounty(); b.slot = p.bounties.size(); p.bounties.add(b); }
        while (p.bounties.size() > slots(p)) p.bounties.removeLast();
        for (var b : p.bounties) if (b.expiresAt <= rt.state().effectiveTicks || invalid(b) || b.material == null && !context(p).equals(b.attemptContext)) fill(p, b);
    }
    public void initialize(IslandState.Party p) {
        if (!p.featureDefaultsInitialized) {
            for (var feature : catalog.features.values()) if (feature.defaultUnlocked()) p.unlockedFeatures.add(feature.id());
            p.featureDefaultsInitialized = true;
        }
        maintain(p);
    }
    private void fill(IslandState.Party p, IslandState.Bounty b) {
        b.generation++; b.createdAt = rt.state().effectiveTicks;
        b.expiresAt = Math.addExact(b.createdAt, Math.multiplyExact(rules.get("durationGameDays").getAsLong(), 24000));
        b.material = null; b.reward = null; b.rewardStack=null; b.quantity = 0; b.attemptContext = context(p);
        var materials = rt.economy.entries.values().stream().filter(e -> e.product().canSell() && e.product().rarity().equals("common") && p.soldProductIds.contains(e.product().id())).toList();
        if (materials.isEmpty() || !allows(p, "shop.special")) return;
        var pool = rt.economy.entries.values().stream().filter(e -> rt.economy.canBuy(e) && !e.product().id().equals("yumis_sky_island:flight_ticket") && !e.product().id().startsWith("yumis_sky_island:biome_ball_") && !e.product().rarity().equals("common") && e.product().level() <= p.highestIslandLevel).map(e -> {
            int rarity = switch (e.product().rarity()) { case "rare" -> 1; case "epic" -> 2; default -> 3; };
            return new DailyShopSelector.Candidate(e.product().id(), rarity, e.product().level(), e.product().weight(), 1);
        }).toList();
        int[] weights = {0, rules.getAsJsonObject("rarityWeights").get("rare").getAsInt(), rules.getAsJsonObject("rarityWeights").get("epic").getAsInt(), rules.getAsJsonObject("rarityWeights").get("legendary").getAsInt()};
        long seed = DailyShopSelector.mix(rt.state().worldSeed ^ b.slot ^ DailyShopSelector.mix(b.generation) ^ p.overworldEpoch);
        var selection = DailyShopSelector.roll(seed, p.id, b.generation, p.highestIslandLevel, pool, 0, pool.size(), pool.size(), weights, weights);
        var random = new Random(seed ^ p.id.getLeastSignificantBits());
        for (var offer : selection.offers()) {
            var reward = rt.economy.entries.get(offer.productId());
            long rewardPrice; try { rewardPrice=rt.economy.buyPrice(reward,p); } catch(ArithmeticException e){continue;} long bestGap = Long.MAX_VALUE;
            var attempts = new ArrayList<>(materials); Collections.shuffle(attempts, random);
            for (var material : attempts.subList(0, Math.min(attempts.size(), rules.get("materialAttemptsPerReward").getAsInt()))) {
                long price; try { price=rt.economy.full(material,material.template()).floorLongExact(); } catch(ArithmeticException e){continue;}
                int count = BountyRules.materialCount(rewardPrice, price, rules.get("maximumMaterialQuantity").getAsInt());
                if (count == 0) continue;
                long remainder=rewardPrice%price, gap=remainder==0?0:price-remainder;
                if (gap < bestGap || gap == bestGap && count < b.quantity) {
                    bestGap = gap; b.material = material.product().id(); b.quantity = count; b.reward = reward.product().id(); b.materialPrice = price; b.rewardPrice = rewardPrice;
                }
            }
            if (b.material != null) {b.rewardStack=rt.economy.encode(reward.template());return;}
        }
    }
    public void buySlot(ServerPlayer player, int expectedPaid, long expectedEpoch) {
        var p = rt.trades.available(player);
        if(expectedEpoch!=p.overworldEpoch)throw new IllegalArgumentException("岛屿已经重开，请重新确认");
        if (!allows(p, "bounty.use")) throw new IllegalArgumentException("悬赏尚未通过任务解锁");
        if (expectedPaid != p.paidBountySlots) throw new IllegalArgumentException("购格价格已变化，请查看新报价后确认");
        if (slots(p) >= rules.get("maximumSlots").getAsInt()) throw new IllegalArgumentException("悬赏格已达到上限");
        long after = Money.subtract(p.coins, slotCost(p));
        rt.change(player, s -> { var current = s.parties.get(p.id); current.coins = after; current.paidBountySlots++; maintain(current); }, () -> SkyServer.tell(player, "已购买悬赏格"));
    }
    public void claimBounty(ServerPlayer player, String id) {
        var p = rt.trades.available(player);
        if (!allows(p, "bounty.use") || !allows(p, "shop.special")) throw new IllegalArgumentException("请先完成悬赏与特殊物资解锁任务");
        String[] parts = id.split(":"); if (parts.length != 3 || Long.parseLong(parts[2])!=p.overworldEpoch) throw new IllegalArgumentException("悬赏标识无效或岛屿已经重开");
        int slot = Integer.parseInt(parts[0]); long generation = Long.parseLong(parts[1]);
        if (slot < 0 || slot >= p.bounties.size()) throw new IllegalArgumentException("悬赏格不存在");
        var b = p.bounties.get(slot);
        if (b.generation != generation || b.expiresAt <= rt.state().effectiveTicks || b.material == null || invalid(b)) throw new IllegalArgumentException("悬赏已更替，请查看新条目");
        var reward = rt.economy.entries.get(b.reward);
        JsonArray inventory = exchange(player, List.of(new Requirement(b.material, b.quantity)), List.of(b.rewardStack==null?reward.template():rt.economy.decode(b.rewardStack)));
        rt.trades.commitInventory(player, inventory, s -> {
            var current = s.parties.get(p.id); acquired(current, b.reward);
            PersonalTokens.quest(s,current,player.getUUID(),false,rt.lottery.taskReward());
            fill(current, current.bounties.get(slot));
        }, () -> SkyServer.tell(player, net.minecraft.network.chat.Component.literal("悬赏兑换完成，获得 ").append(reward.template().getHoverName()).append(" ×1；新悬赏已生成")));
    }
    private JsonObject next(IslandState.Party p) {
        var quest = catalog.nextMain(p);
        return quest == null ? null : catalog.detail(p,quest);
    }
    /** Compatibility for server-side callers only; the network protocol requires explicit revisions. */
    public void claimQuest(ServerPlayer player, String encoded) {
        var party = rt.parties.requireMember(player); int split = encoded.lastIndexOf('@');
        if (split < 0 || Long.parseLong(encoded.substring(split+1)) != party.overworldEpoch) throw new IllegalArgumentException("岛屿已经重开，请重新确认任务");
        String id = encoded.substring(0,split);
        claimQuest(player,id,catalog.revision,catalog.resetVersion(party,rt.state().players.get(player.getUUID()),catalog.get(id)));
    }
    public void claimQuest(ServerPlayer player, String id, long expectedRevision, long expectedReset) {
        var p = rt.trades.available(player);
        if (expectedRevision != catalog.revision) throw new IllegalArgumentException("任务内容已修改，请查看新任务后重新确认");
        var quest = catalog.get(id); JsonObject q = quest.definition();
        var person=rt.state().players.get(player.getUUID());
        if (expectedReset != catalog.resetVersion(p,person,quest)) throw new IllegalArgumentException("任务进度已重置，请重新确认");
        if (!catalog.available(p,person,quest)) throw new IllegalArgumentException("该任务尚未解锁或已经完成");
        if (!quest.objective().equals("materials")&&!catalog.status(p,person,quest).equals("ready")) throw new IllegalArgumentException("请先完成本人的任务目标");
        long balance = Money.add(p.coins,q.get("rewardCoins").getAsLong());
        int accessoryDraws = catalog.accessoryDraws(quest);
        JsonArray inventory = exchangeMaterials(player,quest.requirements(),quest.rewards());
        rt.trades.commitInventory(player,inventory,q.get("rewardExperiencePoints").getAsInt(),state -> {
            var current=state.parties.get(p.id); current.coins=balance;
            PersonalTokens.quest(state,current,player.getUUID(),quest.kind().equals("main"),rt.lottery.taskReward());
            if (accessoryDraws > 0) {
                var owner = state.players.get(player.getUUID());
                if (owner != null) owner.pendingAccessoryDraws = Math.addExact(owner.pendingAccessoryDraws, accessoryDraws);
            }
            var progress=QuestCatalog.progress(current,state.players.get(player.getUUID()),quest);progress.completed().add(id);progress.ready().remove(id);
            for (var key : q.getAsJsonArray("unlockFeatures")) current.unlockedFeatures.add(key.getAsString());
            for (ItemStack reward : quest.rewards()) { var entry=rt.economy.match(reward); if(entry!=null)acquired(current,entry.product().id()); }
        }, () -> {SkyServer.tell(player,"任务完成："+q.get("name").getAsString()+"，奖励已领取"+(accessoryDraws>0?"，饰品抽奖 +1":""));rt.questEditor.sendDetail(player,id);});
    }
    public void shippingPlaced(ServerPlayer player, net.minecraft.server.level.ServerLevel world, net.minecraft.core.BlockPos position) {
        var p = rt.parties.active(player.getUUID()); var claim=rt.claim(position);
        if (p==null || claim==null || !p.id.equals(claim.id) || !claim.members.contains(player.getUUID()) || claim.resetInProgress) return;
        Map<String,Long> eligible = new LinkedHashMap<>();
        var person=rt.state().players.get(player.getUUID());
        for (var quest : catalog.quests) if (quest.objective().equals("place_shipping_chest") && catalog.available(p,person,quest))
            eligible.put(quest.id(),catalog.resetVersion(p,person,quest));
        if (eligible.isEmpty()) return;
        UUID party=p.id; long epoch=p.overworldEpoch, definitionRevision=catalog.revision;
        rt.deferred(() -> {
            var current=rt.state().parties.get(party);
            var owner=rt.state().players.get(player.getUUID());
            if(current==null || owner==null||!party.equals(owner.activeParty)||current.resetInProgress || current.overworldEpoch!=epoch || catalog.revision!=definitionRevision) return;
            rt.change(null,state -> {
                var saved=state.parties.get(party);
                eligible.forEach((id,version) -> {var progress=QuestCatalog.progress(saved,state.players.get(player.getUUID()),catalog.get(id));if(Objects.equals(progress.resetVersions().getOrDefault(id,0L),version) && !progress.completed().contains(id))progress.ready().add(id);});
            }, () -> {});
        });
    }
    private record ActionTarget(String quest,QuestCatalog.Goal goal,long reset) {}
    public void action(ServerPlayer player,String type,String targetId,int amount,String sourceBlockId,String toolId){
        if(amount<=0||player.isCreative())return;
        var party=rt.parties.active(player.getUUID());var person=rt.state().players.get(player.getUUID());
        if(party==null||person==null||party.resetInProgress)return;
        List<ActionTarget> targets=new ArrayList<>();
        for(var quest:catalog.quests)if(catalog.available(party,person,quest))for(var goal:quest.goals())
            if(goal.matches(type,targetId,sourceBlockId,toolId)&&catalog.count(party,person,quest,goal)<goal.quantity())targets.add(new ActionTarget(quest.id(),goal,catalog.resetVersion(party,person,quest)));
        if(targets.isEmpty())return;
        UUID owner=player.getUUID(),partyId=party.id;long epoch=party.overworldEpoch,revision=catalog.revision;
        rt.deferred(()->{
            var current=rt.state().parties.get(partyId);var actor=rt.state().players.get(owner);
            if(current==null||actor==null||!partyId.equals(actor.activeParty)||current.resetInProgress||current.overworldEpoch!=epoch||catalog.revision!=revision)return;
            rt.change(null,state->{
                var saved=state.players.get(owner);var savedParty=state.parties.get(partyId);
                for(var target:targets){var quest=catalog.get(target.quest());
                    if(!catalog.available(savedParty,saved,quest)||catalog.resetVersion(savedParty,saved,quest)!=target.reset())continue;
                    var counters=saved.questProgress.computeIfAbsent(quest.id(),k->new LinkedHashMap<>());
                    counters.compute(target.goal().key(),(k,old)->(int)Math.min(target.goal().quantity(),(long)(old==null?0:old)+amount));
                }
            },()->{});
        });
    }
    public void produced(ServerPlayer player,String source,ItemStack output,int amount){
        action(player,source,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem()).toString(),amount,"","");
    }
    public void fished(ServerPlayer player,Collection<ItemStack> loot,String medium){
        for(var stack:loot)if(!stack.isEmpty())produced(player,"fish",stack,stack.getCount());
        rt.professions.fished(player,loot,medium==null?"water":medium);
    }
    private JsonArray exchangeMaterials(ServerPlayer player, List<cn.yumis.skyisland.shop.TaskMaterialPlanner.Requirement> requirements, List<ItemStack> rewards) {
        player.stopUsingItem(); if(player.containerMenu!=player.inventoryMenu)player.closeContainer();
        JsonArray inventory=rt.economy.capture(player.getInventory()); SimpleContainer copy=new SimpleContainer(36);
        for(int i=0;i<36;i++)copy.setItem(i,rt.economy.decode(inventory.get(i)));
        int[] removals=cn.yumis.skyisland.shop.TaskMaterialPlanner.allocate(copy,requirements).orElseThrow(() -> new IllegalArgumentException("任务材料不足或属性未达到要求"));
        for(int i=0;i<removals.length;i++)copy.getItem(i).shrink(removals[i]);
        addRewards(copy,rewards);
        for(int i=0;i<36;i++)inventory.set(i,rt.economy.encode(copy.getItem(i)));
        return inventory;
    }
    private static void addRewards(SimpleContainer copy,List<ItemStack> rewards) {
        for(var reward:rewards){int left=reward.getCount();while(left>0){int count=Math.min(left,reward.getMaxStackSize());if(!copy.addItem(reward.copyWithCount(count)).isEmpty())throw new IllegalArgumentException("背包空间不足，材料未扣除，任务仍可领取");left-=count;}}
    }
    public void acquired(IslandState.Party p, String id) {
        var e = rt.economy.entries.get(id); if (e != null && rt.economy.starter(e)) p.acquiredStarterIds.add(id);
    }
    private record Requirement(String product, int quantity) {}
    private ItemEconomy.Entry materialEntry(String id) { return rt.economy.entries.get(id); }
    private JsonArray exchange(ServerPlayer player, List<Requirement> requirements, List<ItemStack> rewards) {
        player.stopUsingItem(); if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        JsonArray inventory = rt.economy.capture(player.getInventory()); SimpleContainer copy = new SimpleContainer(36);
        for (int i = 0; i < 36; i++) copy.setItem(i, rt.economy.decode(inventory.get(i)));
        for (var r : requirements) {
            var entry = materialEntry(r.product); int left = r.quantity;
            for (int i = 0; i < 36 && left > 0; i++) {
                var stack = copy.getItem(i);
                if (!ItemEconomy.safeToSell(stack) || !ItemEconomy.matchesCanonical(stack, entry.template(), entry.product().canonical().getAsJsonObject("componentPatch"))) continue;
                int take = Math.min(left, stack.getCount()); stack.shrink(take); left -= take;
            }
            if (left != 0) throw new IllegalArgumentException("悬赏材料不足，请检查数量 ×" + r.quantity);
        }
        for (var reward : rewards) {
            int left = reward.getCount();
            while (left > 0) { int amount = Math.min(left, reward.getMaxStackSize()); if (!copy.addItem(reward.copyWithCount(amount)).isEmpty()) throw new IllegalArgumentException("背包空间不足，材料未扣除"); left -= amount; }
        }
        for (int i = 0; i < 36; i++) inventory.set(i, rt.economy.encode(copy.getItem(i)));
        return inventory;
    }
    public JsonObject snapshot(IslandState.Party p) {return snapshot(p,null);}
    public JsonObject snapshot(IslandState.Party p,IslandState.Person person) {
        JsonObject data = new JsonObject(); data.addProperty("slots", slots(p)); data.addProperty("maximumSlots", rules.get("maximumSlots").getAsInt());
        data.addProperty("paidSlots", p.paidBountySlots); data.addProperty("slotCost", slotCost(p)); data.addProperty("bountyAllowed", allows(p, "bounty.use"));
        data.addProperty("specialAllowed",allows(p,"shop.special"));data.addProperty("shippingAllowed",allows(p,"automation.shipping"));
        JsonArray offers = new JsonArray();
        for (var b : p.bounties) {
            JsonObject o = GameConfig.JSON.toJsonTree(b).getAsJsonObject();
            if (b.material != null && !invalid(b)) { o.add("rewardStack", b.rewardStack==null?rt.economy.encode(rt.economy.entries.get(b.reward).template()):b.rewardStack.deepCopy()); }
            else o.add("material",JsonNull.INSTANCE);
            offers.add(o);
        }
        JsonObject quest=next(p);
        data.add("offers",offers);data.add("quest",quest==null?JsonNull.INSTANCE:quest);
        data.add("quests",catalog.summaries(p,person));data.addProperty("revision",catalog.revision);
        Set<String> completed=new LinkedHashSet<>(p.completedQuestIds),ready=new LinkedHashSet<>(p.readyQuestIds);Map<String,Long> reset=new LinkedHashMap<>(p.questResetVersions);
        if(person!=null){completed.addAll(person.completedQuestIds);ready.addAll(person.readyQuestIds);reset.putAll(person.questResetVersions);}
        data.add("completed",GameConfig.JSON.toJsonTree(completed));data.add("ready",GameConfig.JSON.toJsonTree(ready));data.add("resetVersions",GameConfig.JSON.toJsonTree(reset));return data;
    }
    public JsonObject preview() {
        JsonObject data=new JsonObject();data.add("quests",catalog.summaries(null));data.addProperty("revision",catalog.revision);return data;
    }
    public static void reset(IslandState.Party p) {
        p.highestIslandLevel = 0; p.cachedPoints = "0"; p.paidBountySlots = 0; p.initialScanEpoch = -1;
        p.placedChunks.clear(); p.readyQuestIds.clear(); p.questResetVersions.clear(); p.featureDefaultsInitialized=false;
        p.soldProductIds.clear(); p.acquiredStarterIds.clear(); p.completedQuestIds.clear(); p.unlockedFeatures.clear(); p.bounties.clear(); p.research.clear();
    }
}
