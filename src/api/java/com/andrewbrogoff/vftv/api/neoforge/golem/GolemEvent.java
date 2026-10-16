package com.andrewbrogoff.vftv.api.neoforge.golem;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.golem.GolemPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after the state it reports has been written: a golem placed or picked up, an act that
 * moved something, and at death its spilled essentia (if any) and then its death, after the drops.
 * Once per occurrence: nothing fires per think, travel check, path or probe.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the golems spec, "Events".
 */
public final class GolemEvent extends Event {
    private final GolemPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the golem event reported
     */
    @Internal
    public GolemEvent(GolemPayload payload) {
        this.payload = payload;
    }

    /**
     * What the golem event reported.
     *
     * @return the payload
     */
    public GolemPayload payload() {
        return payload;
    }
}
