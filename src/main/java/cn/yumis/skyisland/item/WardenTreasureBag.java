package cn.yumis.skyisland.item;

import cn.yumis.skyisland.server.WardenBags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Opens into the current warden-bag configuration. Already held bags follow a later reload. */
public final class WardenTreasureBag extends Item {
    public WardenTreasureBag(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player user, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(user instanceof ServerPlayer player)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        WardenBags.give(player);
        return InteractionResult.SUCCESS;
    }
}
