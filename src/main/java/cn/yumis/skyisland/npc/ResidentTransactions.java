package cn.yumis.skyisland.npc;

import cn.yumis.skyisland.server.SyncImpact;
import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.prefab.PrefabJournal;
import cn.yumis.skyisland.server.SkyServer;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

/** Dedicated forced journal plus per-native-chunk receipts; completed frames remain replayable. */
public final class ResidentTransactions implements AutoCloseable {
    private final SkyServer runtime;
    private final PrefabJournal journal;
    private final Map<UUID,PrefabJournal.Index> indexes=new LinkedHashMap<>();
    private final Map<UUID,Map<Long,Integer>> cursors=new LinkedHashMap<>();
    private final Set<Long> lockedChunks=new HashSet<>();
    private final Set<Long> reserved=new HashSet<>();
    private JsonObject pending;
    private UUID pendingParty;
    private boolean writing,applying,failed;
    public ResidentTransactions(SkyServer runtime)throws IOException{
        this.runtime=runtime;journal=new PrefabJournal(runtime.server.getWorldPath(LevelResource.ROOT).resolve("yumis_sky_island/resident_operations"),runtime.server::execute);
        for(var party:runtime.state().parties.values())index(party.id);
        for(var party:runtime.state().parties.values())for(long key:indexes.get(party.id).chunks().keySet()){ChunkPos pos=ChunkPos.unpack(key);if(runtime.server.overworld().hasChunk(pos.x(),pos.z()))loaded(runtime.server.overworld(),runtime.server.overworld().getChunk(pos.x(),pos.z()));}
    }
    private void index(UUID party)throws IOException{if(!indexes.containsKey(party)){indexes.put(party,journal.index(party.toString()));cursors.put(party,new LinkedHashMap<>());}}
    public boolean busy(){return writing||pending!=null||failed;}
    public boolean pending(UUID resident){return pending!=null&&pending.get("resident").getAsString().equals(resident.toString());}
    public boolean locked(Level level,BlockPos pos){return !applying&&level==runtime.server.overworld()&&(reserved.contains(pos.asLong())||lockedChunks.contains(ChunkPos.containing(pos).pack()));}
    public boolean locked(Container container){if(container instanceof net.minecraft.world.CompoundContainer){var pair=(cn.yumis.skyisland.mixin.CompoundContainerAccessor)container;return locked(pair.yumis$first())||locked(pair.yumis$second());}return container instanceof BlockEntity entity&&locked(entity.getLevel(),entity.getBlockPos());}
    public boolean recovering(){return !lockedChunks.isEmpty();}
    private String receiptKey(IslandState.Party party){return party.id+":"+party.overworldEpoch;}
    public void loaded(ServerLevel world,LevelChunk chunk){
        if(world!=runtime.server.overworld())return;long key=chunk.getPos().pack();
        for(var party:runtime.state().parties.values()){
            var index=indexes.get(party.id);if(index==null||party.resetInProgress)continue;var offsets=index.chunks().get(key);if(offsets==null)continue;
            long receipt=((AttachmentTarget)chunk).getAttachedOrElse(ResidentContent.RECEIPTS,Map.of()).getOrDefault(receiptKey(party),0L);
            long skip=Math.max(receipt,party.settlement.journalBaseSequence);int cursor=0;
            while(cursor<offsets.size()&&sequence(index,offsets.get(cursor))<=skip)cursor++;
            if(cursor<offsets.size()){cursors.get(party.id).put(key,cursor);lockedChunks.add(key);}
        }
    }
    private static long sequence(PrefabJournal.Index index,long offset){return Collections.binarySearch(index.offsets(),offset)+1L;}
    public void execute(IslandState.Party party,IslandState.House house,IslandState.Resident next,ResidentSupply supply,long chefDays){
        if(busy()||!runtime.store.available())throw new IllegalStateException("居民物资事务正在保存");
        try{index(party.id);}catch(IOException error){throw new IllegalStateException("居民生产日志无法打开",error);}
        long seq=indexes.get(party.id).offsets().size()+1L;JsonObject frame=new JsonObject();frame.addProperty("seq",seq);frame.addProperty("party",party.id.toString());frame.addProperty("islandEpoch",party.overworldEpoch);frame.addProperty("resident",next.id.toString());frame.addProperty("round",runtime.epoch());frame.addProperty("chefDays",chefDays);frame.add("next",cn.yumis.skyisland.config.GameConfig.JSON.toJsonTree(next));frame.add("groups",supply.groups());
        reserved.addAll(house.supply);reserved.addAll(house.output);pending=frame;pendingParty=party.id;writing=true;runtime.views.impact(SyncImpact.party(party.id,"npc","npcEffects"));
        journal.append(party.id.toString(),frame,()->{writing=false;try{indexes.put(party.id,journal.index(party.id.toString()));advance(frame,()->applyPending());}catch(Exception error){fail(error);}},this::fail);
    }
    private void advance(JsonObject frame,Runnable after){
        UUID id=UUID.fromString(frame.get("party").getAsString());long seq=frame.get("seq").getAsLong();var party=runtime.state().parties.get(id);
        if(party==null||party.resetInProgress||party.overworldEpoch!=frame.get("islandEpoch").getAsLong()){clearPending();return;}
        if(party.settlement.journalSequence>=seq){after.run();return;}
        runtime.deferred(()->runtime.change(null, SyncImpact.party(id,"npc","npcEffects"),state->{var p=state.parties.get(id);if(p==null||p.resetInProgress||p.overworldEpoch!=frame.get("islandEpoch").getAsLong())return;
            p.settlement.journalSequence=seq;p.settlement.chefDays=Math.max(p.settlement.chefDays,frame.get("chefDays").getAsLong());
            IslandState.Resident current=p.settlement.residents.get(UUID.fromString(frame.get("resident").getAsString()));
            if(current!=null){var next=cn.yumis.skyisland.config.GameConfig.JSON.fromJson(frame.get("next"),IslandState.Resident.class);current.processedRound=next.processedRound;current.suppliedRound=next.suppliedRound;current.producedBatches=next.producedBatches;current.missedDays=next.missedDays;current.status=next.status;current.message=next.message;}
            p.settlement.effectRevision++;
        },after));
    }
    private void clearPending(){if(runtime.views!=null)runtime.views.impact(SyncImpact.party(pendingParty,"npc","npcEffects"));pending=null;pendingParty=null;reserved.clear();writing=false;}
    private void applyPending(){if(pending==null)return;try{var party=runtime.state().parties.get(pendingParty);if(party==null||party.overworldEpoch!=pending.get("islandEpoch").getAsLong()||party.resetInProgress){clearPending();return;}
        for(var value:pending.getAsJsonArray("groups")){long key=value.getAsJsonObject().get("chunk").getAsLong();ChunkPos chunk=ChunkPos.unpack(key);if(!runtime.server.overworld().hasChunk(chunk.x(),chunk.z()))return;}
        apply(party,pending,null);clearPending();runtime.syncAll();
    }catch(Exception error){fail(error);}}
    private void apply(IslandState.Party party,JsonObject frame,Long only){
        if(party.resetInProgress||party.overworldEpoch!=frame.get("islandEpoch").getAsLong())return;
        ServerLevel world=runtime.server.overworld();long seq=frame.get("seq").getAsLong();applying=true;
        try{for(var value:frame.getAsJsonArray("groups")){JsonObject group=value.getAsJsonObject();long key=group.get("chunk").getAsLong();if(only!=null&&key!=only)continue;ChunkPos pos=ChunkPos.unpack(key);if(!world.hasChunk(pos.x(),pos.z()))throw new HousingScanner.Unloaded();LevelChunk chunk=world.getChunk(pos.x(),pos.z());var target=(AttachmentTarget)chunk;Map<String,Long> receipts=target.getAttachedOrElse(ResidentContent.RECEIPTS,Map.of());String receipt=receiptKey(party);if(receipts.getOrDefault(receipt,0L)>=seq)continue;
            for(var edit:group.getAsJsonArray("slots")){var row=edit.getAsJsonObject();BlockPos block=BlockPos.of(row.get("position").getAsLong());if(runtime.claim(block)!=party||!(world.getBlockEntity(block) instanceof Container container))throw new IllegalStateException("居民恢复所需箱子已变化："+block.toShortString());int slot=row.get("slot").getAsInt();JsonElement current=runtime.economy.encode(container.getItem(slot));if(!current.equals(row.get("before"))&&!current.equals(row.get("after")))throw new IllegalStateException("居民恢复格内容冲突："+block.toShortString());}
            for(var edit:group.getAsJsonArray("slots")){var row=edit.getAsJsonObject();Container container=(Container)world.getBlockEntity(BlockPos.of(row.get("position").getAsLong()));container.setItem(row.get("slot").getAsInt(),runtime.economy.decode(row.get("after")));container.setChanged();}
            Map<String,Long> next=new LinkedHashMap<>(receipts);next.put(receipt,seq);target.setAttached(ResidentContent.RECEIPTS,Map.copyOf(next));chunk.markUnsaved();
        }}finally{applying=false;}
    }
    public void tick(){
        if(failed||writing||!runtime.store.available())return;if(pending!=null){applyPending();return;}
        try{
            for(var party:runtime.state().parties.values()){
                index(party.id);var index=indexes.get(party.id);
                if(party.resetInProgress){cursors.get(party.id).clear();continue;}
                if(party.settlement.journalSequence<index.offsets().size()){
                    var frame=journal.at(party.id.toString(),index.offsets().get((int)party.settlement.journalSequence));
                    if(frame.get("islandEpoch").getAsLong()!=party.overworldEpoch){runtime.change(null, SyncImpact.none(),state->state.parties.get(party.id).settlement.journalSequence=frame.get("seq").getAsLong(),()->{});return;}
                    pending=frame;pendingParty=party.id;for(var value:frame.getAsJsonArray("groups")){var group=value.getAsJsonObject();for(var edit:group.getAsJsonArray("slots"))reserved.add(edit.getAsJsonObject().get("position").getAsLong());}
                    advance(frame,this::applyPending);return;
                }
                var replays=cursors.get(party.id);for(var entry:List.copyOf(replays.entrySet())){
                    long key=entry.getKey();ChunkPos chunk=ChunkPos.unpack(key);if(!runtime.server.overworld().hasChunk(chunk.x(),chunk.z()))continue;
                    var offsets=index.chunks().get(key);int cursor=entry.getValue();if(cursor>=offsets.size()){replays.remove(key);rebuildLocks();continue;}
                    var frame=journal.at(party.id.toString(),offsets.get(cursor));apply(party,frame,key);replays.put(key,cursor+1);if(cursor+1>=offsets.size()){replays.remove(key);rebuildLocks();}return;
                }
            }
            rebuildLocks();
        }catch(Exception error){fail(error);}
    }
    private void rebuildLocks(){lockedChunks.clear();for(var map:cursors.values())lockedChunks.addAll(map.keySet());}
    private void fail(Throwable error){failed=true;writing=false;YumisSkyIsland.LOGGER.error("居民物资恢复暂停，容器保持锁定，请检查存档",error);}
    @Override public void close()throws IOException{journal.close();}
}
