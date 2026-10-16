package com.andrewbrogoff.vftv.api.neoforge.focus;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.focus.FocusPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after a committed cast (a launch, a Touch, a Burst, or each Beam pulse that paid), compose
 * or rank-up. It reports the focus as it then stands; a {@code CAST} reports a paid attempt, not
 * what was affected. Nothing fires for a refusal, an attach or detach, a fizzle or a beam that pays
 * nothing.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the foci spec, "Events".
 */
public final class FocusEvent extends Event {
    private final FocusPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the focus event reported
     */
    @Internal
    public FocusEvent(FocusPayload payload) {
        this.payload = payload;
    }

    /**
     * What the focus event reported.
     *
     * @return the payload
     */
    public FocusPayload payload() {
        return payload;
    }
}
