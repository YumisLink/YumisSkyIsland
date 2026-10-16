package cn.yumis.skyisland.prefab;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class BlueprintCannonMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final Container container;
    public BlueprintCannonMenu(int id,Inventory inventory,BlockPos pos){this(id,inventory,new SimpleContainer(BlueprintCannonEntity.SIZE),pos);}
    public BlueprintCannonMenu(int id,Inventory inventory,BlueprintCannonEntity cannon){this(id,inventory,cannon,cannon.getBlockPos());}
    private BlueprintCannonMenu(int id,Inventory inventory,Container container,BlockPos pos){
        super(PrefabBlocks.CANNON_MENU,id);this.pos=pos;this.container=container;container.startOpen(inventory.player);
        addSlot(new Slot(container,0,18,26){@Override public boolean mayPlace(ItemStack stack){return stack.is(PrefabItems.BLUEPRINT)&&!PrefabService.locked(inventory.player.level(),pos);}});
        addSlot(new Slot(container,1,54,26){@Override public boolean mayPlace(ItemStack stack){return stack.is(PrefabItems.AMMUNITION);}});
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)addSlot(new Slot(container,2+row*9+column,8+column*18,66+row*18){@Override public boolean mayPlace(ItemStack stack){return container.canPlaceItem(getContainerSlot(),stack);}});
        addStandardInventorySlots(inventory,8,140);
    }
    @Override public boolean stillValid(Player player){return container.stillValid(player);}
    @Override public void removed(Player player){super.removed(player);container.stopOpen(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;ItemStack stack=slot.getItem(),copy=stack.copy();
        if(index<BlueprintCannonEntity.SIZE){if(!moveItemStackTo(stack,BlueprintCannonEntity.SIZE,slots.size(),true))return ItemStack.EMPTY;}
        else {int start=stack.is(PrefabItems.BLUEPRINT)?0:stack.is(PrefabItems.AMMUNITION)?1:2,end=start<2?start+1:BlueprintCannonEntity.SIZE;if(!moveItemStackTo(stack,start,end,false))return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();return copy;
    }
}
