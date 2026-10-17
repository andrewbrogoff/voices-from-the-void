package com.andrewbrogoff.vftv.core.crucible.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A crucible's pool (crucible spec, "The pool"): aspect id to a positive amount, at most {@value
 * #MAX_ENTRIES} entries and a total at most {@value #MAX_TOTAL}. Ids the registry no longer holds
 * are kept like any other. Every change bumps {@link #revision}. Server thread only.
 */
public final class Pool {
    /** The most entries: {@code CrucibleLimits.MAX_POOL_ENTRIES}. */
    public static final int MAX_ENTRIES = 64;

    /** The format bound on the total: {@code CrucibleLimits.MAX_POOL_TOTAL}. */
    public static final int MAX_TOTAL = 10_000;

    private final TreeMap<String, Integer> amounts = new TreeMap<>();
    private int revision;

    /** An empty pool. */
    public Pool() {}

    /** A pool holding {@code contents}. */
    public Pool(Map<String, Integer> contents) {
        amounts.putAll(contents);
    }

    /** Bumped by every change; wraps. */
    public int revision() {
        return revision;
    }

    /** The total, missing ids included. */
    public long total() {
        long t = 0;
        for (int v : amounts.values()) {
            t += v;
        }
        return t;
    }

    /** The amount of one id, 0 when absent. */
    public int amount(String id) {
        return amounts.getOrDefault(id, 0);
    }

    /** The number of entries. */
    public int size() {
        return amounts.size();
    }

    /** Whether the pool holds nothing. */
    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /** An unmodifiable copy, in ascending id. */
    public SortedMap<String, Integer> snapshot() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(amounts));
    }

    /** Replaces the contents with {@code contents}. */
    public void restore(Map<String, Integer> contents) {
        amounts.clear();
        amounts.putAll(contents);
        revision++;
    }

    /** Empties the pool. */
    public void clear() {
        amounts.clear();
        revision++;
    }

    /**
     * The fit rule (crucible spec, "Dissolving"): how many of {@code count} items whose values are
     * {@code v} dissolve whole, in {@code long} arithmetic. Zero when the id union would pass
     * {@value #MAX_ENTRIES}, when the room left under {@code capacity} holds none, or for empty
     * values.
     */
    public int fit(Map<String, Integer> v, int count, int capacity) {
        long t = 0;
        for (int a : v.values()) {
            t += a;
        }
        if (t <= 0 || count <= 0) {
            return 0;
        }
        Set<String> union = new HashSet<>(amounts.keySet());
        union.addAll(v.keySet());
        if (union.size() > MAX_ENTRIES) {
            return 0;
        }
        long room = Math.max(0, capacity - total());
        return (int) Math.min(count, room / t);
    }

    /** Adds {@code k} times {@code v}; the caller has checked the fit. */
    public void credit(Map<String, Integer> v, int k) {
        v.forEach((id, a) -> amounts.merge(id, a * k, Integer::sum));
        revision++;
    }

    /** Whether every cost aspect's amount is at least its cost. */
    public boolean affordable(Map<String, Integer> cost) {
        for (Map.Entry<String, Integer> e : cost.entrySet()) {
            if (amount(e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Cost minus amount for each short aspect, in ascending id; empty when affordable. */
    public SortedMap<String, Integer> shortfall(Map<String, Integer> cost) {
        TreeMap<String, Integer> out = new TreeMap<>();
        cost.forEach(
                (id, c) -> {
                    int have = amount(id);
                    if (have < c) {
                        out.put(id, c - have);
                    }
                });
        return out;
    }

    /**
     * Takes {@code cost} from the pool.
     *
     * @throws IllegalStateException when it is not affordable
     */
    public void debit(Map<String, Integer> cost) {
        if (!affordable(cost)) {
            throw new IllegalStateException("not affordable: " + cost);
        }
        cost.forEach((id, c) -> take(id, c));
        revision++;
    }

    /** Takes up to {@code amount} of one id; returns how much was taken. */
    public int extract(String id, int amount) {
        int n = Math.min(amount, amount(id));
        if (n > 0) {
            take(id, n);
            revision++;
        }
        return n;
    }

    private void take(String id, int n) {
        int left = amount(id) - n;
        if (left > 0) {
            amounts.put(id, left);
        } else {
            amounts.remove(id);
        }
    }

    @Override
    public String toString() {
        return "Pool" + amounts;
    }
}
