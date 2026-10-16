package com.andrewbrogoff.vftv.api.neoforge.aspect;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.ThingScannedPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after a strike's scan is committed to the player's knowledge: a first scan, a partial
 * scan, a completion or a repeat. It reports the key, the outcome and the points actually awarded.
 * Never posted for sounding, silence, "nothing to learn" or a {@code vftv:strike} conversion.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the aspects spec, "Events".
 */
public final class ThingScannedEvent extends Event {
    private final ThingScannedPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the scan reported
     */
    @Internal
    public ThingScannedEvent(ThingScannedPayload payload) {
        this.payload = payload;
    }

    /**
     * What the scan reported.
     *
     * @return the payload
     */
    public ThingScannedPayload payload() {
        return payload;
    }
}
