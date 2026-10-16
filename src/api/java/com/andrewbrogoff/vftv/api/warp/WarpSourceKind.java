package com.andrewbrogoff.vftv.api.warp;

/**
 * What changed a player's warp. Grows in minor releases: a switch over it needs a default arm. See
 * the warp spec, "The commit".
 */
public enum WarpSourceKind {
    /** A research entry's checked commit; the source carries the entry id. */
    ENTRY,
    /** An accepted Bargain; the source carries the bargain id. */
    BARGAIN,
    /** The Listen rite's committed cost. */
    LISTEN,
    /** A Stilling Draught. */
    STILLING,
    /** Temporary warp fading. */
    DECAY,
    /** A gear sample. */
    GEAR,
    /** A named permanent milestone; the source carries the milestone id. */
    MILESTONE,
    /** Silencing. */
    SILENCING,
    /** An admin command. */
    COMMAND
}
