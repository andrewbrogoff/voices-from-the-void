package com.andrewbrogoff.vftv.api.neoforge.research;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.research.ResearchLearnedPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after a research entry is learned, once the whole state transition (the resolution
 * included) is committed, one post per entry in learning order. It reports the entry, how it was
 * learned, the warp applied and the points paid. A revocation posts nothing.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the research spec, "Events".
 */
public final class ResearchLearnedEvent extends Event {
    private final ResearchLearnedPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the learning reported
     */
    @Internal
    public ResearchLearnedEvent(ResearchLearnedPayload payload) {
        this.payload = payload;
    }

    /**
     * What the learning reported.
     *
     * @return the payload
     */
    public ResearchLearnedPayload payload() {
        return payload;
    }
}
