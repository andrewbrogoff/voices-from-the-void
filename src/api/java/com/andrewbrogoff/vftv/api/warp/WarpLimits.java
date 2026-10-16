package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The warp feature's fixed limits, from the warp spec's "Tuning and limits". javac inlines these
 * constants into addon code: an addon keeps the value it compiled against, even after a minor
 * changes ours.
 */
@Stable
public final class WarpLimits {
    private WarpLimits() {}

    /**
     * The most temporary warp a record holds: 1,000. A data-format bound: may only widen within a
     * major.
     */
    public static final int T_MAX = 1000;

    /**
     * The most lasting warp a record holds: 1,000. A data-format bound: may only widen within a
     * major.
     */
    public static final int L_MAX = 1000;

    /**
     * The most permanent warp a record's format holds: 100 (the tuned cap is lower). A data-format
     * bound: may only widen within a major.
     */
    public static final int P_FORMAT_MAX = 100;

    /**
     * The largest Voice counter a record's format holds: 10,000. A data-format bound: may only
     * widen within a major.
     */
    public static final int COUNTER_FORMAT_MAX = 10000;

    /**
     * The most entries of the {@code vftv:voice_event} registry: 64. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_EVENTS = 64;

    /**
     * The most entries of the {@code vftv:bargain} registry: 64. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_BARGAINS = 64;

    /**
     * The most carried stacks one gear sample reads, in all: 128. A runtime cap: may change in any
     * minor.
     */
    public static final int MAX_GEAR_SLOTS = 128;

    /** The most placement attempts per apparition: 16. A runtime cap: may change in any minor. */
    public static final int MAX_APPARITION_ATTEMPTS = 16;

    /**
     * The longest a Voice cue is held while a screen is open, in ticks: 200. A runtime cap: may
     * change in any minor.
     */
    public static final int MAX_CUE_HOLD = 200;

    /**
     * The most named milestones, both in tuning {@code permanent.milestones} and in a record's paid
     * set: 16. A data-format bound: may only widen within a major.
     */
    public static final int MAX_MILESTONES = 16;

    /**
     * The longest id the warp record and its packets hold, in characters: 256. A data-format bound:
     * may only widen within a major.
     */
    public static final int MAX_ID_CHARS = 256;
}
