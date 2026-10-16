package com.andrewbrogoff.vftv.api.crucible;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The crucible feature's fixed limits: formats and invariants, not balance. See the crucible spec,
 * "Tuning and limits".
 *
 * <p>javac inlines these constants into addon code, so an addon keeps the value it compiled against
 * even after a later minor changes ours. A data-format bound may only widen within a major; a
 * runtime cap may change in any minor.
 */
@Stable
public final class CrucibleLimits {
    private CrucibleLimits() {}

    /**
     * The most {@code vftv:crucible} recipes a snapshot holds; more fails startup or empties the
     * snapshot on reload. A data-format bound: may only widen within a major.
     */
    public static final int MAX_CRUCIBLE_RECIPES = 4096;

    /**
     * The most entries in a recipe's aspect cost. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_COST_ENTRIES = 8;

    /** The smallest cost per aspect. A data-format bound: may only widen within a major. */
    public static final int MIN_COST = 1;

    /** The largest cost per aspect. A data-format bound: may only widen within a major. */
    public static final int MAX_COST = 1000;

    /**
     * The most entities one crucible considers per step (its candidate set). A runtime cap: may
     * change in any minor.
     */
    public static final int MAX_ENTITIES_PER_STEP = 16;

    /**
     * The most aspects in a crucible's pool. A data-format bound: may only widen within a major.
     */
    public static final int MAX_POOL_ENTRIES = 64;

    /**
     * The largest total of a crucible's pool, and the ceiling of the {@code pool.capacity} tuning;
     * each amount is 1 to this. A data-format bound: may only widen within a major.
     */
    public static final int MAX_POOL_TOTAL = 10_000;

    /**
     * The most crucible refreshes processed per level per tick. A runtime cap: may change in any
     * minor.
     */
    public static final int MAX_REFRESHES_PER_TICK = 64;

    /**
     * The most characters in a recipe's research or result id. A data-format bound: may only widen
     * within a major.
     */
    public static final int MAX_ID_CHARS = 256;

    /** The most flux one spill generates (0 to this). A runtime cap: may change in any minor. */
    public static final int MAX_SPILL_FLUX = 1_000_000;

    /**
     * The most doses in a first-Brass dose plan (1 to this). A data-format bound: may only widen
     * within a major.
     */
    public static final int MAX_DOSES = 16;

    /** The most pool cells drawn on the HUD card. A runtime cap: may change in any minor. */
    public static final int MAX_CARD_ENTRIES = 12;
}
