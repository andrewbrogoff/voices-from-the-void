package com.andrewbrogoff.vftv.core.crucible.model;

import java.util.Arrays;

/**
 * The admission set (crucible spec, "Admission"): the lowest {@value #MAX} distinct entity ids
 * offered in one game time, at constant work and no allocation per offer.
 */
public final class CandidateSet {
    /** {@code CrucibleLimits.MAX_ENTITIES_PER_STEP}. */
    public static final int MAX = 16;

    private static final int[] NONE = new int[0];

    private final int[] ids = new int[MAX];
    private int size;
    private long time = Long.MIN_VALUE;

    /** Offers one id; an offer in a new game time first empties the set. */
    public void offer(long gameTime, int id) {
        if (gameTime != time) {
            time = gameTime;
            size = 0;
        }
        int largest = -1;
        for (int i = 0; i < size; i++) {
            if (ids[i] == id) {
                return;
            }
            if (largest < 0 || ids[i] > ids[largest]) {
                largest = i;
            }
        }
        if (size < MAX) {
            ids[size++] = id;
        } else if (id < ids[largest]) {
            ids[largest] = id;
        }
    }

    /**
     * The ids offered in {@code gameTime}, ascending, and empties the set; nothing when they were
     * offered in another game time.
     */
    public int[] drain(long gameTime) {
        if (time != gameTime || size == 0) {
            size = 0;
            return NONE;
        }
        int[] out = Arrays.copyOf(ids, size);
        Arrays.sort(out);
        size = 0;
        return out;
    }
}
