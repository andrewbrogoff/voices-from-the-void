package com.andrewbrogoff.vftv.core.hush.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * One due queue (Hush spec, "Scheduling" and "Pealing"): an ordered set of {@code (due, key)} with
 * a map from key to its due, so a key is in it at most once. Each change is O(log n).
 */
public final class DueQueue {
    /**
     * One entry.
     *
     * @param due the tick it is due
     * @param key the chunk's {@code pack()} or the bell's {@code asLong()}
     */
    public record Entry(long due, long key) {}

    private static final Comparator<Entry> ORDER =
            Comparator.comparingLong(Entry::due).thenComparingLong(Entry::key);

    private final TreeSet<Entry> order = new TreeSet<>(ORDER);
    private final Map<Long, Long> dues = new HashMap<>();

    /** Whether {@code key} is queued. */
    public boolean contains(long key) {
        return dues.containsKey(key);
    }

    /** The due of {@code key}, or null if it isn't queued. */
    public @Nullable Long due(long key) {
        return dues.get(key);
    }

    /** Queues {@code key} at {@code due} unless it is queued already, which keeps its due. */
    public void offer(long key, long due) {
        if (!dues.containsKey(key)) {
            dues.put(key, due);
            order.add(new Entry(due, key));
        }
    }

    /** Queues {@code key} at {@code due}, replacing any due it had. */
    public void put(long key, long due) {
        remove(key);
        offer(key, due);
    }

    /** Removes {@code key}; whether it was queued. */
    public boolean remove(long key) {
        Long due = dues.remove(key);
        if (due == null) {
            return false;
        }
        order.remove(new Entry(due, key));
        return true;
    }

    /** The oldest entry, or null when empty. */
    public @Nullable Entry first() {
        return order.isEmpty() ? null : order.first();
    }

    /** The number of keys queued. */
    public int size() {
        return dues.size();
    }

    /** Every key queued, in due order. */
    public List<Long> keys() {
        List<Long> keys = new ArrayList<>(order.size());
        order.forEach(e -> keys.add(e.key()));
        return keys;
    }

    /** Empties the queue. */
    public void clear() {
        order.clear();
        dues.clear();
    }
}
