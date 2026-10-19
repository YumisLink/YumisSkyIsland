package cn.yumis.skyisland.furnace;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.server.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Advances vanilla ticks in batches that cannot cross a cooking or fuel boundary.
 * Vanilla itself handles each boundary: fuel remainders, output components, recipes, XP and wet sponges.
 * Progress and fuel remain in vanilla units and therefore use the native save/menu fields unchanged. */
public final class UpgradedFurnaceEntity extends AbstractFurnaceBlockEntity {
    public UpgradedFurnaceEntity(BlockPos pos,BlockState state){super(FurnaceContent.ENTITY,pos,state,RecipeType.SMELTING);}
    @Override protected Component getDefaultName(){return getBlockState().getBlock().getName();}
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory){return new FurnaceMenu(id,inventory,this,dataAccess);}
    public static void serverTick(ServerLevel level,BlockPos pos,UpgradedFurnaceEntity furnace){
        if(Protection.locked(level,pos))return;
        String id=BuiltInRegistries.BLOCK.getKey(furnace.getBlockState().getBlock()).getPath();
        String tier=id.substring(0,id.length()-"_furnace".length());
        int remaining=YumisSkyIsland.CONFIG.furnaces.speed(tier);
        boolean advanced=false;
        while(remaining>0){
            int beforeProgress=furnace.dataAccess.get(DATA_COOKING_PROGRESS);
            int beforeInput=furnace.getItem(0).getCount();
            // Always delegate ignition, completion, fuel exhaustion and idle transitions to vanilla.
            AbstractFurnaceBlockEntity.serverTick(level,pos,furnace.getBlockState(),furnace);
            remaining--;
            if(remaining==0)break;
            int lit=furnace.dataAccess.get(DATA_LIT_TIME);
            int progress=furnace.dataAccess.get(DATA_COOKING_PROGRESS);
            int total=furnace.dataAccess.get(DATA_COOKING_TOTAL_TIME);
            boolean consumed=beforeInput!=furnace.getItem(0).getCount();
            if(!consumed&&progress==beforeProgress+1&&lit>1){
                // Leave the final tick at either boundary for the next vanilla call.
                int skip=Math.min(remaining,Math.min(lit-1,total-progress-1));
                if(skip>0){
                    furnace.dataAccess.set(DATA_LIT_TIME,lit-skip);
                    furnace.dataAccess.set(DATA_COOKING_PROGRESS,progress+skip);
                    remaining-=skip;advanced=true;
                }
            }else if(!consumed&&progress==beforeProgress){
                if(lit==0)break; // Stable idle furnace cannot change until a slot changes.
                int skip=Math.min(remaining,lit-1);
                if(skip>0){furnace.dataAccess.set(DATA_LIT_TIME,lit-skip);remaining-=skip;advanced=true;}
            }else if(!consumed&&lit==0&&progress<beforeProgress){
                // Native unlit cooling is two work units per virtual tick.
                furnace.dataAccess.set(DATA_COOKING_PROGRESS,Math.max(0,progress-2*remaining));
                advanced=true;break;
            }
        }
        if(advanced)furnace.setChanged();
    }
}
