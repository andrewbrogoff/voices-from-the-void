package com.andrewbrogoff.vftv.api.vis;

/**
 * What caused a node event. See the wands spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum NodeCause {
    /** Natural spread at world generation. */
    WORLDGEN,
    /** A structure's template. */
    STRUCTURE,
    /** The starter node's placement. */
    STARTER,
    /** A player's action, such as a Bell Jar or cultivation. */
    PLAYER,
    /** An operator command. */
    COMMAND,
    /** Reconciliation of a loaded chunk against the node registry. */
    RECONCILE,
    /** Another vftv feature calling the node service. */
    API
}
