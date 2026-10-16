package com.andrewbrogoff.vftv.api.hush;

/**
 * The kind of a Hush mutation. See the Hush spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum HushSpreadKind {
    /** A block converted to its hushed form. */
    CONVERT,
    /** A node turned into the {@code vftv:hushed} node type. */
    NODE,
    /** A Hollow rising from a converted block. */
    RISE,
    /** A Pealing Bell restoring a hushed block or node. */
    RESTORE
}
