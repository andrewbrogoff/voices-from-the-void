package com.andrewbrogoff.vftv.api.neoforge.research;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.research.StageReachedPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after each Stage step, once the whole state transition is committed. It reports the new
 * and previous Stage and the source.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the research spec, "Stage gates".
 */
public final class StageReachedEvent extends Event {
    private final StageReachedPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the Stage step reported
     */
    @Internal
    public StageReachedEvent(StageReachedPayload payload) {
        this.payload = payload;
    }

    /**
     * What the Stage step reported.
     *
     * @return the payload
     */
    public StageReachedPayload payload() {
        return payload;
    }
}
