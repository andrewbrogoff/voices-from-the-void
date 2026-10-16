package com.andrewbrogoff.vftv.api.neoforge.essentia;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.essentia.EssentiaPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after the settled state of an essentia spill, once per spill. It reports what spilled,
 * where and why; nothing fires for a move, a pour, a label, a valve or a rebuild.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the essentia logistics spec, "Events".
 */
public final class EssentiaEvent extends Event {
    private final EssentiaPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the spill reported
     */
    @Internal
    public EssentiaEvent(EssentiaPayload payload) {
        this.payload = payload;
    }

    /**
     * What the spill reported.
     *
     * @return the payload
     */
    public EssentiaPayload payload() {
        return payload;
    }
}
