package com.andrewbrogoff.vftv.api.arcane;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The arcane crafting feature's fixed limits: formats and invariants, not balance. See the arcane
 * crafting spec, "Tuning and limits".
 *
 * <p>javac inlines these constants into addon code, so an addon keeps the value it compiled against
 * even after a later minor changes ours. A data-format bound may only widen within a major; a
 * runtime cap may change in any minor.
 */
@Stable
public final class ArcaneLimits {
    private ArcaneLimits() {}

    /**
     * The most {@code vftv:arcane} recipes a snapshot holds; more rejects the set. A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_ARCANE_RECIPES = 4096;

    /**
     * The side of the crafting grid, in cells (a pattern is at most this square). A data-format
     * bound: may only widen within a major.
     */
    public static final int GRID_SIZE = 3;

    /**
     * The most entries in a recipe's base vis cost. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_COST_ENTRIES = 8;

    /** The smallest base cost per aspect. A data-format bound: may only widen within a major. */
    public static final int MIN_BASE_COST = 1;

    /** The largest base cost per aspect. A data-format bound: may only widen within a major. */
    public static final int MAX_BASE_COST = 1000;

    /** The most iterations of one shift-activated batch. A runtime cap: may change in any minor. */
    public static final int MAX_BATCH = 64;

    /**
     * The most craft payloads applied per player per tick. A runtime cap: may change in any minor.
     */
    public static final int MAX_CRAFTS_PER_TICK = 1;

    /**
     * The most characters in a recipe, research or result id. A data-format bound: may only widen
     * within a major.
     */
    public static final int MAX_ID_CHARS = 256;

    /**
     * The largest echoed effective cost per aspect in a craft payload. A runtime cap: may change in
     * any minor.
     */
    public static final int MAX_ECHO_EFFECTIVE = 10_000;

    /**
     * The largest echoed multiplier, per mille, in a craft payload. A runtime cap: may change in
     * any minor.
     */
    public static final int MAX_ECHO_MULTIPLIER = 10_000;

    /** The largest echoed spill in a craft payload. A runtime cap: may change in any minor. */
    public static final int MAX_ECHO_SPILL = 1_000_000;
}
