package com.andrewbrogoff.vftv.api.road;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The Choristers' fixed limits: the fairness limits every {@code vftv:chorister} instance is
 * validated against, and the creature's fixed sizes. See the Choristers spec, "Fairness limits" and
 * "Tuning and limits".
 *
 * <p>javac inlines these constants into addon code, so an addon keeps the value it compiled against
 * even after a later release changes ours.
 */
@Stable
public final class ChoristerLimits {
    private ChoristerLimits() {}

    /**
     * The shortest move windup, in ticks: 20. A data-format bound: may only widen within a major.
     */
    public static final int MIN_WINDUP = 20;

    /**
     * The shortest lock of a directional move, in ticks: 16. A data-format bound: may only widen
     * within a major.
     */
    public static final int MIN_LOCK = 16;

    /**
     * The shortest move recovery, in ticks: 8. A data-format bound: may only widen within a major.
     */
    public static final int MIN_RECOVERY = 8;

    /**
     * The largest damage of one move, in tenths of a health point: 130. A data-format bound: may
     * only widen within a major.
     */
    public static final int MAX_MOVE_DAMAGE = 130;

    /**
     * How far, in tenths of a block, some knell in every cycle must reach with its last front plus
     * band: 340. A data-format bound: may only widen within a major.
     */
    public static final int ARENA_REACH = 340;

    /**
     * The most knell waves one Chorister holds at once: 2. A data-format bound: may only widen
     * within a major.
     */
    public static final int MAX_WAVES = 2;

    /**
     * The longest life of a legal knell wave, in ticks: 134. A data-format bound: may only widen
     * within a major.
     */
    public static final int MAX_LIFETIME = 134;

    /**
     * The longest a wave record is held after its release, in ticks: 174. A data-format bound: may
     * only widen within a major.
     */
    public static final int MAX_HELD_TICKS = 174;

    /**
     * The most moves one instance defines: 8. A data-format bound: may only widen within a major.
     */
    public static final int MAX_MOVES = 8;

    /**
     * The most entries in one instance's cycle: 12. A data-format bound: may only widen within a
     * major.
     */
    public static final int MAX_CYCLE = 12;

    /** The longest cycle, in ticks: 2,400. A data-format bound: may only widen within a major. */
    public static final int MAX_CYCLE_TICKS = 2400;

    /**
     * The radius of the choir walk about the Road's axis, in blocks: 12. A runtime cap: may change
     * in any minor.
     */
    public static final int WALK_RADIUS = 12;

    /**
     * The Chorister's box width at scale 1, in blocks: 1.2. A runtime cap: may change in any minor.
     */
    public static final double BASE_WIDTH = 1.2;

    /**
     * The Chorister's box height at scale 1, in blocks: 3.4. A runtime cap: may change in any
     * minor.
     */
    public static final double BASE_HEIGHT = 3.4;

    /**
     * The Chorister's eye height at scale 1, in blocks: 3.0. A runtime cap: may change in any
     * minor.
     */
    public static final double EYE_HEIGHT = 3.0;

    /**
     * The largest instance {@code scale}, so the box fits the rise box: 1.4. A data-format bound:
     * may only widen within a major.
     */
    public static final double MAX_SCALE = 1.4;

    /**
     * The lowest a participant may stand below the Chorister and still be in a strike, breath or
     * hear zone, in blocks: -2.0. A runtime cap: may change in any minor.
     */
    public static final double ZONE_MIN_DY = -2.0;

    /**
     * The highest a participant may stand above the Chorister and still be in a strike, breath or
     * hear zone, in blocks: 4.0. A runtime cap: may change in any minor.
     */
    public static final double ZONE_MAX_DY = 4.0;

    /**
     * The dodge assumption's reaction and network allowance, in ticks: 6. A runtime cap: may change
     * in any minor.
     */
    public static final int DODGE_LATENCY_TICKS = 6;

    /**
     * The dodge assumption's walking speed, in blocks a tick: 0.2158. A runtime cap: may change in
     * any minor.
     */
    public static final double DODGE_WALK_SPEED = 0.2158;

    /**
     * The most mark quads drawn for one Chorister in one frame: 384. A runtime cap: may change in
     * any minor.
     */
    public static final int MAX_MARK_QUADS = 384;
}
