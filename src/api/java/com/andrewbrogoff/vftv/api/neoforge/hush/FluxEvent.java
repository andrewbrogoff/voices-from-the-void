package com.andrewbrogoff.vftv.api.neoforge.hush;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.hush.FluxPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after a flux deposit or clear has settled. It reports, for a deposit, where the generated
 * flux went and its source; for a clear, the amount, its source and the flux before and after.
 * Never posted for a zero deposit, a miss or a merge of pending flux.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the Hush spec, "Events".
 */
public final class FluxEvent extends Event {
    private final FluxPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload the settled deposit or clear
     */
    @Internal
    public FluxEvent(FluxPayload payload) {
        this.payload = payload;
    }

    /**
     * The settled deposit or clear.
     *
     * @return the payload
     */
    public FluxPayload payload() {
        return payload;
    }
}
