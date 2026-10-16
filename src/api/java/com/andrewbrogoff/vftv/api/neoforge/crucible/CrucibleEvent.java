package com.andrewbrogoff.vftv.api.neoforge.crucible;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.crucible.CruciblePayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after a crucible's commit: once per dissolve action, once per craft and once per non-empty
 * spill. It reports what was dissolved, crafted or spilled, where and by whom. Nothing is posted
 * for a refusal, an extraction, a fill, a refresh or a step.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the crucible spec, "Events".
 */
public final class CrucibleEvent extends Event {
    private final CruciblePayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the crucible committed
     */
    @Internal
    public CrucibleEvent(CruciblePayload payload) {
        this.payload = payload;
    }

    /**
     * What the crucible committed.
     *
     * @return the payload
     */
    public CruciblePayload payload() {
        return payload;
    }
}
