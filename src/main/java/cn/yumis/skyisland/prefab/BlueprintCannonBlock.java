package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.server.SkyServer;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class BlueprintCannonBlock extends BaseEntityBlock {
    public static final MapCodec<BlueprintCannonBlock> CODEC=simpleCodec(BlueprintCannonBlock::new);
    private static final VoxelShape SHAPE=Shapes.or(Block.box(1,0,1,15,5,15),Block.box(4,5,4,12,14,12));
    public BlueprintCannonBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends BlueprintCannonBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.HORIZONTAL_FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BlueprintCannonEntity(pos,state);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return open(level,pos,player);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return open(level,pos,player);}
    private InteractionResult open(Level level,BlockPos pos,Player player) {
        if(player instanceof ServerPlayer serverPlayer&&level.getBlockEntity(pos) instanceof BlueprintCannonEntity cannon) {
            var rt=SkyServer.get(serverPlayer.level().getServer());
            if(rt==null||!rt.prefabs.canOperate(serverPlayer,cannon)){SkyServer.tell(serverPlayer,"蓝图大炮仅限本队成员在本队空岛操作");return InteractionResult.FAIL;}
            serverPlayer.openMenu(cannon);rt.prefabs.sendCannon(serverPlayer,cannon);
        }return InteractionResult.SUCCESS;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide()?null:createTickerHelper(type,PrefabBlocks.CANNON_TYPE,(_,_,_,cannon)->cannon.serverTick());
    }
}
