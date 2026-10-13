package cn.yumis.skyisland.core;

import java.util.OptionalLong;

/** Territories align to Minecraft chunks; exclusive upper bounds avoid double ownership. */
public final class ClaimGrid {
    public static final int SIZE = 10_000;
    public static final int COLUMNS = 2_999;
    public static final long CAPACITY = (long) COLUMNS * COLUMNS;
    public static final int LOBBY_X = -20_000_000, LOBBY_Z = -20_000_000, ISLAND_Y = 80;
    public static final int LOBBY_Y = ISLAND_Y;
    public record Region(long slot, int minX, int minZ) {
        public int maxX() { return minX + SIZE; }
        public int maxZ() { return minZ + SIZE; }
        public int centerX() { return minX + SIZE / 2; }
        public int centerZ() { return minZ + SIZE / 2; }
        public boolean contains(int x, int z) { return x >= minX && x < minX + SIZE && z >= minZ && z < minZ + SIZE; }
    }
    private ClaimGrid() {}
    public static Region region(long slot) {
        if (slot < 0 || slot >= CAPACITY) throw new IllegalArgumentException("No available island grid slot");
        return new Region(slot, (int) (slot % COLUMNS) * SIZE, (int) (slot / COLUMNS) * SIZE);
    }
    public static OptionalLong slotAt(int x, int z) {
        if (x < 0 || z < 0 || x >= SIZE * COLUMNS || z >= SIZE * COLUMNS) return OptionalLong.empty();
        return OptionalLong.of((long) (z / SIZE) * COLUMNS + x / SIZE);
    }
    public static boolean isLobby(int x, int z) { return x >= LOBBY_X && x < LOBBY_X + 20 && z >= LOBBY_Z && z < LOBBY_Z + 20; }
}
