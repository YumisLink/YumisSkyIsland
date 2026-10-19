package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.config.LotterySettings;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.item.ModItems;
import cn.yumis.skyisland.network.LotteryPayloads;
import cn.yumis.skyisland.shop.*;
import com.google.gson.*;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** One authoritative pool; costs, results and pity publish with the inventory receipt. */
public final class LotteryService {
    public static final String FILE="gameplay/lottery.toml";
    private final SkyServer runtime;
    private LotterySettings settings;
    private String problem="";
    private final Map<String,ItemStack> templates=new LinkedHashMap<>();
    private List<LotterySelector.Candidate> candidates=List.of();
    private int unlockLevel;
    private String catalogKey="";
    private boolean initializing;
    public LotteryService(SkyServer runtime){this.runtime=runtime;load(true);}
    public void reload(){load(true);}
    private void load(boolean catalog) {
        try {
            var parsed=new LotterySettings(runtime.config.object(FILE));
            if(catalog||templates.isEmpty()) {
                Map<String,ItemStack> loaded=new LinkedHashMap<>();List<LotterySelector.Candidate> rows=new ArrayList<>();
                Map<String,ItemStack> configuredStacks=new LinkedHashMap<>();List<LotterySelector.Candidate> configured=new ArrayList<>();
                for(var prize:parsed.prizes) {
                    var key=Identifier.parse(prize.itemId());if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("未注册奖品："+prize.itemId());
                    JsonObject encoded=new JsonObject();encoded.addProperty("id",prize.itemId());encoded.addProperty("count",1);encoded.add("components",prize.components());
                    ItemStack stack=runtime.economy.decode(encoded);if(stack.isEmpty())throw new IllegalArgumentException("奖品为空："+prize.id());
                    LotteryItemRarity.apply(stack,prize.rarity());
                    if(configuredStacks.values().stream().anyMatch(existing->ItemStack.isSameItemSameComponents(existing,stack)))throw new IllegalArgumentException("同档奖品模板重复："+prize.id());
                    configuredStacks.put(prize.id(),stack.copyWithCount(1));
                    configured.add(new LotterySelector.Candidate(prize.id(),LotterySettings.RARITIES.indexOf(prize.rarity()),prize.islandLevel()));
                }
                if(!LotterySelector.available(configured,parsed.unlockLevel,parsed.policy))throw new IllegalArgumentException("最低传说等级下缺少可兑现的奖品或史诗保底");
                if(parsed.allItems){
                    for(var entry:runtime.economy.entries.values()){
                        var product=entry.product();
                        int rarity=LotterySettings.RARITIES.indexOf(product.rarity());
                        if(rarity<0||entry.template().isEmpty())continue;
                        loaded.put(product.id(),entry.template().copyWithCount(1));
                        rows.add(new LotterySelector.Candidate(product.id(),rarity,product.level()));
                    }
                }
                if(rows.isEmpty()){loaded.putAll(configuredStacks);rows.addAll(configured);}
                templates.clear();templates.putAll(loaded);candidates=List.copyOf(rows);unlockLevel=parsed.unlockLevel;
                catalogKey=UUID.nameUUIDFromBytes(String.join("\n",rows.stream().filter(c->c.rarity()==3).map(c->c.id()+":"+c.minimumLevel()).sorted().toList()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            }
            settings=parsed;problem="";
        }catch(RuntimeException failure){problem="奖池暂不可用，请联系管理员";settings=null;YumisSkyIsland.LOGGER.error("奖池配置错误："+FILE,failure);}
    }
    public int taskReward(){return settings==null?20:settings.taskReward;}
    public int levelReward(){return settings==null?20:settings.levelReward;}
    public void tick() {
        if(initializing||runtime.closing||!runtime.store.available())return;
        if(runtime.state().lotterySeed==null) {
            long seed=new SecureRandom().nextLong();initializing=true;
            if(!runtime.tryChange(null,s->{if(s.lotterySeed==null)s.lotterySeed=seed;},()->initializing=false,e->initializing=false))initializing=false;
            return;
        }
        if(settings==null)return;
        for(var party:runtime.state().parties.values()) {
            if(party.resetInProgress)continue;
            String up=featured(party);
            if(party.lotteryUpEpoch==runtime.epoch()&&catalogKey.equals(party.lotteryUpCatalog)&&(up.isEmpty()||Objects.equals(up,party.lotteryUpId)))continue;
            UUID id=party.id;
            runtime.change(null,s->{var current=s.parties.get(id);updateUp(s,current);},()->{});return;
        }
    }
    private void updateUp(IslandState state,IslandState.Party party) {
        if(party.lotteryUpEpoch!=state.marketRound) {
            party.lotteryUpOrder=new ArrayList<>(LotterySelector.dailyOrder(state.lotterySeed,party.id,state.marketRound,candidates,party.lotteryUpId));
            party.lotteryUpEpoch=state.marketRound;
            party.lotteryUpCatalog=catalogKey;
        }else if(!catalogKey.equals(party.lotteryUpCatalog)){
            // Keep today's order (including temporarily ineligible IDs), deterministically append new entries.
            var order=new ArrayList<>(party.lotteryUpOrder);
            for(String id:LotterySelector.dailyOrder(state.lotterySeed,party.id,state.marketRound,candidates,""))if(!order.contains(id))order.add(id);
            if(candidates.stream().anyMatch(c->c.rarity()==3&&c.id().equals(party.lotteryUpId)&&c.minimumLevel()<=runtime.level(party))){order.remove(party.lotteryUpId);order.addFirst(party.lotteryUpId);}
            party.lotteryUpOrder=order;party.lotteryUpCatalog=catalogKey;
        }
        String selected=LotterySelector.featured(party.lotteryUpOrder,candidates,party.highestIslandLevel);
        if(!selected.isEmpty())party.lotteryUpId=selected;
    }
    private String featured(IslandState.Party party) {
        if(party==null||party.lotteryUpEpoch!=runtime.epoch()||!catalogKey.equals(party.lotteryUpCatalog))return "";
        return LotterySelector.featured(party.lotteryUpOrder,candidates,runtime.level(party));
    }
    private String context(IslandState.Party party) {
        return party.id+"/"+party.overworldEpoch+"/"+runtime.epoch()+"/"+runtime.level(party)+"/"+catalogKey+"/"+featured(party);
    }
    public static LotterySelector.Pity pity(IslandState.Person person){return new LotterySelector.Pity(person.drawsWithoutLegendary,person.drawsWithoutEpic,person.epicOwed,person.featuredGuaranteed);}
    public JsonObject snapshot(ServerPlayer player) {
        var party=runtime.parties.active(player.getUUID());var person=runtime.state().players.get(player.getUUID());
        boolean member=party!=null&&person!=null&&party.members.contains(player.getUUID())&&!party.resetInProgress;
        boolean ready=settings!=null&&settings.enabled&&runtime.state().lotterySeed!=null&&member;
        JsonObject data=new JsonObject();data.addProperty("enabled",ready);data.addProperty("problem",problem);
        data.addProperty("rulesRevision",runtime.lotteryRulesRevision);data.addProperty("sequence",person==null?0:person.lotteryOperationSequence);
        data.addProperty("drawSequence",person==null?0:person.drawSequence);data.addProperty("tokens",person==null?"0":person.tokens.toString());
        data.addProperty("islandLevel",member?runtime.level(party):0);data.addProperty("unlockLevel",unlockLevel);
        data.addProperty("experienceCost",settings==null?30:settings.experienceCost);data.addProperty("tokenCost",settings==null?160:settings.tokenCost);
        data.addProperty("taskTokenReward",taskReward());data.addProperty("levelTokenReward",levelReward());
        data.addProperty("epicRemaining",settings==null?10:person==null?settings.policy.epicAt():Math.max(1,settings.policy.epicAt()-person.drawsWithoutEpic));
        data.addProperty("legendaryRemaining",settings==null?50:person==null?settings.policy.legendaryAt():Math.max(1,settings.policy.legendaryAt()-person.drawsWithoutLegendary));
        data.addProperty("epicOwed",person!=null&&person.epicOwed);data.addProperty("featuredGuaranteed",person!=null&&person.featuredGuaranteed);
        int tickets=0;for(int slot=0;slot<36;slot++)if(player.getInventory().getItem(slot).is(ModItems.LOTTERY_TICKET))tickets+=player.getInventory().getItem(slot).getCount();
        if(player.getOffhandItem().is(ModItems.LOTTERY_TICKET))tickets+=player.getOffhandItem().getCount();data.addProperty("tickets",tickets);
        data.addProperty("context",member?context(party):"");String up=member?featured(party):"";
        boolean available=ready&&runtime.level(party)>=unlockLevel&&!up.isEmpty()&&LotterySelector.available(candidates,runtime.level(party),settings.policy);
        data.addProperty("canDraw",available);if(!up.isEmpty())data.add("upStack",runtime.economy.encode(templates.get(up)));
        return data;
    }
    public void operate(ServerPlayer player,LotteryPayloads.Operation request) {
        if(settings==null)throw new IllegalArgumentException(problem);
        if(!settings.enabled)throw new IllegalArgumentException(LotteryNames.draw()+"当前关闭");
        if(request.rulesRevision()!=runtime.lotteryRulesRevision)throw new IllegalArgumentException("抽奖规则已更新，请重新确认");
        if(!Set.of("draw","draw_10","exchange_xp","exchange_tokens").contains(request.action()))throw new IllegalArgumentException("抽奖操作无效");
        var party=runtime.parties.requireMember(player);runtime.trades.requireInventoryReady(player);
        if(party.resetInProgress)throw new IllegalStateException("岛屿重开期间暂停操作");
        var person=runtime.state().players.get(player.getUUID());
        if(person==null||request.expectedSequence()<0||person.lotteryOperationSequence!=request.expectedSequence())throw new IllegalArgumentException("操作已完成或状态已变化，请等待同步");
        if(!context(party).equals(request.context()))throw new IllegalArgumentException("队伍、等级或每日UP已变化，请重新确认");
        if(runtime.state().lotterySeed==null)throw new IllegalStateException("正在准备奖池，请稍后重试");
        int count=request.action().equals("draw_10")?10:request.action().equals("draw")?1:0;
        boolean draw=count>0,xp=request.action().equals("exchange_xp");
        if(xp&&player.experienceLevel<settings.experienceCost)throw new IllegalArgumentException("需要至少"+settings.experienceCost+"级附魔经验");
        if(!draw&&!xp&&person.tokens.compareTo(BigInteger.valueOf(settings.tokenCost))<0)throw new IllegalArgumentException(LotteryNames.token()+"不足");
        List<Rolled> rolled=new ArrayList<>();String up=featured(party);
        LotterySelector.Pity pity=pity(person);
        if(draw) {
            if(runtime.level(party)<unlockLevel)throw new IllegalArgumentException("需要空岛"+unlockLevel+"级解锁"+LotteryNames.draw());
            if(!LotterySelector.available(candidates,runtime.level(party),settings.policy)||up.isEmpty())throw new IllegalArgumentException("奖池尚未就绪");
            for(int i=0;i<count;i++){
                boolean marked=pity.epicOwed()||pity.withoutLegendary()+1>=settings.policy.legendaryAt();
                var result=LotterySelector.draw(runtime.state().lotterySeed,player.getUUID(),Math.addExact(person.drawSequence,i),runtime.level(party),candidates,up,settings.policy,pity);
                rolled.add(new Rolled(result,templates.get(result.prize().id()).copyWithCount(1),marked));
                pity=result.next();
            }
        }
        ItemStack reward=draw?rolled.getFirst().stack():new ItemStack(ModItems.LOTTERY_TICKET);
        player.stopUsingItem();if(player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty())player.closeContainer();
        JsonArray inventory=runtime.economy.capture(player.getInventory());
        if(draw&&takeTickets(inventory,count)>0)throw new IllegalArgumentException("需要"+count+"个"+LotteryNames.ticket());
        SimpleContainer copy=new SimpleContainer(36);for(int slot=0;slot<36;slot++)copy.setItem(slot,runtime.economy.decode(inventory.get(slot)));
        List<ItemStack> grants=draw?rolled.stream().map(Rolled::stack).toList():List.of(reward);
        for(ItemStack grant:grants)if(!copy.addItem(grant.copy()).isEmpty())throw new IllegalArgumentException("普通背包空间不足，未扣除资源");
        for(int slot=0;slot<36;slot++)inventory.set(slot,runtime.economy.encode(copy.getItem(slot)));
        long sequence=person.drawSequence,operation=Math.addExact(person.lotteryOperationSequence,1);var nextPity=pity;var rules=settings;
        UUID owner=player.getUUID(),partyId=party.id;long island=party.overworldEpoch;String actor=player.getGameProfile().name();
        var receipt=new IslandState.LotteryDrawResult();receipt.sequence=draw?Math.addExact(sequence,count):sequence;receipt.operationSequence=operation;
        receipt.action=request.action();receipt.islandLevel=runtime.level(party);
        receipt.productId=draw?rolled.getFirst().result().prize().id():"yumis_sky_island:lottery_ticket";
        receipt.rarity=draw?LotterySettings.RARITIES.get(rolled.getFirst().result().prize().rarity()):"legendary";
        receipt.rewardStack=runtime.economy.encode(reward);receipt.prizes=new ArrayList<>();
        if(draw)for(Rolled row:rolled){var prize=new IslandState.LotteryPrize();prize.productId=row.result().prize().id();prize.rarity=LotterySettings.RARITIES.get(row.result().prize().rarity());prize.pity=row.pity();prize.rewardStack=runtime.economy.encode(row.stack());receipt.prizes.add(prize);}
        var change=new TradeService.InventoryChange(inventory,xp?TradeService.ExperienceChange.levels(rules.experienceCost):TradeService.ExperienceChange.none(),receipt);
        if(!runtime.trades.commitInventory(player,change,state->{
            var current=state.players.get(owner);var currentParty=state.parties.get(partyId);
            if(current==null||current.lotteryOperationSequence!=request.expectedSequence()||!partyId.equals(current.activeParty)||currentParty==null||currentParty.overworldEpoch!=island||currentParty.resetInProgress)
                throw new IllegalArgumentException("操作上下文已变化");
            current.lotteryOperationSequence=operation;
            if(draw){current.drawSequence=receipt.sequence;current.drawsWithoutLegendary=nextPity.withoutLegendary();current.drawsWithoutEpic=nextPity.withoutEpic();current.epicOwed=nextPity.epicOwed();current.featuredGuaranteed=nextPity.featuredGuaranteed();for(Rolled row:rolled)runtime.progression.acquired(currentParty,BuiltInRegistries.ITEM.getKey(row.stack().getItem()).toString());}
            else if(!xp){current.tokens=current.tokens.subtract(BigInteger.valueOf(rules.tokenCost));if(current.tokens.signum()<0)throw new IllegalArgumentException(LotteryNames.token()+"不足");}
        },()->{
            if(!draw)return;
            for(Rolled row:rolled){String rarity=LotterySettings.RARITIES.get(row.result().prize().rarity());if(rarity.equals("epic")||rarity.equals("legendary"))runtime.server.getPlayerList().broadcastSystemMessage(
                Component.literal("[空岛] "+actor+" 在"+LotteryNames.draw()+"中获得 ").append(row.stack().getHoverName()).append(" ×1"),false);}
        },current->{
            if(draw)for(Rolled row:rolled)SkyServer.tell(current,Component.literal(LotteryNames.draw()+"获得 ").append(row.stack().getHoverName()).append(" ×1"));
            else SkyServer.tell(current,Component.literal("兑换获得 ").append(reward.getHoverName()).append(" ×1"));
        }))throw new IllegalStateException("数据正在保存，请稍后重试");
    }
    private int takeTickets(JsonArray inventory,int needed){
        int left=needed;
        for(int slot=0;slot<inventory.size()&&left>0;slot++){
            if(slot>=36&&slot!=Inventory.SLOT_OFFHAND)continue;
            ItemStack stack=runtime.economy.decode(inventory.get(slot));
            if(!stack.is(ModItems.LOTTERY_TICKET)||stack.isEmpty())continue;
            int take=Math.min(left,stack.getCount());stack.shrink(take);inventory.set(slot,runtime.economy.encode(stack));left-=take;
        }
        return left;
    }
    private record Rolled(LotterySelector.Result result,ItemStack stack,boolean pity) {}
}
