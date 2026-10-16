package com.andrewbrogoff.vftv.api.essentia;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The essentia feature's fixed limits, from the essentia logistics spec's "Tuning and limits".
 *
 * <p>javac inlines these constants into addon code: an addon keeps the value it compiled against,
 * even after a later minor changes ours.
 */
@Stable
public final class EssentiaLimits {
    private EssentiaLimits() {}

    /**
     * The highest suction or hold rank, 128; ranks run 0 to this, and 16 to this is the consumer
     * band. A data-format bound: may only widen within a major.
     */
    public static final int MAX_SUCTION = 128;

    /**
     * The highest storage rank, 15; ranks 1 to this are the storage band (jars and addon buffers).
     * A data-format bound: may only widen within a major.
     */
    public static final int STORAGE_MAX = 15;

    /** The most members of one line, 256. A data-format bound: may only widen within a major. */
    public static final int MAX_MEMBERS = 256;

    /** The most endpoints of one line, 64. A data-format bound: may only widen within a major. */
    public static final int MAX_ENDPOINTS = 64;

    /**
     * The most slots of one endpoint's handler, 128; a handler with more is skipped. A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_HANDLER_SLOTS = 128;

    /** The most move tries per line pulse, 4. A runtime cap: may change in any minor. */
    public static final int MAX_TRIES_PER_PULSE = 4;

    /** The most line pulses per level per tick, 16. A runtime cap: may change in any minor. */
    public static final int MAX_PULSES_PER_TICK = 16;

    /**
     * The most scheduler pops per level per tick, 256, stale entries and rechecks included. A
     * runtime cap: may change in any minor.
     */
    public static final int MAX_SCHEDULER_POPS_PER_TICK = 256;

    /**
     * The most topology work units per level per tick, 4,096 (pops, visits, finalisation and
     * cleanup). A runtime cap: may change in any minor.
     */
    public static final int MAX_TOPOLOGY_WORK_PER_TICK = 4096;

    /**
     * The most face refreshes per level per tick, 64 (tubes, valves and Alembics). A runtime cap:
     * may change in any minor.
     */
    public static final int MAX_FACE_REFRESHES_PER_TICK = 64;

    /**
     * The most essentia units one jar holds, 10,000: the format bound and the capacity's ceiling. A
     * data-format bound: may only widen within a major.
     */
    public static final int MAX_JAR_AMOUNT = 10_000;

    /**
     * The most essentia units one spill reports, 640,000 (a stack of 64 full jars). A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_ESSENTIA_SPILL = 640_000;

    /** The most live flow motes on a client, 64. A runtime cap: may change in any minor. */
    public static final int MAX_LIVE_MOTES = 64;

    /** The most positions on one flow mote's path, 256. A runtime cap: may change in any minor. */
    public static final int MAX_FLOW_PATH = 256;

    /** The most characters of an id, 256. A data-format bound: may only widen within a major. */
    public static final int MAX_ID_CHARS = 256;
}
