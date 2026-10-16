package com.andrewbrogoff.vftv.api.vis;

/**
 * What happened to a node. See the wands spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum NodeEventKind {
    /** An id was minted, or an unknown id was registered by recovery; never posted for a move. */
    CREATED,
    /** A Bell Jar took the node up. */
    CAPTURED,
    /** A Bell Jar set the node down; never posted for a birth. */
    PLACED,
    /** The id was tombstoned; posted once, whichever path did it. */
    REMOVED,
    /** A cultivation transaction committed. */
    CULTIVATED,
    /** The node's type, aspects or clarity were updated. */
    CHANGED
}
