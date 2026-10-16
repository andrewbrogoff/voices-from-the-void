package com.andrewbrogoff.vftv.api.hush;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The flux and Hush features' fixed limits. javac inlines these constants into addon code, so an
 * addon keeps the value it compiled against even after a minor release changes ours.
 */
@Stable
public final class HushLimits {
    private HushLimits() {}

    /** The most flux one chunk's aura holds. A data-format bound: may only widen within a major. */
    public static final int FLUX_FORMAT_MAX = 1_000_000;

    /** The most flux one spill generates. A data-format bound: may only widen within a major. */
    public static final int MAX_SPILL_FLUX = 1_000_000;

    /**
     * The most essentia, in units, one spill may total. A data-format bound: may only widen within
     * a major.
     */
    public static final int MAX_ESSENTIA_SPILL = 640_000;

    /**
     * The most pending-deposit entries per level, one per chunk. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_PENDING = 4_096;

    /** The most hushed nodes journaled. A data-format bound: may only widen within a major. */
    public static final int MAX_HUSHED_NODES = 65_536;

    /** Due-queue pops per level per tick. A runtime cap: may change in any minor. */
    public static final int MAX_POPS_PER_TICK = 64;

    /** Chunk visits (attempts) per level per tick. A runtime cap: may change in any minor. */
    public static final int MAX_VISITS_PER_TICK = 32;

    /** Hush mutations by the spread per level per tick. A runtime cap: may change in any minor. */
    public static final int MAX_SPREAD_MUTATIONS_PER_TICK = 4;

    /** Pealing Bell due-queue pops per level per tick. A runtime cap: may change in any minor. */
    public static final int MAX_PEAL_POPS_PER_TICK = 16;

    /** Peals per level per tick. A runtime cap: may change in any minor. */
    public static final int MAX_PEALS_PER_TICK = 4;

    /** Clearing Bell ring attempts per player per tick. A runtime cap: may change in any minor. */
    public static final int MAX_RING_ATTEMPTS_PER_PLAYER_PER_TICK = 1;

    /**
     * The top of the {@code hush.walk} tuning range, in steps per attempt. A data-format bound: may
     * only widen within a major.
     */
    public static final int MAX_WALK = 64;

    /**
     * The top of the {@code hush.radius} tuning range, in blocks from the seep. A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_RADIUS = 16;

    /**
     * The top of the {@code bell.radius} tuning range, in blocks. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_BELL_RADIUS = 8;

    /**
     * The top of the {@code peal.scan} tuning range, in cells per peal. A data-format bound: may
     * only widen within a major.
     */
    public static final int MAX_PEAL_SCAN = 4_096;

    /** Cells one Hush command examines at most. A runtime cap: may change in any minor. */
    public static final int MAX_COMMAND_CELLS = 4_096;

    /** Mutations one Hush command makes at most. A runtime cap: may change in any minor. */
    public static final int MAX_COMMAND_MUTATIONS = 64;
}
