package com.andrewbrogoff.vftv.api.essentia;

/**
 * Why essentia spilled, shared by the crucible, essentia, infusion and golem spill payloads. Which
 * event posts which cause is the crucible spec's table.
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum SpillCause {
    /** Deliberately emptied, as a crucible emptied with a bucket. */
    EMPTIED,
    /** The holding block was removed, or a jar's unsettled excess at removal. */
    REMOVED,
    /** A filled vessel item was destroyed. */
    DESTROYED,
    /** An infusion upset spilled drawn essentia. */
    UPSET,
    /** An infusion run was abandoned and spilled what it had drawn. */
    ABANDONED,
    /** A golem carrying essentia died. */
    DIED
}
