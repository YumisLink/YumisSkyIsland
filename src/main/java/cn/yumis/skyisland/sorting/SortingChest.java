package cn.yumis.skyisland.sorting;

import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.server.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Native slots are addressed individually even when the player sees a double chest. */
public record SortingChest(Map<Long,String> identities,List<ChestBlockEntity> halves) {
    public static final class Waiting extends RuntimeException { public Waiting(String message){super(message);} }
    public static SortingChest at(SkyServer rt,ServerLevel world,IslandState.Party party,BlockPos anchor,boolean checkLocks){
        if(!world.hasChunkAt(anchor))throw new Waiting("等待箱子区块加载");
        var state=world.getBlockState(anchor);if(!state.is(Blocks.CHEST))throw new IllegalArgumentException("只支持普通单箱或完整大箱");
        List<BlockPos> positions=new ArrayList<>();positions.add(anchor);
        if(state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
            BlockPos other=anchor.relative(ChestBlock.getConnectedDirection(state));if(!world.hasChunkAt(other))throw new Waiting("等待大箱另一半加载");
            var mate=world.getBlockState(other);
            if(!mate.is(Blocks.CHEST)||mate.getValue(ChestBlock.TYPE)==ChestType.SINGLE||mate.getValue(ChestBlock.TYPE)==state.getValue(ChestBlock.TYPE)
                    ||mate.getValue(ChestBlock.FACING)!=state.getValue(ChestBlock.FACING)||!other.relative(ChestBlock.getConnectedDirection(mate)).equals(anchor))
                throw new IllegalArgumentException("大箱结构不完整");
            positions.add(other);
        }
        positions.sort(Comparator.comparingLong(BlockPos::asLong));Map<Long,String> ids=new LinkedHashMap<>();List<ChestBlockEntity> halves=new ArrayList<>();
        for(BlockPos pos:positions){
            if(rt.claim(pos)!=party||!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest))throw new IllegalArgumentException("箱子必须在本队领地");
            if(checkLocks&&Protection.locked(world,pos))throw new Waiting("箱子正在保存或恢复");
            String identity=chest.getAttachedOrCreate(SortingContent.CHEST_ID,()->UUID.randomUUID().toString());
            ids.put(pos.asLong(),identity);halves.add(chest);
        }
        return new SortingChest(ids,List.copyOf(halves));
    }
    public static SortingChest resolve(SkyServer rt,ServerLevel world,IslandState.Party party,Map<Long,String> expected,boolean locks){
        if(expected.isEmpty())throw new Waiting("请先绑定来源箱A和目标箱B");
        // Check every previously bound half before creating IDs for any newly added half.
        for(var entry:expected.entrySet()){
            BlockPos pos=BlockPos.of(entry.getKey());if(!world.hasChunkAt(pos))throw new Waiting("等待箱子区块加载");
            if(!world.getBlockState(pos).is(Blocks.CHEST)||!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)
                    ||!Objects.equals(chest.getAttached(SortingContent.CHEST_ID),entry.getValue()))throw new IllegalArgumentException("原箱子已拆除或替换，请重新绑定");
        }
        SortingChest now=at(rt,world,party,BlockPos.of(expected.keySet().iterator().next()),locks);
        if(!now.identities.keySet().containsAll(expected.keySet()))throw new IllegalArgumentException("大箱已拆分，请重新绑定");
        return now;
    }
    public List<ItemStack> items(){List<ItemStack> items=new ArrayList<>();for(var half:halves)for(int i=0;i<half.getContainerSize();i++)items.add(half.getItem(i).copy());return items;}
    public ChestBlockEntity half(int slot){return halves.get(slot/27);}
    public double x(){return halves.stream().mapToDouble(h->h.getBlockPos().getX()+0.5).average().orElseThrow();}
    public double y(){return halves.stream().mapToDouble(h->h.getBlockPos().getY()+0.5).average().orElseThrow();}
    public double z(){return halves.stream().mapToDouble(h->h.getBlockPos().getZ()+0.5).average().orElseThrow();}
}
