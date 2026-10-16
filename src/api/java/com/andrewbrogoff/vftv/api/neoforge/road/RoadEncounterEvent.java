package com.andrewbrogoff.vftv.api.neoforge.road;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.road.RoadEncounterPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after the change it reports: {@code STARTED} after a counted rise, {@code DEFEATED} after
 * a defeat's credit, {@code RESET} after a reset. It reports the Step, the outcome and the
 * participants at that moment.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable. See the Eldritch
 * tier spec, "Encounters".
 */
public final class RoadEncounterEvent extends Event {
    private final RoadEncounterPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload the encounter's change
     */
    @Internal
    public RoadEncounterEvent(RoadEncounterPayload payload) {
        this.payload = payload;
    }

    /**
     * The encounter's change.
     *
     * @return the payload
     */
    public RoadEncounterPayload payload() {
        return payload;
    }
}
