package com.andrewbrogoff.vftv.api.neoforge.infusion;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after the state an infusion occurrence reports has been written: a run started, upset,
 * spilled, completed or was abandoned, once per occurrence. Nothing fires per draw, absorb, stall
 * or pause.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the infusion spec, "Events".
 */
public final class InfusionEvent extends Event {
    private final InfusionPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the occurrence reported
     */
    @Internal
    public InfusionEvent(InfusionPayload payload) {
        this.payload = payload;
    }

    /**
     * What the occurrence reported.
     *
     * @return the payload
     */
    public InfusionPayload payload() {
        return payload;
    }
}
