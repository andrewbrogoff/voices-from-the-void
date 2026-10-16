package com.andrewbrogoff.vftv.api.research;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The research feature's fixed limits, from the research spec's "Tuning". javac inlines these
 * constants into addon code: an addon keeps the value it compiled against, even after a minor
 * changes ours.
 */
@Stable
public final class ResearchLimits {
    private ResearchLimits() {}

    /**
     * The fewest anchors a {@code note} entry may carry: 2. A data-format bound: may only widen
     * within a major.
     */
    public static final int MIN_ANCHORS = 2;

    /**
     * The most anchors a {@code note} entry may carry: 5. A data-format bound: may only widen
     * within a major.
     */
    public static final int MAX_ANCHORS = 5;

    /**
     * The largest note grid radius, in hex steps: 7, which is 169 cells. A data-format bound: may
     * only widen within a major.
     */
    public static final int MAX_GRID_RADIUS = 7;

    /**
     * The deepest synthesis recursion of a debit plan, in levels: 3. A runtime cap: may change in
     * any minor.
     */
    public static final int MAX_SYNTHESIS_DEPTH = 3;

    /** The most research actions per player per tick: 8. A runtime cap: may change in any minor. */
    public static final int MAX_ACTIONS_PER_TICK = 8;

    /**
     * The most commits per player per tick, counted within {@link #MAX_ACTIONS_PER_TICK}: 1. A
     * runtime cap: may change in any minor.
     */
    public static final int MAX_COMMITS_PER_TICK = 1;

    /** The most open notes per player: 16. A runtime cap: may change in any minor. */
    public static final int MAX_OPEN_NOTES = 16;

    /** The most bookmarks per player: 9. A runtime cap: may change in any minor. */
    public static final int MAX_BOOKMARKS = 9;
}
