package cn.yumis.skyisland.core;

import java.util.*;

/** Exact small bipartite flow: general requirements cannot consume all specialised materials. */
public final class MaterialAllocation {
    private MaterialAllocation() {}
    public static Optional<int[]> allocate(int[] available, int[] required, boolean[][] matches) {
        int slots = available.length, requests = required.length, source = slots + requests, sink = source + 1;
        int[][] residual = new int[sink + 1][sink + 1];
        int demand = 0, supply = 0;
        for (int count : available) { if (count < 0) throw new IllegalArgumentException("Negative supply"); supply = Math.addExact(supply,count); }
        for (int count : required) { if (count < 1) throw new IllegalArgumentException("Invalid demand"); demand = Math.addExact(demand,count); }
        if (demand > supply) return Optional.empty();
        for (int i = 0; i < slots; i++) {
            residual[source][i] = available[i];
            if (matches[i].length != requests) throw new IllegalArgumentException("Material matrix size");
            for (int j = 0; j < requests; j++) if (matches[i][j]) residual[i][slots+j] = available[i];
        }
        for (int j = 0; j < requests; j++) residual[slots+j][sink] = required[j];
        int flow = 0;
        while (flow < demand) {
            int[] parent = new int[sink+1]; Arrays.fill(parent,-1); parent[source] = source;
            ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(source);
            while (!queue.isEmpty() && parent[sink] < 0) {
                int node = queue.remove();
                for (int next = 0; next <= sink; next++) if (parent[next] < 0 && residual[node][next] > 0) { parent[next] = node; queue.add(next); }
            }
            if (parent[sink] < 0) return Optional.empty();
            int amount = demand - flow;
            for (int node = sink; node != source; node = parent[node]) amount = Math.min(amount,residual[parent[node]][node]);
            for (int node = sink; node != source; node = parent[node]) { int previous = parent[node]; residual[previous][node] -= amount; residual[node][previous] += amount; }
            flow += amount;
        }
        int[] removals = new int[slots];
        for (int i = 0; i < slots; i++) removals[i] = available[i] - residual[source][i];
        return Optional.of(removals);
    }
}
