package com.andrewbrogoff.vftv.core.crucible.model;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * What one drained entity gets (crucible spec, "The decision" and "Selection"), first match:
 * protected, the dissolve mark, the lowest-id affordable learned candidate, the lowest-id
 * candidate's shortfall, else dissolve.
 *
 * @param kind the outcome
 * @param recipe the crafted or short candidate, null otherwise
 * @param shortfall cost minus pool per short aspect, empty unless {@code SHORT}
 */
public record Decision(
        Kind kind, @Nullable Candidate recipe, SortedMap<String, Integer> shortfall) {
    /** The outcomes. */
    public enum Kind {
        /** Refused: the stack is protected. */
        PROTECTED,
        /** Dissolved into the pool. */
        DISSOLVE,
        /** Crafts one of {@link #recipe}. */
        CRAFT,
        /** Refused: the lowest-id candidate is unaffordable. */
        SHORT
    }

    /**
     * One recipe the thrower has learned and whose catalyst matches.
     *
     * @param id the recipe id
     * @param cost the cost by aspect id
     */
    public record Candidate(String id, SortedMap<String, Integer> cost) {}

    /**
     * Decides.
     *
     * @param candidates the learned, matching candidates in ascending id (none without a thrower)
     */
    public static Decision decide(
            boolean protectedStack, boolean dissolveMark, List<Candidate> candidates, Pool pool) {
        if (protectedStack) {
            return new Decision(Kind.PROTECTED, null, new TreeMap<>());
        }
        if (dissolveMark || candidates.isEmpty()) {
            return new Decision(Kind.DISSOLVE, null, new TreeMap<>());
        }
        for (Candidate c : candidates) {
            if (pool.affordable(c.cost())) {
                return new Decision(Kind.CRAFT, c, new TreeMap<>());
            }
        }
        Candidate first = candidates.getFirst();
        return new Decision(Kind.SHORT, first, pool.shortfall(first.cost()));
    }
}
