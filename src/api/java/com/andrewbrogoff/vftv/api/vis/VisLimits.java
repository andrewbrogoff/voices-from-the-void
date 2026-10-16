package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The wand feature's fixed limits: formats and invariants, not balance. See the wands spec,
 * "Tuning".
 *
 * <p>javac inlines these constants into addon code, so an addon keeps the value it compiled against
 * even after a later minor changes ours. A data-format bound may only widen within a major; a
 * runtime cap may change in any minor.
 */
@Stable
public final class VisLimits {
    private VisLimits() {}

    /**
     * The side of a node index cell, in blocks (cells span all heights). A runtime cap: may change
     * in any minor.
     */
    public static final int CELL_SIZE = 64;

    /** The most nodes one index cell holds. A runtime cap: may change in any minor. */
    public static final int MAX_NODES_PER_CELL = 8;

    /** The most natural nodes minted into one cell. A runtime cap: may change in any minor. */
    public static final int MAX_NATURAL_NODES_PER_CELL = 1;

    /** The most structure nodes minted into one cell. A runtime cap: may change in any minor. */
    public static final int MAX_STRUCTURE_NODES_PER_CELL = 2;

    /**
     * The most aspects a node or node type holds (a node type lists 1 to this many). A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_NODE_ASPECTS = 6;

    /**
     * The most vis aspects (exposed primals) a world may have, and the most entries in a base cost;
     * more refuses the world. A data-format bound: may only widen within a major.
     */
    public static final int MAX_VIS_ASPECTS = 8;

    /** The most members in one relay network. A runtime cap: may change in any minor. */
    public static final int MAX_NETWORK_MEMBERS = 32;

    /**
     * The farthest the starter node is placed from the bootstrap position, in blocks (3-D distance
     * and Chebyshev ring). A runtime cap: may change in any minor.
     */
    public static final int STARTER_MAX_DISTANCE = 180;

    /**
     * The largest supported {@code nearest} radius, in blocks (0 to this); it also bounds the
     * scanning tuning's {@code sounding.radius}. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_NEAREST_RADIUS = 256;

    /** The wand's use duration while tapping, in ticks. A runtime cap: may change in any minor. */
    public static final int USE_DURATION_TICKS = 72_000;

    /** The most slot changes one spend applies. A runtime cap: may change in any minor. */
    public static final int MAX_SLOT_CHANGES = 64;

    /**
     * The smallest base cost per aspect (0 means none). A data-format bound: may only widen within
     * a major.
     */
    public static final int MIN_BASE_COST = 0;

    /** The largest base cost per aspect. A data-format bound: may only widen within a major. */
    public static final int MAX_BASE_COST = 1000;

    /**
     * The smallest {@code capacity} of a {@code vftv:wand_rod} entry, in vis per pool. A
     * data-format bound: may only widen within a major.
     */
    public static final int MIN_ROD_CAPACITY = 1;

    /**
     * The largest {@code capacity} of a {@code vftv:wand_rod} entry, in vis per pool. A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_ROD_CAPACITY = 1000;

    /**
     * The smallest {@code cost_multiplier} of a {@code vftv:wand_cap} entry (at most 2 decimal
     * places). A data-format bound: may only widen within a major.
     */
    public static final double MIN_COST_MULTIPLIER = 0.25;

    /**
     * The largest {@code cost_multiplier} of a {@code vftv:wand_cap} entry (at most 2 decimal
     * places). A data-format bound: may only widen within a major.
     */
    public static final double MAX_COST_MULTIPLIER = 2.00;

    /**
     * The most entries in a {@code vftv:vis} component. A data-format bound: may only widen within
     * a major.
     */
    public static final int MAX_VIS_ENTRIES = 16;

    /**
     * The largest stored vis amount, node aspect max or pool (amounts range 0 to this). A
     * data-format bound: may only widen within a major.
     */
    public static final int MAX_AMOUNT = 1000;

    /**
     * The largest per-aspect cultivation counter (0 to this). A data-format bound: may only widen
     * within a major.
     */
    public static final int MAX_CULTIVATION_COUNTER = 63;

    /**
     * The largest stored cultivation Sonus (0 to this). A data-format bound: may only widen within
     * a major.
     */
    public static final int MAX_CULTIVATION_SONUS = 4095;

    /**
     * The most relay members in one per-dimension saved record (2 to the 20th). A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_RELAY_RECORD_MEMBERS = 1 << 20;

    /**
     * The vis HUD frame sprite's nine-slice border, in GUI-scaled pixels. A runtime cap: may change
     * in any minor.
     */
    public static final int HUD_FRAME_BORDER = 4;

    /**
     * The vis HUD frame's inner padding, in GUI-scaled pixels. A runtime cap: may change in any
     * minor.
     */
    public static final int HUD_PADDING = 4;

    /** The vis HUD row height, in GUI-scaled pixels. A runtime cap: may change in any minor. */
    public static final int HUD_ROW_HEIGHT = 16;

    /**
     * The gap between vis HUD rows, in GUI-scaled pixels. A runtime cap: may change in any minor.
     */
    public static final int HUD_ROW_GAP = 2;

    /**
     * The x offset of a vis HUD row's icon, in GUI-scaled pixels. A runtime cap: may change in any
     * minor.
     */
    public static final int HUD_ICON_X = 4;

    /**
     * The x offset of a vis HUD row's number, in GUI-scaled pixels. A runtime cap: may change in
     * any minor.
     */
    public static final int HUD_NUMBER_X = 24;

    /**
     * The vis HUD frame's width with rows, in GUI-scaled pixels. A runtime cap: may change in any
     * minor.
     */
    public static final int HUD_FRAME_WIDTH = 52;

    /**
     * The widest the vis HUD frame grows for an inert wand's text, in GUI-scaled pixels. A runtime
     * cap: may change in any minor.
     */
    public static final int HUD_MAX_FRAME_WIDTH = 160;

    /**
     * The width at which the vis HUD's text wraps, in GUI-scaled pixels. A runtime cap: may change
     * in any minor.
     */
    public static final int HUD_WRAP_WIDTH = 152;
}
