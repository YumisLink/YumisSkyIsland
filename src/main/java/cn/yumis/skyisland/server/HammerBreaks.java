package cn.yumis.skyisland.server;

import net.minecraft.server.level.ServerPlayer;

/** Thread-local target for chain-mining item drops spawned during the current break. */
public final class HammerBreaks {
    private static final ThreadLocal<ServerPlayer> FEET = new ThreadLocal<>();
    private HammerBreaks() {}
    public static void feet(ServerPlayer player) { if (player == null) FEET.remove(); else FEET.set(player); }
    public static ServerPlayer feet() { return FEET.get(); }
}