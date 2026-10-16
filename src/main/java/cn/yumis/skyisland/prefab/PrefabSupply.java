package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.mixin.CompoundContainerAccessor;
import cn.yumis.skyisland.server.SkyServer;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Plans changes on copies; nothing is removed until the write-ahead record is durable. */
final class PrefabSupply {
    private record Key(BlockPos pos,int slot) {}
    private final Map<Key,ItemStack> before=new LinkedHashMap<>(),after=new LinkedHashMap<>();
    private final List<BlockEntity> containers=new ArrayList<>();
    private final SkyServer runtime;
    private final BlueprintCannonEntity cannon;
    PrefabSupply(SkyServer runtime,BlueprintCannonEntity cannon){
        this.runtime=runtime;this.cannon=cannon;containers.add(cannon);Set<BlockPos> seen=new HashSet<>();seen.add(cannon.getBlockPos());
        ServerLevel world=(ServerLevel)cannon.getLevel();var owner=runtime.claim(cannon.getBlockPos());
        for(Direction direction:Direction.values()){
            BlockPos pos=cannon.getBlockPos().relative(direction);if(!world.hasChunkAt(pos)||runtime.claim(pos)!=owner||cn.yumis.skyisland.server.Protection.locked(world,pos))continue;
            BlockEntity entity=world.getBlockEntity(pos);if(entity==null||entity instanceof BlueprintCannonEntity)continue;
            if(world.getBlockState(pos).getBlock() instanceof ChestBlock chest){
                var state=world.getBlockState(pos);
                if(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.CHEST_TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){BlockPos other=pos.relative(ChestBlock.getConnectedDirection(state));if(!world.hasChunkAt(other)||runtime.claim(other)!=owner){add((Container)entity,seen,world,owner);continue;}}
                Container combined=ChestBlock.getContainer(chest,state,world,pos,false);if(combined!=null)add(combined,seen,world,owner);
            }
            else if(entity instanceof Container container)add(container,seen,world,owner);
        }
    }
    private void add(Container container,Set<BlockPos> seen,ServerLevel world,cn.yumis.skyisland.data.IslandState.Party owner){
        if(container instanceof CompoundContainer){var pair=(CompoundContainerAccessor)container;add(pair.yumis$first(),seen,world,owner);add(pair.yumis$second(),seen,world,owner);}
        else if(container instanceof BlockEntity entity&&runtime.claim(entity.getBlockPos())==owner&&!cn.yumis.skyisland.server.Protection.locked(world,entity.getBlockPos())&&seen.add(entity.getBlockPos()))containers.add(entity);
    }
    ItemStack stack(BlockPos pos,int slot){
        Key key=new Key(pos,slot);if(after.containsKey(key))return after.get(key);
        var entity=cannon.getLevel().getBlockEntity(pos);if(!(entity instanceof Container container))throw new IllegalStateException("供料容器已变化");
        ItemStack original=container.getItem(slot).copy();before.put(key,original);ItemStack copy=original.copy();after.put(key,copy);return copy;
    }
    void set(BlockPos pos,int slot,ItemStack stack){stack(pos,slot);after.put(new Key(pos,slot),stack.copy());}
    void take(List<PrefabTemplate.Ingredient> ingredients){for(var ingredient:ingredients)take(ingredient.item(),ingredient.count());}
    private void take(String id,int count){
        if(count<1)return;Item item=BuiltInRegistries.ITEM.getValue(Identifier.parse(id));if(item==Items.AIR)throw new IllegalArgumentException("未知建材："+id);
        for(BlockEntity entity:containers){Container inventory=(Container)entity;int start=entity==cannon?2:0;
            for(int slot=start;slot<inventory.getContainerSize()&&count>0;slot++){
                ItemStack parent=stack(entity.getBlockPos(),slot);
                if(ordinary(parent)&&parent.is(item)){int n=Math.min(count,parent.getCount());parent.shrink(n);if(bucket(item)&&parent.isEmpty())set(entity.getBlockPos(),slot,new ItemStack(Items.BUCKET));count-=n;}
                else if(parent.getItem() instanceof BlockItem block&&block.getBlock() instanceof ShulkerBoxBlock&&parent.has(DataComponents.CONTAINER)){
                    List<ItemStack> children=new ArrayList<>(parent.get(DataComponents.CONTAINER).allItemsCopyStream().toList());boolean changed=false;
                    for(int child=0;child<children.size()&&count>0;child++){
                        ItemStack material=children.get(child);if(!ordinary(material)||!material.is(item))continue;int n=Math.min(count,material.getCount());material.shrink(n);count-=n;changed=true;
                        if(bucket(item)&&material.isEmpty())children.set(child,new ItemStack(Items.BUCKET));
                    }
                    if(changed)parent.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(children));
                }
            }
            if(count==0)return;
        }
        throw new IllegalStateException("缺少建材："+new ItemStack(item).getHoverName().getString()+" × "+count);
    }
    private static boolean bucket(Item item){return item==Items.WATER_BUCKET||item==Items.LAVA_BUCKET||item==Items.POWDER_SNOW_BUCKET;}
    private static boolean ordinary(ItemStack stack){return !stack.isEmpty()&&stack.getComponentsPatch().isEmpty();}
    JsonArray changes(){JsonArray array=new JsonArray();for(var entry:after.entrySet()){
        Key key=entry.getKey();ItemStack old=before.get(key);if(ItemStack.matches(old,entry.getValue()))continue;
        JsonObject row=new JsonObject();row.add("pos",PrefabService.xyz(key.pos));row.addProperty("slot",key.slot);row.add("before",runtime.economy.encode(old));row.add("after",runtime.economy.encode(entry.getValue()));array.add(row);
    }return array;}
}
