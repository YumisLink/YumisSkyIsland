package cn.yumis.skyisland.sorting;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.core.SortingRules;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.server.*;
import cn.yumis.skyisland.world.VoidWorlds;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class SortingService implements AutoCloseable {
    private record Location(String dimension,long position) {}
    private record Selection(UUID device,UUID party,String dimension,long revision,int expires,boolean source) {}
    private final SkyServer rt;
    public final SortingTransactions transactions;
    private final Map<Location,Set<UUID>> reverse=new HashMap<>();
    private final Map<UUID,Selection> selections=new HashMap<>();
    private final Set<UUID> invalidated=new LinkedHashSet<>();
    private final Map<UUID,String> messages=new HashMap<>();
    private final Map<UUID,Integer> cursors=new HashMap<>(),nextRuns=new HashMap<>();
    private final int interval,limit,budget,timeout;
    private int cursor;
    public SortingService(SkyServer rt)throws IOException{
        this.rt=rt;var config=rt.config.object("gameplay/sorting.toml");
        interval=number(config,"intervalTicks",5,1,1200);limit=number(config,"maxItemsPerTransfer",64,1,64);
        budget=number(config,"devicesPerTick",32,1,256);timeout=number(config,"selectionTimeoutSeconds",120,5,600)*20;
        transactions=new SortingTransactions(rt);rebuild();transactions.bootstrap();
    }
    private static int number(JsonObject config,String key,int fallback,int min,int max){
        int value=config.has(key)?config.get(key).getAsInt():fallback;if(value<min||value>max)throw new IllegalArgumentException("分类器配置 "+key+" 须在 "+min+"～"+max);return value;
    }
    private static String dim(Level world){return world.dimension().identifier().toString();}
    private ServerLevel world(SorterData data){return rt.server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(data.dimension)));}
    public static SorterData copy(SorterData data){return GameConfig.JSON.fromJson(GameConfig.JSON.toJsonTree(data),SorterData.class);}
    private IslandState.Party party(SorterData data){
        var party=rt.state().parties.get(data.party);return party!=null&&!party.resetInProgress&&(!data.dimension.equals("minecraft:overworld")||party.overworldEpoch==data.epoch)?party:null;
    }
    private void rebuild(){
        reverse.clear();for(var data:rt.state().sorters.values()){
            for(long pos:data.source.keySet())reverse.computeIfAbsent(new Location(data.dimension,pos),_ ->new HashSet<>()).add(data.id);
            for(long pos:data.target.keySet())reverse.computeIfAbsent(new Location(data.dimension,pos),_ ->new HashSet<>()).add(data.id);
        }
    }
    public boolean canOperate(ServerPlayer player,BlockPos pos){
        var world=player.level();if(!VoidWorlds.isSky(world)||!world.hasChunkAt(pos)||!(world.getBlockEntity(pos) instanceof SorterEntity machine))return false;
        var claim=rt.claim(pos);if(claim==null||claim.resetInProgress||!claim.members.contains(player.getUUID()))return false;
        var data=rt.state().sorters.get(machine.id);return data==null||data.party.equals(claim.id)&&data.dimension.equals(dim(world))&&data.position==pos.asLong()&&party(data)!=null;
    }
    public void placed(ServerPlayer player,BlockPos pos){
        if(!canOperate(player,pos))return;
        var entity=(SorterEntity)player.level().getBlockEntity(pos);if(rt.state().sorters.containsKey(entity.id))return;
        var claim=rt.claim(pos);SorterData data=new SorterData();data.id=entity.id;data.party=claim.id;data.epoch=claim.overworldEpoch;data.dimension=dim(player.level());data.position=pos.asLong();entity.setChanged();
        // The device may be placed while another durable operation is being published.
        rt.deferred(()->{if(player.level().hasChunkAt(pos)&&player.level().getBlockEntity(pos) instanceof SorterEntity current&&current.id.equals(data.id)&&canOperate(player,pos))
            rt.change(player,state->state.sorters.putIfAbsent(data.id,copy(data)),this::rebuild);});
    }
    public InteractionResult open(ServerPlayer player,BlockPos pos){
        selections.remove(player.getUUID());
        if(!canOperate(player,pos)){SkyServer.tell(player,"分类器仅限本队正式成员操作，保存期间请稍候");return InteractionResult.FAIL;}
        var machine=(SorterEntity)player.level().getBlockEntity(pos);
        if(!rt.state().sorters.containsKey(machine.id)){placed(player,pos);SkyServer.tell(player,"分类器登记中，请稍后重新打开");return InteractionResult.SUCCESS;}
        player.openMenu(machine);send(player,rt.state().sorters.get(machine.id));return InteractionResult.SUCCESS;
    }
    public void operation(ServerPlayer player,SorterPayloads.Operation operation){
        try{
            if(operation.action().equals("cancel")){selections.remove(player.getUUID());return;}
            UUID id=UUID.fromString(operation.device());SorterData data=rt.state().sorters.get(id);
            if(data==null||!(player.containerMenu instanceof SorterMenu menu)||!menu.pos.equals(BlockPos.of(data.position))
                    ||!menu.stillValid(player)||!dim(player.level()).equals(data.dimension))throw new IllegalArgumentException("请在设备旁重新打开分类器");
            if(data.revision!=operation.revision())throw new IllegalArgumentException("分类器设置已变化，请重新操作");
            if(!rt.store.available()||transactions.busy())throw new IllegalArgumentException("数据正在保存，请稍后重试");
            switch(operation.action()){
                case "source","target" -> {
                    selections.put(player.getUUID(),new Selection(id,data.party,data.dimension,data.revision,rt.tick+timeout,operation.action().equals("source")));
                    player.closeContainer();SkyServer.tell(player,"右键本队普通箱子，选择"+(operation.action().equals("source")?"来源A":"目标B")+"；潜行右键或重新打开设备可取消");
                }
                case "pause","resume" -> {SorterData next=copy(data);next.paused=operation.action().equals("pause");save(data,next,player);}
                case "clear" -> {SorterData next=copy(data);next.source.clear();next.target.clear();save(data,next,player);}
                default -> throw new IllegalArgumentException("未知分类器操作");
            }
        }catch(IllegalArgumentException|IllegalStateException error){SkyServer.tell(player,error.getMessage());}
    }
    private void save(SorterData before,SorterData next,ServerPlayer actor){
        next.revision=before.revision+1;UUID id=before.id;long revision=before.revision;
        rt.change(actor,state->{var current=state.sorters.get(id);if(current==null||current.revision!=revision)throw new IllegalArgumentException("分类器已变化，请重试");state.sorters.put(id,copy(next));},()->{invalidated.remove(id);messages.remove(id);rebuild();});
    }
    public InteractionResult select(ServerPlayer player,BlockPos pos){
        Selection selection=selections.get(player.getUUID());if(selection==null)return InteractionResult.PASS;
        SorterData data=rt.state().sorters.get(selection.device);var claim=data==null?null:party(data);var active=rt.parties.active(player.getUUID());
        if(data==null||claim==null||active!=claim||!claim.members.contains(player.getUUID())||data.revision!=selection.revision||!dim(player.level()).equals(selection.dimension)||rt.tick>selection.expires){
            selections.remove(player.getUUID());SkyServer.tell(player,"选箱操作已失效，请重新打开分类器");return InteractionResult.FAIL;
        }
        if(player.isShiftKeyDown()||player.level().getBlockState(pos).is(SortingContent.BLOCK)){selections.remove(player.getUUID());SkyServer.tell(player,"已取消选箱");return player.isShiftKeyDown()?InteractionResult.SUCCESS:InteractionResult.PASS;}
        try{
            if(!rt.store.available()||transactions.busy())throw new SortingChest.Waiting("数据正在保存，请稍后重试");
            if(player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>64)throw new IllegalArgumentException("请靠近箱子后右键");
            var world=(ServerLevel)player.level();var chosen=SortingChest.at(rt,world,claim,pos,true);SorterData next=copy(data);
            if(selection.source)next.source=new LinkedHashMap<>(chosen.identities());else next.target=new LinkedHashMap<>(chosen.identities());
            if(!next.source.isEmpty()&&!next.target.isEmpty()){
                var a=SortingChest.resolve(rt,world,claim,next.source,true);var b=SortingChest.resolve(rt,world,claim,next.target,true);validatePair(a,b);
                next.source=new LinkedHashMap<>(a.identities());next.target=new LinkedHashMap<>(b.identities());
            }
            save(data,next,player);selections.remove(player.getUUID());SkyServer.tell(player,"已选择"+(selection.source?"来源箱A":"目标箱B")+"，两箱绑定完整后自动搬运");
        }catch(SortingChest.Waiting|IllegalArgumentException error){SkyServer.tell(player,error.getMessage());}
        return InteractionResult.SUCCESS;
    }
    private static void validatePair(SortingChest a,SortingChest b){
        if(!Collections.disjoint(a.identities().keySet(),b.identities().keySet()))throw new IllegalArgumentException("来源和目标不能是同一个箱子或大箱的两半");
        if(!SortingRules.inRange(a.x(),a.y(),a.z(),b.x(),b.y(),b.z()))throw new IllegalArgumentException("两箱三维直线距离不能超过100格");
    }
    public void changed(ServerLevel world,BlockPos pos,BlockState old,BlockState next){
        if(old.equals(next))return;
        if(old.is(SortingContent.BLOCK)&&!next.is(SortingContent.BLOCK)){
            for(var data:rt.state().sorters.values())if(data.dimension.equals(dim(world))&&data.position==pos.asLong())invalidated.add(data.id);
        }
        boolean broken=old.is(Blocks.CHEST)&&(!next.is(Blocks.CHEST)||old.getValue(ChestBlock.TYPE)!=ChestType.SINGLE&&next.getValue(ChestBlock.TYPE)==ChestType.SINGLE);
        if(broken){var affected=reverse.get(new Location(dim(world),pos.asLong()));if(affected!=null)invalidated.addAll(affected);}
    }
    public void disconnected(UUID player){selections.remove(player);}
    private void run(SorterData data){
        var claim=party(data);var world=world(data);if(claim==null||world==null)return;
        BlockPos pos=BlockPos.of(data.position);if(!world.hasChunkAt(pos)){messages.put(data.id,"等待设备区块加载");return;}
        if(!(world.getBlockEntity(pos) instanceof SorterEntity machine)||!machine.id.equals(data.id)){invalidated.add(data.id);return;}
        if(data.paused){messages.put(data.id,"已暂停");return;}
        if(Protection.locked(world,pos))return;
        try{
            var source=SortingChest.resolve(rt,world,claim,data.source,true);var target=SortingChest.resolve(rt,world,claim,data.target,true);validatePair(source,target);
            if(!source.identities().equals(data.source)||!target.identities().equals(data.target)){
                SorterData next=copy(data);next.source=new LinkedHashMap<>(source.identities());next.target=new LinkedHashMap<>(target.identities());save(data,next,null);return;
            }
            var beforeSource=source.items();var beforeTarget=target.items();var move=SortingRules.plan(beforeSource,beforeTarget,cursors.getOrDefault(data.id,0),limit);
            if(move==null){messages.put(data.id,beforeTarget.stream().allMatch(ItemStack::isEmpty)?"B箱为空：放入样本后开始匹配":"等待匹配物品或目标空间");return;}
            List<ItemStack> afterSource=new ArrayList<>();for(var stack:beforeSource)afterSource.add(stack.copy());afterSource.get(move.sourceSlot()).shrink(move.count());
            transactions.execute(data,transactions.groups(source,beforeSource,afterSource,target,beforeTarget,move.target()));
            cursors.put(data.id,move.nextCursor());messages.put(data.id,"运行中 · 每"+interval+"tick最多"+limit+"件");
        }catch(SortingChest.Waiting waiting){messages.put(data.id,waiting.getMessage());}
        catch(IllegalArgumentException invalid){messages.put(data.id,invalid.getMessage());invalidated.add(data.id);}
    }
    public void tick(){
        transactions.tick();
        selections.entrySet().removeIf(entry->{var s=entry.getValue();var player=rt.server.getPlayerList().getPlayer(entry.getKey());var data=rt.state().sorters.get(s.device);var active=rt.parties.active(entry.getKey());
            return rt.tick>s.expires||player==null||active==null||!active.id.equals(s.party)||!dim(player.level()).equals(s.dimension)||data==null||data.revision!=s.revision||party(data)==null;});
        if(rt.store.available()&&!transactions.busy()){
            if(!invalidated.isEmpty()){
                var ids=Set.copyOf(invalidated);rt.change(null,state->{for(UUID id:ids){var data=state.sorters.get(id);if(data==null)continue;
                    var world=world(data);BlockPos pos=BlockPos.of(data.position);
                    if(world!=null&&world.hasChunkAt(pos)&&(!(world.getBlockEntity(pos) instanceof SorterEntity be)||!be.id.equals(id)))state.sorters.remove(id);
                    else{data.source.clear();data.target.clear();data.revision++;}
                }},()->{invalidated.removeAll(ids);rebuild();});
            }else{
                var devices=new ArrayList<>(rt.state().sorters.keySet());int visits=Math.min(budget,devices.size());
                for(int i=0;i<visits&&rt.store.available()&&!transactions.busy();i++){
                    UUID id=devices.get(Math.floorMod(cursor++,devices.size()));if(rt.tick<nextRuns.getOrDefault(id,0))continue;
                    nextRuns.put(id,rt.tick+interval);run(rt.state().sorters.get(id));
                }
            }
        }
        if(rt.tick%10==0)for(var player:rt.server.getPlayerList().getPlayers())if(player.containerMenu instanceof SorterMenu menu&&menu.stillValid(player)){
            var be=(SorterEntity)player.level().getBlockEntity(menu.pos);var data=rt.state().sorters.get(be.id);if(data!=null)send(player,data);
        }
    }
    private String boxLabel(Map<Long,String> ids){if(ids.isEmpty())return "未选择";BlockPos p=BlockPos.of(ids.keySet().iterator().next());return p.getX()+", "+p.getY()+", "+p.getZ()+(ids.size()==2?" 大箱":"");}
    private void send(ServerPlayer player,SorterData data){
        JsonObject json=new JsonObject();json.addProperty("position",data.position);json.addProperty("dimension",data.dimension);json.addProperty("device",data.id.toString());json.addProperty("revision",data.revision);json.addProperty("paused",data.paused);json.addProperty("source",boxLabel(data.source));json.addProperty("target",boxLabel(data.target));
        String status=transactions.status();if(status.isEmpty())status=messages.getOrDefault(data.id,"选择A和B后自动搬运；按物品种类匹配");json.addProperty("status",status);
        JsonArray samples=new JsonArray();var claim=party(data);var world=world(data);
        try{if(world!=null&&claim!=null){var b=SortingChest.resolve(rt,world,claim,data.target,false);Set<String> ids=new LinkedHashSet<>();for(var item:b.items())if(!item.isEmpty())ids.add(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());ids.forEach(samples::add);
            if(!data.source.isEmpty()){var a=SortingChest.resolve(rt,world,claim,data.source,false);json.addProperty("distance",Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2)));}
        }}catch(SortingChest.Waiting|IllegalArgumentException ignored){}
        json.add("samples",samples);ServerPlayNetworking.send(player,new SorterPayloads.Snapshot(json.toString()));
    }
    @Override public void close()throws IOException{transactions.close();}
}
