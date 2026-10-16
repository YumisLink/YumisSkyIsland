package cn.yumis.skyisland.npc;

import cn.yumis.skyisland.config.ResidentSettings;
import cn.yumis.skyisland.data.IslandState;
import cn.yumis.skyisland.server.SkyServer;
import com.google.gson.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;

/** Bounded, non-nested snapshots of the two registered containers. */
public final class ResidentSupply {
    private record Key(long position,int slot) {}
    private final SkyServer runtime;
    private final Map<Key,ItemStack> before=new LinkedHashMap<>(),after=new LinkedHashMap<>();
    private final List<Key> input=new ArrayList<>(),output=new ArrayList<>();
    public ResidentSupply(SkyServer runtime,IslandState.House house){this.runtime=runtime;read(house.supply,input);read(house.output,output);}
    private void read(List<Long> positions,List<Key> target){for(long position:positions){BlockPos pos=BlockPos.of(position);if(!runtime.server.overworld().hasChunkAt(pos))throw new HousingScanner.Unloaded();if(!(runtime.server.overworld().getBlockEntity(pos) instanceof Container inventory))throw new IllegalStateException("登记箱子已不存在");for(int slot=0;slot<inventory.getContainerSize();slot++){Key key=new Key(position,slot);ItemStack stack=inventory.getItem(slot).copy();before.put(key,stack);after.put(key,stack.copy());target.add(key);}}}
    private boolean take(Predicate<ItemStack> predicate,int amount){for(Key key:input){ItemStack stack=after.get(key);if(!stack.isEmpty()&&predicate.test(stack)){int n=Math.min(amount,stack.getCount());stack.shrink(n);amount-=n;if(amount==0)return true;}}return false;}
    public boolean supply(ResidentSettings.Job job,Set<String> foods,boolean freeMeal){
        Map<Key,ItemStack> saved=copy();
        if(!freeMeal&&!take(stack->foods.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()),1)){restore(saved);return false;}
        for(var supply:job.supplies()){boolean found=supply.item().equals("#planks")?take(stack->stack.is(ItemTags.PLANKS),supply.count()):take(stack->BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(supply.item()),supply.count());if(!found){restore(saved);return false;}}
        return true;
    }
    public boolean produce(List<ResidentSettings.Stack> items){Map<Key,ItemStack> saved=copy();for(var item:items){ItemStack remaining=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item.item())),item.count());
        for(int pass=0;pass<2;pass++)for(Key key:output){ItemStack current=after.get(key);if(pass==0&&current.isEmpty()||pass==1&&!current.isEmpty())continue;
            if(current.isEmpty()){int n=Math.min(remaining.getCount(),remaining.getMaxStackSize());after.put(key,remaining.copyWithCount(n));remaining.shrink(n);}
            else if(ItemStack.isSameItemSameComponents(current,remaining)){int n=Math.min(remaining.getCount(),Math.max(0,current.getMaxStackSize()-current.getCount()));current.grow(n);remaining.shrink(n);}
            if(remaining.isEmpty())break;
        }
        if(!remaining.isEmpty()){restore(saved);return false;}
    }return true;}
    private Map<Key,ItemStack> copy(){Map<Key,ItemStack> out=new LinkedHashMap<>();after.forEach((key,value)->out.put(key,value.copy()));return out;}
    private void restore(Map<Key,ItemStack> saved){after.clear();after.putAll(saved);}
    public JsonArray groups(){Map<Long,JsonArray> chunks=new LinkedHashMap<>();for(var entry:after.entrySet()){Key key=entry.getKey();ItemStack old=before.get(key);if(ItemStack.matches(old,entry.getValue()))continue;BlockPos pos=BlockPos.of(key.position);long chunk=net.minecraft.world.level.ChunkPos.containing(pos).pack();JsonObject row=new JsonObject();row.addProperty("position",key.position);row.addProperty("slot",key.slot);row.add("before",runtime.economy.encode(old));row.add("after",runtime.economy.encode(entry.getValue()));chunks.computeIfAbsent(chunk,_ ->new JsonArray()).add(row);}JsonArray out=new JsonArray();chunks.forEach((chunk,slots)->{JsonObject row=new JsonObject();row.addProperty("chunk",chunk);row.add("slots",slots);out.add(row);});return out;}
}
