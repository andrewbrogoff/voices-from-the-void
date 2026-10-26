package com.andrewbrogoff.vftv.core.vis.model;

/**
 * Cultivation's arithmetic (wands spec, "Cultivation"): feeding a node essentia through a Node Tap
 * raises a vis aspect's max, one step per {@code per_max} units, up to {@code max_capacity}, and
 * Sonus raises its clarity one step per {@code clarity_cost}. Pure integer functions; a carry after
 * lowered thresholds is an insert of 0 units.
 */
public final class Cultivate {
    /** The clearest clarity's index (faint 0, steady 1, clear 2). */
    public static final int CLEAR = 2;

    private Cultivate() {}

    /**
     * A vis aspect's max and counter.
     *
     * @param max its max
     * @param counter units toward the next step
     */
    public record Vis(int max, int counter) {}

    /**
     * A node's clarity and Sonus progress.
     *
     * @param clarity the clarity index, 0 to 2
     * @param sonus Sonus toward the next step
     */
    public record Clarity(int clarity, int sonus) {}

    /**
     * Units a vis aspect still accepts: {@code (cap − max) × perMax − counter}, or 0 once {@code
     * max} has reached {@code cap}.
     */
    public static int visRoom(int max, int counter, int cap, int perMax) {
        if (max >= cap) {
            return 0;
        }
        return (int) Math.max(0, (long) (cap - max) * perMax - counter);
    }

    /**
     * Adds {@code k} units (at most the room) to a vis aspect: {@code max += (counter + k) /
     * perMax}, the remainder kept; at the cap the counter becomes 0. A lowered cap never shrinks a
     * max already past it.
     */
    public static Vis insertVis(int max, int counter, int k, int cap, int perMax) {
        int total = counter + k;
        int next = max + total / perMax;
        int left = total % perMax;
        if (max >= cap) {
            return new Vis(max, 0);
        }
        if (next >= cap) {
            return new Vis(cap, 0);
        }
        return new Vis(next, left);
    }

    /** Sonus a node still accepts: the steps left to clear times the cost, less its progress. */
    public static int sonusRoom(int clarity, int sonus, int cost) {
        if (clarity >= CLEAR) {
            return 0;
        }
        return (int) Math.max(0, (long) (CLEAR - clarity) * cost - sonus);
    }

    /** Adds {@code k} Sonus (at most the room): each {@code cost} raises clarity one step. */
    public static Clarity insertSonus(int clarity, int sonus, int k, int cost) {
        if (clarity >= CLEAR) {
            return new Clarity(clarity, 0);
        }
        int total = sonus + k;
        int next = Math.min(CLEAR, clarity + total / cost);
        return new Clarity(next, next == CLEAR ? 0 : total % cost);
    }
}
