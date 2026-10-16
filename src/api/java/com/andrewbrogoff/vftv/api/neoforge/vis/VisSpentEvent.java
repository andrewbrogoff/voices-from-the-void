package com.andrewbrogoff.vftv.api.neoforge.vis;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.vis.VisSpentPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after the whole commit of a vis spend: the debited vis, the consumed materials and the
 * filled output are already written. It reports the player, the place, the context and the price
 * paid.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the wands spec, "The spend" and "Events".
 */
public final class VisSpentEvent extends Event {
    private final VisSpentPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the spend reported
     */
    @Internal
    public VisSpentEvent(VisSpentPayload payload) {
        this.payload = payload;
    }

    /**
     * What the spend reported.
     *
     * @return the payload
     */
    public VisSpentPayload payload() {
        return payload;
    }
}
