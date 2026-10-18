package cn.yumis.skyisland.sorting;

import cn.yumis.skyisland.server.SkyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** The only real slots are the player's 36 slots. B samples are rendered separately and never accept items. */
public final class SorterMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public static final int WIDTH=280,HEIGHT=222,INVENTORY_X=12,INVENTORY_Y=138,HOTBAR_Y=196;
    public SorterMenu(int id,Inventory inventory,BlockPos pos){
        super(SortingContent.MENU,id);this.pos=pos;
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,INVENTORY_X+18*col,INVENTORY_Y+18*row));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,INVENTORY_X+18*col,HOTBAR_Y));
    }
    @Override public boolean stillValid(Player player){
        if(!(player instanceof ServerPlayer sp))return true;
        var rt=SkyServer.get(sp.level().getServer());return rt!=null&&rt.sorting.canOperate(sp,pos)&&sp.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)<=64;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();if(!moveItemStackTo(stack,index<27?27:0,index<27?36:27,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();return copy;
    }
}
