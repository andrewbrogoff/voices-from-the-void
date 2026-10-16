package com.andrewbrogoff.vftv.api.focus;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The foci spec's fixed limits ("Tuning and limits"): formats and invariants, not balance.
 *
 * <p>javac inlines these constants into addon code, so an addon keeps the value it compiled against
 * even after a minor changes ours. Each constant says whether it bounds a data format (it may only
 * widen within a major) or caps runtime work (it may change in any minor).
 */
@Stable
public final class FocusLimits {
    private FocusLimits() {}

    /**
     * The highest rank of a focus, in modifier levels (rank is 0 to this). A data-format bound: may
     * only widen within a major.
     */
    public static final int MAX_RANK = 3;

    /**
     * The most modifier entries in one composition. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_MODIFIER_ENTRIES = 3;

    /** The lowest level of a modifier entry. A data-format bound: may only widen within a major. */
    public static final int MIN_MODIFIER_LEVEL = 1;

    /**
     * The highest level of a modifier entry. A data-format bound: may only widen within a major.
     */
    public static final int MAX_MODIFIER_LEVEL = 3;

    /**
     * The most {@code vftv:focus_effect} entries a world may hold. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_EFFECTS = 64;

    /**
     * The most {@code vftv:focus_medium} entries a world may hold. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_MEDIA = 16;

    /**
     * The most {@code vftv:focus_modifier} entries a world may hold. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_MODIFIERS = 32;

    /**
     * The lowest value of one aspect in an effect's {@code cost}, in vis. A data-format bound: may
     * only widen within a major.
     */
    public static final int MIN_COST = 1;

    /**
     * The highest value of one aspect in an effect's {@code cost}, in vis. A data-format bound: may
     * only widen within a major.
     */
    public static final int MAX_COST = 1000;

    /**
     * The lowest {@code cost_pm} of a medium, per mille. A data-format bound: may only widen within
     * a major.
     */
    public static final int MIN_COST_PM = 100;

    /**
     * The highest {@code cost_pm} of a medium or modifier, per mille. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_COST_PM = 4000;

    /**
     * The longest Touch or Beam {@code range}, in blocks. A data-format bound: may only widen
     * within a major.
     */
    public static final double MAX_RANGE = 16.0;

    /**
     * The largest Burst {@code radius}, in blocks. A data-format bound: may only widen within a
     * major.
     */
    public static final double MAX_BURST_RADIUS = 6.0;

    /**
     * The largest Burst {@code block_radius_sq}, in squared blocks. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_BLOCK_RADIUS_SQ = 16;

    /** The most entities one Burst affects. A runtime cap: may change in any minor. */
    public static final int MAX_BURST_ENTITIES = 16;

    /** The most block positions one Burst works. A runtime cap: may change in any minor. */
    public static final int MAX_BURST_BLOCKS = 27;

    /** The most live bolts per player. A runtime cap: may change in any minor. */
    public static final int MAX_LIVE_BOLTS = 8;

    /**
     * The highest bolt {@code speed}, in blocks per tick. A data-format bound: may only widen
     * within a major.
     */
    public static final double MAX_BOLT_SPEED = 3.0;

    /**
     * The longest bolt {@code lifetime}, in ticks. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_BOLT_LIFETIME = 100;

    /**
     * The shortest Beam {@code pulse}, in ticks. A data-format bound: may only widen within a
     * major.
     */
    public static final int MIN_BEAM_PULSE = 5;

    /**
     * The longest Beam {@code pulse}, in ticks. A data-format bound: may only widen within a major.
     */
    public static final int MAX_BEAM_PULSE = 40;

    /**
     * The horizontal distance, in blocks, below which Gust pushes along the caster's facing instead
     * of away from the origin. A runtime cap: may change in any minor.
     */
    public static final double GUST_MIN_DISTANCE = 0.0001;

    /**
     * The distance, in blocks, below which Undertow adds no pull. A runtime cap: may change in any
     * minor.
     */
    public static final double UNDERTOW_MIN_DISTANCE = 0.5;

    /**
     * The highest speed Undertow leaves a target with, in blocks per tick. A runtime cap: may
     * change in any minor.
     */
    public static final double UNDERTOW_MAX_SPEED = 3.0;

    /** The longest block ray of a Beam, in blocks. A runtime cap: may change in any minor. */
    public static final double MAX_BEAM_BLOCK_RAY = 16.0;

    /**
     * The highest value of a scaled effect param ({@code damage}, {@code heal}, {@code push},
     * {@code pull}, {@code ignite_ticks}, {@code slow_ticks}), in thousandths or ticks. A
     * data-format bound: may only widen within a major.
     */
    public static final int MAX_SCALED_PARAM = 20_000;

    /**
     * The cap on a scaled damage or heal, in thousandths of health (40.0 health). A runtime cap:
     * may change in any minor.
     */
    public static final int MAX_HEALTH_MAGNITUDE = 40_000;

    /**
     * The cap on a scaled push or pull, in thousandths of a block per tick (3.0 blocks per tick). A
     * runtime cap: may change in any minor.
     */
    public static final int MAX_MOVEMENT_MAGNITUDE = 3000;

    /** The cap on a scaled duration, in ticks. A runtime cap: may change in any minor. */
    public static final int MAX_TICKS_MAGNITUDE = 1200;

    /**
     * The clamp on the amplifier warp {@code w}, in warp points (0 to this). A runtime cap: may
     * change in any minor.
     */
    public static final int MAX_AMPLIFIER_WARP = 10_000;

    /**
     * The longest id in a focus record, in characters. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_ID_CHARS = 256;

    /**
     * The most Lathe actions accepted per player per tick. A runtime cap: may change in any minor.
     */
    public static final int MAX_LATHE_ACTIONS_PER_TICK = 1;
}
