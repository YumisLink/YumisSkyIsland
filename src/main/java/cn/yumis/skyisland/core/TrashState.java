package cn.yumis.skyisland.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Immutable personal filter IDs; empty strings preserve the positions of removed samples. */
public record TrashState(List<String> samples, boolean enabled) {
    public static final int CAPACITY = 54;
    public static final TrashState EMPTY = new TrashState(List.of(), true);

    public TrashState {
        if (samples.size() > CAPACITY) throw new IllegalArgumentException("垃圾桶最多54种物品");
        var normalized = new ArrayList<String>(Collections.nCopies(CAPACITY, ""));
        var seen = new HashSet<String>();
        for (int i = 0; i < samples.size(); i++) {
            String id = samples.get(i);
            if (id != null && !id.isEmpty() && seen.add(id)) normalized.set(i, id);
        }
        samples = List.copyOf(normalized);
    }

    public TrashState withSample(int slot, String id) {
        if (slot < 0 || slot >= CAPACITY || !id.isEmpty() && samples.contains(id)) return this;
        var next = new ArrayList<>(samples);
        next.set(slot, id);
        return new TrashState(next, enabled);
    }

    public TrashState withEnabled(boolean value) { return new TrashState(samples, value); }
    public TrashState cleared() { return new TrashState(List.of(), enabled); }
}
