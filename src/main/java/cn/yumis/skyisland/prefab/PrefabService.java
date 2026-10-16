package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.config.GameConfig;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.network.PrefabPayloads;
import cn.yumis.skyisland.server.*;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;

/** Server authority for positioning, costs, chunk-safe construction and recovery. */
public final class PrefabService implements AutoCloseable {
    private static AttachmentType<Map<String,Long>> RECEIPTS;
    private record Location(ServerLevel level,BlockPos pos) {}
    private record ChunkLocation(ServerLevel level,long chunk) {}
    private static final Map<Location,String> LOCKS=new HashMap<>();
    private static final Map<ChunkLocation,Set<String>> RECOVERY_LOCKS=new HashMap<>();
    private static final ThreadLocal<String> APPLYING=new ThreadLocal<>();
    public static void register() {
        RECEIPTS=AttachmentRegistry.create(YumisSkyIsland.id("prefab_receipts"),b->b.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING,Codec.LONG)));
        PayloadTypeRegistry.serverboundPlay().register(PrefabPayloads.Request.TYPE,PrefabPayloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PrefabPayloads.Snapshot.TYPE,PrefabPayloads.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PrefabPayloads.Request.TYPE,(p,c)->{var rt=SkyServer.get(c.server());if(rt!=null)rt.prefabs.request(c.player(),p.json());});
    }
    public static boolean locked(Level level,BlockPos pos){if(!(level instanceof ServerLevel world))return false;String id=LOCKS.get(new Location(world,pos));if(id!=null&&!id.equals(APPLYING.get()))return true;return APPLYING.get()==null&&RECOVERY_LOCKS.containsKey(new ChunkLocation(world,ChunkPos.containing(pos).pack()));}
    public static boolean locked(Container container){return container instanceof BlockEntity be&&locked(be.getLevel(),be.getBlockPos());}
    public final PrefabRules rules;
    public final PrefabCatalog catalog;
    private final SkyServer runtime;
    private final PrefabJournal journal;
    private final Path pinned;
    private final Map<String,Job> jobs=new LinkedHashMap<>();
    private final Map<String,BlueprintCannonEntity> cannons=new HashMap<>();
    private final Map<UUID,Preview> previews=new HashMap<>();
    private final Map<UUID,Long> lastRequest=new HashMap<>();
    private final Map<UUID,ArrayDeque<JsonObject>> previewQueue=new HashMap<>();
    private final List<Validation> validations=new ArrayList<>();
    private boolean closed;
    private static final class Preview {
        String id,dimension,token=UUID.randomUUID().toString();PrefabTemplate template;BlockPos origin;int rotation;boolean replace;InteractionHand hand;UUID party;long epoch;ItemStack held;
    }
    private static final class Job {
        String id,cannonId,templateId,revision,dimension,status="running",message="",mode;
        UUID party;long epoch,sequence,nextTick;BlockPos cannon;BlueprintSelection selection;PrefabTemplate template;
        int cursor,airRun,airOffset,entityCursor,finishCursor,fluidStage,settleTicks=200;boolean pending;
        PrefabJournal.Index index;Map<Long,Integer> replay=new LinkedHashMap<>();
        CompletableFuture<Void> write;Runnable ready;
    }
    private static final class Validation {
        ServerPlayer player;BlueprintCannonEntity cannon;PrefabTemplate template;PrefabCatalog.Definition definition;BlueprintSelection selection;String mode;
        int cursor,airRun,airOffset,changes;boolean waiting;
    }
    public PrefabService(SkyServer runtime) throws IOException {
        this.runtime=runtime;rules=PrefabRules.read(runtime.config);catalog=new PrefabCatalog(runtime.config,rules);
        Path root=runtime.server.getWorldPath(LevelResource.ROOT).resolve("yumis_sky_island/prefab_jobs");
        journal=new PrefabJournal(root,runtime.server::execute);pinned=root.resolve("templates");Files.createDirectories(pinned);
        for(var entry:runtime.state().prefabJobs.entrySet())try{loadJob(entry.getKey(),entry.getValue().getAsJsonObject());}
        catch(Exception e){YumisSkyIsland.LOGGER.error("预制施工 {} 恢复暂停：{}",entry.getKey(),e.getMessage());}
        for(var world:runtime.server.getAllLevels())for(var job:jobs.values())if(job.dimension.equals(world.dimension().identifier().toString()))for(long key:job.replay.keySet()){ChunkPos cp=ChunkPos.unpack(key);if(world.hasChunk(cp.x(),cp.z()))recoveryLock(world,key,job.id);}
    }
    private static void recoveryLock(ServerLevel world,long key,String id){RECOVERY_LOCKS.computeIfAbsent(new ChunkLocation(world,key),_ ->new HashSet<>()).add(id);}
    public void loaded(ServerLevel world,net.minecraft.world.level.chunk.LevelChunk chunk){long key=chunk.getPos().pack();for(var job:jobs.values())if(job.dimension.equals(world.dimension().identifier().toString())&&job.replay.containsKey(key)&&validParty(job))recoveryLock(world,key,job.id);}
    public void visit(BlueprintCannonEntity cannon){if(!closed&&!cannon.isRemoved())cannons.put(cannon.cannonId,cannon);}
    public boolean canOperate(ServerPlayer player,BlueprintCannonEntity cannon){
        if(cannon.isRemoved()||cannon.getLevel()!=player.level()||player.level().getBlockEntity(cannon.getBlockPos())!=cannon||!cn.yumis.skyisland.world.VoidWorlds.isSky(player.level()))return false;var party=runtime.parties.active(player.getUUID());var claim=runtime.claim(cannon.getBlockPos());
        return party!=null&&party==claim&&party.members.contains(player.getUUID())&&!party.resetInProgress&&!runtime.inventoryLocks.contains(player.getUUID());
    }
    private boolean validParty(Job job){var p=runtime.state().parties.get(job.party);return p!=null&&(!job.dimension.equals("minecraft:overworld")||p.overworldEpoch==job.epoch);}
    public boolean busy(BlueprintCannonEntity cannon){return locked(cannon.getLevel(),cannon.getBlockPos())||validations.stream().anyMatch(v->v.cannon==cannon)||jobFor(cannon)!=null;}
    private Job jobFor(BlueprintCannonEntity cannon){return jobs.values().stream().filter(j->j.cannonId.equals(cannon.cannonId)&&!Set.of("completed","terminated").contains(j.status)).findFirst().orElse(null);}
    public void beginPreview(ServerPlayer player,BlockPos origin,InteractionHand hand){
        try {
            ItemStack held=player.getItemInHand(hand);String id=PrefabItems.templateId(held);var d=catalog.definitions.get(id);if(!catalog.available(d))throw new IllegalArgumentException("该建筑尚未通过本服26.2验收或文件未安装");
            var party=runtime.parties.requireMember(player);PrefabTemplate template=catalog.load(id);BlueprintSelection saved=BlueprintSelection.read(held);
            Preview preview=new Preview();preview.id=id;preview.dimension=player.level().dimension().identifier().toString();preview.template=template;preview.origin=origin;preview.hand=hand;preview.party=party.id;preview.epoch=party.overworldEpoch;preview.held=held;preview.rotation=d.rotations().stream().min(Integer::compare).orElse(0);
            if(saved!=null&&saved.party().equals(party.id)&&saved.islandEpoch()==party.overworldEpoch&&saved.dimension().equals(player.level().dimension().identifier().toString())&&saved.revision().equals(template.revision)){
                preview.origin=saved.origin();preview.rotation=saved.rotation();preview.replace=saved.replace();
            }
            previews.put(player.getUUID(),preview);sendPreview(player,preview,true);
        }catch(Exception e){SkyServer.tell(player,e.getMessage());}
    }
    public void request(ServerPlayer player,String json){
        try {
            JsonObject request=JsonParser.parseString(json).getAsJsonObject();String action=request.get("action").getAsString();
            long tick=runtime.tick;if(tick-lastRequest.getOrDefault(player.getUUID(),-100L)<2)return;lastRequest.put(player.getUUID(),tick);
            if(action.equals("catalog")){JsonObject out=new JsonObject();out.addProperty("kind","catalog");out.add("templates",catalog.describe());send(player,out);return;}
            if(action.startsWith("preview_")){
                if(action.equals("preview_cancel")){previews.remove(player.getUUID());previewQueue.remove(player.getUUID());return;}
                Preview preview=previews.get(player.getUUID());if(preview==null||player.getItemInHand(preview.hand)!=preview.held||!PrefabItems.templateId(preview.held).equals(preview.id))throw new IllegalStateException("请重新手持图纸选择位置");
                if(!preview.dimension.equals(player.level().dimension().identifier().toString()))throw new IllegalStateException("维度已变化，请重新定位图纸");
                var p=runtime.parties.requireMember(player);if(!p.id.equals(preview.party)||p.overworldEpoch!=preview.epoch)throw new IllegalStateException("队伍或岛屿已变化，请重新定位");
                switch(action){
                    case "preview_move"->{int dx=request.get("dx").getAsInt(),dy=request.get("dy").getAsInt(),dz=request.get("dz").getAsInt();long distance=Math.abs((long)dx)+Math.abs((long)dy)+Math.abs((long)dz);if(distance!=1&&distance!=5||(dx!=0?1:0)+(dy!=0?1:0)+(dz!=0?1:0)!=1)throw new IllegalArgumentException("移动步长须为单方向1或5格");preview.origin=preview.origin.offset(dx,dy,dz);}
                    case "preview_rotate"->{var allowed=catalog.definitions.get(preview.id).rotations();for(int i=1;i<=4;i++){int next=(preview.rotation+i)%4;if(allowed.contains(next)){preview.rotation=next;break;}}}
                    case "preview_replace"->preview.replace=!preview.replace;
                    case "preview_save"->{checkBounds(player.level(),preview.origin,preview.rotation,preview.template,preview.party,catalog.definitions.get(preview.id));
                        BlueprintSelection selection=new BlueprintSelection(preview.id,preview.template.revision,player.level().dimension().identifier().toString(),preview.origin.getX(),preview.origin.getY(),preview.origin.getZ(),preview.rotation,preview.replace,preview.party,preview.epoch);
                        var inventory=runtime.economy.capture(player.getInventory());int slot=preview.hand==InteractionHand.MAIN_HAND?player.getInventory().getSelectedSlot():40;
                        ItemStack copy=runtime.economy.decode(inventory.get(slot));selection.write(copy);inventory.set(slot,runtime.economy.encode(copy));
                        if(!runtime.store.available())throw new IllegalStateException("数据正在保存，请稍后确认");
                        runtime.trades.commitInventory(player,inventory,_->{},()->{previews.remove(player.getUUID());previewQueue.remove(player.getUUID());JsonObject out=new JsonObject();out.addProperty("kind","positioned");send(player,out);SkyServer.tell(player,"图纸位置已保存，请放入本队蓝图大炮施工");});return;}
                    case "preview_cancel"->{previews.remove(player.getUUID());previewQueue.remove(player.getUUID());return;}
                    default->throw new IllegalArgumentException("未知图纸操作");
                }
                sendPreview(player,preview,false);return;
            }
            if(!(player.containerMenu instanceof BlueprintCannonMenu menu)||!menu.pos.equals(pos(request.getAsJsonArray("pos")))||!(player.level().getBlockEntity(menu.pos) instanceof BlueprintCannonEntity cannon)||!canOperate(player,cannon)||!cannon.stillValid(player))throw new IllegalStateException("请在本队蓝图大炮界面操作");
            if(locked(cannon.getLevel(),cannon.getBlockPos()))throw new IllegalStateException("大炮正在保存操作，请稍后");
            switch(action){
                case "start_material","start_paid"->validate(player,cannon,action.equals("start_paid")?"paid":"materials");
                case "pause"->{Job j=jobFor(cannon);if(j!=null)control(j,"paused","玩家暂停");}
                case "resume"->{Job j=jobFor(cannon);if(j!=null){if(!validParty(j))throw new IllegalStateException("施工所属岛屿已变化");control(j,"running","");}}
                case "terminate"->{Job j=jobFor(cannon);if(j!=null)control(j,"terminated","施工已终止，图纸及成品费不退");}
                case "refresh"->{}
                default->throw new IllegalArgumentException("未知大炮操作");
            }
            sendCannon(player,cannon);
        }catch(Exception e){SkyServer.tell(player,e.getMessage()==null?"预制建筑操作失败":e.getMessage());}
    }
    private void checkBounds(ServerLevel world,BlockPos origin,int rotation,PrefabTemplate template,UUID party,PrefabCatalog.Definition definition){
        if(!cn.yumis.skyisland.world.VoidWorlds.isSky(world))throw new IllegalArgumentException("预制施工仅用于本服空岛维度");
        if(!definition.rotations().contains(rotation))throw new IllegalArgumentException("该机器不支持此旋转方向");
        if(!definition.dimension().isBlank()&&!world.dimension().identifier().toString().equals(definition.dimension()))throw new IllegalArgumentException("该建筑要求维度："+definition.dimension());
        if(Math.floorMod(origin.getX(),definition.alignX())!=0||Math.floorMod(origin.getZ(),definition.alignZ())!=0)throw new IllegalArgumentException("需要区块对齐：X为"+definition.alignX()+"的倍数，Z为"+definition.alignZ()+"的倍数");
        if(world.isOutsideBuildHeight(origin)||world.isOutsideBuildHeight(origin.above(template.height-1)))throw new IllegalArgumentException("建筑超出世界高度");
        for(int x:new int[]{0,template.width-1})for(int z:new int[]{0,template.depth-1}){
            BlockPos corner=origin.offset(template.rotate(new BlockPos(x,0,z),rotation));var claim=runtime.claim(corner);
            if(claim==null||!claim.id.equals(party)||claim.resetInProgress||!world.getWorldBorder().isWithinBounds(corner))throw new IllegalArgumentException("整座建筑必须位于本队领地和世界边界内");
        }
        if(!definition.biome().isBlank()&&(!world.hasChunkAt(origin)||!world.getBiome(origin).is(Identifier.parse(definition.biome()))))throw new IllegalArgumentException("该建筑要求生物群系："+definition.biome());
    }
    private void sendPreview(ServerPlayer player,Preview preview,boolean open){
        var d=catalog.definitions.get(preview.id);JsonObject out=new JsonObject();out.addProperty("kind","preview");out.addProperty("open",open);out.addProperty("token",preview.token);out.addProperty("dimension",preview.dimension);out.addProperty("name",d.name());out.add("origin",xyz(preview.origin));out.addProperty("rotation",preview.rotation);out.addProperty("rotationCount",d.rotations().size());out.addProperty("replace",preview.replace);out.addProperty("notes",d.notes());out.addProperty("count",preview.template.cells.size());
        out.addProperty("width",preview.rotation%2==0?preview.template.width:preview.template.depth);out.addProperty("height",preview.template.height);out.addProperty("depth",preview.rotation%2==0?preview.template.depth:preview.template.width);out.addProperty("sourceWidth",preview.template.width);out.addProperty("sourceDepth",preview.template.depth);
        try{checkBounds(player.level(),preview.origin,preview.rotation,preview.template,preview.party,d);out.addProperty("error","");}catch(Exception e){out.addProperty("error",e.getMessage());}
        send(player,out);if(!open)return;ArrayDeque<JsonObject> queue=new ArrayDeque<>();JsonArray batch=new JsonArray();int sequence=0;
        for(var cell:preview.template.cells){JsonArray row=xyz(cell.pos());row.add(Block.getId(cell.state()));batch.add(row);
            if(batch.size()==rules.previewBatch()){queue.add(batch(preview.token,sequence++,batch));batch=new JsonArray();}}
        if(!batch.isEmpty())queue.add(batch(preview.token,sequence,batch));previewQueue.put(player.getUUID(),queue);
    }
    private static JsonObject batch(String token,int seq,JsonArray rows){JsonObject out=new JsonObject();out.addProperty("kind","preview_blocks");out.addProperty("token",token);out.addProperty("sequence",seq);out.add("blocks",rows);return out;}
    private void validate(ServerPlayer player,BlueprintCannonEntity cannon,String mode)throws IOException {
        if(busy(cannon))throw new IllegalStateException("大炮已有施工或正在检查");ItemStack blueprint=cannon.getItem(0);String id=PrefabItems.templateId(blueprint);BlueprintSelection selection=BlueprintSelection.read(blueprint);var definition=catalog.definitions.get(id);
        if(selection==null)throw new IllegalArgumentException("先手持图纸定位，再放进大炮");if(!catalog.available(definition))throw new IllegalArgumentException("建筑未通过本服验收");
        PrefabTemplate template=catalog.load(id);var party=runtime.parties.requireMember(player);
        if(!selection.template().equals(id)||!selection.revision().equals(template.revision)||!selection.party().equals(party.id)||selection.islandEpoch()!=party.overworldEpoch||!selection.dimension().equals(player.level().dimension().identifier().toString()))throw new IllegalArgumentException("图纸版本、队伍、岛屿或维度已变化，请重新定位");
        if(runtime.level(party)<definition.level())throw new IllegalArgumentException("需要空岛"+definition.level()+"级");
        checkBounds(player.level(),selection.origin(),selection.rotation(),template,party.id,definition);
        if(cannon.fuel<1&&PrefabItems.ammunition(cannon.getItem(1),rules.ammunitionCredits())<1)throw new IllegalStateException("请先装入特殊弹药");
        if(mode.equals("paid")&&party.coins<definition.buildFee())throw new IllegalArgumentException("成品费用不足："+cn.yumis.skyisland.core.Money.format(definition.buildFee()));
        Validation v=new Validation();v.player=player;v.cannon=cannon;v.template=template;v.selection=selection;v.definition=definition;v.mode=mode;validations.add(v);cannon.message="正在检查完整施工范围……";
    }
    private boolean blocked(ServerLevel world,BlockPos pos,BlockState target,boolean replace,BlueprintCannonEntity cannon){
        if(!world.hasChunkAt(pos))return true;if(cannon!=null&&pos.equals(cannon.getBlockPos()))return true;
        BlockState current=world.getBlockState(pos);if(current.hasBlockEntity())return true;
        if(current==target)return false;
        return current.getDestroySpeed(world,pos)<0||!replace&&!current.isAir();
    }
    private void inspect(Validation v,BlockPos local,BlockState target){
        ServerLevel world=(ServerLevel)v.cannon.getLevel();BlockPos p=v.selection.origin().offset(v.template.rotate(local,v.selection.rotation()));
        if(!world.hasChunkAt(p))throw new IllegalStateException("目标区块未加载，请靠近完整投影范围后重试");
        if(runtime.claim(p)!=runtime.claim(v.cannon.getBlockPos())||!world.getWorldBorder().isWithinBounds(p)||locked(world,p))throw new IllegalStateException("施工范围存在越界、锁定或重叠任务");
        if(blocked(world,p,target,v.selection.replace(),v.cannon))throw new IllegalStateException("施工冲突："+p.toShortString()+"；既有容器和大炮不可覆盖");
        if(target!=world.getBlockState(p))v.changes++;
    }
    public void tick(){
        if(closed)return;
        if(runtime.tick%100==0)cannons.values().removeIf(c->c.isRemoved()||c.getLevel()==null||!c.getLevel().hasChunkAt(c.getBlockPos()));
        for(var iterator=validations.iterator();iterator.hasNext();){Validation v=iterator.next();boolean removed=false;
            try {
                if(v.player.hasDisconnected()||!canOperate(v.player,v.cannon)||!Objects.equals(BlueprintSelection.read(v.cannon.getItem(0)),v.selection))throw new IllegalStateException("检查已取消：玩家、图纸或队伍变化");
                int remaining=rules.validationBudget();
                while(remaining-->0){
                    if(v.cursor<v.template.cells.size()){var cell=v.template.cells.get(v.cursor++);inspect(v,cell.pos(),cell.state().rotate(PrefabTemplate.rotation(v.selection.rotation())));}
                    else if(v.airRun<v.template.air.size()){
                        var run=v.template.air.get(v.airRun);inspect(v,new BlockPos(run.x()+v.airOffset,run.y(),run.z()),Blocks.AIR.defaultBlockState());if(++v.airOffset>=run.length()){v.airOffset=0;v.airRun++;}
                    }else{if(!runtime.store.available()){v.cannon.message="范围检查完成，等待当前保存后开工……";break;}iterator.remove();removed=true;if(v.changes==0)throw new IllegalStateException("投影位置没有需要施工的方块");start(v);break;}
                }
            }catch(Exception e){if(!removed)iterator.remove();v.cannon.message=e.getMessage();SkyServer.tell(v.player,e.getMessage());}
        }
        for(Job job:List.copyOf(jobs.values())){
            if(job.ready!=null&&runtime.tick>=job.nextTick)job.ready.run();
            if(job.pending)continue;ServerLevel world=runtime.server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(job.dimension)));if(world==null)continue;
            if(!validParty(job)){job.status="terminated";job.message="所属岛屿已重开或队伍已变化";unlock(job);RECOVERY_LOCKS.values().forEach(ids->ids.remove(job.id));RECOVERY_LOCKS.values().removeIf(Set::isEmpty);continue;}
            if(runtime.state().parties.get(job.party).resetInProgress)continue;
            try {
                if(!recover(job,world))continue;BlueprintCannonEntity cannon=cannons.get(job.cannonId);if(cannon==null||cannon.isRemoved()||!world.hasChunkAt(cannon.getBlockPos()))continue;
                if(!job.status.equals("running")){cannon.message=job.message;continue;}
                if(job.fluidStage==1){if(!primeFluids(job,world))throw new IllegalStateException("等待加载流体区域后继续装配");if(--job.settleTicks<=0){JsonObject ready=frame(job);ready.addProperty("fluidStage",2);addCannon(ready,cannon,cannon.fuel);write(job,ready,world,()->{});}continue;}
                if(runtime.tick<job.nextTick-1)continue;
                step(job,cannon);
            }catch(Exception e){job.nextTick=runtime.tick+10;job.message=e.getMessage()==null?"施工异常，请查看服务端日志":e.getMessage();var cannon=cannons.get(job.cannonId);if(cannon!=null)cannon.message=job.message;YumisSkyIsland.LOGGER.debug("蓝图大炮 {} 暂停：{}",job.id,job.message);}
        }
        for(ServerPlayer player:runtime.server.getPlayerList().getPlayers()){
            ArrayDeque<JsonObject> queue=previewQueue.get(player.getUUID());if(queue!=null){for(int i=0;i<2&&!queue.isEmpty();i++)send(player,queue.remove());if(queue.isEmpty())previewQueue.remove(player.getUUID());}
            if(runtime.tick%10==0&&player.containerMenu instanceof BlueprintCannonMenu menu&&player.level().getBlockEntity(menu.pos) instanceof BlueprintCannonEntity cannon&&canOperate(player,cannon))sendCannon(player,cannon);
        }
    }
    private void start(Validation v)throws IOException {
        var party=runtime.parties.active(v.player.getUUID());if(party==null||!runtime.store.available())throw new IllegalStateException("数据正在保存，请重新确认开工");
        if(!v.selection.party().equals(party.id)||party.resetInProgress||party.overworldEpoch!=v.selection.islandEpoch())throw new IllegalStateException("岛屿已经变化");
        for(Job other:jobs.values())if(other.dimension.equals(v.selection.dimension())&&!Set.of("terminated","completed").contains(other.status)){
            var a=box(v.selection,v.template);var b=box(other.selection,other.template);if(a.intersects(b))throw new IllegalStateException("与另一座大炮的施工范围重叠");
        }
        Job job=new Job();job.id=UUID.randomUUID().toString();job.cannonId=v.cannon.cannonId;job.cannon=v.cannon.getBlockPos();job.party=party.id;job.epoch=party.overworldEpoch;job.dimension=v.selection.dimension();job.templateId=v.definition.id();job.revision=v.template.revision;job.selection=v.selection;job.template=v.template;job.mode=v.mode;
        long fee=v.mode.equals("paid")?v.definition.buildFee():0;if(party.coins<fee)throw new IllegalStateException("队伍余额不足");
        Path target=pinned.resolve(job.revision+".yprefab");catalog.pin(v.definition,v.template,target);
        JsonObject header=new JsonObject();header.addProperty("id",job.id);header.addProperty("cannonId",job.cannonId);header.add("cannon",xyz(job.cannon));header.addProperty("party",job.party.toString());header.addProperty("epoch",job.epoch);header.addProperty("dimension",job.dimension);header.addProperty("template",job.templateId);header.addProperty("revision",job.revision);header.add("selection",GameConfig.JSON.toJsonTree(job.selection));header.addProperty("mode",job.mode);header.addProperty("fee",fee);
        JsonObject first=frame(job);first.add("header",header);JsonObject group=group(first,ChunkPos.containing(job.cannon).pack());JsonArray edits=new JsonArray();JsonObject blueprint=new JsonObject();blueprint.add("pos",xyz(job.cannon));blueprint.addProperty("slot",0);blueprint.add("before",runtime.economy.encode(v.cannon.getItem(0)));blueprint.add("after",JsonNull.INSTANCE);edits.add(blueprint);group.add("slots",edits);
        lock(job,first,(ServerLevel)v.cannon.getLevel());job.pending=true;v.cannon.message="正在保存图纸与施工费用……";
        job.write=journal.append(job.id,first,()->{
            if(closed){unlock(job);job.pending=false;return;}
            runtime.deferred(()->{
            var current=runtime.state().parties.get(job.party);
            if(closed||v.cannon.isRemoved()||current==null||current.resetInProgress||current.overworldEpoch!=job.epoch||current.coins<fee){unlock(job);job.pending=false;v.cannon.message="开工取消：大炮、岛屿或余额已变化，未扣费用";return;}
            runtime.change(v.player,state->{var p=state.parties.get(job.party);if(p==null||p.resetInProgress||p.overworldEpoch!=job.epoch)throw new IllegalStateException("岛屿已变化");p.coins=cn.yumis.skyisland.core.Money.subtract(p.coins,fee);state.prefabJobs.put(job.id,header.deepCopy());},()->{
                try{job.index=journal.index(job.id);jobs.put(job.id,job);applyProgress(job,first);applyFrame(job,first,(ServerLevel)v.cannon.getLevel(),null);job.pending=false;unlock(job);v.cannon.message="施工中";sendCannon(v.player,v.cannon);}
                catch(Exception e){for(var value:first.getAsJsonArray("groups"))job.replay.put(value.getAsJsonObject().get("chunk").getAsLong(),0);job.pending=false;v.cannon.message="恢复暂停："+e.getMessage();YumisSkyIsland.LOGGER.error("预制开工恢复失败",e);}
            });
            });
        },error->{job.pending=false;unlock(job);v.cannon.message="保存失败，未开放施工";YumisSkyIsland.LOGGER.error("预制开工写入失败",error);});
    }
    private static net.minecraft.world.phys.AABB box(BlueprintSelection selection,PrefabTemplate template){int w=selection.rotation()%2==0?template.width:template.depth,d=selection.rotation()%2==0?template.depth:template.width;return new net.minecraft.world.phys.AABB(selection.x(),selection.y(),selection.z(),(double)selection.x()+w,(double)selection.y()+template.height,(double)selection.z()+d);}
    private void loadJob(String id,JsonObject header)throws IOException {
        Job job=new Job();job.id=id;job.cannonId=header.get("cannonId").getAsString();job.cannon=pos(header.getAsJsonArray("cannon"));job.party=UUID.fromString(header.get("party").getAsString());job.epoch=header.get("epoch").getAsLong();job.dimension=header.get("dimension").getAsString();job.templateId=header.get("template").getAsString();job.revision=header.get("revision").getAsString();job.selection=GameConfig.JSON.fromJson(header.get("selection"),BlueprintSelection.class);job.mode=header.get("mode").getAsString();
        job.template=catalog.pinned(pinned.resolve(job.revision+".yprefab"),job.revision);job.index=journal.index(id);if(job.index.last()==null)throw new IOException("缺少已付款任务的施工日志");
        applyProgress(job,job.index.last());for(long chunk:job.index.chunks().keySet())job.replay.put(chunk,0);jobs.put(id,job);
    }
    private boolean recover(Job job,ServerLevel world)throws IOException {
        int budget=rules.validationBudget();boolean waiting=false;
        for(var it=job.replay.entrySet().iterator();it.hasNext()&&budget>0;){var entry=it.next();ChunkPos cp=ChunkPos.unpack(entry.getKey());if(!world.hasChunk(cp.x(),cp.z())){waiting=true;continue;}
            List<Long> offsets=job.index.chunks().get(entry.getKey());int cursor=entry.getValue();
            long receipt=((AttachmentTarget)world.getChunk(cp.x(),cp.z())).getAttachedOrElse(RECEIPTS,Map.of()).getOrDefault(job.id,0L);
            if(receipt>0&&!job.index.offsets().isEmpty()){
                long appliedOffset=job.index.offsets().get((int)Math.min(receipt,job.index.offsets().size())-1);int found=Collections.binarySearch(offsets,appliedOffset);int skip=found>=0?found+1:-found-1;cursor=Math.max(cursor,skip);
            }
            while(cursor<offsets.size()&&budget-->0){JsonObject frame=journal.at(job.id,offsets.get(cursor++));applyFrame(job,frame,world,entry.getKey());}
            if(cursor==offsets.size()){it.remove();var location=new ChunkLocation(world,entry.getKey());var ids=RECOVERY_LOCKS.get(location);if(ids!=null){ids.remove(job.id);if(ids.isEmpty())RECOVERY_LOCKS.remove(location);}}else entry.setValue(cursor);
        }
        if(job.replay.isEmpty()&&!job.pending)unlock(job);
        // Historical, unloaded chunks recover when loaded. They need not pin or halt the current target.
        return job.replay.keySet().stream().noneMatch(key->{ChunkPos cp=ChunkPos.unpack(key);return world.hasChunk(cp.x(),cp.z());});
    }
    private static JsonObject frame(Job job){JsonObject frame=new JsonObject();frame.addProperty("seq",job.sequence+1);frame.addProperty("cursor",job.cursor);frame.addProperty("airRun",job.airRun);frame.addProperty("airOffset",job.airOffset);frame.addProperty("entities",job.entityCursor);frame.addProperty("finish",job.finishCursor);frame.addProperty("fluidStage",job.fluidStage);frame.addProperty("status",job.status);frame.addProperty("message",job.message);frame.add("groups",new JsonArray());return frame;}
    private static JsonObject group(JsonObject frame,long chunk){for(var e:frame.getAsJsonArray("groups"))if(e.getAsJsonObject().get("chunk").getAsLong()==chunk)return e.getAsJsonObject();JsonObject group=new JsonObject();group.addProperty("chunk",chunk);group.add("slots",new JsonArray());group.add("blocks",new JsonArray());frame.getAsJsonArray("groups").add(group);return group;}
    private static void applyProgress(Job job,JsonObject frame){job.sequence=frame.get("seq").getAsLong();job.cursor=frame.get("cursor").getAsInt();job.airRun=frame.get("airRun").getAsInt();job.airOffset=frame.get("airOffset").getAsInt();job.entityCursor=frame.get("entities").getAsInt();job.finishCursor=frame.get("finish").getAsInt();job.fluidStage=frame.has("fluidStage")?frame.get("fluidStage").getAsInt():0;job.status=frame.get("status").getAsString();job.message=frame.get("message").getAsString();}
    private static void lock(Job job,JsonObject frame,ServerLevel world){LOCKS.put(new Location(world,job.cannon),job.id);for(var value:frame.getAsJsonArray("groups")){var group=value.getAsJsonObject();for(var slot:group.getAsJsonArray("slots"))LOCKS.put(new Location(world,pos(slot.getAsJsonObject().getAsJsonArray("pos"))),job.id);for(var block:group.getAsJsonArray("blocks"))LOCKS.put(new Location(world,pos(block.getAsJsonObject().getAsJsonArray("pos"))),job.id);}}
    private static void unlock(Job job){LOCKS.entrySet().removeIf(e->e.getValue().equals(job.id));}
    private void write(Job job,JsonObject frame,ServerLevel world,Runnable after){
        if(job.pending)throw new IllegalStateException("上一施工步骤尚未保存");lock(job,frame,world);job.pending=true;
        job.write=journal.append(job.id,frame,()->{
            Runnable publish=()->{
            job.ready=null;
            try {
                long offset=journal.lastOffset(job.id);job.index.offsets().add(offset);for(var value:frame.getAsJsonArray("groups")){long key=value.getAsJsonObject().get("chunk").getAsLong();job.index.chunks().computeIfAbsent(key,_ ->new ArrayList<>()).add(offset);}
                applyProgress(job,frame);applyFrame(job,frame,world,null);job.pending=false;job.nextTick=runtime.tick+rules.interval();unlock(job);after.run();
            }catch(Exception e){for(var value:frame.getAsJsonArray("groups"))job.replay.put(value.getAsJsonObject().get("chunk").getAsLong(),0);job.pending=false;job.message="等待恢复："+e.getMessage();YumisSkyIsland.LOGGER.error("预制施工步骤恢复失败：{}",job.id,e);}
            };
            boolean placement=frame.getAsJsonArray("groups").asList().stream().anyMatch(value->!value.getAsJsonObject().getAsJsonArray("blocks").isEmpty());
            if(placement&&runtime.tick<job.nextTick)job.ready=publish;else publish.run();
        },error->{job.pending=false;unlock(job);job.status="paused";job.message="施工日志写入失败，请联系管理员";YumisSkyIsland.LOGGER.error("施工持久化失败",error);});
    }
    private void applyFrame(Job job,JsonObject frame,ServerLevel world,Long onlyChunk){
        if(!validParty(job))throw new IllegalStateException("岛屿世代已变化，拒绝恢复旧施工");long sequence=frame.get("seq").getAsLong();APPLYING.set(job.id);
        try {for(var value:frame.getAsJsonArray("groups")){
            JsonObject group=value.getAsJsonObject();long key=group.get("chunk").getAsLong();if(onlyChunk!=null&&key!=onlyChunk)continue;
            ChunkPos cp=ChunkPos.unpack(key);if(!world.hasChunk(cp.x(),cp.z()))throw new IllegalStateException("施工目标或供料区块已卸载");
            var chunk=world.getChunk(cp.x(),cp.z());var attached=(AttachmentTarget)chunk;
            Map<String,Long> receipts=attached.getAttachedOrElse(RECEIPTS,Map.of());if(receipts.getOrDefault(job.id,0L)>=sequence)continue;
            // Validate all slots before applying any mutation in this chunk.
            for(var edit:group.getAsJsonArray("slots")){
                JsonObject e=edit.getAsJsonObject();BlockPos p=pos(e.getAsJsonArray("pos"));if(!(world.getBlockEntity(p) instanceof Container inventory))throw new IllegalStateException("恢复所需供料容器已变化："+p.toShortString());
                if(runtime.claim(p)==null||!runtime.claim(p).id.equals(job.party))throw new IllegalStateException("供料容器领地已变化");
                int slot=e.get("slot").getAsInt();JsonElement current=runtime.economy.encode(inventory.getItem(slot));if(!current.equals(e.get("before"))&&!current.equals(e.get("after")))throw new IllegalStateException("供料格在保存后发生变化，需管理员检查："+p.toShortString());
            }
            for(var edit:group.getAsJsonArray("blocks")){
                JsonObject e=edit.getAsJsonObject();BlockPos p=pos(e.getAsJsonArray("pos"));String current=BlockStateParser.serialize(world.getBlockState(p));
                if(!current.equals(e.get("before").getAsString())&&!current.equals(e.get("after").getAsString()))throw new IllegalStateException("恢复目标方块已发生其他变化："+p.toShortString());
            }
            for(var edit:group.getAsJsonArray("slots")){
                JsonObject e=edit.getAsJsonObject();Container inventory=(Container)world.getBlockEntity(pos(e.getAsJsonArray("pos")));inventory.setItem(e.get("slot").getAsInt(),runtime.economy.decode(e.get("after")));inventory.setChanged();
            }
            for(var edit:group.getAsJsonArray("blocks")){
                JsonObject e=edit.getAsJsonObject();BlockPos p=pos(e.getAsJsonArray("pos"));BlockState state;
                try{state=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,e.get("after").getAsString(),true).blockState();}catch(Exception ex){throw new IllegalStateException("施工方块状态无法读取",ex);}
                Protection.push(world,job.cannon);
                try {
                    if(world.getBlockState(p)!=state&&!world.setBlock(p,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS|Block.UPDATE_SKIP_ON_PLACE))throw new IllegalStateException("服务器拒绝施工："+p.toShortString());
                    if(e.has("nbt")&&world.getBlockEntity(p) instanceof BlockEntity entity)try{entity.loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,world.registryAccess(),TagParser.parseCompoundFully(e.get("nbt").getAsString())));entity.setChanged();}catch(Exception ex){throw new IllegalStateException("机器配置无法写入",ex);}
                }finally{Protection.pop(world);}
            }
            if(group.has("fuel")){
                if(!(world.getBlockEntity(job.cannon) instanceof BlueprintCannonEntity cannon)||!cannon.cannonId.equals(job.cannonId))throw new IllegalStateException("原蓝图大炮不存在");
                cannon.fuel=group.get("fuel").getAsLong();cannon.setChanged();
            }
            if(group.has("spawn"))spawn(job,world,group.getAsJsonObject("spawn"));
            Map<String,Long> next=new LinkedHashMap<>(receipts);next.put(job.id,sequence);attached.setAttached(RECEIPTS,Map.copyOf(next));chunk.markUnsaved();
        }}finally{APPLYING.remove();}
    }
    private void step(Job job,BlueprintCannonEntity cannon)throws IOException {
        ServerLevel world=(ServerLevel)cannon.getLevel();JsonObject next=frame(job);int runIndex=job.airRun,runOffset=job.airOffset,budget=rules.validationBudget();
        while(runIndex<job.template.air.size()&&budget-->0){
            var run=job.template.air.get(runIndex);BlockPos p=job.selection.origin().offset(job.template.rotate(new BlockPos(run.x()+runOffset,run.y(),run.z()),job.selection.rotation()));
            if(!world.hasChunkAt(p))throw new IllegalStateException("目标区块未加载，等待靠近后续建");BlockState old=world.getBlockState(p);
            if(++runOffset>=run.length()){runOffset=0;runIndex++;}next.addProperty("airRun",runIndex);next.addProperty("airOffset",runOffset);
            if(old.isAir())continue;assertTarget(job,cannon,p,Blocks.AIR.defaultBlockState());addBlock(next,p,old,Blocks.AIR.defaultBlockState(),null);addCannon(next,cannon,cannon.fuel);write(job,next,world,()->shot(world,cannon,p));return;
        }
        if(runIndex<job.template.air.size()){addCannon(next,cannon,cannon.fuel);write(job,next,world,()->{});return;}
        int cursor=job.cursor;budget=rules.validationBudget();
        while(cursor<job.template.cells.size()&&budget-->0){var cell=job.template.cells.get(cursor);BlockPos p=job.selection.origin().offset(job.template.rotate(cell.pos(),job.selection.rotation()));
            if(cell.stage()>=4&&job.fluidStage==0&&!job.template.fluids.isEmpty()){next.addProperty("cursor",cursor);next.addProperty("fluidStage",1);next.addProperty("message","等待流体稳定后装配红石与爆破部件");addCannon(next,cannon,cannon.fuel);write(job,next,world,()->{});return;}
            if(cell.stage()>=6&&job.entityCursor<job.template.entities.size()){next.addProperty("cursor",cursor);assembleEntity(job,cannon,next,world);return;}
            if(!world.hasChunkAt(p))throw new IllegalStateException("目标区块未加载，等待靠近后续建");BlockState target=cell.state().rotate(PrefabTemplate.rotation(job.selection.rotation()));
            if(world.getBlockState(p)==target){next.addProperty("cursor",++cursor);continue;}
            assertTarget(job,cannon,p,target);PrefabSupply supply=new PrefabSupply(runtime,cannon);if(job.mode.equals("materials"))supply.take(cell.ingredients());
            long fuel=cannon.fuel;if(fuel<1){ItemStack ammo=supply.stack(cannon.getBlockPos(),1);int credits=PrefabItems.ammunition(ammo,rules.ammunitionCredits());if(credits<1)throw new IllegalStateException("缺少特殊弹药，补充后自动续建");fuel=credits;ammo.shrink(1);}
            for(var value:supply.changes()){JsonObject row=value.getAsJsonObject();group(next,ChunkPos.containing(pos(row.getAsJsonArray("pos"))).pack()).getAsJsonArray("slots").add(row);}
            addCannon(next,cannon,fuel-1);CompoundTag nbt=cell.nbt()==null?null:cell.nbt().copy();if(nbt!=null)rotateNbt(nbt,job.selection.rotation());
            addBlock(next,p,world.getBlockState(p),target,nbt);next.addProperty("cursor",cursor+1);next.addProperty("message","");write(job,next,world,()->shot(world,cannon,p));return;
        }
        if(cursor<job.template.cells.size()){addCannon(next,cannon,cannon.fuel);write(job,next,world,()->{});return;}
        if(job.entityCursor<job.template.entities.size()){assembleEntity(job,cannon,next,world);return;}
        // All static components exist before vanilla neighbour and fluid updates can start machines.
        int finish=job.finishCursor;budget=rules.validationBudget();
        Protection.push(world,job.cannon);
        try{while(finish<job.template.cells.size()&&budget-->0){var cell=job.template.cells.get(finish++);BlockPos p=job.selection.origin().offset(job.template.rotate(cell.pos(),job.selection.rotation()));if(!world.hasChunkAt(p))throw new IllegalStateException("等待加载完整机器后完成更新");BlockState state=world.getBlockState(p);world.updateNeighborsAt(p,state.getBlock());state.updateNeighbourShapes(world,p,Block.UPDATE_ALL);if(!state.getFluidState().isEmpty())world.scheduleTick(p,state.getFluidState().getType(),1);}}
        finally{Protection.pop(world);}
        next.addProperty("finish",finish);addCannon(next,cannon,cannon.fuel);
        if(finish==job.template.cells.size()){next.addProperty("status","completed");next.addProperty("message","施工完成；需要的生物与运营耗材请自行补充，机器由玩家启动");}
        write(job,next,world,()->{cannon.message=job.message;});
    }
    private void assembleEntity(Job job,BlueprintCannonEntity cannon,JsonObject next,ServerLevel world){
            CompoundTag tag=job.template.entities.get(job.entityCursor).copy();UUID uuid=UUID.nameUUIDFromBytes((job.id+":"+job.entityCursor).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var list=tag.getListOrEmpty("Pos");double x=list.getDoubleOr(0,0),y=list.getDoubleOr(1,0),z=list.getDoubleOr(2,0);Vec3 location=rotateEntity(job.template,x,y,z,job.selection.rotation()).add(job.selection.x(),job.selection.y(),job.selection.z());BlockPos p=BlockPos.containing(location);
            if(!world.hasChunkAt(p))throw new IllegalStateException("装配实体所在区块未加载");if(runtime.claim(p)==null||!runtime.claim(p).id.equals(job.party)||!world.getWorldBorder().isWithinBounds(p))throw new IllegalStateException("装配实体超出本队领地");PrefabSupply supply=new PrefabSupply(runtime,cannon);String item=PrefabTemplate.entityItem(tag);
            if(world.getEntity(uuid)==null&&job.mode.equals("materials"))supply.take(List.of(new PrefabTemplate.Ingredient(item,1)));
            for(var value:supply.changes()){var row=value.getAsJsonObject();group(next,ChunkPos.containing(pos(row.getAsJsonArray("pos"))).pack()).getAsJsonArray("slots").add(row);}
            JsonObject spawn=new JsonObject();spawn.addProperty("uuid",uuid.toString());spawn.addProperty("nbt",tag.toString());spawn.addProperty("x",location.x);spawn.addProperty("y",location.y);spawn.addProperty("z",location.z);spawn.addProperty("yaw",job.selection.rotation()*90);group(next,ChunkPos.containing(p).pack()).add("spawn",spawn);
            next.addProperty("entities",job.entityCursor+1);addCannon(next,cannon,cannon.fuel);write(job,next,world,()->{});return;
    }
    private void assertTarget(Job job,BlueprintCannonEntity cannon,BlockPos p,BlockState state){
        ServerLevel world=(ServerLevel)cannon.getLevel();var claim=runtime.claim(p);if(claim==null||!claim.id.equals(job.party)||claim.resetInProgress||!world.getWorldBorder().isWithinBounds(p)||locked(world,p))throw new IllegalStateException("目标权限或领地变化，施工暂停");
        if(blocked(world,p,state,job.selection.replace(),cannon))throw new IllegalStateException("目标新增冲突，施工暂停："+p.toShortString());
    }
    private boolean primeFluids(Job job,ServerLevel world){
        for(var cell:job.template.fluids){BlockPos p=job.selection.origin().offset(job.template.rotate(cell.pos(),job.selection.rotation()));if(!world.hasChunkAt(p))return false;}
        if(job.settleTicks%20==0){Protection.push(world,job.cannon);try{for(var cell:job.template.fluids){BlockPos p=job.selection.origin().offset(job.template.rotate(cell.pos(),job.selection.rotation()));var state=world.getBlockState(p);world.updateNeighborsAt(p,state.getBlock());if(!state.getFluidState().isEmpty())world.scheduleTick(p,state.getFluidState().getType(),1);}}finally{Protection.pop(world);}}
        return true;
    }
    private static void addBlock(JsonObject frame,BlockPos pos,BlockState before,BlockState after,CompoundTag nbt){JsonObject block=new JsonObject();block.add("pos",xyz(pos));block.addProperty("before",BlockStateParser.serialize(before));block.addProperty("after",BlockStateParser.serialize(after));if(nbt!=null)block.addProperty("nbt",nbt.toString());group(frame,ChunkPos.containing(pos).pack()).getAsJsonArray("blocks").add(block);}
    private static void addCannon(JsonObject frame,BlueprintCannonEntity cannon,long fuel){group(frame,ChunkPos.containing(cannon.getBlockPos()).pack()).addProperty("fuel",fuel);}
    private static Vec3 rotateEntity(PrefabTemplate t,double x,double y,double z,int r){return switch(r%4){case 1->new Vec3(t.depth-z,y,x);case 2->new Vec3(t.width-x,y,t.depth-z);case 3->new Vec3(z,y,t.width-x);default->new Vec3(x,y,z);};}
    private static void rotateNbt(CompoundTag nbt,int rotation){
        // Orientation lives in BlockState. Strip source-world identities and update-position metadata.
        for(String key:List.of("x","y","z","keepPacked","yumis_box_id","yumis_party","yumis_island_epoch","UUID","LootTable","loot_table"))nbt.remove(key);
    }
    private void spawn(Job job,ServerLevel world,JsonObject spawn){
        UUID uuid=UUID.fromString(spawn.get("uuid").getAsString());if(world.getEntity(uuid)!=null)return;
        try{
            CompoundTag tag=TagParser.parseCompoundFully(spawn.get("nbt").getAsString());tag.putIntArray("UUID",new int[]{(int)(uuid.getMostSignificantBits()>>32),(int)uuid.getMostSignificantBits(),(int)(uuid.getLeastSignificantBits()>>32),(int)uuid.getLeastSignificantBits()});
            if(tag.contains("Facing")){Direction facing=Direction.from3DDataValue(tag.getByteOr("Facing",(byte)0));if(facing.getAxis().isHorizontal())for(int turn=0;turn<job.selection.rotation();turn++)facing=facing.getClockWise();tag.putByte("Facing",(byte)facing.get3DDataValue());}
            BlockPos anchor=BlockPos.containing(spawn.get("x").getAsDouble(),spawn.get("y").getAsDouble(),spawn.get("z").getAsDouble());tag.putIntArray("block_pos",new int[]{anchor.getX(),anchor.getY(),anchor.getZ()});
            Entity entity=EntityType.loadEntityRecursive(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,world.registryAccess(),tag),world,EntitySpawnReason.STRUCTURE,e->{e.setPos(spawn.get("x").getAsDouble(),spawn.get("y").getAsDouble(),spawn.get("z").getAsDouble());e.setYRot(e.getYRot()+spawn.get("yaw").getAsFloat());return e;});
            if(entity==null||!world.addFreshEntity(entity))throw new IllegalStateException("装配实体无法生成");((AttachmentTarget)entity).setAttached(YumisSkyIsland.ENTITY_PARTY,job.party.toString());
        }catch(Exception e){throw new IllegalStateException("装配实体恢复失败",e);}
    }
    private void control(Job job,String status,String message){
        if(job.pending)throw new IllegalStateException("施工步骤正在保存");var cannon=cannons.get(job.cannonId);if(cannon==null||!(cannon.getLevel() instanceof ServerLevel world))throw new IllegalStateException("大炮区块未加载");
        JsonObject next=frame(job);next.addProperty("status",status);next.addProperty("message",message);addCannon(next,cannon,cannon.fuel);write(job,next,world,()->cannon.message=message);
    }
    public void removed(BlueprintCannonEntity cannon){Job job=jobFor(cannon);if(job==null)return;job.status="terminated";job.message="大炮拆除，已建部分保留，费用不退";
        JsonObject next=frame(job);next.addProperty("status","terminated");job.pending=true;job.write=journal.append(job.id,next,()->{applyProgress(job,next);job.pending=false;unlock(job);},e->{job.pending=false;YumisSkyIsland.LOGGER.error("拆炮终止记录失败",e);});}
    private static void shot(ServerLevel world,BlueprintCannonEntity cannon,BlockPos target){
        Vec3 from=Vec3.atCenterOf(cannon.getBlockPos()).add(0,.4,0),to=Vec3.atCenterOf(target);int count=(int)Math.min(24,Math.max(3,from.distanceTo(to)/3));
        for(int i=0;i<count;i++){double t=(double)i/(count-1);Vec3 p=from.lerp(to,t).add(0,Math.sin(t*Math.PI)*Math.min(8,from.distanceTo(to)*.1),0);world.sendParticles(new DustParticleOptions(0x8EDCFF,0.8F),p.x,p.y,p.z,1,0,0,0,0);}
    }
    public void sendCannon(ServerPlayer player,BlueprintCannonEntity cannon){
        JsonObject out=new JsonObject();out.addProperty("kind","cannon");out.add("pos",xyz(cannon.getBlockPos()));out.addProperty("fuel",cannon.fuel);out.addProperty("availableFuel",cannon.fuel+(long)PrefabItems.ammunition(cannon.getItem(1),rules.ammunitionCredits())*cannon.getItem(1).getCount());out.addProperty("interval",rules.interval());out.addProperty("message",cannon.message);out.addProperty("busy",busy(cannon));out.addProperty("saving",locked(cannon.getLevel(),cannon.getBlockPos()));
        Job job=jobFor(cannon);if(job!=null){out.addProperty("status",job.status);out.addProperty("message",job.message.isBlank()?cannon.message:job.message);out.addProperty("progress",job.cursor);out.addProperty("total",job.template.cells.size());out.addProperty("mode",job.mode);out.addProperty("job",job.id);}
        else {var definition=catalog.definitions.get(PrefabItems.templateId(cannon.getItem(0)));out.addProperty("status","idle");if(definition!=null){out.addProperty("name",definition.name());out.addProperty("fee",definition.buildFee());try{var template=catalog.load(definition.id());out.addProperty("total",template.cells.size());JsonArray materials=new JsonArray();for(var item:template.materials.entrySet()){JsonObject row=new JsonObject();row.addProperty("item",item.getKey());row.addProperty("count",item.getValue());materials.add(row);}out.add("materials",materials);}catch(Exception e){out.addProperty("message",e.getMessage());}}
        }
        send(player,out);
    }
    private static void send(ServerPlayer player,JsonObject value){if(ServerPlayNetworking.canSend(player,PrefabPayloads.Snapshot.TYPE))ServerPlayNetworking.send(player,new PrefabPayloads.Snapshot(value.toString()));}
    public static JsonArray xyz(BlockPos pos){JsonArray out=new JsonArray();out.add(pos.getX());out.add(pos.getY());out.add(pos.getZ());return out;}
    public static BlockPos pos(JsonArray array){if(array==null||array.size()!=3)throw new IllegalArgumentException("位置格式无效");return new BlockPos(array.get(0).getAsInt(),array.get(1).getAsInt(),array.get(2).getAsInt());}
    public void disconnected(UUID player){previews.remove(player);previewQueue.remove(player);lastRequest.remove(player);}
    @Override public void close()throws IOException{closed=true;journal.close();LOCKS.clear();RECOVERY_LOCKS.clear();previews.clear();previewQueue.clear();}
}

