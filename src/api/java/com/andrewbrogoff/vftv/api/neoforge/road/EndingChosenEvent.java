package com.andrewbrogoff.vftv.api.neoforge.road;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.road.EndingChosenPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after a Listener's choice at the Heart is made: after the warp change, the ending
 * advancement's award and the ending cue. It reports the player, the ending and the warp change the
 * choice actually made.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the choice stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the Eldritch tier spec, "The choice".
 */
public final class EndingChosenEvent extends Event {
    private final EndingChosenPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload the choice made
     */
    @Internal
    public EndingChosenEvent(EndingChosenPayload payload) {
        this.payload = payload;
    }

    /**
     * The choice made.
     *
     * @return the payload
     */
    public EndingChosenPayload payload() {
        return payload;
    }
}
