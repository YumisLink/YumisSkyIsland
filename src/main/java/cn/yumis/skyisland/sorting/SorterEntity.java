package cn.yumis.skyisland.sorting;

import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class SorterEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
    public UUID id=UUID.randomUUID();
    public SorterEntity(BlockPos pos,BlockState state){super(SortingContent.ENTITY,pos,state);}
    @Override public Component getDisplayName(){return Component.translatable("block.yumis_sky_island.sorter");}
    @Override public AbstractContainerMenu createMenu(int syncId,Inventory inventory,Player player){return new SorterMenu(syncId,inventory,worldPosition);}
    @Override public BlockPos getScreenOpeningData(ServerPlayer player){return worldPosition;}
    @Override protected void loadAdditional(ValueInput input){super.loadAdditional(input);try{id=UUID.fromString(input.getStringOr("device_id",id.toString()));}catch(IllegalArgumentException ignored){}}
    @Override protected void saveAdditional(ValueOutput output){super.saveAdditional(output);output.putString("device_id",id.toString());}
}
