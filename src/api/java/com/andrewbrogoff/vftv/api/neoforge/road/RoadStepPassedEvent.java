package com.andrewbrogoff.vftv.api.neoforge.road;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.road.RoadStepPassedPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after a Chorister's defeat, once for each credited player whose progress advanced, after
 * every credited player's progress is written and before any keepsake is handed out. It reports the
 * player, the Step passed and their progress after.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back, and the remaining steps still run. The payload
 * is immutable and its live handles are valid only during dispatch. See the Eldritch tier spec,
 * "Defeat and credit".
 */
public final class RoadStepPassedEvent extends Event {
    private final RoadStepPassedPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload the Step passed
     */
    @Internal
    public RoadStepPassedEvent(RoadStepPassedPayload payload) {
        this.payload = payload;
    }

    /**
     * The Step passed.
     *
     * @return the payload
     */
    public RoadStepPassedPayload payload() {
        return payload;
    }
}
