package cn.yumis.skyisland.block;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.redstone.Orientation;
public final class ShippingChestBlock extends ChestBlock {
    public static final MapCodec<ShippingChestBlock> CODEC = simpleCodec(ShippingChestBlock::new);
    public ShippingChestBlock(BlockBehaviour.Properties properties) { super(() -> ModBlocks.SHIPPING_TYPE, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties); }
    @Override public MapCodec<? extends ChestBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ShippingChestEntity(pos, state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { BlockState s = super.getStateForPlacement(context); return s == null ? null : s.setValue(TYPE, ChestType.SINGLE); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos adjacent, BlockState adjacentState, RandomSource random) { return super.updateShape(state, level, ticks, pos, direction, adjacent, adjacentState, random).setValue(TYPE, ChestType.SINGLE); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if (level instanceof net.minecraft.server.level.ServerLevel world && level.getBlockEntity(pos) instanceof ShippingChestEntity box) {
            box.freshPlacement();
            var runtime = cn.yumis.skyisland.server.SkyServer.get(world.getServer());
            if (runtime != null && placer instanceof net.minecraft.server.level.ServerPlayer player && level.getBlockState(pos).getBlock() == this)
                runtime.progression.shippingPlaced(player,world,pos);
        }
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moved) { super.neighborChanged(state,level,pos,block,orientation,moved); if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ShippingChestEntity box) box.powerChanged(); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return super.getTicker(level,state,type);
        return type == ModBlocks.SHIPPING_TYPE ? (world,pos,s,be) -> ((ShippingChestEntity)be).serverTick() : null;
    }
}
