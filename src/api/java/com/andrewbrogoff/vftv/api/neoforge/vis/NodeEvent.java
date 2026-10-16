package com.andrewbrogoff.vftv.api.neoforge.vis;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.vis.NodePayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after the whole commit of a node's creation, capture, placement, removal, cultivation or
 * update. It reports the kind, the node and the cause. Nothing is posted for a tap or relay pulse,
 * regeneration, a duplicate's removal, or a removal hook that found nothing to do.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable. See the wands spec,
 * "Events".
 */
public final class NodeEvent extends Event {
    private final NodePayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the node change reported
     */
    @Internal
    public NodeEvent(NodePayload payload) {
        this.payload = payload;
    }

    /**
     * What the node change reported.
     *
     * @return the payload
     */
    public NodePayload payload() {
        return payload;
    }
}
