package com.andrewbrogoff.vftv.api.neoforge.aspect;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.AspectDiscoveredPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after a player discovers an aspect during a strike and the discovery is committed, once
 * per aspect, in dependency order. It reports the aspect and the bonus actually awarded. Voice
 * insight credits never discover, so they never post it.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the aspects spec, "Events".
 */
public final class AspectDiscoveredEvent extends Event {
    private final AspectDiscoveredPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the discovery reported
     */
    @Internal
    public AspectDiscoveredEvent(AspectDiscoveredPayload payload) {
        this.payload = payload;
    }

    /**
     * What the discovery reported.
     *
     * @return the payload
     */
    public AspectDiscoveredPayload payload() {
        return payload;
    }
}
