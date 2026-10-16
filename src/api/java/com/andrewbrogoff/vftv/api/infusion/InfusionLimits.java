package com.andrewbrogoff.vftv.api.infusion;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The infusion feature's fixed limits, from the infusion spec's "Tuning and limits".
 *
 * <p>javac inlines these constants into addon code: an addon keeps the value it compiled against,
 * even after a later minor changes ours.
 */
@Stable
public final class InfusionLimits {
    private InfusionLimits() {}

    /**
     * The altar box's horizontal radius around the bowl, 4 blocks (the box is 324 positions). A
     * data-format bound: may only widen within a major.
     */
    public static final int BOX_RADIUS = 4;

    /**
     * The altar box's depth below the bowl, 1 block. A data-format bound: may only widen within a
     * major.
     */
    public static final int BOX_BELOW = 1;

    /**
     * The altar box's height above the bowl, 2 blocks. A data-format bound: may only widen within a
     * major.
     */
    public static final int BOX_ABOVE = 2;

    /**
     * The most pedestals at the bowl's level, 16. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_PEDESTALS = 16;

    /**
     * The most components of one recipe, 16. A data-format bound: may only widen within a major.
     */
    public static final int MAX_COMPONENTS = 16;

    /**
     * The most essentia sources a draw reads from the box, 32. A runtime cap: may change in any
     * minor.
     */
    public static final int MAX_SOURCES = 32;

    /**
     * The most infusion recipes loaded, 4,096. A data-format bound: may only widen within a major.
     */
    public static final int MAX_INFUSION_RECIPES = 4096;

    /**
     * The most aspect costs of one recipe, 8. A data-format bound: may only widen within a major.
     */
    public static final int MAX_COST_ENTRIES = 8;

    /**
     * The least units of one aspect cost, 1. A data-format bound: may only widen within a major.
     */
    public static final int MIN_COST = 1;

    /**
     * The most units of one aspect cost, 1,000. A data-format bound: may only widen within a major.
     */
    public static final int MAX_COST = 1000;

    /**
     * The most instability, 100 percentage points. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_INSTABILITY = 100;

    /**
     * The most warp penalty, 100 percentage points. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_WARP_PENALTY = 100;

    /** The most run steps per level per tick, 16. A runtime cap: may change in any minor. */
    public static final int MAX_STEPS_PER_TICK = 16;

    /**
     * The most step-queue pops per level per tick, 64, stale entries included. A runtime cap: may
     * change in any minor.
     */
    public static final int MAX_QUEUE_POPS_PER_TICK = 64;

    /** The most arm refreshes per level per tick, 16. A runtime cap: may change in any minor. */
    public static final int MAX_ARM_REFRESHES_PER_TICK = 16;

    /**
     * The most altar assessments per level per tick, 16, shared by strikes, commit attempts and
     * armed refreshes. A runtime cap: may change in any minor.
     */
    public static final int MAX_ASSESSMENTS_PER_TICK = 16;

    /**
     * How long a pending assessment waits before it expires, 20 ticks. A runtime cap: may change in
     * any minor.
     */
    public static final int ASSESS_TTL = 20;

    /**
     * The most "held now" units shown per cost aspect, 99,999 (shown as "99999+" when clamped). A
     * data-format bound: may only widen within a major.
     */
    public static final int HELD_NOW_MAX = 99_999;

    /**
     * The most runs of one bowl, 2,147,483,647 (2 to the 31st, minus 1); then the bowl takes a new
     * id. A data-format bound: may only widen within a major.
     */
    public static final int MAX_RUNS = Integer.MAX_VALUE;

    /**
     * The most rolled steps of one run, 18,016 (the ceiling at the tuning maxima). A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_ROLLS = 18_016;

    /** The most characters of an id, 256. A data-format bound: may only widen within a major. */
    public static final int MAX_ID_CHARS = 256;

    /**
     * The most total upset spill of one run, 10,000 units ({@code upset.max} times {@code
     * upset.spill}). A data-format bound: may only widen within a major.
     */
    public static final int MAX_UPSET_SPILL_TOTAL = 10_000;
}
