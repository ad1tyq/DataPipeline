package com.example.datapipeline_server.pipeline;

import java.util.HashMap;
import java.util.Map;

/**
 * Disjoint-set forest with union-by-rank and path compression. Used to group
 * rows into clusters based on co-occurring deterministic match keys.
 *
 * <p>Not thread-safe by design — the pipeline confines mutation to one thread
 * after the parallel fetch stage completes.
 */
public final class UnionFind<T> {

    private final Map<T, T> parent = new HashMap<>();
    private final Map<T, Integer> rank = new HashMap<>();

    public void makeSet(T x) {
        parent.putIfAbsent(x, x);
        rank.putIfAbsent(x, 0);
    }

    public T find(T x) {
        var p = parent.computeIfAbsent(x, k -> k);
        if (!p.equals(x)) {
            p = find(p);
            parent.put(x, p);          // path compression
        }
        return p;
    }

    public void union(T a, T b) {
        var ra = find(a);
        var rb = find(b);
        if (ra.equals(rb)) return;
        int rankA = rank.getOrDefault(ra, 0);
        int rankB = rank.getOrDefault(rb, 0);
        if      (rankA < rankB) parent.put(ra, rb);
        else if (rankA > rankB) parent.put(rb, ra);
        else { parent.put(rb, ra); rank.put(ra, rankA + 1); }
    }
}
