package cn.yumis.skyisland.trash;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.core.TrashState;
import cn.yumis.skyisland.server.SkyServer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** A server-owned personal filter, saved with vanilla player data in every world type. */
public final class TrashService {
    public static MenuType<TrashMenu> MENU;
    private static final Codec<TrashState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.listOf().fieldOf("samples").forGetter(TrashState::samples),
        Codec.BOOL.fieldOf("enabled").forGetter(TrashState::enabled)
    ).apply(instance, TrashState::new));
    public static final AttachmentType<TrashState> STATE = AttachmentRegistry.create(YumisSkyIsland.id("auto_trash"),
        builder -> builder.initializer(() -> TrashState.EMPTY).persistent(CODEC).copyOnDeath());

    private TrashService() {}

    public static void register() {
        MENU = Registry.register(BuiltInRegistries.MENU, YumisSkyIsland.id("auto_trash"),
            new MenuType<>(TrashMenu::new, FeatureFlags.DEFAULT_FLAGS));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) tick(player);
        });
    }

    public static TrashState state(ServerPlayer player) { return player.getAttachedOrCreate(STATE); }

    public static boolean ready(ServerPlayer player) {
        var server = player.level().getServer();
        if (!player.isAlive() || player.isSpectator() || player.isRemoved() || player.hasDisconnected()
            || server.getPlayerList().getPlayer(player.getUUID()) != player) return false;
        var runtime = SkyServer.get(server);
        if (runtime == null) return true;
        if (runtime.closing || runtime.inventoryLocks.contains(player.getUUID())
            || runtime.worlds.waitingPlayers.contains(player.getUUID())) return false;
        var receipt = runtime.state().inventoryRecovery.get(player.getUUID());
        return receipt == null || player.getAttachedOrCreate(YumisSkyIsland.TRANSACTION_SEQUENCE) >= receipt.sequence;
    }

    public static void open(ServerPlayer player) {
        if (!ready(player) || player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new TrashMenu(id, inventory), Component.literal("自动垃圾桶")));
    }

    public static void tick(ServerPlayer player) {
        if (!ready(player) || player.containerMenu instanceof TrashMenu) return;
        TrashState state = state(player);
        if (!state.enabled() || state.samples().stream().allMatch(String::isEmpty)) return;
        boolean changed = false;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && state.samples().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
    }
}
