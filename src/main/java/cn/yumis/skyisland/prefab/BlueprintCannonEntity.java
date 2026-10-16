package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.server.SkyServer;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class BlueprintCannonEntity extends BaseContainerBlockEntity implements WorldlyContainer,ExtendedMenuProvider<BlockPos> {
    public static final int SIZE=29;
    private NonNullList<ItemStack> items=NonNullList.withSize(SIZE,ItemStack.EMPTY);
    public String cannonId=UUID.randomUUID().toString();
    public long fuel;
    public String message="放入已定位的图纸与特殊弹药";
    public BlueprintCannonEntity(BlockPos pos,BlockState state){super(PrefabBlocks.CANNON_TYPE,pos,state);}
    @Override public int getContainerSize(){return SIZE;}
    @Override public ItemStack getItem(int slot){
        // Inventory overlays can probe beyond this custom container's last slot.
        return slot>=0&&slot<items.size()?super.getItem(slot):ItemStack.EMPTY;
    }
    @Override protected NonNullList<ItemStack> getItems(){return items;}
    @Override protected void setItems(NonNullList<ItemStack> values){items=values;}
    @Override protected Component getDefaultName(){return Component.translatable("block.yumis_sky_island.blueprint_cannon");}
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory){return new BlueprintCannonMenu(id,inventory,this);}
    @Override public BlockPos getScreenOpeningData(ServerPlayer player){return worldPosition;}
    @Override public boolean stillValid(Player player){return super.stillValid(player)&&player instanceof ServerPlayer sp&&SkyServer.get(sp.level().getServer()) instanceof SkyServer rt&&rt.prefabs.canOperate(sp,this);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0?stack.is(PrefabItems.BLUEPRINT):slot==1?stack.is(PrefabItems.AMMUNITION):!stack.is(PrefabItems.BLUEPRINT)&&!stack.is(PrefabItems.AMMUNITION);}
    @Override public int[] getSlotsForFace(Direction direction){int[] slots=new int[28];for(int i=0;i<28;i++)slots[i]=i+1;return slots;}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction direction){return slot!=0&&canPlaceItem(slot,stack)&&!PrefabService.locked(level,worldPosition);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction direction){return false;}
    public void serverTick(){if(level!=null){var rt=SkyServer.get(level.getServer());if(rt!=null)rt.prefabs.visit(this);}}
    @Override protected void loadAdditional(ValueInput input){super.loadAdditional(input);items=NonNullList.withSize(SIZE,ItemStack.EMPTY);ContainerHelper.loadAllItems(input,items);cannonId=input.getStringOr("cannon_id",cannonId);fuel=Math.max(0,input.getLongOr("ammunition_credits",0));}
    @Override protected void saveAdditional(ValueOutput output){super.saveAdditional(output);ContainerHelper.saveAllItems(output,items);output.putString("cannon_id",cannonId);output.putLong("ammunition_credits",fuel);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState newState){
        if(level!=null&&!level.isClientSide()){
            var rt=SkyServer.get(level.getServer());if(rt!=null){rt.prefabs.removed(this);int full=rt.prefabs.rules.ammunitionCredits();
                while(fuel>0){int credits=(int)Math.min(full,fuel);net.minecraft.world.level.block.Block.popResource(level,worldPosition,PrefabItems.ammunition(credits,full));fuel-=credits;}}
        }super.preRemoveSideEffects(pos,newState);
    }
}
