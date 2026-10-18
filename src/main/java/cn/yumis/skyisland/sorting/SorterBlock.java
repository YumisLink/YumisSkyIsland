package cn.yumis.skyisland.sorting;

import cn.yumis.skyisland.server.SkyServer;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public final class SorterBlock extends BaseEntityBlock {
    public static final MapCodec<SorterBlock> CODEC=simpleCodec(SorterBlock::new);
    public SorterBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends SorterBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(BlockStateProperties.HORIZONTAL_FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,c.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new SorterEntity(p,s);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack){
        super.setPlacedBy(level,pos,state,entity,stack);
        if(entity instanceof ServerPlayer player){var rt=SkyServer.get(level.getServer());if(rt!=null)rt.sorting.placed(player,pos);}
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level w,BlockPos p,Player player,BlockHitResult hit){return open(w,p,player);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState s,Level w,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return open(w,p,player);}
    private InteractionResult open(Level world,BlockPos pos,Player player){
        if(player instanceof ServerPlayer sp){var rt=SkyServer.get(world.getServer());if(rt==null){sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("分类器仅可在本队空岛使用"));return InteractionResult.FAIL;}return rt.sorting.open(sp,pos);}
        return InteractionResult.SUCCESS;
    }
}
