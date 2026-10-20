package cn.yumis.skyisland.trash;

import cn.yumis.skyisland.core.TrashState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Phantom filter slots are never passed to vanilla's item-transfer code. */
public final class TrashMenu extends AbstractContainerMenu {
    public static final int WIDTH = 280, HEIGHT = 222;
    private final SimpleContainer samples = new SimpleContainer(TrashState.CAPACITY);
    private final DataSlot enabled = DataSlot.standalone();

    public TrashMenu(int id, Inventory inventory) {
        super(TrashService.MENU, id);
        for (int row = 0; row < 6; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(samples, row * 9 + col, 8 + col * 18, 18 + row * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public boolean isFake() { return true; }
            });
        }
        addStandardInventorySlots(inventory, 8, 140);
        addDataSlot(enabled);
        enabled.set(1);
        if (inventory.player instanceof ServerPlayer player) refresh(TrashService.state(player));
    }

    public boolean enabled() { return enabled.get() != 0; }
    public int sampleCount() { int count = 0; for (int i = 0; i < 54; i++) if (!samples.getItem(i).isEmpty()) count++; return count; }
    @Override public boolean stillValid(Player player) { return !(player instanceof ServerPlayer online) || TrashService.ready(online); }
    private boolean editable(Player player) { return !(player instanceof ServerPlayer online) || online.containerMenu == this && TrashService.ready(online); }

    private void refresh(TrashState state) {
        for (int i = 0; i < 54; i++) {
            Identifier id = Identifier.tryParse(state.samples().get(i));
            samples.setItem(i, id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.getValue(id)) : ItemStack.EMPTY);
        }
        enabled.set(state.enabled() ? 1 : 0);
    }

    private void sample(Player player, int index, ItemStack source) {
        if (!(player instanceof ServerPlayer online)) return; // Server sync is authoritative; the cursor never changes.
        TrashState before = TrashService.state(online);
        String id = source.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
        if (!id.isEmpty() && before.samples().contains(id)) return;
        if (index < 0) index = before.samples().indexOf("");
        if (index < 0) { online.sendOverlayMessage(Component.literal("垃圾桶名单已满（54种）")); return; }
        TrashState next = before.withSample(index, id);
        online.setAttached(TrashService.STATE, next);
        refresh(next);
        broadcastChanges();
    }

    @Override public void clicked(int index, int button, ContainerInput input, Player player) {
        if (!editable(player)) return;
        if (index >= 0 && index < 54) {
            resetQuickCraft();
            if (input == ContainerInput.PICKUP && (button == 0 || button == 1)) sample(player, index, getCarried());
            return;
        }
        if (input == ContainerInput.QUICK_MOVE) { quickMoveStack(player, index); return; }
        super.clicked(index, button, input, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (editable(player) && index >= 54 && index < slots.size() && slots.get(index).hasItem())
            sample(player, -1, slots.get(index).getItem());
        return ItemStack.EMPTY;
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!editable(player) || !(player instanceof ServerPlayer online) || button < 0 || button > 1) return false;
        TrashState before = TrashService.state(online);
        TrashState next = button == 0 ? before.withEnabled(!before.enabled()) : before.cleared();
        online.setAttached(TrashService.STATE, next);
        refresh(next);
        broadcastChanges();
        return true;
    }

    @Override public boolean canDragTo(Slot slot) { return slot.container != samples && super.canDragTo(slot); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container != samples && super.canTakeItemForPickAll(stack, slot); }
}
