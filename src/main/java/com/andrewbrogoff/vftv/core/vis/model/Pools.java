package com.andrewbrogoff.vftv.core.vis.model;

import com.andrewbrogoff.vftv.api.vis.VisLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A wand's pools (wands spec, "Vis storage"): one per vis aspect, each read clamped to the rod's
 * capacity; entries for aspects that aren't vis aspects are kept as they are and ignored.
 */
public final class Pools {
    private Pools() {}

    /**
     * A write's result.
     *
     * @param pools the entries to store
     * @param dropped the ignored entries dropped to stay within 16, ascending
     */
    public record Written(SortedMap<String, Integer> pools, List<String> dropped) {}

    /** A pool's value as read: the stored amount clamped to {@code capacity}; missing is 0. */
    public static int read(Map<String, Integer> stored, String aspect, int capacity) {
        return Math.min(stored.getOrDefault(aspect, 0), capacity);
    }

    /**
     * The entries to store after a change: every vis aspect's pool clamped to the capacity (from
     * {@code changed} where given, else the stored amount), zero pools omitted, every other entry
     * carried through unchanged; if more than 16 remain, the ignored entries with the highest ids
     * are dropped.
     */
    public static Written write(
            Map<String, Integer> stored,
            Set<String> visAspects,
            Map<String, Integer> changed,
            int capacity) {
        SortedMap<String, Integer> out = new TreeMap<>();
        List<String> ignored = new ArrayList<>();
        stored.forEach(
                (aspect, amount) -> {
                    if (!visAspects.contains(aspect)) {
                        out.put(aspect, amount);
                        ignored.add(aspect);
                    }
                });
        for (String aspect : visAspects) {
            int value =
                    Math.min(
                            changed.getOrDefault(aspect, read(stored, aspect, capacity)), capacity);
            if (value > 0) {
                out.put(aspect, value);
            }
        }
        List<String> dropped = new ArrayList<>();
        ignored.sort(null);
        while (out.size() > VisLimits.MAX_VIS_ENTRIES && !ignored.isEmpty()) {
            String last = ignored.removeLast();
            out.remove(last);
            dropped.add(0, last);
        }
        return new Written(out, dropped);
    }
}
