package cn.yumis.skyisland.npc;

import cn.yumis.skyisland.config.ResidentSettings;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.server.SkyServer;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Bounded flood fill. Air count and traversal budget are deliberately separate. */
public final class HousingScanner {
    public record Result(Set<Long> interior,Set<Long> boundary,Map<Long,String> facilities,int air,boolean lit) {}
    public static final class Unloaded extends IllegalStateException {public Unloaded(){super("房屋所需区块未加载，请靠近后重试");}}
    private final SkyServer runtime;
    private final ResidentSettings rules;
    public HousingScanner(SkyServer runtime,ResidentSettings rules){this.runtime=runtime;this.rules=rules;}
    public static String id(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();}
    public boolean station(BlockState state){String id=id(state);return rules.jobs.values().stream().anyMatch(job->!job.station().isEmpty()&&job.station().equals(id));}
    public boolean light(BlockState state){return rules.lights.contains(id(state));}
    public static boolean lit(BlockState state){return !state.hasProperty(BlockStateProperties.LIT)||state.getValue(BlockStateProperties.LIT);}
    public static BlockPos bedHead(BlockPos pos,BlockState state){return state.getValue(BedBlock.PART)==BedPart.HEAD?pos:pos.relative(state.getValue(BedBlock.FACING));}
    public static List<BlockPos> bedParts(BlockPos pos,BlockState state){BlockPos head=bedHead(pos,state);return List.of(head,head.relative(state.getValue(BedBlock.FACING).getOpposite()));}
    public static List<BlockPos> doorParts(BlockPos pos,BlockState state){BlockPos lower=state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER?pos:pos.below();return List.of(lower,lower.above());}
    public static List<Long> chestParts(ServerLevel world,BlockPos pos){
        if(!world.hasChunkAt(pos))throw new Unloaded();BlockState state=world.getBlockState(pos);
        if(!state.is(Blocks.CHEST))throw new IllegalArgumentException("请登记普通箱子；支持独立单箱或完整大箱");
        List<Long> parts=new ArrayList<>();parts.add(pos.asLong());
        if(state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){BlockPos other=pos.relative(ChestBlock.getConnectedDirection(state));
            if(!world.hasChunkAt(other))throw new Unloaded();BlockState mate=world.getBlockState(other);
            if(!mate.is(Blocks.CHEST)||mate.getValue(ChestBlock.TYPE)==ChestType.SINGLE||!other.relative(ChestBlock.getConnectedDirection(mate)).equals(pos))throw new IllegalArgumentException("大箱结构不完整");parts.add(other.asLong());}
        parts.sort(Long::compare);return List.copyOf(parts);
    }
    private boolean enclosing(ServerLevel world,BlockPos pos,BlockState state){
        if(state.getBlock() instanceof DoorBlock)return true;
        if(state.getBlock() instanceof StairBlock||state.getBlock() instanceof SlabBlock)return true;
        if(state.isAir()||!state.getFluidState().isEmpty()||state.getBlock() instanceof FenceBlock||state.getBlock() instanceof WallBlock||state.getBlock() instanceof IronBarsBlock)return false;
        return state.getBlock() instanceof StairBlock||state.getBlock() instanceof SlabBlock||state.isCollisionShapeFullBlock(world,pos);
    }
    private void validateDoor(ServerLevel world,BlockPos pos,BlockState door){
        var parts=doorParts(pos,door);
        for(BlockPos half:parts){
            if(!world.hasChunkAt(half))throw new Unloaded();BlockState other=world.getBlockState(half);
            DoubleBlockHalf expected=half.equals(parts.getFirst())?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER;
            if(other.getBlock()!=door.getBlock()||other.getValue(DoorBlock.FACING)!=door.getValue(DoorBlock.FACING)||other.getValue(DoorBlock.HINGE)!=door.getValue(DoorBlock.HINGE)||other.getValue(DoorBlock.HALF)!=expected)throw new IllegalArgumentException("房屋的门必须上下完整且朝向一致");
        }
    }
    public Result scan(IslandState.Party party,BlockPos seed){
        ServerLevel world=runtime.server.overworld();Set<Long> seen=new LinkedHashSet<>(),inside=new LinkedHashSet<>(),boundary=new LinkedHashSet<>();Map<Long,String> facilities=new LinkedHashMap<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(seed);int air=0;boolean lit=true;
        while(!queue.isEmpty()){
            BlockPos pos=queue.removeFirst();long key=pos.asLong();if(!seen.add(key))continue;
            if(seen.size()>rules.searchBudget)throw new IllegalArgumentException("房屋空间过于复杂或未封闭，超过搜索预算");
            if(!world.isInWorldBounds(pos)||runtime.claim(pos)!=party)throw new IllegalArgumentException("房屋未封闭或超出本队领地");
            if(!world.hasChunkAt(pos))throw new Unloaded();BlockState state=world.getBlockState(pos);
            if(state.getBlock() instanceof DoorBlock||station(state)||light(state))facilities.put(key,id(state));
            if(light(state)&&!lit(state))lit=false;
            if(enclosing(world,pos,state)){boundary.add(key);continue;}
            inside.add(key);if(state.isAir()&&++air>rules.maxAir)throw new IllegalArgumentException("室内空气超过"+rules.maxAir+"格，房屋太大或存在漏口");
            for(Direction direction:Direction.values())queue.add(pos.relative(direction));
        }
        if(air<rules.minAir)throw new IllegalArgumentException("室内空气不足"+rules.minAir+"格（当前"+air+"格）");
        if(facilities.keySet().stream().noneMatch(p->world.getBlockState(BlockPos.of(p)).getBlock() instanceof DoorBlock))throw new IllegalArgumentException("房屋必须有门");
        for(long key:facilities.keySet()){BlockPos pos=BlockPos.of(key);BlockState door=world.getBlockState(pos);if(door.getBlock() instanceof DoorBlock)validateDoor(world,pos,door);}
        if(facilities.keySet().stream().noneMatch(p->station(world.getBlockState(BlockPos.of(p)))))throw new IllegalArgumentException("房屋必须有认可的工作站");
        if(facilities.keySet().stream().noneMatch(p->light(world.getBlockState(BlockPos.of(p)))))throw new IllegalArgumentException("房屋必须有认可的光源");
        return new Result(Set.copyOf(inside),Set.copyOf(boundary),Map.copyOf(facilities),air,lit);
    }
    public void validateBindings(IslandState.Party party,IslandState.House house,Result result,boolean submitting){
        ServerLevel world=runtime.server.overworld();
        if(house.supply.isEmpty()||house.output.isEmpty())throw new IllegalArgumentException("请先登记消耗箱和产出箱");
        if(!Collections.disjoint(house.supply,house.output))throw new IllegalArgumentException("消耗箱与产出箱必须是两个独立容器");
        for(List<Long> box:List.of(house.supply,house.output)){
            if(!chestParts(world,BlockPos.of(box.getFirst())).equals(box))throw new IllegalArgumentException("登记箱子已合并、拆分或替换");
            for(long pos:box)if(!result.interior.contains(pos))throw new IllegalArgumentException("两个箱子必须在同一室内空间");
        }
        BlockPos bed=BlockPos.of(house.bed);if(!world.hasChunkAt(bed))throw new Unloaded();BlockState bedState=world.getBlockState(bed);
        if(!(bedState.getBlock() instanceof BedBlock)||bedState.getValue(BedBlock.PART)!=BedPart.HEAD)throw new IllegalArgumentException("请登记一张完整的床");
        for(BlockPos part:bedParts(bed,bedState)){BlockState other=world.getBlockState(part);if(!result.interior.contains(part.asLong())||other.getBlock()!=bedState.getBlock()||other.getValue(BedBlock.PART)!=(part.equals(bed)?BedPart.HEAD:BedPart.FOOT)||other.getValue(BedBlock.FACING)!=bedState.getValue(BedBlock.FACING))throw new IllegalArgumentException("床必须完整并在同一室内空间");}
        if(submitting){
            if(!house.markers.containsAll(result.facilities.keySet()))throw new IllegalArgumentException("还有未右键登记的门、工作站或光源");
            if(!result.lit)throw new IllegalArgumentException("认可光源必须点亮，请检查熄灭的红石灯");
            var job=rules.jobs.get(house.target);var original=party.settlement.houses.get(house.id);boolean retainedTarget=original!=null&&original.target.equals(house.target);
            if(job==null||!retainedTarget&&!rules.unlocked(house.target,runtime.level(party),party.settlement.reputation))throw new IllegalArgumentException("目标职业尚未解锁");
            if(result.facilities.values().stream().noneMatch(job.station()::equals))throw new IllegalArgumentException("房屋缺少目标职业工作站");
        }
        Set<Long> used=new HashSet<>(result.facilities.keySet());used.addAll(house.supply);used.addAll(house.output);for(BlockPos part:bedParts(bed,bedState))used.add(part.asLong());
        for(var other:party.settlement.houses.values())if(!other.id.equals(house.id)){
            if(!Collections.disjoint(other.interior,result.interior)||!Collections.disjoint(other.facilities.keySet(),used))throw new IllegalArgumentException("房屋空间或登记设施已被另一栋房屋占用");
        }
    }
    public void capture(IslandState.House house,Result result){
        house.interior=new LinkedHashSet<>(result.interior);house.boundary=new LinkedHashSet<>(result.boundary);house.air=result.air;house.facilities=new LinkedHashMap<>(result.facilities);
        house.chunks.clear();for(long pos:house.interior)house.chunks.add(net.minecraft.world.level.ChunkPos.containing(BlockPos.of(pos)).pack());for(long pos:house.boundary)house.chunks.add(net.minecraft.world.level.ChunkPos.containing(BlockPos.of(pos)).pack());
        ServerLevel world=runtime.server.overworld();for(long pos:house.supply)house.facilities.put(pos,id(world.getBlockState(BlockPos.of(pos))));for(long pos:house.output)house.facilities.put(pos,id(world.getBlockState(BlockPos.of(pos))));
        BlockPos bed=BlockPos.of(house.bed);for(BlockPos pos:bedParts(bed,world.getBlockState(bed)))house.facilities.put(pos.asLong(),id(world.getBlockState(pos)));
    }
}
