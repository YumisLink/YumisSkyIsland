package cn.yumis.skyisland.client;

import cn.yumis.skyisland.item.Hammers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** After a hammer break, place the same block back from the player's own inventory. */
public final class HammerRefill {
    private static final List<Job> PENDING = new ArrayList<>();
    private HammerRefill() {}

    public static void capture(BlockPos aimed) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !player.isShiftKeyDown() || !Hammers.isHammer(player.getMainHandItem())) return;
        List<BlockPos> positions = SkyIslandClient.CATALOG != null && !SkyIslandClient.CATALOG.veinPreview.isEmpty()
            ? SkyIslandClient.CATALOG.veinPreview : List.of(aimed);
        for (BlockPos pos : positions) {
            if (PENDING.stream().anyMatch(job -> job.pos.equals(pos))) continue;
            BlockState state = minecraft.level.getBlockState(pos);
            Item item = state.getBlock().asItem();
            if (item == Items.AIR) continue;
            PENDING.add(new Job(pos.immutable(), item, 0));
        }
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !player.isShiftKeyDown() || minecraft.gui.screen() != null) {
            PENDING.clear();
            return;
        }
        var jobs = new ArrayList<>(PENDING);
        for (Job job : jobs) {
            job.age++;
            boolean air = minecraft.level.getBlockState(job.pos).isAir();
            if (job.age > 300 || (!air && !minecraft.options.keyAttack.isDown() && job.age > 8)) {
                PENDING.remove(job);
                continue;
            }
            if (air) {
                place(minecraft, player, job);
                PENDING.remove(job);
            }
        }
    }

    private static void place(Minecraft minecraft, LocalPlayer player, Job job) {
        int slot = find(player, job.item);
        if (slot < 0 || minecraft.gameMode == null) return;
        int selected = player.getInventory().getSelectedSlot();
        boolean swapped = false;
        try {
            if (slot < 9 && slot != selected) {
                player.getInventory().setSelectedSlot(slot);
                player.connection.send(new ServerboundSetCarriedItemPacket(slot));
            } else if (slot >= 9) {
                minecraft.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, selected, ContainerInput.SWAP, player);
                swapped = true;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = job.pos.relative(direction);
                if (player.level().getBlockState(neighbor).isAir()) continue;
                Vec3 hit = Vec3.atCenterOf(neighbor).relative(direction.getOpposite(), 0.5);
                var result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, direction.getOpposite(), neighbor, false));
                if (result.consumesAction()) break;
            }
        } finally {
            if (swapped) minecraft.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, selected, ContainerInput.SWAP, player);
            else if (slot < 9 && player.getInventory().getSelectedSlot() != selected) {
                player.getInventory().setSelectedSlot(selected);
                player.connection.send(new ServerboundSetCarriedItemPacket(selected));
            }
        }
    }

    private static int find(LocalPlayer player, Item item) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return slot;
        }
        return -1;
    }

    private static final class Job {
        private final BlockPos pos;
        private final Item item;
        private int age;
        private Job(BlockPos pos, Item item, int age) { this.pos = pos; this.item = item; this.age = age; }
    }
}
