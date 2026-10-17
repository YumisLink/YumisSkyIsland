package cn.yumis.skyisland.server;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/** 空桶对着黑曜石使用时，消耗该方块和一只桶，得到熔岩桶。 */
public final class ObsidianBucket {
    private ObsidianBucket() {}

    public static void register() {
        UseBlockCallback.EVENT.register(ObsidianBucket::use);
    }

    private static InteractionResult use(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (!player.getItemInHand(hand).is(Items.BUCKET) || !world.getBlockState(pos).is(Blocks.OBSIDIAN)) {
            return InteractionResult.PASS;
        }
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!Protection.canBuild(level, serverPlayer, pos) || !level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
            return InteractionResult.FAIL;
        }
        if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) return InteractionResult.FAIL;
        ItemStack lava = new ItemStack(Items.LAVA_BUCKET);
        serverPlayer.awardStat(Stats.ITEM_USED.get(Items.BUCKET));
        level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(serverPlayer, GameEvent.FLUID_PICKUP, pos);
        ItemStack result = ItemUtils.createFilledResult(serverPlayer.getItemInHand(hand), serverPlayer, lava);
        serverPlayer.setItemInHand(hand, result);
        serverPlayer.inventoryMenu.broadcastChanges();
        CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, lava);
        return InteractionResult.SUCCESS;
    }
}
