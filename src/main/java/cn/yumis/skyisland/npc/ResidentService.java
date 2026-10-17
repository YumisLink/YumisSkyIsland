package cn.yumis.skyisland.npc;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.config.*;
import cn.yumis.skyisland.core.*;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.server.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Settlement authority: rooms, once-per-day residents, production, team effects and appearance. */
public final class ResidentService implements AutoCloseable {
    private record Ref(UUID party,UUID house) {}
    private static final class Draft {UUID party;long epoch;IslandState.House house;boolean editing,bed;String nextBox="supply";}
    public record Effects(int ordinary,Set<String> jobs) {public Effects{jobs=Set.copyOf(jobs);}}
    private final SkyServer runtime;
    public final ResidentSettings rules;
    public final HousingScanner scanner;
    public final ResidentTransactions transactions;
    private final Map<UUID,Draft> drafts=new HashMap<>();
    private final Map<Long,Set<Ref>> index=new HashMap<>();
    private final Set<Ref> dirty=new LinkedHashSet<>();
    private final Set<UUID> dead=new HashSet<>();
    private final Set<Long> registrationLocks=new HashSet<>();
    private final Map<UUID,ResidentEntity> bodies=new HashMap<>();
    private final Map<UUID,BlockPos> lastPositions=new HashMap<>();
    private final Map<UUID,UUID> bodyHomes=new HashMap<>();
    private final Map<UUID,String> effectFingerprints=new HashMap<>();
    private final Map<UUID,Long> effectVersions=new HashMap<>(),lastRequest=new HashMap<>(),skinTimes=new HashMap<>();
    private final Map<UUID,UUID> skinTokens=new HashMap<>();
    private boolean closed;
    public ResidentService(SkyServer runtime)throws IOException{this.runtime=runtime;rules=new ResidentSettings(runtime.config.object("gameplay/residents.toml"));scanner=new HousingScanner(runtime,rules);transactions=new ResidentTransactions(runtime);reindex();for(var party:runtime.state().parties.values())for(var house:party.settlement.houses.values())dirty.add(new Ref(party.id,house.id));}
    private static <T>T copy(T data,Class<T> type){return GameConfig.JSON.fromJson(GameConfig.JSON.toJsonTree(data),type);}
    private static String requestText(JsonObject request,String key){var value=request.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("居民请求缺少有效字段："+key);return value.getAsString();}
    private static long requestLong(JsonObject request,String key){var value=request.get(key);try{if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();return value.getAsBigDecimal().longValueExact();}catch(RuntimeException error){throw new IllegalArgumentException("居民请求数字字段无效："+key,error);}}
    public boolean locked(Level world,BlockPos pos){return world==runtime.server.overworld()&&(registrationLocks.contains(pos.asLong())||transactions.locked(world,pos));}
    public boolean locked(Container container){return transactions.locked(container);}
    public boolean boxAllowed(ServerPlayer player,BlockPos pos){for(Ref ref:index.getOrDefault(pos.asLong(),Set.of())){var party=runtime.state().parties.get(ref.party);var house=party==null?null:party.settlement.houses.get(ref.house);if(house!=null&&(house.supply.contains(pos.asLong())||house.output.contains(pos.asLong())))return member(player,party);}return true;}
    private boolean member(ServerPlayer player,IslandState.Party party){return party!=null&&party.members.contains(player.getUUID())&&runtime.parties.active(player.getUUID())==party;}
    private IslandState.Party available(ServerPlayer player){var party=runtime.parties.requireMember(player);runtime.trades.requireInventoryReady(player);if(party.resetInProgress)throw new IllegalStateException("重开期间暂停居民操作");if(transactions.busy())throw new IllegalStateException("居民物资正在保存，请稍后操作");return party;}
    private IslandState.Party here(ServerPlayer player){var party=available(player);if(player.level()!=runtime.server.overworld()||runtime.claim(player.blockPosition())!=party)throw new IllegalArgumentException("请在本队空岛主世界操作");return party;}
    private Draft draft(ServerPlayer player){var party=here(player);Draft draft=drafts.get(player.getUUID());if(draft==null||!draft.party.equals(party.id)||draft.epoch!=party.overworldEpoch){draft=new Draft();draft.party=party.id;draft.epoch=party.overworldEpoch;draft.house=new IslandState.House();draft.house.id=UUID.randomUUID();draft.house.islandEpoch=party.overworldEpoch;drafts.put(player.getUUID(),draft);}return draft;}
    public boolean editing(ServerPlayer player){Draft draft=drafts.get(player.getUUID());return draft!=null&&draft.editing;}
    public void mark(ServerPlayer player,BlockPos pos,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND)throw new IllegalArgumentException("请用主手登记");Draft draft=draft(player);var party=runtime.state().parties.get(draft.party);if(runtime.claim(pos)!=party||!runtime.server.overworld().hasChunkAt(pos))throw new IllegalArgumentException("设施必须位于本队已加载领地");
        BlockState state=player.level().getBlockState(pos);var house=draft.house;String message;
        if(state.is(Blocks.CHEST)){List<Long> parts=HousingScanner.chestParts(runtime.server.overworld(),pos);if(draft.nextBox.equals("supply")){if(!Collections.disjoint(parts,house.output))throw new IllegalArgumentException("两个角色不可使用同一箱子");house.supply=new ArrayList<>(parts);draft.nextBox="output";message="已登记消耗箱；下一只箱登记为产出箱";}else{if(!Collections.disjoint(parts,house.supply))throw new IllegalArgumentException("请选择另一只箱子作为产出箱");house.output=new ArrayList<>(parts);draft.nextBox="supply";message="已登记产出箱";}house.markers.addAll(parts);}
        else if(state.getBlock() instanceof BedBlock){for(BlockPos part:HousingScanner.bedParts(pos,state))house.markers.add(part.asLong());house.bed=HousingScanner.bedHead(pos,state).asLong();draft.bed=true;message="已登记床";}
        else if(state.getBlock() instanceof DoorBlock){for(BlockPos part:HousingScanner.doorParts(pos,state))house.markers.add(part.asLong());message="已登记整扇门";}
        else if(scanner.station(state)){house.markers.add(pos.asLong());for(var job:rules.jobs.values())if(job.station().equals(HousingScanner.id(state))&&rules.unlocked(job.id(),runtime.level(party),party.settlement.reputation))house.target=job.id();message="已登记工作站，当前招募目标："+ResidentRules.name(house.target);}
        else if(scanner.light(state)){house.markers.add(pos.asLong());message="已登记光源";}
        else throw new IllegalArgumentException("请右键门、床、工作站、认可光源或普通箱子");
        SkyServer.tell(player,message+"；在屋内Shift＋右键空气提交（补登记可在居民面板保存）");runtime.sendState(player);
    }
    public void submit(ServerPlayer player,InteractionHand hand){
        var party=here(player);Draft draft=drafts.get(player.getUUID());if(draft==null||!draft.party.equals(party.id)||draft.epoch!=party.overworldEpoch)throw new IllegalArgumentException("请先逐一右键登记房屋设施");
        if(!draft.bed)throw new IllegalArgumentException("请先登记床");if(!draft.editing&&party.settlement.houses.size()>=rules.capacity(party.settlement.reputation))throw new IllegalArgumentException("已达到当前房屋名额上限");
        IslandState.House house=copy(draft.house,IslandState.House.class);house.seed=player.blockPosition().asLong();house.eligibleRound=runtime.epoch()+1;var result=scanner.scan(party,player.blockPosition());scanner.validateBindings(party,house,result,true);scanner.capture(house,result);house.status="valid";house.message="有效房屋";
        Set<Long> locks=new HashSet<>(result.interior());locks.addAll(result.boundary());registrationLocks.addAll(locks);
        Runnable after=()->{registrationLocks.removeAll(locks);drafts.remove(player.getUUID());reindex();SkyServer.tell(player,draft.editing?"房屋补登记完成":"房屋登记成功，等待下一次04:00入驻抽取");};
        try{if(draft.editing){if(!party.settlement.houses.containsKey(house.id))throw new IllegalArgumentException("原房屋已取消，请使用新许可证登记");runtime.change(player,state->{var p=state.parties.get(party.id);p.settlement.houses.put(house.id,house);p.settlement.effectRevision++;},after);}
            else{int slot=hand==InteractionHand.OFF_HAND?net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND:player.getInventory().getSelectedSlot();if(!player.getInventory().getItem(slot).is(ResidentContent.PERMIT))throw new IllegalArgumentException("请手持居住许可证提交");JsonArray inventory=runtime.economy.capture(player.getInventory());ItemStack permit=runtime.economy.decode(inventory.get(slot));permit.shrink(1);inventory.set(slot,runtime.economy.encode(permit));runtime.trades.commitInventory(player,inventory,state->{var p=state.parties.get(party.id);p.settlement.houses.put(house.id,house);p.settlement.effectRevision++;},after);}
        }catch(RuntimeException error){registrationLocks.removeAll(locks);throw error;}
    }
    private void starter(ServerPlayer player,IslandState.Party party){if(party.settlement.starterClaimed)throw new IllegalArgumentException("本队已领取首次免费许可证");JsonArray inventory=runtime.economy.capture(player.getInventory());int empty=-1;for(int i=0;i<36;i++)if(player.getInventory().getItem(i).isEmpty()){empty=i;break;}if(empty<0)throw new IllegalArgumentException("普通背包格不足");inventory.set(empty,runtime.economy.encode(new ItemStack(ResidentContent.PERMIT)));runtime.trades.commitInventory(player,inventory,state->state.parties.get(party.id).settlement.starterClaimed=true,()->SkyServer.tell(player,"已领取本队首张免费居住许可证"));}
    public void open(ServerPlayer player,UUID resident){var party=runtime.parties.requireMember(player);if(resident!=null&&!party.settlement.residents.containsKey(resident))throw new IllegalArgumentException("只能管理本队居民");if(player.containerMenu!=player.inventoryMenu)player.closeContainer();runtime.sendState(player);JsonObject selected=new JsonObject();selected.addProperty("resident",resident==null?"":resident.toString());runtime.send(player,"resident_open",selected);}
    public void request(ServerPlayer player,String json){
        try{long now=runtime.tick;if(now-lastRequest.getOrDefault(player.getUUID(),-100L)<4)return;lastRequest.put(player.getUUID(),now);JsonObject request=JsonParser.parseString(json).getAsJsonObject();var party=available(player);
            if(!party.id.toString().equals(requestText(request,"party"))||party.overworldEpoch!=requestLong(request,"islandEpoch"))throw new IllegalArgumentException("队伍或岛屿已变化，请重新打开居民面板");
            String action=requestText(request,"action");UUID resident=request.has("resident")&&!requestText(request,"resident").isBlank()?UUID.fromString(requestText(request,"resident")):null;
            if(action.equals("starter")){starter(player,party);return;}
            if(action.equals("draft_cancel")){drafts.remove(player.getUUID());runtime.sendState(player);return;}
            if(action.equals("draft_submit")){submit(player,InteractionHand.MAIN_HAND);return;}
            if(action.equals("draft_target")){var draft=draft(player);String target=requestText(request,"target");if(!rules.unlocked(target,runtime.level(party),party.settlement.reputation)||target.equals("ordinary"))throw new IllegalArgumentException("该目标职业尚未解锁");draft.house.target=target;runtime.sendState(player);return;}
            if(action.equals("draft_box")){var draft=draft(player);draft.nextBox=requestText(request,"role").equals("output")?"output":"supply";runtime.sendState(player);return;}
            if(action.equals("house_edit")){UUID houseId=UUID.fromString(requestText(request,"house"));var house=party.settlement.houses.get(houseId);if(house==null)throw new IllegalArgumentException("房屋已取消");Draft draft=new Draft();draft.party=party.id;draft.epoch=party.overworldEpoch;draft.house=copy(house,IslandState.House.class);draft.editing=true;draft.bed=true;drafts.put(player.getUUID(),draft);SkyServer.tell(player,"已进入补登记：主手右键设施，回屋内居民面板保存；无需再消耗许可证");runtime.sendState(player);return;}
            if(action.equals("commission")){commission(player,party,UUID.fromString(requestText(request,"commission")));return;}
            var data=party.settlement.residents.get(resident);if(data==null||dead.contains(resident))throw new IllegalArgumentException("居民已离开");
            if(action.equals("evict")){UUID id=resident;runtime.change(player,state->{var p=state.parties.get(party.id);p.settlement.residents.remove(id);p.settlement.commissions.values().removeIf(c->id.equals(c.resident)&&!c.completed);p.settlement.effectRevision++;},()->{discard(id);notice(party.id,ResidentRules.name(data.job)+"已迁出");});}
            else if(action.equals("relocate")){UUID houseId=UUID.fromString(requestText(request,"house"));var house=party.settlement.houses.get(houseId);if(house==null||!usable(party,house)||occupied(party,houseId))throw new IllegalArgumentException("目标不是可用空房");UUID id=resident;runtime.change(player,state->{var p=state.parties.get(party.id);var r=p.settlement.residents.get(id);r.house=houseId;r.absentDays=0;r.status="waiting";r.message="已迁居，等待下一次04:00";r.suppliedRound=-1;p.settlement.effectRevision++;},()->{});}
            else if(action.equals("skin")||action.equals("skin_refresh"))skin(player,party,data,request,action.equals("skin_refresh"));
        }catch(IllegalArgumentException|IllegalStateException|ArithmeticException|JsonParseException error){SkyServer.tell(player,error.getMessage()==null?"居民请求无效":error.getMessage());JsonObject message=new JsonObject();message.addProperty("action","residents");message.addProperty("message",error.getMessage()==null?"居民请求无效":error.getMessage());runtime.send(player,"request_error",message);runtime.sendState(player);}
    }
    private void commission(ServerPlayer player,IslandState.Party party,UUID id){var task=party.settlement.commissions.get(id);if(task==null||task.completed||task.round!=runtime.epoch()||!party.settlement.residents.containsKey(task.resident))throw new IllegalArgumentException("居民委托已完成或过期");JsonArray inventory=runtime.economy.capture(player.getInventory());int left=task.count;for(int slot=0;slot<36&&left>0;slot++){ItemStack stack=runtime.economy.decode(inventory.get(slot));if(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(task.item)){int take=Math.min(left,stack.getCount());stack.shrink(take);left-=take;inventory.set(slot,runtime.economy.encode(stack));}}if(left>0)throw new IllegalArgumentException("委托材料不足");runtime.trades.commitInventory(player,inventory,state->{var p=state.parties.get(party.id);var current=p.settlement.commissions.get(id);if(current==null||current.completed||current.round!=runtime.epoch())throw new IllegalArgumentException("委托已变化");current.completed=true;p.settlement.reputation=Math.addExact(p.settlement.reputation,rules.commissionReward);},()->notice(party.id,"居民委托完成，村落声望＋"+rules.commissionReward));}
    private void skin(ServerPlayer player,IslandState.Party party,IslandState.Resident resident,JsonObject request,boolean refresh){
        long now=System.nanoTime()/1_000_000L;if(now-skinTimes.getOrDefault(resident.id,-30000L)<30000)throw new IllegalArgumentException("同一居民的皮肤查询间隔为30秒");String account=refresh?resident.account:requestText(request,"account");if(!account.matches("[A-Za-z0-9_]{3,16}"))throw new IllegalArgumentException("请输入3～16位正版账号名");
        UUID id=resident.id,actor=player.getUUID(),partyId=party.id,token=UUID.randomUUID();long epoch=party.overworldEpoch;skinTimes.put(id,now);skinTokens.put(id,token);
        ResolvableProfile profile=refresh&&resident.profileId!=null?ResolvableProfile.createUnresolved(resident.profileId):ResolvableProfile.createUnresolved(account);SkyServer.tell(player,"正在查询正版账号皮肤…");
        profile.resolveProfile(runtime.server.services().profileResolver()).whenComplete((resolved,error)->runtime.server.execute(()->runtime.deferred(()->{
            var current=runtime.server.getPlayerList().getPlayer(actor);var p=runtime.state().parties.get(partyId);if(closed||current!=player||p==null||p.overworldEpoch!=epoch||p.resetInProgress||!member(player,p)||!p.settlement.residents.containsKey(id)||!token.equals(skinTokens.get(id)))return;
            if(error!=null||resolved==null||resolved.id().equals(new UUID(0,0))){SkyServer.tell(player,"账号查询失败，保留原皮肤");return;}
            JsonElement saved=ResolvableProfile.CODEC.encodeStart(JsonOps.INSTANCE,ResolvableProfile.createResolved(resolved)).getOrThrow();
            runtime.change(player,state->{var r=state.parties.get(partyId).settlement.residents.get(id);if(r!=null){r.account=resolved.name();r.profileId=resolved.id();r.profile=saved;}},()->{var body=bodies.get(id);if(body!=null){var data=runtime.state().parties.get(partyId).settlement.residents.get(id);body.bind(partyId,data);body.profile(ResolvableProfile.createResolved(resolved));}SkyServer.tell(player,"NPC账号与皮肤已更新："+resolved.name());});
        })));
    }
    public void disconnected(ServerPlayer player){drafts.remove(player.getUUID());lastRequest.remove(player.getUUID());}
    public void changed(ServerLevel world,BlockPos pos,BlockState old,BlockState next){
        if(world!=runtime.server.overworld()||old.equals(next))return;
        if(old.getBlock()==next.getBlock()){
            if(old.getBlock() instanceof DoorBlock&&old.getValue(DoorBlock.HALF)==next.getValue(DoorBlock.HALF)&&old.getValue(DoorBlock.FACING)==next.getValue(DoorBlock.FACING)&&old.getValue(DoorBlock.HINGE)==next.getValue(DoorBlock.HINGE))return;
            if(old.getBlock() instanceof BedBlock&&old.getValue(BedBlock.PART)==next.getValue(BedBlock.PART)&&old.getValue(BedBlock.FACING)==next.getValue(BedBlock.FACING))return;
            if(scanner.station(old))return;
        }
        for(Ref ref:index.getOrDefault(pos.asLong(),Set.of()))dirty.add(ref);
    }
    public void loaded(ServerLevel world,LevelChunk chunk){transactions.loaded(world,chunk);if(world==runtime.server.overworld())for(var party:runtime.state().parties.values())for(var house:party.settlement.houses.values())if(house.chunks.contains(chunk.getPos().pack()))dirty.add(new Ref(party.id,house.id));}
    public void unloaded(ServerLevel world,LevelChunk chunk){if(world!=runtime.server.overworld())return;long key=chunk.getPos().pack();for(var party:runtime.state().parties.values())for(var house:party.settlement.houses.values())if(house.chunks.contains(key)){Ref ref=new Ref(party.id,house.id);dirty.add(ref);runtime.deferred(()->runtime.change(null,state->{var p=state.parties.get(party.id);if(p==null||p.overworldEpoch!=house.islandEpoch)return;for(var r:p.settlement.residents.values())if(house.id.equals(r.house)){r.processedRound=runtime.epoch();r.suppliedRound=-1;r.status="paused";r.message="区块卸载，等待下一次04:00";}p.settlement.effectRevision++;},()->{}));}}
    private void reindex(){index.clear();for(var party:runtime.state().parties.values())for(var house:party.settlement.houses.values()){Ref ref=new Ref(party.id,house.id);Set<Long> positions=new HashSet<>(house.interior);positions.addAll(house.boundary);positions.addAll(house.facilities.keySet());for(long pos:positions)index.computeIfAbsent(pos,_ ->new LinkedHashSet<>()).add(ref);}}
    private boolean allLoaded(IslandState.House house){for(long key:house.chunks){var chunk=net.minecraft.world.level.ChunkPos.unpack(key);if(!runtime.server.overworld().hasChunk(chunk.x(),chunk.z()))return false;}return true;}
    private boolean usable(IslandState.Party party,IslandState.House house){return house.islandEpoch==party.overworldEpoch&&house.status.equals("valid")&&!dirty.contains(new Ref(party.id,house.id))&&allLoaded(house)&&house.supply.stream().noneMatch(pos->Protection.locked(runtime.server.overworld(),BlockPos.of(pos)))&&house.output.stream().noneMatch(pos->Protection.locked(runtime.server.overworld(),BlockPos.of(pos)));}
    private boolean occupied(IslandState.Party party,UUID house){return party.settlement.residents.values().stream().anyMatch(r->house.equals(r.house)&&!dead.contains(r.id));}
    private void inspect(Ref ref){
        var party=runtime.state().parties.get(ref.party);var house=party==null?null:party.settlement.houses.get(ref.house);if(house==null||party.resetInProgress){dirty.remove(ref);return;}
        if(!allLoaded(house))return;String reason=null;HousingScanner.Result result=null;boolean removed=false;
        try{for(var entry:house.facilities.entrySet()){BlockPos pos=BlockPos.of(entry.getKey());if(!HousingScanner.id(runtime.server.overworld().getBlockState(pos)).equals(entry.getValue()))throw new IllegalArgumentException("登记设施被拆除或替换");}
            result=scanner.scan(party,BlockPos.of(house.seed));scanner.validateBindings(party,house,result,false);
            if(!house.markers.containsAll(result.facilities().keySet()))reason="新增设施尚未补登记";else if(!result.lit())reason="光源未点亮";
        }catch(HousingScanner.Unloaded unloaded){return;}catch(IllegalArgumentException|IllegalStateException error){removed=true;reason=error.getMessage();}
        dirty.remove(ref);String message=reason;boolean cancel=removed;var scan=result;
        if(!cancel&&Objects.equals(house.status,reason==null?"valid":"paused")&&Objects.equals(house.message,reason==null?"有效房屋":reason)&&house.interior.equals(scan.interior())&&house.boundary.equals(scan.boundary())&&house.air==scan.air())return;
        runtime.change(null,state->{var p=state.parties.get(ref.party);if(p==null||p.overworldEpoch!=house.islandEpoch)return;var current=p.settlement.houses.get(ref.house);if(current==null)return;
            if(cancel){p.settlement.houses.remove(ref.house);for(var resident:p.settlement.residents.values())if(ref.house.equals(resident.house)){resident.house=null;resident.status="homeless";resident.message="房屋取消，等待安置";resident.suppliedRound=-1;}}
            else{current.status=message==null?"valid":"paused";current.message=message==null?"有效房屋":message;current.interior=new LinkedHashSet<>(scan.interior());current.boundary=new LinkedHashSet<>(scan.boundary());current.air=scan.air();current.chunks.clear();for(long key:current.interior)current.chunks.add(net.minecraft.world.level.ChunkPos.containing(BlockPos.of(key)).pack());for(long key:current.boundary)current.chunks.add(net.minecraft.world.level.ChunkPos.containing(BlockPos.of(key)).pack());}
            p.settlement.effectRevision++;
        },()->{reindex();if(cancel)notice(ref.party,"居住房屋已取消："+message);});
    }
    private boolean online(IslandState.Party party){return runtime.server.getPlayerList().getPlayers().stream().anyMatch(player->member(player,party));}
    public void captureOnline(IslandState state,long round){for(var player:runtime.server.getPlayerList().getPlayers()){var person=state.players.get(player.getUUID());var party=person==null?null:state.parties.get(person.activeParty);if(party!=null&&party.members.contains(player.getUUID()))party.settlement.onlineRound=round;}}
    private void beginDay(IslandState.Party party){
        long round=runtime.epoch();boolean online=party.settlement.onlineRound==round&&online(party);Set<UUID> usable=new HashSet<>();if(online)for(var house:party.settlement.houses.values())if(usable(party,house))usable.add(house.id);
        List<String> arrivals=new ArrayList<>();runtime.change(null,state->{var p=state.parties.get(party.id);p.settlement.processedRound=round;p.settlement.commissions.clear();p.settlement.commissionsRound=-1;
            relocate(p,usable);
            for(var iterator=p.settlement.residents.values().iterator();iterator.hasNext();){var r=iterator.next();if(!online){r.processedRound=round;r.status="paused";r.message="全队离线，等待下次04:00";continue;}
                if(r.house==null){r.processedRound=round;r.absentDays++;if(r.absentDays>=rules.graceDays){iterator.remove();arrivals.add(ResidentRules.name(r.job)+"因无房迁出");}continue;}
                if(!usable.contains(r.house)){r.processedRound=round;r.status="paused";r.message="房屋暂停或区块未加载";}
            }
            if(online)for(var house:p.settlement.houses.values())if(usable.contains(house.id)&&house.eligibleRound<=round&&!occupied(p,house.id)&&p.settlement.residents.size()<rules.capacity(p.settlement.reputation)){
                Random random=ResidentRules.random(state.worldSeed,p.id,house.id,round,"arrival");if(random.nextInt(10000)>=rules.arrivalBps)continue;var r=new IslandState.Resident();r.id=UUID.nameUUIDFromBytes((p.id+":"+p.overworldEpoch+":"+house.id+":"+round).getBytes(java.nio.charset.StandardCharsets.UTF_8));r.entity=r.id;r.house=house.id;r.islandEpoch=p.overworldEpoch;r.job=rules.unlocked(house.target,runtime.level(p),p.settlement.reputation)&&random.nextInt(10000)<rules.targetBps?house.target:"ordinary";BlockPos bed=BlockPos.of(house.bed);r.x=bed.getX();r.y=bed.getY()+1;r.z=bed.getZ();p.settlement.residents.put(r.id,r);arrivals.add(ResidentRules.name(r.job)+"入驻了村落");
            }
            p.settlement.effectRevision++;
        },()->{for(String message:arrivals)notice(party.id,message);});
    }
    private void relocate(IslandState.Party party,Set<UUID> available){Set<UUID> occupied=new HashSet<>();for(var resident:party.settlement.residents.values())if(resident.house!=null)occupied.add(resident.house);
        for(var resident:party.settlement.residents.values())if(resident.house==null){var target=party.settlement.houses.values().stream().filter(h->available.contains(h.id)&&!occupied.contains(h.id)).min(Comparator.comparingLong(h->{BlockPos pos=BlockPos.of(h.bed);long x=(long)pos.getX()-resident.x,z=(long)pos.getZ()-resident.z;return x*x+z*z;})).orElse(null);if(target!=null){resident.house=target.id;resident.absentDays=0;resident.status="waiting";resident.message="已自动安置到空房";resident.suppliedRound=-1;occupied.add(target.id);}}
    }
    private void care(IslandState.Party party,IslandState.Resident resident){
        long round=runtime.epoch();var house=party.settlement.houses.get(resident.house);if(house==null||!usable(party,house)||!online(party)){runtime.change(null,state->{var r=state.parties.get(party.id).settlement.residents.get(resident.id);if(r!=null){r.processedRound=round;r.status="paused";r.message="房屋或经营暂停";}},()->{});return;}
        var job=rules.jobs.get(resident.job);if(job==null)return;ResidentSupply supply=new ResidentSupply(runtime,house);IslandState.Resident next=copy(resident,IslandState.Resident.class);next.processedRound=round;
        boolean cookActive=party.settlement.residents.values().stream().anyMatch(r->r.job.equals("cook")&&r.suppliedRound==round&&r.status.equals("active")&&!dead.contains(r.id));
        boolean free=!resident.job.equals("cook")&&cookActive&&party.settlement.chefDays%rules.chefEvery==0;
        if(!supply.supply(job,rules.foods,free)){runtime.change(null,state->{var p=state.parties.get(party.id);var r=p.settlement.residents.get(resident.id);if(r==null)return;r.processedRound=round;r.missedDays++;r.status="hungry";r.message="缺少食物或用品（"+r.missedDays+"/"+rules.graceDays+"日）";if(r.missedDays>=rules.graceDays){p.settlement.residents.remove(r.id);p.settlement.commissions.values().removeIf(c->r.id.equals(c.resident)&&!c.completed);}p.settlement.effectRevision++;},()->{if(!runtime.state().parties.get(party.id).settlement.residents.containsKey(resident.id)){discard(resident.id);notice(party.id,ResidentRules.name(resident.job)+"连续缺供已迁出");}});return;}
        next.suppliedRound=round;next.missedDays=0;next.status="active";next.message="已供养";Random random=ResidentRules.random(runtime.state().worldSeed,party.id,resident.id,round,"output");if(supply.produce(rules.outputs(job,next.producedBatches,random))){next.producedBatches++;next.message="供养与产出完成";}else next.message="已供养，产出箱满，本轮不补发";
        long chefDays=party.settlement.chefDays;if(resident.job.equals("cook")&&!cookActive)chefDays++;
        transactions.execute(party,house,next,supply,chefDays);
    }
    public void tick(){
        if(closed)return;transactions.tick();if(!runtime.store.available()||transactions.busy())return;
        if(runtime.tick%100==0)reindex();
        if(!dirty.isEmpty()){for(Ref ref:List.copyOf(dirty)){var party=runtime.state().parties.get(ref.party);var house=party==null?null:party.settlement.houses.get(ref.house);if(house==null||allLoaded(house)){inspect(ref);return;}}}
        for(var party:runtime.state().parties.values())if(!party.resetInProgress){
            if(party.settlement.processedRound!=runtime.epoch()){beginDay(party);return;}
            Set<UUID> free=new HashSet<>();for(var house:party.settlement.houses.values())if(usable(party,house)&&!occupied(party,house.id))free.add(house.id);
            if(!free.isEmpty()&&party.settlement.residents.values().stream().anyMatch(r->r.house==null)){runtime.change(null,state->relocate(state.parties.get(party.id),free),()->{});return;}
            var waiting=party.settlement.residents.values().stream().filter(r->r.processedRound!=runtime.epoch()&&!dead.contains(r.id)).sorted(Comparator.comparingInt(r->r.job.equals("cook")?0:1)).findFirst();if(waiting.isPresent()){try{care(party,waiting.get());}catch(HousingScanner.Unloaded ignored){}return;}
            if(party.settlement.commissionsRound!=runtime.epoch()){boolean online=online(party);runtime.change(null,state->{var p=state.parties.get(party.id);p.settlement.commissionsRound=runtime.epoch();if(!online)return;List<IslandState.Resident> residents=new ArrayList<>(p.settlement.residents.values());Collections.shuffle(residents,ResidentRules.random(state.worldSeed,p.id,p.id,runtime.epoch(),"commission"));int count=0;for(var resident:residents){if(resident.house==null||count>=rules.commissionLimit)continue;Random random=ResidentRules.random(state.worldSeed,p.id,resident.id,runtime.epoch(),"commission_material");var material=rules.commissions.get(random.nextInt(rules.commissions.size()));var task=new IslandState.ResidentCommission();task.id=UUID.nameUUIDFromBytes((resident.id+":"+runtime.epoch()+":commission").getBytes(java.nio.charset.StandardCharsets.UTF_8));task.resident=resident.id;task.round=runtime.epoch();task.item=material.item();task.count=material.count();p.settlement.commissions.put(task.id,task);count++;}},()->{});return;}
        }
        if(runtime.tick%20==0)reconcile();
    }
    public Effects effects(IslandState.Party party){if(party==null||party.resetInProgress)return new Effects(0,Set.of());int ordinary=0;Set<String> jobs=new HashSet<>();for(var resident:party.settlement.residents.values()){
        var house=party.settlement.houses.get(resident.house);if(dead.contains(resident.id)||resident.suppliedRound!=runtime.epoch()||!resident.status.equals("active")||house==null||!usable(party,house)||transactions.pending(resident.id))continue;
        if(resident.job.equals("ordinary"))ordinary++;else jobs.add(resident.job);
    }return new Effects(ordinary,jobs);}
    public long effectVersion(IslandState.Party party){if(party==null)return 0;Effects effects=effects(party);String fingerprint=party.overworldEpoch+":"+runtime.epoch()+":"+effects.ordinary+":"+new TreeSet<>(effects.jobs);if(!fingerprint.equals(effectFingerprints.get(party.id))){effectFingerprints.put(party.id,fingerprint);long next=Math.addExact(Math.max(effectVersions.getOrDefault(party.id,0L),party.settlement.effectRevision),1);effectVersions.put(party.id,next);party.settlement.effectRevision=next;}return effectVersions.getOrDefault(party.id,party.settlement.effectRevision);}
    public int saleBps(IslandState.Party party,String item){Effects effects=effects(party);return ResidentRules.saleBps(item,effects.ordinary,effects.jobs,rules.ordinarySaleBps,rules.expertSaleBps);}
    public int buyBps(IslandState.Party party,String item){return ResidentRules.discounted(item,effects(party).jobs)?10000-rules.discountBps:10000;}
    public int xpBps(IslandState.Party party,String profession){Set<String> jobs=effects(party).jobs;return 10000+(jobs.contains("librarian")?rules.librarianXpBps:0)+(profession.equals("farmer")&&jobs.contains("gardener")||profession.equals("miner")&&jobs.contains("prospector")?rules.specialXpBps:0);}
    public IslandState.Resident record(ResidentEntity entity){var party=runtime.state().parties.get(entity.partyId);if(party==null||party.resetInProgress||party.overworldEpoch!=entity.islandEpoch||dead.contains(entity.residentId))return null;var data=party.settlement.residents.get(entity.residentId);return data!=null&&data.islandEpoch==entity.islandEpoch&&entity.getUUID().equals(data.entity)?data:null;}
    public boolean allowed(ResidentEntity entity,BlockPos pos){var data=record(entity);if(data==null)return false;var party=runtime.state().parties.get(entity.partyId);var house=party.settlement.houses.get(data.house);if(house==null||runtime.claim(pos)!=party||!runtime.server.overworld().hasChunkAt(pos))return false;BlockPos home=BlockPos.of(house.seed);long dx=(long)pos.getX()-home.getX(),dz=(long)pos.getZ()-home.getZ();return dx*dx+dz*dz<=(long)rules.radius*rules.radius;}
    public void entityLoaded(ResidentEntity entity){var data=record(entity);if(data==null){entity.discard();return;}ResidentEntity prior=bodies.get(data.id);if(prior!=null&&prior!=entity&&!prior.isRemoved()){entity.discard();return;}bodies.put(data.id,entity);entity.bind(entity.partyId,data);if(data.profile!=null&&!data.profile.isJsonNull())try{entity.profile(ResolvableProfile.CODEC.parse(JsonOps.INSTANCE,data.profile).getOrThrow());}catch(RuntimeException error){YumisSkyIsland.LOGGER.warn("居民皮肤缓存无法读取：{}",data.id);}}
    public void entityUnloaded(ResidentEntity entity){if(bodies.get(entity.residentId)==entity)bodies.remove(entity.residentId);bodyHomes.remove(entity.residentId);var data=record(entity);if(data==null)return;BlockPos position=entity.blockPosition().immutable();lastPositions.put(data.id,position);UUID partyId=entity.partyId,id=data.id;long epoch=entity.islandEpoch;runtime.deferred(()->runtime.change(null,state->{var p=state.parties.get(partyId);if(p==null||p.overworldEpoch!=epoch)return;var r=p.settlement.residents.get(id);if(r!=null){r.x=position.getX();r.y=position.getY();r.z=position.getZ();}},()->{}));}
    private void reconcile(){
        for(var body:List.copyOf(bodies.values()))if(body.isRemoved()||record(body)==null){bodies.remove(body.residentId);if(!body.isRemoved())body.discard();}
        for(var body:List.copyOf(bodies.values())){var data=record(body);if(data==null||data.house==null)continue;var party=runtime.state().parties.get(body.partyId);var house=party.settlement.houses.get(data.house);if(house==null||!allLoaded(house))continue;UUID previous=bodyHomes.put(data.id,data.house);if(previous!=null&&!previous.equals(data.house)||!allowed(body,body.blockPosition())){BlockPos home=BlockPos.of(house.seed);body.stopSleeping();body.getNavigation().stop();body.snapTo(home.getX()+0.5,home.getY(),home.getZ()+0.5,0,0);lastPositions.remove(data.id);}}
        for(var party:runtime.state().parties.values())if(!party.resetInProgress)for(var resident:party.settlement.residents.values())if(!dead.contains(resident.id)&&!bodies.containsKey(resident.id)){
            var house=party.settlement.houses.get(resident.house);if(house==null||!allLoaded(house))continue;BlockPos last=lastPositions.getOrDefault(resident.id,new BlockPos(resident.x,resident.y,resident.z));if(!runtime.server.overworld().hasChunkAt(last))continue;
            Entity existing=runtime.server.overworld().getEntity(resident.entity);if(existing instanceof ResidentEntity body){entityLoaded(body);continue;}
            if(!runtime.server.overworld().isPositionEntityTicking(last))continue;
            var body=new ResidentEntity(ResidentContent.TYPE,runtime.server.overworld());body.setUUID(resident.entity);body.bind(party.id,resident);body.snapTo(last.getX()+0.5,last.getY(),last.getZ()+0.5,0,0);if(runtime.server.overworld().addFreshEntity(body))entityLoaded(body);
        }
    }
    public void died(ResidentEntity entity){var data=record(entity);if(data==null||!dead.add(data.id))return;UUID id=data.id,partyId=entity.partyId;runtime.deferred(()->runtime.change(null,state->{var p=state.parties.get(partyId);if(p==null||p.overworldEpoch!=entity.islandEpoch)return;p.settlement.residents.remove(id);p.settlement.commissions.values().removeIf(c->id.equals(c.resident)&&!c.completed);p.settlement.effectRevision++;},()->{bodies.remove(id);lastPositions.remove(id);bodyHomes.remove(id);notice(partyId,ResidentRules.name(data.job)+"死亡，原房屋已空置");}));}
    private void discard(UUID id){var body=bodies.remove(id);lastPositions.remove(id);bodyHomes.remove(id);if(body!=null)body.discard();}
    private void notice(UUID party,String text){JsonObject event=new JsonObject();event.addProperty("text",text);runtime.partyEvent(party,"resident_notice",event);for(var player:runtime.server.getPlayerList().getPlayers()){var p=runtime.state().parties.get(party);if(member(player,p))SkyServer.tell(player,text);}}
    public JsonObject snapshot(ServerPlayer player,IslandState.Party party){
        JsonObject out=new JsonObject();if(party==null)return out;out.addProperty("reputation",party.settlement.reputation);out.addProperty("capacity",rules.capacity(party.settlement.reputation));out.addProperty("starterClaimed",party.settlement.starterClaimed);out.addProperty("effectVersion",effectVersion(party));out.addProperty("busy",transactions.busy()||transactions.recovering());Effects effects=effects(party);out.addProperty("ordinary",effects.ordinary);out.add("experts",GameConfig.JSON.toJsonTree(effects.jobs));out.addProperty("ordinarySaleBps",rules.ordinarySaleBps);out.addProperty("expertSaleBps",rules.expertSaleBps);out.addProperty("rawDiscountBps",rules.discountBps);
        out.addProperty("arrivalBps",rules.arrivalBps);out.addProperty("targetBps",rules.targetBps);out.addProperty("minAir",rules.minAir);out.addProperty("maxAir",rules.maxAir);out.addProperty("commissionLimit",rules.commissionLimit);out.addProperty("commissionReward",rules.commissionReward);out.add("care",GameConfig.JSON.toJsonTree(rules.jobs));
        JsonArray houses=new JsonArray();for(var house:party.settlement.houses.values()){JsonObject row=new JsonObject();row.addProperty("id",house.id.toString());row.addProperty("target",house.target);row.addProperty("air",house.air);row.addProperty("bed",house.bed);row.addProperty("status",dirty.contains(new Ref(party.id,house.id))?"checking":allLoaded(house)?house.status:"unloaded");row.addProperty("message",house.message);row.add("supply",GameConfig.JSON.toJsonTree(house.supply));row.add("output",GameConfig.JSON.toJsonTree(house.output));row.addProperty("occupied",occupied(party,house.id));houses.add(row);}out.add("houses",houses);
        JsonArray residents=new JsonArray();for(var resident:party.settlement.residents.values()){JsonObject row=new JsonObject();row.addProperty("id",resident.id.toString());row.addProperty("job",resident.job);row.addProperty("account",resident.account);row.addProperty("house",resident.house==null?"":resident.house.toString());row.addProperty("status",resident.status);row.addProperty("message",resident.message);row.addProperty("missedDays",resident.missedDays);row.addProperty("batches",resident.producedBatches);row.addProperty("active",resident.suppliedRound==runtime.epoch()&&resident.status.equals("active")&&resident.house!=null&&party.settlement.houses.containsKey(resident.house)&&usable(party,party.settlement.houses.get(resident.house))&&!dead.contains(resident.id));residents.add(row);}out.add("residents",residents);out.add("commissions",GameConfig.JSON.toJsonTree(party.settlement.commissions.values()));
        JsonArray jobs=new JsonArray();for(var job:rules.jobs.values())if(!job.id().equals("ordinary")){JsonObject row=new JsonObject();row.addProperty("id",job.id());row.addProperty("name",ResidentRules.name(job.id()));row.addProperty("station",job.station());row.addProperty("level",job.level());row.addProperty("reputation",job.reputation());row.addProperty("unlocked",rules.unlocked(job.id(),runtime.level(party),party.settlement.reputation));jobs.add(row);}out.add("jobs",jobs);
        Draft draft=drafts.get(player.getUUID());if(draft!=null&&draft.party.equals(party.id)&&draft.epoch==party.overworldEpoch){JsonObject row=new JsonObject();row.addProperty("editing",draft.editing);row.addProperty("target",draft.house.target);row.addProperty("bed",draft.bed);row.addProperty("markers",draft.house.markers.size());row.addProperty("supply",!draft.house.supply.isEmpty());row.addProperty("output",!draft.house.output.isEmpty());row.addProperty("nextBox",draft.nextBox);out.add("draft",row);}return out;
    }
    public static void reset(IslandState.Party party){var old=party.settlement;var next=new IslandState.Settlement();next.starterClaimed=old.starterClaimed;next.effectRevision=old.effectRevision+1;next.journalSequence=old.journalSequence;next.journalBaseSequence=old.journalSequence;party.settlement=next;}
    @Override public void close()throws IOException{closed=true;transactions.close();drafts.clear();bodies.clear();}
}
