package cn.yumis.skyisland.core;

/** Personal reserve; arrows already in the inventory do not count toward capacity. */
public record QuiverState(int count, boolean enabled) {
    public static final QuiverState EMPTY = new QuiverState(0, true);
    public QuiverState {
        if (count < 0) throw new IllegalArgumentException("箭库数量不能为负数");
    }
    public int accepted(int offered, int capacity) {
        return Math.min(Math.max(0, offered), Math.max(0, capacity - count));
    }
    public int refillCount() { return enabled ? Math.min(64, count) : 0; }
    public QuiverState withCount(int next) { return new QuiverState(next, enabled); }
    public QuiverState withEnabled(boolean next) { return new QuiverState(count, next); }
}
