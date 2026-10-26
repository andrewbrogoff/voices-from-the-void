package com.andrewbrogoff.vftv.core.arcane.model;

import java.util.Map;

/**
 * The Worktable's pure arithmetic (arcane spec, "The screen" and "Status and refusals"): when a
 * ready status is taxed, and how many crafts a batch can make at most.
 */
public final class WorktableRules {
    /** Neutral warp, per mille. */
    public static final int NEUTRAL_WARP = 1000;

    private WorktableRules() {}

    /** Whether a ready status names the tax: the quote's warp is above 1000 or it spills. */
    public static boolean taxed(int warpPerMille, int spill) {
        return warpPerMille > NEUTRAL_WARP || spill > 0;
    }

    /**
     * How many crafts every pool can pay at the effective cost: the least {@code pool / cost} over
     * the cost's aspects, an absent pool counting as 0.
     */
    public static int wandCrafts(Map<String, Integer> pools, Map<String, Integer> costs) {
        int crafts = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> cost : costs.entrySet()) {
            if (cost.getValue() > 0) {
                crafts = Math.min(crafts, pools.getOrDefault(cost.getKey(), 0) / cost.getValue());
            }
        }
        return crafts;
    }

    /**
     * How many results the output slot has room for.
     *
     * @param mergeable whether the output is empty or holds the same item and components
     * @param held how many it holds
     * @param max the result's maximum stack size
     * @param count the result's count
     */
    public static int outputRoom(boolean mergeable, int held, int max, int count) {
        return mergeable && count > 0 ? Math.max(0, (max - held) / count) : 0;
    }

    /**
     * The batch total's {@code n}: the least of the batch cap, the crafts the wand can pay, the
     * smallest occupied grid stack and the output's room, never below 0.
     */
    public static int batchSize(int cap, int wandCrafts, int smallestStack, int outputRoom) {
        return Math.max(
                0, Math.min(Math.min(cap, wandCrafts), Math.min(smallestStack, outputRoom)));
    }
}
