package cn.yumis.skyisland.server;

import cn.yumis.skyisland.world.VoidWorlds;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Server-selected, capped 26-neighbour chain. Every extra block uses vanilla destroyBlock. */
public final class VeinMiningService {
    private final SkyServer runtime;
    private final Set<UUID> held=new HashSet<>();
    private final Set<UUID> breaking=new HashSet<>();
    private final Map<UUID,String> lastPreview=new HashMap<>();
    public VeinMiningService(SkyServer runtime){this.runtime=runtime;}
    public void setHeld(ServerPlayer player,boolean value){if(value)held.add(player.getUUID());else held.remove(player.getUUID());sendPreview(player,List.of());}
    public boolean held(ServerPlayer player){return held.contains(player.getUUID());}
    public boolean breaking(ServerPlayer player){return breaking.contains(player.getUUID());}
    public int cap(ServerPlayer player){return AccessoryService.active(runtime,player,"chain_hammer")?16:8;}
    public void tick(){
        if(runtime.tick%4!=0)return;
        for(ServerPlayer player:runtime.server.getPlayerList().getPlayers()){
            if(!held(player)){sendPreview(player,List.of());continue;}
            if(!(player.level() instanceof ServerLevel level)||!VoidWorlds.isSky(level)||player.isSpectator()){sendPreview(player,List.of());continue;}
            if(player.pick(6,1,false) instanceof BlockHitResult hit){
                BlockPos origin=hit.getBlockPos();
                BlockState state=level.getBlockState(origin);IntegerProperty age=ageProperty(state);
                sendPreview(player,age!=null&&mature(state,age)?selectCrops(level,player,origin,state,cap(player)):select(level,player,origin,state,cap(player)));
            }else sendPreview(player,List.of());
        }
    }
    public void broken(ServerPlayer player,ServerLevel level,BlockPos origin,BlockState original){
        if(!held(player)||breaking(player)||player.isCreative()||!VoidWorlds.isSky(level)||original.isAir())return;
        List<BlockPos> selected=select(level,player,origin,original,cap(player));
        breaking.add(player.getUUID());
        try {
            player.causeFoodExhaustion(0.1f);
            for(int i=1;i<selected.size();i++){
                BlockPos pos=selected.get(i);
                if(!level.getBlockState(pos).is(original.getBlock()))continue;
                if(player.gameMode.destroyBlock(pos))player.causeFoodExhaustion(0.1f);
            }
        }finally{breaking.remove(player.getUUID());sendPreview(player,List.of());}
    }
    /** Right-click replants one ripe crop; the held chain key expands the same operation. */
    public boolean harvestCrop(ServerPlayer player,ServerLevel level,BlockPos origin){
        if(player.isCreative()||player.isSpectator())return false;
        BlockState original=level.getBlockState(origin);
        IntegerProperty age=ageProperty(original);if(age==null||!mature(original,age)||!Protection.canBuild(level,player,origin))return false;
        if(plantingItem(original)==null && !(original.getBlock() instanceof SweetBerryBushBlock))return false;
        // A normal berry click remains entirely vanilla, including its age-two harvest and loot table.
        if(original.getBlock() instanceof SweetBerryBushBlock && (!held(player)||original.getValue(SweetBerryBushBlock.AGE)<3&&player.getMainHandItem().is(Items.BONE_MEAL)))return false;
        if(!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS))return false;
        List<BlockPos> positions=held(player)?selectCrops(level,player,origin,original,cap(player)):List.of(origin);
        boolean harvested=false;
        for(BlockPos pos:positions){
            BlockState state=level.getBlockState(pos);IntegerProperty property=ageProperty(state);
            if(property==null||!mature(state,property)||!Protection.canBuild(level,player,pos))continue;
            if(state.getBlock() instanceof SweetBerryBushBlock){
                harvested |= state.useWithoutItem(level,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)).consumesAction();
                continue;
            }
            List<ItemStack> drops=new ArrayList<>(Block.getDrops(state,level,pos,level.getBlockEntity(pos),player,player.getMainHandItem()));
            Item planting=plantingItem(state);
            int plantingSlot=-1;
            if(planting!=null){
                boolean paid=false;
                for(ItemStack drop:drops)if(drop.is(planting)&&!drop.isEmpty()){drop.shrink(1);paid=true;break;}
                if(!paid)for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
                    ItemStack available=player.getInventory().getItem(slot);
                    if(available.is(planting)&&!available.isEmpty()){plantingSlot=slot;paid=true;break;}
                }
                if(!paid)continue;
            }
            if(!level.getBlockState(pos).equals(state)||!level.setBlock(pos,state.setValue(property,0),3))continue;
            if(plantingSlot>=0)player.getInventory().getItem(plantingSlot).shrink(1);
            harvested=true;
            String blockId=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            String toolId=BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
            for(ItemStack drop:drops)if(!drop.isEmpty())runtime.progression.action(player,"harvest",BuiltInRegistries.ITEM.getKey(drop.getItem()).toString(),drop.getCount(),blockId,toolId);
            for(ItemStack drop:runtime.professions.harvest(player,ProfessionService.captureHarvest(state),drops))if(!drop.isEmpty())Block.popResource(level,pos,drop);
        }
        sendPreview(player,List.of());
        return harvested;
    }
    private static Item plantingItem(BlockState state){
        Block block=state.getBlock();
        if(block==Blocks.WHEAT)return Items.WHEAT_SEEDS;
        if(block==Blocks.BEETROOTS)return Items.BEETROOT_SEEDS;
        if(block==Blocks.CARROTS)return Items.CARROT;
        if(block==Blocks.POTATOES)return Items.POTATO;
        if(block==Blocks.NETHER_WART)return Items.NETHER_WART;
        if(block==Blocks.COCOA)return Items.COCOA_BEANS;
        return null;
    }
    private List<BlockPos> selectCrops(ServerLevel level,ServerPlayer player,BlockPos origin,BlockState original,int max){
        List<BlockPos> positions=new ArrayList<>();positions.add(origin);
        Set<BlockPos> seen=new HashSet<>();seen.add(origin);
        for(int i=0;i<positions.size()&&positions.size()<max;i++){
            BlockPos around=positions.get(i);
            for(int dx=-1;dx<=1&&positions.size()<max;dx++)for(int dy=-1;dy<=1&&positions.size()<max;dy++)for(int dz=-1;dz<=1&&positions.size()<max;dz++){
                if(dx==0&&dy==0&&dz==0)continue;
                BlockPos next=around.offset(dx,dy,dz);
                if(!seen.add(next)||!level.hasChunkAt(next)||!Protection.canBuild(level,player,next))continue;
                BlockState state=level.getBlockState(next);IntegerProperty property=ageProperty(state);
                if(state.is(original.getBlock())&&property!=null&&mature(state,property))positions.add(next);
            }
        }
        return positions;
    }
    private static IntegerProperty ageProperty(BlockState state){
        Block block=state.getBlock();
        if(block instanceof CropBlock) return state.hasProperty(BlockStateProperties.AGE_7)?BlockStateProperties.AGE_7:state.hasProperty(BlockStateProperties.AGE_3)?BlockStateProperties.AGE_3:null;
        if(block instanceof NetherWartBlock)return NetherWartBlock.AGE;
        if(block instanceof CocoaBlock)return CocoaBlock.AGE;
        if(block instanceof SweetBerryBushBlock)return SweetBerryBushBlock.AGE;
        return null;
    }
    private static boolean mature(BlockState state,IntegerProperty age){return state.getBlock() instanceof SweetBerryBushBlock?state.getValue(age)>=2:state.getValue(age)==Collections.max(age.getPossibleValues());}
    public List<BlockPos> select(ServerLevel level,ServerPlayer player,BlockPos origin,BlockState original,int max){
        if(original.isAir()||!level.hasChunkAt(origin)||!Protection.canBuild(level,player,origin))return List.of();
        List<BlockPos> result=new ArrayList<>();result.add(origin);
        Set<BlockPos> seen=new HashSet<>();seen.add(origin);
        for(int i=0;i<result.size()&&result.size()<max;i++){
            BlockPos around=result.get(i);
            for(int dx=-1;dx<=1&&result.size()<max;dx++)for(int dy=-1;dy<=1&&result.size()<max;dy++)for(int dz=-1;dz<=1&&result.size()<max;dz++){
                if(dx==0&&dy==0&&dz==0)continue;
                BlockPos next=around.offset(dx,dy,dz);
                if(!seen.add(next)||!level.hasChunkAt(next)||!level.getBlockState(next).is(original.getBlock())||!Protection.canBuild(level,player,next))continue;
                result.add(next);
            }
        }
        return result;
    }
    private void sendPreview(ServerPlayer player,List<BlockPos> positions){
        JsonArray list=new JsonArray();for(BlockPos pos:positions){JsonArray point=new JsonArray();point.add(pos.getX());point.add(pos.getY());point.add(pos.getZ());list.add(point);}
        String serialized=list.toString();if(Objects.equals(lastPreview.put(player.getUUID(),serialized),serialized))return;
        JsonObject data=new JsonObject();data.add("blocks",list);runtime.send(player,"vein_preview",data);
    }
    public void disconnect(ServerPlayer player){held.remove(player.getUUID());breaking.remove(player.getUUID());lastPreview.remove(player.getUUID());}
}
