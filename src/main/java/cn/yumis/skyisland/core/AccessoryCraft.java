package cn.yumis.skyisland.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Crafted accessories keep their ingredient unlocks and pause that chain while equipped. */
public final class AccessoryCraft {
    public static final String FISHING_LINE = "fishing_line";
    public static final String TACKLE_BOX = "tackle_box";
    public static final String ANGLER_BAG = "angler_bag";
    public static final int DRAWABLE_COUNT = 31;
    private static final Map<String, List<String>> RECIPES = new LinkedHashMap<>();
    static {
        RECIPES.put(FISHING_LINE, List.of("bobber", "fisher_charm"));
        RECIPES.put(TACKLE_BOX, List.of("treasure_compass"));
        RECIPES.put(ANGLER_BAG, List.of(FISHING_LINE, TACKLE_BOX));
    }
    private AccessoryCraft() {}
    public static boolean crafted(String id) { return RECIPES.containsKey(id); }
    public static List<String> materials(String id) { return RECIPES.getOrDefault(id, List.of()); }
    public static List<String> missing(Set<String> unlocked, String id) {
        List<String> missing = new ArrayList<>();
        for (String material : materials(id)) if (!unlocked.contains(material)) missing.add(material);
        return missing;
    }
    /** Direct and nested ingredients, so a bag also pauses the line's parts. */
    public static Set<String> closure(String id) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(materials(id));
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            if (result.add(next)) pending.addAll(materials(next));
        }
        return result;
    }
    /** Equipping this accessory frees every ingredient and every finished piece that contains it. */
    public static Set<String> displaced(String id) {
        LinkedHashSet<String> displaced = new LinkedHashSet<>(closure(id));
        for (String product : RECIPES.keySet()) if (closure(product).contains(id)) displaced.add(product);
        displaced.remove(id);
        return displaced;
    }
    public static boolean fits(Collection<String> active, String id, int slots) {
        if (active.contains(id)) return true;
        int size = active.size();
        for (String removed : displaced(id)) if (active.contains(removed)) size--;
        return size < slots;
    }
    public static void enable(Set<String> active, String id) {
        active.removeAll(displaced(id));
        active.add(id);
    }
    public static List<String> drawable(Collection<String> catalog) {
        List<String> result = new ArrayList<>();
        for (String id : catalog) if (!crafted(id)) result.add(id);
        return result;
    }
}
