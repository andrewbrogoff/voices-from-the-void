package com.andrewbrogoff.vftv.api.road;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The Road's fixed limits: formats and invariants, not balance. The Road's geometry is in {@link
 * RoadLayout}. See the Eldritch tier spec, "Tuning and limits".
 *
 * <p>javac inlines these constants into addon code, so an addon keeps the value it compiled against
 * even after a later release changes ours.
 */
@Stable
public final class RoadLimits {
    private RoadLimits() {}

    /** The most players admitted to one encounter: 16. A runtime cap: may change in any minor. */
    public static final int MAX_PARTICIPANTS = 16;

    /**
     * The most arena-index entries one admission refresh examines for newcomers: 64. A runtime cap:
     * may change in any minor.
     */
    public static final int MAX_ARENA_SCAN = 64;

    /**
     * The largest entry health a Chorister rises with, in health points: 1024, vanilla's ceiling on
     * maximum health. A runtime cap: may change in any minor.
     */
    public static final int MAX_ENTRY_HEALTH = 1024;

    /**
     * The most threat one participant holds, in tenths of health: 1,000,000. A runtime cap: may
     * change in any minor.
     */
    public static final int THREAT_MAX = 1_000_000;

    /**
     * The Hollow Bell, Step Bell and Heart attempts one player may make in one server tick, all
     * together: 1. A runtime cap: may change in any minor.
     */
    public static final int MAX_ATTEMPTS_PER_PLAYER_PER_TICK = 1;

    /**
     * The gap before each next line of the ending scene, in ticks: 20. A runtime cap: may change in
     * any minor.
     */
    public static final int ENDING_LINE_GAP_TICKS = 20;

    /**
     * How long an ending line that never started counts as playing, in ticks from its start: 100. A
     * runtime cap: may change in any minor.
     */
    public static final int ENDING_LINE_FALLBACK_TICKS = 100;

    /**
     * How long an ending line whose sound is missing shows its text, in ticks: 60. A runtime cap:
     * may change in any minor.
     */
    public static final int ENDING_MISSING_LINE_TICKS = 60;
}
