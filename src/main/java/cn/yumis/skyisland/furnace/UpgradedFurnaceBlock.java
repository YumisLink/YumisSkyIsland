package cn.yumis.skyisland.furnace;

import cn.yumis.skyisland.server.Protection;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

public final class UpgradedFurnaceBlock extends AbstractFurnaceBlock {
    public static final MapCodec<UpgradedFurnaceBlock> CODEC=simpleCodec(UpgradedFurnaceBlock::new);
    public UpgradedFurnaceBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends AbstractFurnaceBlock> codec(){return CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new UpgradedFurnaceEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level instanceof ServerLevel server?createTickerHelper(type,FurnaceContent.ENTITY,(_,pos,_,entity)->UpgradedFurnaceEntity.serverTick(server,pos,entity)):null;
    }
    @Override protected void openContainer(Level level,BlockPos pos,Player player){
        if(level instanceof ServerLevel server&&player instanceof ServerPlayer online&&Protection.canBuild(server,online,pos)
                &&level.getBlockEntity(pos) instanceof UpgradedFurnaceEntity furnace){
            online.openMenu(furnace);online.awardStat(Stats.INTERACT_WITH_FURNACE);
        }
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        Blocks.FURNACE.animateTick(state,level,pos,random);
    }
}
