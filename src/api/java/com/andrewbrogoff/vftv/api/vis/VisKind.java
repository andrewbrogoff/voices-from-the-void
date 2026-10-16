package com.andrewbrogoff.vftv.api.vis;

/**
 * Which kind of magic a vis spend is, so the warp factor and the spill know how to price it. See
 * the wands spec, "The context".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum VisKind {
    /** Ordinary magic: warp makes it dearer, and only the vis warp added spills. */
    ORDINARY,
    /** Void work: it spills a share of its whole effective cost. */
    VOID
}
