package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Internal;

/**
 * A committed change to a node's identity, place or state. See the wands spec, "Events".
 *
 * @param kind what happened to the node
 * @param node the node's id and, while placed, its dimension and position
 * @param cause what caused it
 */
public record NodePayload(NodeEventKind kind, NodeRef node, NodeCause cause) {
    /** Constructed by vftv only. */
    @Internal
    public NodePayload {}
}
