package com.andrewbrogoff.vftv.core.crucible.model;

import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.jspecify.annotations.Nullable;

/** The crucible's pure functions: phase, ejection, tint and card cells (crucible spec). */
public final class CrucibleMath {
    /** The ejection's upward speed. */
    public static final double UP = 0.3;

    /** The ejection's speed toward the thrower. */
    public static final double TOWARD = 0.15;

    /** Below this squared distance the thrower counts as coincident. */
    public static final double COINCIDENT = 1e-8;

    private CrucibleMath() {}

    /** {@code phase(CRUC, [pos.asLong()], interval)}. */
    public static long phase(long pos, int interval) {
        return Hashes.phase(Salts.CRUC, new long[] {pos}, interval);
    }

    /**
     * A refused entity's or crafted result's velocity: up {@value #UP}, and {@value #TOWARD} toward
     * the thrower from the crucible's centre column.
     *
     * @param thrower the thrower's (x, z) in the crucible's level, or null for none or elsewhere
     * @return {x, y, z}
     */
    public static double[] ejection(int blockX, int blockZ, double @Nullable [] thrower) {
        if (thrower == null) {
            return new double[] {0, UP, 0};
        }
        double dx = thrower[0] - (blockX + 0.5);
        double dz = thrower[1] - (blockZ + 0.5);
        double l2 = dx * dx + dz * dz;
        if (l2 < COINCIDENT) {
            return new double[] {0, UP, 0};
        }
        double l = Math.sqrt(l2);
        return new double[] {TOWARD * dx / l, UP, TOWARD * dz / l};
    }

    /**
     * The liquid's dominant aspect: the registry aspect with the largest amount, ties to the lowest
     * id, missing ids skipped; {@code fallback} when none.
     */
    public static String dominant(
            Collection<String> registry, Map<String, Integer> pool, String fallback) {
        String best = null;
        int amount = 0;
        for (Map.Entry<String, Integer> e : pool.entrySet()) {
            if (!registry.contains(e.getKey())) {
                continue;
            }
            if (best == null
                    || e.getValue() > amount
                    || (e.getValue() == amount && e.getKey().compareTo(best) < 0)) {
                best = e.getKey();
                amount = e.getValue();
            }
        }
        return best == null ? fallback : best;
    }

    /**
     * The HUD's cells: every entry when at most {@code max}, else the {@code max} largest (ties to
     * the lower id), in ascending id.
     */
    public static List<String> cells(SortedMap<String, Integer> pool, int max) {
        List<String> ids = new ArrayList<>(pool.keySet());
        if (ids.size() <= max) {
            return ids;
        }
        ids.sort(
                Comparator.<String>comparingInt(id -> -pool.getOrDefault(id, 0))
                        .thenComparing(Comparator.naturalOrder()));
        List<String> top = new ArrayList<>(ids.subList(0, max));
        top.sort(Comparator.naturalOrder());
        return top;
    }
}
