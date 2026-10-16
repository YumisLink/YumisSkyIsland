package cn.yumis.skyisland.wand;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.network.WandPayloads;
import cn.yumis.skyisland.server.*;
import cn.yumis.skyisland.world.VoidWorlds;
import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Surface-expansion concept adapted from ConstructionWand (ThetaDev, MIT; see packaged license).
 * All selection and mutations run on the server. No chunk loading or client-supplied coordinates.
 */
public final class ConstructionWandService {
    private record Key(ResourceKey<Level> dimension,BlockPos pos) {}
    private record Candidate(BlockPos pos,BlockState before,BlockState after,Item item,int cost) {}
    private static final class Placed {
        final Candidate change; final boolean paid; boolean valid=true;
        Placed(Candidate change,boolean paid){this.change=change;this.paid=paid;}
    }
    private record Job(ResourceKey<Level> dimension,List<Placed> changes) {}
    private static final Map<UUID,Deque<Job>> HISTORY=new HashMap<>();
    private static final Map<Key,Set<Placed>> WATCHED=new HashMap<>();
    private static final Map<UUID,Integer> LAST_ACTION=new HashMap<>();
    private static final class PreviewContext {
        ServerPlayer player; ResourceKey<Level> dimension; Item item; int slot,hand;
        long contextRevision,revision; int lastForced=-10; String content;
    }
    private static final Map<UUID,PreviewContext> PREVIEWS=new HashMap<>();
    private static WandRules rules;
    private ConstructionWandService() {}

    public static void register() {
        rules=WandRules.load(YumisSkyIsland.CONFIG);
        PayloadTypeRegistry.serverboundPlay().register(WandPayloads.Request.TYPE,WandPayloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WandPayloads.Snapshot.TYPE,WandPayloads.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WandPayloads.Request.TYPE,(request,context)->request(context.player(),request));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->clear(handler.player.getUUID()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{HISTORY.clear();WATCHED.clear();LAST_ACTION.clear();PREVIEWS.clear();});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%4!=0)return;
            for(ServerPlayer player:server.getPlayerList().getPlayers()) {
                if(ConstructionWands.isWand(ConstructionWands.held(player))||PREVIEWS.containsKey(player.getUUID()))sendPreview(player);
            }
        });
    }
    private static boolean allowed(ServerPlayer player) {
        SkyServer runtime=SkyServer.get(player.level().getServer());
        return player.isAlive()&&!player.isSpectator()&&player.getAbilities().mayBuild&&VoidWorlds.isSky(player.level())&&runtime!=null
                &&!runtime.closing&&!runtime.inventoryLocks.contains(player.getUUID())
                &&!runtime.worlds.waitingPlayers.contains(player.getUUID());
    }
    private static BlockHitResult hit(ServerPlayer player) {
        Vec3 eye=player.getEyePosition();
        return player.level().clip(new ClipContext(eye,eye.add(player.getLookAngle().scale(player.blockInteractionRange())),
                ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
    }
    private static void request(ServerPlayer player,WandPayloads.Request request) {
        PreviewContext context=context(player);
        if(request.action()==3) { sendPreview(player,true);return; }
        if(!player.level().dimension().identifier().toString().equals(request.dimension())
                ||request.contextRevision()!=context.contextRevision) { sendPreview(player,true);return; }
        ItemStack wand=ConstructionWands.held(player);
        if(wand.isEmpty()||!allowed(player)||player.containerMenu!=player.inventoryMenu)return;
        if(request.action()==2) {
            if(request.direction()<0||request.direction()>4||request.matching()<0||request.matching()>2)return;
            new WandOptions(request.direction(),request.matching(),request.copyFacing(),request.random()).write(wand);
            player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();sendPreview(player);return;
        }
        if(request.action()!=0&&request.action()!=1)return;
        int now=player.level().getServer().getTickCount();
        if(now-LAST_ACTION.getOrDefault(player.getUUID(),now-4)<4)return;
        LAST_ACTION.put(player.getUUID(),now);
        if(request.action()==1) { if(player.isShiftKeyDown())undo(player); }
        else build(player,wand);
        sendPreview(player);
    }
    private static boolean permitted(ServerPlayer player,BlockPos pos) {
        ServerLevel level=player.level();
        return level.isInWorldBounds(pos)&&level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)
                &&Protection.canBuild(level,player,pos)&&!level.getBlockState(pos).hasBlockEntity();
    }
    private static boolean directionAllows(BlockPos origin,BlockPos next,int direction) {
        int x=next.getX()-origin.getX(),y=next.getY()-origin.getY(),z=next.getZ()-origin.getZ();
        return switch(direction){case 1->y==0;case 2->x==0&&z==0;case 3->x==0&&y==0;case 4->z==0&&y==0;default->true;};
    }
    private static List<Candidate> select(ServerPlayer player,ItemStack wand) {
        if(!allowed(player))return List.of();
        BlockHitResult ray=hit(player);if(ray.getType()!=HitResult.Type.BLOCK)return List.of();
        ServerLevel level=player.level();BlockPos origin=ray.getBlockPos();Direction face=ray.getDirection();
        if(!permitted(player,origin))return List.of();
        BlockState root=level.getBlockState(origin);if(root.isAir())return List.of();
        String rootId=BuiltInRegistries.BLOCK.getKey(root.getBlock()).toString();
        WandOptions options=WandOptions.read(wand);WandMaterials materials=new WandMaterials(player,true);
        List<Item> choices=materials.choices(player);
        ItemStack other=wand==player.getMainHandItem()?player.getOffhandItem():player.getMainHandItem();
        Item specified=WandMaterials.ordinary(other)?other.getItem():null;
        List<Candidate> selected=new ArrayList<>();Deque<BlockPos> queue=new ArrayDeque<>();Set<BlockPos> seen=new HashSet<>();
        queue.add(origin);seen.add(origin);int checked=0,max=rules.limits().get(ConstructionWands.tier(wand));
        while(!queue.isEmpty()&&checked++<rules.searchLimit()&&selected.size()<max) {
            BlockPos support=queue.removeFirst();
            if(!permitted(player,support))continue;
            BlockState base=level.getBlockState(support);
            if(base.isAir()||!rules.matches(rootId,BuiltInRegistries.BLOCK.getKey(base.getBlock()).toString(),options.matching()))continue;
            BlockPos target=support.relative(face);
            if(permitted(player,target)) {
                Item item;
                if(options.random()&&!choices.isEmpty()) {
                    List<Item> available=choices.stream().filter(i->materials.available(i)>0).toList();
                    item=available.isEmpty()?Items.AIR:available.get(new Random(origin.asLong()^support.asLong()^player.getUUID().getLeastSignificantBits()).nextInt(available.size()));
                }else item=specified!=null?specified:materials.fallback(base,rules,options.matching());
                Candidate candidate=candidate(player,ray.withPosition(support),target,base,item,options);
                if(candidate!=null&&materials.take(item,candidate.cost()))selected.add(candidate);
            }
            for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++) {
                if(a==0&&b==0)continue;
                BlockPos next=switch(face.getAxis()){case X->support.offset(0,a,b);case Y->support.offset(a,0,b);case Z->support.offset(a,b,0);};
                if(seen.size()<rules.searchLimit()&&directionAllows(origin,next,options.direction())&&seen.add(next))queue.addLast(next);
            }
        }
        return selected;
    }
    private static Candidate candidate(ServerPlayer player,BlockHitResult hit,BlockPos target,BlockState base,Item item,WandOptions options) {
        if(!(item instanceof BlockItem blockItem)||!WandMaterials.supported(blockItem.getBlock()))return null;
        ServerLevel level=player.level();BlockState before=level.getBlockState(target);
        if(!before.canBeReplaced()&&!before.isAir())return null;
        InteractionHand hand=ConstructionWands.isWand(player.getMainHandItem())?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
        ItemStack material=new ItemStack(item);
        BlockPlaceContext context=new BlockPlaceContext(level,player,hand,material,hit) {
            @Override public BlockPos getClickedPos(){return target;}
        };
        BlockState state=blockItem.getBlock().getStateForPlacement(context);if(state==null)return null;
        if(options.copyFacing())for(Property<?> property:state.getProperties()) {
            if(Set.of("facing","axis","half","type","shape","layers","rotation","face","attachment","hinge").contains(property.getName())&&base.hasProperty(property))
                state=copyProperty(state,base,property);
        }
        if(state.hasProperty(SlabBlock.TYPE)&&state.getValue(SlabBlock.TYPE)==SlabType.DOUBLE&&state.hasProperty(BlockStateProperties.WATERLOGGED))
            state=state.setValue(BlockStateProperties.WATERLOGGED,false);
        if(state.hasBlockEntity()||!state.canSurvive(level,target)||!level.isUnobstructed(state,target,CollisionContext.of(player)))return null;
        int cost=state.hasProperty(SlabBlock.TYPE)&&state.getValue(SlabBlock.TYPE)==SlabType.DOUBLE?2:
                state.hasProperty(SnowLayerBlock.LAYERS)?state.getValue(SnowLayerBlock.LAYERS):1;
        return new Candidate(target.immutable(),before,state,item,cost);
    }
    private static <T extends Comparable<T>> BlockState copyProperty(BlockState state,BlockState source,Property<T> property) {
        return state.setValue(property,source.getValue(property));
    }
    private static void build(ServerPlayer player,ItemStack wand) {
        List<Candidate> candidates=select(player,wand);if(candidates.isEmpty())return;
        ServerLevel level=player.level();WandMaterials materials=new WandMaterials(player,false);List<Placed> done=new ArrayList<>();
        for(Candidate candidate:candidates) {
            if(!permitted(player,candidate.pos)||level.getBlockState(candidate.pos)!=candidate.before
                    ||materials.available(candidate.item)<candidate.cost||!candidate.after.canSurvive(level,candidate.pos)
                    ||!level.isUnobstructed(candidate.after,candidate.pos,CollisionContext.of(player)))continue;
            Protection.pushPlayer(level,candidate.pos,player);
            Protection.beginPlacement(level,player);Protection.expectPlacement(level,candidate.pos,candidate.after);
            try {
                if(!level.setBlock(candidate.pos,candidate.after,Block.UPDATE_ALL))continue;
                materials.take(candidate.item,candidate.cost);
                candidate.after.getBlock().setPlacedBy(level,candidate.pos,candidate.after,player,new ItemStack(candidate.item));
                done.add(new Placed(candidate,!player.isCreative()));
            }finally{Protection.endPlacement(level);Protection.pop(level);}
        }
        if(done.isEmpty())return;
        List<Placed> stable=new ArrayList<>();
        for(Placed entry:done)if(level.getBlockState(entry.change.pos)==entry.change.after)stable.add(entry);
        if(!stable.isEmpty()) {
            Job job=new Job(level.dimension(),stable);Deque<Job> history=HISTORY.computeIfAbsent(player.getUUID(),id->new ArrayDeque<>());history.addLast(job);
            for(Placed entry:stable)WATCHED.computeIfAbsent(new Key(job.dimension,entry.change.pos),key->Collections.newSetFromMap(new IdentityHashMap<>())).add(entry);
            while(history.size()>rules.undoDepth())forget(history.removeFirst());
        }
        var sound=done.getFirst().change.after.getSoundType();
        level.playSound(null,done.getFirst().change.pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*0.8f);
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
    }
    private static List<Placed> undoable(ServerPlayer player,Job job) {
        if(job==null||!job.dimension.equals(player.level().dimension()))return List.of();
        return job.changes.stream().filter(entry->entry.valid&&permitted(player,entry.change.pos)
                &&player.level().getBlockState(entry.change.pos)==entry.change.after).toList();
    }
    private static void undo(ServerPlayer player) {
        Deque<Job> history=HISTORY.get(player.getUUID());if(history==null||history.isEmpty())return;
        Job job=history.peekLast();if(!job.dimension.equals(player.level().dimension()))return;
        BlockHitResult ray=hit(player);
        if(ray.getType()!=HitResult.Type.BLOCK||job.changes.stream().noneMatch(entry->entry.change.pos.distSqr(ray.getBlockPos())<=9))return;
        List<Placed> eligible=undoable(player,job);history.removeLast();forget(job);
        List<Placed> restored=new ArrayList<>();ServerLevel level=player.level();
        // Remove the whole eligible set before notifying neighbours, so attached blocks cannot drop and refund twice.
        for(Placed entry:eligible) {
            Candidate c=entry.change;Protection.pushPlayer(level,c.pos,player);
            try {
                if(level.getBlockState(c.pos)==c.after&&level.setBlock(c.pos,c.before,
                        Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS))restored.add(entry);
            }finally{Protection.pop(level);}
        }
        for(Placed entry:restored) {
            Candidate c=entry.change;Protection.pushPlayer(level,c.pos,player);
            try {level.updateNeighborsAt(c.pos,c.before.getBlock());c.before.updateNeighbourShapes(level,c.pos,Block.UPDATE_ALL);}finally{Protection.pop(level);}
            if(entry.paid) {
                ItemStack refund=new ItemStack(c.item,c.cost);
                if(!player.getInventory().add(refund)&&!refund.isEmpty())player.drop(refund,false);
            }
        }
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
    }
    public static void changed(ServerLevel level,BlockPos pos) {
        Set<Placed> entries=WATCHED.remove(new Key(level.dimension(),pos));
        if(entries!=null)for(Placed entry:entries)entry.valid=false;
    }
    private static void forget(Job job) {
        for(Placed entry:job.changes) {
            Key key=new Key(job.dimension,entry.change.pos);Set<Placed> entries=WATCHED.get(key);
            if(entries!=null){entries.remove(entry);if(entries.isEmpty())WATCHED.remove(key);}
        }
    }
    private static void clear(UUID id) {
        Deque<Job> jobs=HISTORY.remove(id);if(jobs!=null)jobs.forEach(ConstructionWandService::forget);
        LAST_ACTION.remove(id);PREVIEWS.remove(id);
    }
    private static JsonArray positions(List<BlockPos> positions) {
        JsonArray array=new JsonArray();for(BlockPos pos:positions){JsonArray xyz=new JsonArray();xyz.add(pos.getX());xyz.add(pos.getY());xyz.add(pos.getZ());array.add(xyz);}return array;
    }
    private static PreviewContext context(ServerPlayer player) {
        PreviewContext view=PREVIEWS.computeIfAbsent(player.getUUID(),id->new PreviewContext());
        ItemStack held=ConstructionWands.held(player);int hand=held.isEmpty()?-1:held==player.getMainHandItem()?0:1;
        int slot=hand==0?player.getInventory().getSelectedSlot():-1;
        if(view.player!=player||view.dimension!=player.level().dimension()||view.item!=held.getItem()||view.slot!=slot||view.hand!=hand) {
            view.player=player;view.dimension=player.level().dimension();view.item=held.getItem();view.slot=slot;view.hand=hand;
            view.contextRevision++;view.content=null;
        }
        return view;
    }
    private static void sendPreview(ServerPlayer player) { sendPreview(player,false); }
    private static void sendPreview(ServerPlayer player,boolean force) {
        if(!ServerPlayNetworking.canSend(player,WandPayloads.Snapshot.TYPE))return;
        PreviewContext view=context(player);
        JsonObject data=new JsonObject();ItemStack wand=ConstructionWands.held(player);boolean active=allowed(player)&&!wand.isEmpty();
        data.addProperty("active",active);data.add("limits",YumisSkyIsland.CONFIG.object("gameplay/construction_wand.toml").getAsJsonArray("placementLimits"));
        data.addProperty("undoDepth",rules.undoDepth());
        if(!wand.isEmpty())data.add("options",cn.yumis.skyisland.config.GameConfig.JSON.toJsonTree(WandOptions.read(wand)));
        data.add("positions",positions(active?select(player,wand).stream().map(Candidate::pos).toList():List.of()));
        Deque<Job> history=HISTORY.get(player.getUUID());
        data.add("undo",positions(active&&history!=null&&!history.isEmpty()?undoable(player,history.peekLast()).stream().map(entry->entry.change.pos).toList():List.of()));
        String json=data.toString();int now=player.level().getServer().getTickCount();
        boolean changed=!json.equals(view.content);
        if(!changed&&(!force||now-view.lastForced<4))return;
        view.content=json;if(force)view.lastForced=now;
        ServerPlayNetworking.send(player,new WandPayloads.Snapshot(player.level().dimension().identifier().toString(),
                view.contextRevision,++view.revision,json));
    }
}
