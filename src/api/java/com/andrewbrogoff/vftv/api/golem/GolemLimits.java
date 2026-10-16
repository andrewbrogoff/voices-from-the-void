package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The golems spec's fixed limits ("Tuning and limits"): formats and invariants, not balance.
 *
 * <p>javac inlines these constants into addon code, so an addon keeps the value it compiled against
 * even after a minor changes ours. Each constant says whether it bounds a data format (it may only
 * widen within a major) or caps runtime work (it may change in any minor).
 */
@Stable
public final class GolemLimits {
    private GolemLimits() {}

    /** The most marks per golem. A data-format bound: may only widen within a major. */
    public static final int MAX_MARKS = 16;

    /** The most golem thinks run per level per tick. A runtime cap: may change in any minor. */
    public static final int MAX_THINKS_PER_TICK = 32;

    /**
     * The most think-queue entries popped per level per tick. A runtime cap: may change in any
     * minor.
     */
    public static final int MAX_THINK_POPS_PER_TICK = 64;

    /**
     * The most work units one think may spend (so at most 32,768 per level per tick). A runtime
     * cap: may change in any minor.
     */
    public static final int MAX_THINK_WORK = 1024;

    /**
     * The most path searches started per level per tick. A runtime cap: may change in any minor.
     */
    public static final int MAX_PATH_SEARCHES_PER_TICK = 2;

    /**
     * The most search-queue entries popped per level per tick. A runtime cap: may change in any
     * minor.
     */
    public static final int MAX_SEARCH_POPS_PER_TICK = 32;

    /**
     * The most nodes one path search visits (16 times the highest {@code path.max_length}). A
     * runtime cap: may change in any minor.
     */
    public static final int MAX_VISITED_NODES = 1024;

    /** The most handler slots a golem reads at one mark. A runtime cap: may change in any minor. */
    public static final int MAX_HANDLER_SLOTS = 256;

    /**
     * The most item entities a COLLECT mark considers per act. A runtime cap: may change in any
     * minor.
     */
    public static final int MAX_COLLECT_ENTITIES = 16;

    /**
     * The most items a golem carries, and a material's highest {@code carry_items}. A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_CARRY_ITEMS = 64;

    /**
     * The most essentia units a golem carries, and a material's highest {@code carry_essentia}. A
     * data-format bound: may only widen within a major.
     */
    public static final int MAX_CARRY_ESSENTIA = 1000;

    /**
     * The longest id in a golem record, in characters. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_ID_CHARS = 256;

    /**
     * The longest stored owner name, in characters. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_OWNER_NAME = 16;
}
