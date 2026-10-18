package cn.yumis.skyisland.sorting;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.prefab.PrefabJournal;
import cn.yumis.skyisland.server.SkyServer;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

/** Forced WAL before either chest changes, with the applied sequence saved inside each native chunk. */
public final class SortingTransactions implements AutoCloseable {
    private static final String JOURNAL="d411c99b-8f3b-45d7-888e-eae296862164";
    private record Chunk(String dimension,long position) {}
    private record Place(String dimension,long position) {}
    private final SkyServer rt;
    private final PrefabJournal journal;
    private final PrefabJournal.Index index;
    private final Map<Chunk,ArrayDeque<Long>> replay=new LinkedHashMap<>();
    private final Set<Place> reserved=new HashSet<>();
    private JsonObject pending;
    private boolean writing,failed,applying;
    public SortingTransactions(SkyServer rt)throws IOException{
        this.rt=rt;journal=new PrefabJournal(rt.server.getWorldPath(LevelResource.ROOT).resolve("yumis_sky_island/sorting_operations"),rt.server::execute);index=journal.index(JOURNAL);
    }
    private static String dim(Level world){return world.dimension().identifier().toString();}
    private ServerLevel world(String id){return rt.server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(id)));}
    public boolean busy(){return writing||pending!=null||failed;}
    public boolean recovering(){return !replay.isEmpty();}
    public boolean locked(Level world,BlockPos pos){return !applying&&(reserved.contains(new Place(dim(world),pos.asLong()))||replay.containsKey(new Chunk(dim(world),ChunkPos.containing(pos).pack())));}
    public boolean locked(Container container){
        if(container instanceof net.minecraft.world.CompoundContainer pair){var access=(cn.yumis.skyisland.mixin.CompoundContainerAccessor)pair;return locked(access.yumis$first())||locked(access.yumis$second());}
        return container instanceof BlockEntity be&&be.getLevel()!=null&&locked(be.getLevel(),be.getBlockPos());
    }
    public void loaded(ServerLevel world,LevelChunk chunk){
        var offsets=index.chunks().get(chunk.getPos().pack());if(offsets==null)return;
        long receipt=chunk.getAttachedOrElse(SortingContent.RECEIPT,0L);ArrayDeque<Long> queue=new ArrayDeque<>();
        try{for(long offset:offsets){long seq=Collections.binarySearch(index.offsets(),offset)+1L;if(seq<=receipt)continue;
            JsonObject frame=journal.at(JOURNAL,offset);if(frame.get("dimension").getAsString().equals(dim(world)))queue.add(offset);
        }}catch(Exception error){replay.put(new Chunk(dim(world),chunk.getPos().pack()),queue);fail(error);return;}
        if(!queue.isEmpty())replay.put(new Chunk(dim(world),chunk.getPos().pack()),queue);
    }
    public void bootstrap(){
        for(var world:rt.server.getAllLevels())for(long key:index.chunks().keySet()){
            ChunkPos pos=ChunkPos.unpack(key);var chunk=world.getChunkSource().getChunkNow(pos.x(),pos.z());if(chunk!=null)loaded(world,chunk);
        }
    }
    public JsonArray groups(SortingChest source,List<net.minecraft.world.item.ItemStack> beforeSource,List<net.minecraft.world.item.ItemStack> afterSource,
                            SortingChest target,List<net.minecraft.world.item.ItemStack> beforeTarget,List<net.minecraft.world.item.ItemStack> afterTarget){
        Map<Long,JsonObject> groups=new LinkedHashMap<>();edits(groups,source,beforeSource,afterSource);edits(groups,target,beforeTarget,afterTarget);
        JsonArray result=new JsonArray();groups.values().forEach(result::add);return result;
    }
    private void edits(Map<Long,JsonObject> groups,SortingChest chest,List<net.minecraft.world.item.ItemStack> before,List<net.minecraft.world.item.ItemStack> after){
        for(int i=0;i<before.size();i++){
            if(net.minecraft.world.item.ItemStack.matches(before.get(i),after.get(i)))continue;
            var be=chest.half(i);long key=ChunkPos.containing(be.getBlockPos()).pack();
            var group=groups.computeIfAbsent(key,k->{var g=new JsonObject();g.addProperty("chunk",k);g.add("slots",new JsonArray());return g;});
            JsonObject edit=new JsonObject();edit.addProperty("position",be.getBlockPos().asLong());edit.addProperty("identity",chest.identities().get(be.getBlockPos().asLong()));edit.addProperty("slot",i%27);
            edit.add("before",rt.economy.encode(before.get(i)));edit.add("after",rt.economy.encode(after.get(i)));group.getAsJsonArray("slots").add(edit);
        }
    }
    public void execute(SorterData data,JsonArray groups){
        if(busy()||!rt.store.available())return;
        JsonObject frame=new JsonObject();frame.addProperty("seq",index.offsets().size()+1L);frame.addProperty("device",data.id.toString());frame.addProperty("party",data.party.toString());frame.addProperty("dimension",data.dimension);frame.addProperty("epoch",data.epoch);frame.add("groups",groups);
        pending=frame;writing=true;reserved.add(new Place(data.dimension,data.position));
        // Reserve complete logical containers, including unchanged sample and empty slots.
        for(long pos:data.source.keySet())reserved.add(new Place(data.dimension,pos));for(long pos:data.target.keySet())reserved.add(new Place(data.dimension,pos));
        journal.append(JOURNAL,frame,()->{
            long offset=journal.lastOffset(JOURNAL);index.offsets().add(offset);
            for(var value:groups){long chunk=value.getAsJsonObject().get("chunk").getAsLong();index.chunks().computeIfAbsent(chunk,_ ->new ArrayList<>()).add(offset);}
            writing=false;applyPending();
        },this::fail);
    }
    private boolean valid(JsonObject frame){
        var party=rt.state().parties.get(UUID.fromString(frame.get("party").getAsString()));
        return party!=null&&(!frame.get("dimension").getAsString().equals("minecraft:overworld")||!party.resetInProgress&&party.overworldEpoch==frame.get("epoch").getAsLong());
    }
    private void applyPending(){
        if(pending==null||writing||failed)return;ServerLevel world=world(pending.get("dimension").getAsString());if(world==null)return;
        try{for(var value:pending.getAsJsonArray("groups")){
            var group=value.getAsJsonObject();long key=group.get("chunk").getAsLong();ChunkPos pos=ChunkPos.unpack(key);
            if(world.hasChunk(pos.x(),pos.z()))apply(world,pending,group);
            else{
                var queue=replay.computeIfAbsent(new Chunk(dim(world),key),_ ->new ArrayDeque<>());
                long offset=index.offsets().get((int)pending.get("seq").getAsLong()-1);if(!queue.contains(offset))queue.add(offset);
            }
        }pending=null;reserved.clear();}
        catch(Exception error){fail(error);}
    }
    private void apply(ServerLevel world,JsonObject frame,JsonObject group){
        ChunkPos cp=ChunkPos.unpack(group.get("chunk").getAsLong());LevelChunk chunk=world.getChunk(cp.x(),cp.z());long seq=frame.get("seq").getAsLong();
        if(chunk.getAttachedOrElse(SortingContent.RECEIPT,0L)>=seq)return;
        if(valid(frame)){
            // Validate every slot before touching this native chunk. A missing first-use UUID is restored only from the forced WAL.
            for(var value:group.getAsJsonArray("slots")){
                JsonObject row=value.getAsJsonObject();BlockPos pos=BlockPos.of(row.get("position").getAsLong());BlockEntity be=world.getBlockEntity(pos);
                if(!world.getBlockState(pos).is(Blocks.CHEST)||!(be instanceof Container box))throw new IllegalStateException("分类器恢复需要原箱子："+pos.toShortString());
                var party=rt.claim(pos);if(party==null||!party.id.toString().equals(frame.get("party").getAsString()))throw new IllegalStateException("分类器恢复领地变化");
                String id=be.getAttached(SortingContent.CHEST_ID);if(id!=null&&!id.equals(row.get("identity").getAsString()))throw new IllegalStateException("分类器恢复箱体身份冲突");
                JsonElement current=rt.economy.encode(box.getItem(row.get("slot").getAsInt()));if(!current.equals(row.get("before"))&&!current.equals(row.get("after")))throw new IllegalStateException("分类器恢复物品冲突："+pos.toShortString());
            }
            applying=true;try{for(var value:group.getAsJsonArray("slots")){
                JsonObject row=value.getAsJsonObject();BlockEntity be=world.getBlockEntity(BlockPos.of(row.get("position").getAsLong()));
                be.setAttached(SortingContent.CHEST_ID,row.get("identity").getAsString());((Container)be).setItem(row.get("slot").getAsInt(),rt.economy.decode(row.get("after")));be.setChanged();
            }}finally{applying=false;}
        }
        chunk.setAttached(SortingContent.RECEIPT,seq);chunk.markUnsaved();
    }
    public void tick(){
        if(failed||writing)return;if(pending!=null){applyPending();return;}
        int budget=32;
        try{for(var entry:List.copyOf(replay.entrySet())){
            var world=world(entry.getKey().dimension);if(world==null)continue;ChunkPos cp=ChunkPos.unpack(entry.getKey().position);if(!world.hasChunk(cp.x(),cp.z()))continue;
            var queue=entry.getValue();while(!queue.isEmpty()&&budget-->0){JsonObject frame=journal.at(JOURNAL,queue.peek());for(var group:frame.getAsJsonArray("groups"))if(group.getAsJsonObject().get("chunk").getAsLong()==entry.getKey().position)apply(world,frame,group.getAsJsonObject());queue.remove();}
            if(queue.isEmpty())replay.remove(entry.getKey());if(budget<=0)break;
        }}catch(Exception error){fail(error);}
    }
    private void fail(Throwable error){failed=true;writing=false;YumisSkyIsland.LOGGER.error("分类器搬运／恢复失败，相关容器保持锁定，请检查存档",error);}
    public String status(){return failed?"保存或恢复失败：容器已锁定，请检查服务器日志":busy()?"正在保存搬运":recovering()?"等待区块恢复":"";}
    @Override public void close()throws IOException{journal.close();}
}
