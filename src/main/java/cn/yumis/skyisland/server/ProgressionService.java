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
    private final List<JsonObject> quests = new ArrayList<>();
    private final Set<String> gated = new HashSet<>();
    public ProgressionService(SkyServer rt) {
        this.rt = rt; rules = rt.config.object("tasks/bounties.json");
        BountyRules.slotCost(rules.get("maximumSlots").getAsInt(), rules.get("firstSlotCost").getAsLong(), rules.get("subsequentSlotCostStep").getAsLong());
        long weightSum=0; for(String key:List.of("rare","epic","legendary")) { int w=rules.getAsJsonObject("rarityWeights").get(key).getAsInt(); if(w<0)throw new IllegalArgumentException("悬赏权重不能为负"); weightSum+=w; }
        if(weightSum==0)throw new IllegalArgumentException("悬赏权重不能全部为0");
        if (rules.get("initialSlots").getAsInt() < 1 || rules.get("maximumSlots").getAsInt() > 50 ||
            rules.get("maximumSlots").getAsInt() < rules.get("initialSlots").getAsInt() ||
            rules.get("levelsPerSlot").getAsInt() < 1 || rules.get("durationGameDays").getAsInt() < 1 ||
            rules.get("maximumMaterialQuantity").getAsInt() < 1 || rules.get("maximumMaterialQuantity").getAsInt() > 1536 ||
            rules.get("materialAttemptsPerReward").getAsInt() < 1 || rules.get("materialAttemptsPerReward").getAsInt() > 5)
            throw new IllegalArgumentException("悬赏配置范围无效");
        Set<String> ids = new HashSet<>();
        for (JsonElement value : rt.config.object("tasks/quests.json").getAsJsonArray("quests")) {
            JsonObject q = value.getAsJsonObject();
            if (!ids.add(q.get("id").getAsString()) || q.get("rewardCoins").getAsLong() < 0) throw new IllegalArgumentException("任务ID重复或奖励金币无效");
            for (JsonElement e : q.getAsJsonArray("requirements")) {
                var r = e.getAsJsonObject();
                if (materialEntry(r.get("productId").getAsString()) == null || r.get("quantity").getAsInt() < 1)
                    throw new IllegalArgumentException("任务需求物品或数量无效：" + q.get("id"));
            }
            for (JsonElement e : q.getAsJsonArray("rewardItems")) {
                if (rt.economy.decode(e).isEmpty()) throw new IllegalArgumentException("任务奖励为空");
            }
            for (JsonElement e : q.getAsJsonArray("unlockFeatures")) {
                String key = e.getAsString();
                if (!Set.of("bounty.use", "shop.special", "automation.shipping").contains(key)) throw new IllegalArgumentException("未知任务权限：" + key);
                gated.add(key);
            }
            quests.add(q);
        }
    }
    public boolean allows(IslandState.Party p, String feature) { return !gated.contains(feature) || p.unlockedFeatures.contains(feature); }
    public int slots(IslandState.Party p) {
        return Math.min(rules.get("maximumSlots").getAsInt(), rules.get("initialSlots").getAsInt() +
            p.highestIslandLevel / rules.get("levelsPerSlot").getAsInt() + p.paidBountySlots);
    }
    public long slotCost(IslandState.Party p) {
        return BountyRules.slotCost(p.paidBountySlots, rules.get("firstSlotCost").getAsLong(), rules.get("subsequentSlotCostStep").getAsLong());
    }
    private String context(IslandState.Party p) { return rt.epoch() + "/" + p.highestIslandLevel + "/" + p.soldProductIds.hashCode() + "/" + p.acquiredStarterIds.hashCode() + "/" + p.unlockedFeatures.hashCode(); }
    private boolean invalid(IslandState.Bounty b) {
        return b.material != null && (!rt.economy.entries.containsKey(b.material) || !rt.economy.entries.get(b.material).product().canSell() || !rt.economy.entries.get(b.material).product().rarity().equals("common") ||
            !rt.economy.entries.containsKey(b.reward) || !rt.economy.entries.get(b.reward).product().canBuy() || rt.economy.entries.get(b.reward).product().rarity().equals("common"));
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
    public void initialize(IslandState.Party p){maintain(p);}
    private void fill(IslandState.Party p, IslandState.Bounty b) {
        b.generation++; b.createdAt = rt.state().effectiveTicks;
        b.expiresAt = Math.addExact(b.createdAt, Math.multiplyExact(rules.get("durationGameDays").getAsLong(), 24000));
        b.material = null; b.reward = null; b.rewardStack=null; b.quantity = 0; b.attemptContext = context(p);
        var materials = rt.economy.entries.values().stream().filter(e -> e.product().canSell() && e.product().rarity().equals("common") && p.soldProductIds.contains(e.product().id())).toList();
        if (materials.isEmpty() || !allows(p, "shop.special")) return;
        var pool = rt.economy.entries.values().stream().filter(e -> e.product().canBuy() && !e.product().rarity().equals("common") && e.product().level() <= p.highestIslandLevel).map(e -> {
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
            fill(current, current.bounties.get(slot));
        }, () -> SkyServer.tell(player, "悬赏兑换完成，获得 " + reward.product().name() + " ×1；新悬赏已生成"));
    }
    private JsonObject next(IslandState.Party p) { return quests.stream().filter(q -> !p.completedQuestIds.contains(q.get("id").getAsString())).findFirst().orElse(null); }
    public void claimQuest(ServerPlayer player, String id) {
        var p = rt.trades.available(player); JsonObject q = next(p);
        int split=id.lastIndexOf('@');
        if(split<0||Long.parseLong(id.substring(split+1))!=p.overworldEpoch)throw new IllegalArgumentException("岛屿已经重开，请重新确认任务");
        id=id.substring(0,split);final String questId=id;
        if (q == null || !q.get("id").getAsString().equals(id)) throw new IllegalArgumentException("该任务尚未解锁或已经完成");
        var requirements = new ArrayList<Requirement>();
        for (var e : q.getAsJsonArray("requirements")) { var r = e.getAsJsonObject(); requirements.add(new Requirement(r.get("productId").getAsString(), r.get("quantity").getAsInt())); }
        var rewards = new ArrayList<ItemStack>(); for (var e : q.getAsJsonArray("rewardItems")) rewards.add(rt.economy.decode(e));
        long balance = Money.add(p.coins, q.get("rewardCoins").getAsLong());
        JsonArray inventory = exchange(player, requirements, rewards);
        rt.trades.commitInventory(player, inventory, s -> {
            var current = s.parties.get(p.id); current.coins = balance; current.completedQuestIds.add(questId);
            for (var key : q.getAsJsonArray("unlockFeatures")) current.unlockedFeatures.add(key.getAsString());
            for (ItemStack stack : rewards) { var e = rt.economy.match(stack); if (e != null) acquired(current, e.product().id()); }
        }, () -> SkyServer.tell(player, "任务完成：" + q.get("name").getAsString() + "，奖励已领取"));
    }
    public void acquired(IslandState.Party p, String id) {
        var e = rt.economy.entries.get(id); if (e != null && rt.economy.starter(e)) p.acquiredStarterIds.add(id);
    }
    private record Requirement(String product, int quantity) {}
    private ItemEconomy.Entry materialEntry(String id) {
        var active=rt.economy.entries.get(id);if(active!=null)return active;
        var products=rt.config.object("shop/products.json").getAsJsonObject("products");if(!products.has(id))return null;
        var row=products.getAsJsonObject(id);var canonical=row.getAsJsonObject("canonicalStack");
        JsonObject stack=new JsonObject();stack.addProperty("id",canonical.get("itemId").getAsString());stack.addProperty("count",1);stack.add("components",canonical.get("componentPatch"));
        var template=rt.economy.decode(stack);if(template.isEmpty())return null;
        return new ItemEconomy.Entry(new GameConfig.Product(id,row.get("name").getAsString(),canonical,1,10000,"disabled","common",0,0,1,"other"),template);
    }
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
            if (left != 0) throw new IllegalArgumentException("材料不足：" + entry.product().name() + " ×" + r.quantity);
        }
        for (var reward : rewards) {
            int left = reward.getCount();
            while (left > 0) { int amount = Math.min(left, reward.getMaxStackSize()); if (!copy.addItem(reward.copyWithCount(amount)).isEmpty()) throw new IllegalArgumentException("背包空间不足，材料未扣除"); left -= amount; }
        }
        for (int i = 0; i < 36; i++) inventory.set(i, rt.economy.encode(copy.getItem(i)));
        return inventory;
    }
    public JsonObject snapshot(IslandState.Party p) {
        JsonObject data = new JsonObject(); data.addProperty("slots", slots(p)); data.addProperty("maximumSlots", rules.get("maximumSlots").getAsInt());
        data.addProperty("paidSlots", p.paidBountySlots); data.addProperty("slotCost", slotCost(p)); data.addProperty("bountyAllowed", allows(p, "bounty.use"));
        data.addProperty("specialAllowed",allows(p,"shop.special"));data.addProperty("shippingAllowed",allows(p,"automation.shipping"));
        JsonArray offers = new JsonArray();
        for (var b : p.bounties) {
            JsonObject o = GameConfig.JSON.toJsonTree(b).getAsJsonObject();
            if (b.material != null && !invalid(b)) { o.addProperty("materialName", rt.economy.entries.get(b.material).product().name()); o.addProperty("rewardName", rt.economy.entries.get(b.reward).product().name()); o.add("rewardStack", b.rewardStack==null?rt.economy.encode(rt.economy.entries.get(b.reward).template()):b.rewardStack.deepCopy()); }
            else o.add("material",JsonNull.INSTANCE);
            offers.add(o);
        }
        JsonObject quest=next(p)==null?null:next(p).deepCopy();
        if(quest!=null)for(var value:quest.getAsJsonArray("requirements")){var r=value.getAsJsonObject();var entry=materialEntry(r.get("productId").getAsString());r.addProperty("name",entry.product().name());r.add("stack",rt.economy.encode(entry.template()));r.add("canonical",entry.product().canonical());}
        data.add("offers", offers); data.add("quest",quest==null?JsonNull.INSTANCE:quest); data.add("completed", GameConfig.JSON.toJsonTree(p.completedQuestIds)); return data;
    }
    public static void reset(IslandState.Party p) {
        p.highestIslandLevel = 0; p.cachedPoints = "0"; p.paidBountySlots = 0; p.initialScanEpoch = -1;
        p.soldProductIds.clear(); p.acquiredStarterIds.clear(); p.completedQuestIds.clear(); p.unlockedFeatures.clear(); p.bounties.clear(); p.research.clear();
    }
}
