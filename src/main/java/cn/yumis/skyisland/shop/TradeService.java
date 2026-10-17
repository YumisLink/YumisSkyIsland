package cn.yumis.skyisland.shop;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.block.ShippingChestEntity;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.mixin.PlayerListAccessor;
import cn.yumis.skyisland.server.*;
import com.google.gson.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class TradeService {
    private final SkyServer runtime;
    private final SalePlanner planner;
    private final Queue<ShippingChestEntity> boxes = new ArrayDeque<>();
    private record Preview(UUID token, UUID party, long islandEpoch, long marketEpoch, long npcRevision,long priceRevision,String quotas, String selection, SalePlanner.Plan plan) {}
    private final Map<UUID, Preview> previews = new HashMap<>();
    private final Map<UUID, Boolean> saleModes = new HashMap<>();
    private final Set<UUID> applyingRecovery = new HashSet<>();
    private final Set<UUID> pendingInventoryCommits = new HashSet<>();
    public record ExperienceChange(int levelCost, int pointReward) {
        public ExperienceChange {
            if (levelCost < 0 || pointReward < 0) throw new IllegalArgumentException("经验消耗或奖励不能为负");
        }
        public static ExperienceChange none() { return new ExperienceChange(0, 0); }
        public static ExperienceChange levels(int cost) { return new ExperienceChange(cost, 0); }
        public static ExperienceChange points(int points) { return new ExperienceChange(0, points); }
    }
    public record InventoryChange(JsonArray items, ExperienceChange experience, IslandState.LotteryDrawResult lotteryResult) {}
    public TradeService(SkyServer runtime) { this.runtime = runtime; planner = new SalePlanner(runtime.config.quotaCurve); }
    public void roll(IslandState.Party party) {
        List<DailyShopSelector.Candidate> pool = runtime.economy.entries.values().stream().filter(runtime.economy::canBuy).filter(e -> !e.product().id().startsWith("yumis_sky_island:biome_ball_")).map(e -> {
            var p = e.product(); int rarity = switch (p.rarity()) { case "rare" -> 1; case "epic" -> 2; case "legendary" -> 3; default -> 0; };
            return new DailyShopSelector.Candidate(p.id(), rarity, p.level(), p.weight(), p.stock());
        }).toList();
        JsonObject shops = runtime.config.object("shop/shops.toml"); JsonObject definitions = runtime.config.object("shop/rarities.toml").getAsJsonObject("definitions");
        int[] ordinary = new int[4], hidden = new int[4]; String[] rarity = {"common", "rare", "epic", "legendary"};
        for (int i = 1; i < 4; i++) { var row = definitions.getAsJsonObject(rarity[i]); ordinary[i] = row.get("dailyWeight").getAsInt(); hidden[i] = row.get("hiddenWeight").getAsInt(); }
        JsonObject hiddenRules = shops.getAsJsonObject("hidden");
        var selection = DailyShopSelector.rollShop(runtime.state().worldSeed, party.id, runtime.epoch(), runtime.level(party), pool,
            hiddenRules.get("chanceBps").getAsInt(), shops.getAsJsonObject("daily").get("slots").getAsInt(), hiddenRules.get("slots").getAsInt(), ordinary, hidden,
            hiddenRules.get("guaranteedLegendary").getAsInt(), hiddenRules.get("guaranteedEpic").getAsInt());
        runtime.change(null, state -> {
            var p = state.parties.get(party.id); p.marketEpoch = selection.epoch(); p.hidden = selection.hidden(); p.quota.clear(); p.dailyStock.clear();
            for (var offer : selection.offers()) p.dailyStock.put(offer.productId(), offer.remainingStock());
        }, () -> {});
    }
    public IslandState.Party available(ServerPlayer player) {
        IslandState.Party p = runtime.parties.requireMember(player);
        if (!runtime.store.available()) throw new IllegalStateException("数据正在保存，请稍后交易");
        if (p.resetInProgress) throw new IllegalStateException("重开期间暂停交易");
        if (p.marketEpoch != runtime.epoch()) throw new IllegalStateException("正在刷新今日商店，请稍后交易");
        if (runtime.inventoryLocks.contains(player.getUUID())) throw new IllegalStateException("上一笔交易还在保存");
        return p;
    }
    public void buy(ServerPlayer player, String id, int quantity) {
        var party = available(player);
        var entry = runtime.economy.entries.get(id);
        buy(player,id,quantity,entry != null && !entry.product().rarity().equals("common") && !id.startsWith("yumis_sky_island:biome_ball_") ? "special" : "permanent",runtime.epoch(),party.id,party.overworldEpoch);
    }
    public void buy(ServerPlayer player, String id, int quantity, String shop, long expectedMarket, UUID expectedParty, long expectedIsland) {
        if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("每次购买数量须为1～64");
        IslandState.Party party = available(player); ItemEconomy.Entry entry = runtime.economy.entries.get(id);
        if (!runtime.economy.canBuy(entry)) throw new IllegalArgumentException("商品未上架");
        if (runtime.level(party) < entry.product().level()) throw new IllegalArgumentException("需要空岛 " + entry.product().level() + " 级");
        if (!party.id.equals(expectedParty) || party.overworldEpoch != expectedIsland) throw new IllegalArgumentException("队伍或岛屿已变化，请重新打开控制面板");
        if (expectedMarket != runtime.epoch()) throw new IllegalArgumentException("市场已换日，请重新确认购买");
        if (!Set.of("permanent","special").contains(shop)) throw new IllegalArgumentException("商店类型无效");
        boolean special = shop.equals("special");
        boolean biomeBall = id.startsWith("yumis_sky_island:biome_ball_");
        if (!special && !entry.product().rarity().equals("common") && !biomeBall) throw new IllegalArgumentException("该商品仅在每日／隐藏商店供货");
        if (special && biomeBall) throw new IllegalArgumentException("生态球在常驻商店出售");
        if (special && !runtime.progression.allows(party, "shop.special")) throw new IllegalArgumentException("每日/隐藏商店尚未通过任务解锁");
        if (special && (!party.dailyStock.containsKey(id)||(party.dailyStock.get(id)!=-1&&party.dailyStock.get(id)<quantity))) throw new IllegalArgumentException("今日库存不足或今日未抽到该商品");
        long cost = Math.multiplyExact(runtime.economy.buyPrice(entry, party, !special && !biomeBall), quantity); long balance = Money.subtract(party.coins, cost);
        player.stopUsingItem();if(player.containerMenu!=player.inventoryMenu)player.closeContainer(); JsonArray inventory = runtime.economy.capture(player.getInventory());
        SimpleContainer copy = new SimpleContainer(36);
        for (int i = 0; i < 36; i++) copy.setItem(i, runtime.economy.decode(inventory.get(i)));
        int left = quantity;
        while (left > 0) {
            int amount = Math.min(left, entry.template().getMaxStackSize()); ItemStack purchase = entry.template().copyWithCount(amount);
            if (!copy.addItem(purchase).isEmpty()) throw new IllegalArgumentException("普通背包格空间不足");
            left -= amount;
        }
        for (int i = 0; i < 36; i++) inventory.set(i, runtime.economy.encode(copy.getItem(i)));
        String actorName=player.getGameProfile().name();
        if(!commitInventory(player,new InventoryChange(inventory,ExperienceChange.none(),null),state -> {
            var p = state.parties.get(party.id); p.coins = balance;
            runtime.progression.acquired(p, id);
            if (special&&p.dailyStock.get(id)!=-1) p.dailyStock.put(id, p.dailyStock.get(id) - quantity);
        }, () -> {
            JsonObject notice=new JsonObject();notice.addProperty("partyId",party.id.toString());notice.addProperty("actor",actorName);
            notice.addProperty("itemId",id);notice.addProperty("quantity",quantity);notice.addProperty("cost",cost);
            runtime.partyEvent(party.id,"purchase_notice",notice);
        },current -> {}))throw new IllegalStateException("数据正在保存，请稍后重试");
    }
    public void sell(ServerPlayer player, boolean hand) {
        sell(player,hand,true);
    }
    public void sell(ServerPlayer player, boolean hand, boolean command) {
        available(player); player.stopUsingItem(); if(player.containerMenu!=player.inventoryMenu)player.closeContainer();
        if(command)runtime.send(player,"sale_begin",new JsonObject());
        saleModes.put(player.getUUID(), hand); preview(player, "", true);
    }
    public void preview(ServerPlayer player, String selection, boolean open) {
        IslandState.Party party = available(player);
        if (!saleModes.containsKey(player.getUUID())) throw new IllegalArgumentException("请先打开出售背包");
        Map<Integer,Integer> selected = new TreeMap<>();
        if (!selection.isBlank()) for (String value : selection.split(",")) {
            String[] parts = value.split(":"); if (parts.length != 2) throw new IllegalArgumentException("出售选择无效");
            int slot = Integer.parseInt(parts[0]), count = Integer.parseInt(parts[1]);
            if (slot < 0 || slot >= 36 || count < 1 || count > player.getInventory().getItem(slot).getCount() ||
                saleModes.get(player.getUUID()) && slot != player.getInventory().getSelectedSlot() || selected.put(slot,count) != null)
                throw new IllegalArgumentException("背包或出售数量已变化，请重新选择");
        }
        var inputs = new ArrayList<SalePlanner.Input>();
        for (var e : selected.entrySet()) {
            var stack = player.getInventory().getItem(e.getKey()); var q = runtime.economy.quote(stack,party);
            if (q == null || q.normalSale().signum() <= 0) throw new IllegalArgumentException("所选物品无法出售");
            inputs.add(new SalePlanner.Input(e.getKey(), runtime.economy.encode(stack).toString(), e.getValue(), q.quota().group(), q.quota().units(), q.normalSale()));
        }
        var plan = planner.prepare(party.coins, counters(party), inputs); UUID token = UUID.randomUUID();
        previews.put(player.getUUID(), new Preview(token,party.id,party.overworldEpoch,runtime.epoch(),runtime.residents.effectVersion(party),runtime.priceRevision,party.quota.toString(),selection,plan));
        JsonObject data = new JsonObject(); data.addProperty("token",token.toString()); data.addProperty("selection",selection); data.addProperty("open",open); data.addProperty("coins",plan.coins()); data.addProperty("hand",saleModes.get(player.getUUID()));
        JsonArray inventory = new JsonArray(), lines = new JsonArray(); Rational sum = Rational.ZERO; var used = counters(party);
        for (int slot=0;slot<36;slot++) {
            var stack=player.getInventory().getItem(slot); JsonObject row=new JsonObject(); row.addProperty("slot",slot); row.add("stack",runtime.economy.encode(stack));
            var quote=runtime.economy.quote(stack,party); row.addProperty("allowed",quote!=null && quote.normalSale().signum()>0 && (!saleModes.get(player.getUUID()) || slot==player.getInventory().getSelectedSlot())); inventory.add(row);
        }
        for (var input : inputs) {
            Rational before=sum; Rational old=used.getOrDefault(input.group(),Rational.ZERO);
            sum=sum.add(runtime.config.quotaCurve.quote(old,input.quantity(),input.unitsPerItem(),input.normalItemSale()));
            used.put(input.group(),old.add(input.unitsPerItem().multiply(input.quantity())));
            JsonObject row=new JsonObject(); row.addProperty("slot",input.slot()); row.addProperty("quantity",input.quantity());
            row.addProperty("unit",input.normalItemSale().toString());
            row.addProperty("coins",sum.floorLongExact()-before.floorLongExact()); lines.add(row);
        }
        data.add("inventory",inventory); data.add("lines",lines); runtime.send(player,"sale_preview",data);
    }
    public void confirmSale(ServerPlayer player, String token) {
        IslandState.Party party=available(player); Preview preview=previews.get(player.getUUID());
        if(preview==null || !preview.token.toString().equals(token)) throw new IllegalArgumentException("出售报价已失效，请重新查看");
        boolean changed=!preview.party.equals(party.id)||preview.islandEpoch!=party.overworldEpoch||preview.marketEpoch!=runtime.epoch()||preview.npcRevision!=runtime.residents.effectVersion(party)||preview.priceRevision!=runtime.priceRevision||!preview.quotas.equals(party.quota.toString());
        for(var input:preview.plan.removals()) changed |= !runtime.economy.encode(player.getInventory().getItem(input.slot())).toString().equals(input.fingerprint());
        if(changed) {
            previews.remove(player.getUUID());
            try { preview(player,preview.selection,false); } catch(IllegalArgumentException e) { preview(player,"",false); }
            SkyServer.tell(player,"物品、行情或额度已变化，请查看新报价后再次确认"); return;
        }
        if(!preview.plan.canCommit()) throw new IllegalArgumentException("请选择可售物品；整笔收益须至少1"+Money.currencyName());
        previews.remove(player.getUUID()); var plan=planner.prepare(party.coins,counters(party),preview.plan.removals());
        JsonArray inventory=runtime.economy.capture(player.getInventory());
        for(var input:plan.removals()){var stack=runtime.economy.decode(inventory.get(input.slot()));stack.shrink(input.quantity());inventory.set(input.slot(),runtime.economy.encode(stack));}
        commitInventory(player,inventory,s->{var p=s.parties.get(party.id);applyPlan(p,plan);recordSales(p,plan);},()->{runtime.send(player,"sale_done",new JsonObject());SkyServer.tell(player,"出售完成，获得 "+Money.format(plan.coins()));});
    }
    public void forget(ServerPlayer player){previews.remove(player.getUUID());saleModes.remove(player.getUUID());}
    public void commitInventory(ServerPlayer player, JsonArray inventory, java.util.function.Consumer<IslandState> edit, Runnable after) {
        commitInventory(player,inventory,0,edit,after);
    }
    public void commitInventory(ServerPlayer player, JsonArray inventory, int experiencePoints, java.util.function.Consumer<IslandState> edit, Runnable after) {
        if(!commitInventory(player,new InventoryChange(inventory,ExperienceChange.points(experiencePoints),null),edit,()->{},
            current->{if(current==player)after.run();}))throw new IllegalStateException("数据正在保存，请稍后重试");
    }
    /** Validate before taking a lock; no silent rejection may strand a newly acquired lock. */
    public void requireInventoryReady(ServerPlayer player) {
        if(runtime.closing)throw new IllegalStateException("服务器正在关闭，请稍后重试");
        if(!runtime.store.available())throw new IllegalStateException(runtime.store.failure()==null?"数据正在保存，请稍后重试":"数据写入失败，交易已暂停");
        if(runtime.inventoryLocks.contains(player.getUUID()))throw new IllegalStateException("上一笔背包交易尚未完成");
        if(currentPlayer(player.getUUID())!=player)throw new IllegalStateException("玩家连接已变化，请重试");
    }
    public boolean blocksExperienceMutation(ServerPlayer player) {
        return runtime.inventoryLocks.contains(player.getUUID())&&!applyingRecovery.contains(player.getUUID());
    }
    public boolean commitInventory(ServerPlayer player,InventoryChange change,java.util.function.Consumer<IslandState> edit,
                                   Runnable afterDurable,java.util.function.Consumer<ServerPlayer> afterOnline) {
        requireInventoryReady(player);
        Objects.requireNonNull(change.items());Objects.requireNonNull(change.experience());
        if(player.experienceLevel<change.experience().levelCost())throw new IllegalArgumentException("附魔等级不足");
        UUID id=player.getUUID();
        JsonArray items=change.items().deepCopy();
        IslandState.ExperienceSnapshot experience=experienceSnapshot(player);
        IslandState.LotteryDrawResult draw=change.lotteryResult()==null?null:
            cn.yumis.skyisland.config.GameConfig.JSON.fromJson(cn.yumis.skyisland.config.GameConfig.JSON.toJson(change.lotteryResult()),IslandState.LotteryDrawResult.class);
        runtime.inventoryLocks.add(id);pendingInventoryCommits.add(id);
        try {
            boolean accepted=runtime.tryChange(player,state->{
                edit.accept(state);
                recovery(state,id,items,experience,change.experience(),draw);
            },()->{
                pendingInventoryCommits.remove(id);
                ServerPlayer current=currentPlayer(id);
                boolean applied=current!=null&&applyRecovery(current);
                afterDurable.run();
                if(applied)afterOnline.accept(current);
            },failure->{
                // A write/force failure can leave a complete frame on disk. Keep the lock until journal recovery.
                YumisSkyIsland.LOGGER.error("背包事务写入失败，玩家 {} 保持锁定等待恢复",id,failure);
            });
            if(!accepted){runtime.inventoryLocks.remove(id);pendingInventoryCommits.remove(id);}
            return accepted;
        } catch(RuntimeException error) {
            runtime.inventoryLocks.remove(id);pendingInventoryCommits.remove(id);
            throw error;
        }
    }
    private void recordSales(IslandState.Party p, SalePlanner.Plan plan) {
        for(var input:plan.removals()){var e=runtime.economy.match(runtime.economy.decode(com.google.gson.JsonParser.parseString(input.fingerprint())));if(e!=null)p.soldProductIds.add(e.product().id());}
    }
    private static Map<String, Rational> counters(IslandState.Party p) { Map<String, Rational> map = new HashMap<>(); p.quota.forEach((k, v) -> map.put(k, Rational.parse(v))); return map; }
    private static void applyPlan(IslandState.Party p, SalePlanner.Plan plan) { p.coins = plan.resultingWallet(); p.quota.clear(); plan.counters().forEach((k, v) -> p.quota.put(k, v.toString())); }
    private static IslandState.ExperienceSnapshot experienceSnapshot(ServerPlayer player) {
        var snapshot=new IslandState.ExperienceSnapshot();snapshot.level=player.experienceLevel;
        snapshot.progress=player.experienceProgress;snapshot.total=player.totalExperience;return snapshot;
    }
    private static void recovery(IslandState state, UUID player, JsonArray inventory,IslandState.ExperienceSnapshot before,
                                 ExperienceChange experience,IslandState.LotteryDrawResult draw) {
        IslandState.InventoryRecovery r = new IslandState.InventoryRecovery(); r.sequence = Math.addExact(state.sequence,1); r.items = inventory;
        r.beforeExperience=before;r.experienceLevelCost=experience.levelCost();r.experiencePoints=experience.pointReward();r.lotteryResult=draw;
        state.inventoryRecovery.put(player, r);
    }
    private ServerPlayer currentPlayer(UUID id) {
        ServerPlayer current=runtime.server.getPlayerList().getPlayer(id);
        return current==null||current.hasDisconnected()?null:current;
    }
    public void recover(ServerPlayer player) {
        // A reconnect can arrive before the new frame publishes. Never unlock against the previous receipt.
        if(pendingInventoryCommits.contains(player.getUUID()))return;
        IslandState.InventoryRecovery recovery = runtime.state().inventoryRecovery.get(player.getUUID());
        if(recovery!=null)applyRecovery(player);
    }
    private boolean applyRecovery(ServerPlayer player) {
        UUID id=player.getUUID();if(currentPlayer(id)!=player)return false;
        var recovery = runtime.state().inventoryRecovery.get(player.getUUID());
        if(recovery==null)return false;
        if(((AttachmentTarget)player).getAttachedOrCreate(YumisSkyIsland.TRANSACTION_SEQUENCE)>=recovery.sequence){runtime.inventoryLocks.remove(id);return true;}
        runtime.inventoryLocks.add(id);applyingRecovery.add(id);
        try {
            runtime.economy.restore(player.getInventory(),recovery.items);
            if(recovery.beforeExperience!=null){
                var before=recovery.beforeExperience;
                player.setExperienceLevels(before.level);player.experienceProgress=before.progress;player.totalExperience=before.total;
            }
            if(recovery.experienceLevelCost>0)player.giveExperienceLevels(-recovery.experienceLevelCost);
            if(recovery.experiencePoints>0)player.giveExperiencePoints(recovery.experiencePoints);
            ((AttachmentTarget)player).setAttached(YumisSkyIsland.TRANSACTION_SEQUENCE,recovery.sequence);
            player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();
            player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress,player.totalExperience,player.experienceLevel));
            ((PlayerListAccessor)runtime.server.getPlayerList()).yumis$save(player);
            runtime.inventoryLocks.remove(id);
            if(recovery.lotteryResult!=null)runtime.send(player,"lottery_result",cn.yumis.skyisland.config.GameConfig.JSON.toJsonTree(recovery.lotteryResult));
            return true;
        } catch(RuntimeException error) {
            YumisSkyIsland.LOGGER.error("玩家 {} 背包恢复尚未完成，继续保持锁定",id,error);
            SkyServer.tell(player,"背包恢复未完成，请联系管理员；恢复凭据已保留");return false;
        } finally { applyingRecovery.remove(id); }
    }
    public void enqueue(ShippingChestEntity box) { if (box.pending || box.locked || box.isRemoved()) return; box.pending = true;box.setChanged();boxes.add(box); }
    public void tick() {
        if (!runtime.store.available() || boxes.isEmpty()) return;
        long deadline = System.nanoTime() + 1_000_000L;
        while (!boxes.isEmpty() && runtime.store.available() && System.nanoTime() < deadline) {
            ShippingChestEntity box = boxes.remove(); box.pending = false;box.setChanged();
            if (box.isRemoved() || !box.bound(runtime) || box.getLevel() == null || !box.getLevel().hasChunkAt(box.getBlockPos())) continue;
            IslandState.Party p = runtime.state().parties.get(box.party);
            if (p == null || p.resetInProgress || p.marketEpoch != runtime.epoch() || !runtime.progression.allows(p,"automation.shipping")) continue;
            try {
                var plan = planner.prepare(p.coins, counters(p), runtime.economy.saleInputs(box, -1,p));
                if (!plan.canCommit()) continue;
                JsonArray after = runtime.economy.capture(box);
                for (var input : plan.removals()) after.set(input.slot(), JsonNull.INSTANCE);
                box.locked = true;
                runtime.change(null, state -> {
                    applyPlan(state.parties.get(p.id), plan);
                    recordSales(state.parties.get(p.id), plan);
                    IslandState.Box data = state.boxes.computeIfAbsent(box.boxId, _ -> new IslandState.Box());
                    data.party = box.party; data.dimension = box.dimension; data.x = box.getBlockPos().getX(); data.y = box.getBlockPos().getY(); data.z = box.getBlockPos().getZ();
                    data.islandEpoch = box.islandEpoch; data.activationSequence = box.activationSequence;
                    data.saleSequence = state.sequence + 1; data.contents = after; data.powered = box.powered;
                }, () -> { var saved = runtime.state().boxes.get(box.boxId); runtime.economy.restore(box, saved.contents); box.saleSequence = saved.saleSequence; box.locked = false; box.setChanged(); });
            } catch (ArithmeticException | IllegalArgumentException e) { YumisSkyIsland.LOGGER.warn("出货箱保留物品：{}", e.getMessage()); }
        }
    }
}
