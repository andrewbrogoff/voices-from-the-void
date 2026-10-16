package com.andrewbrogoff.vftv.api.road;

import com.andrewbrogoff.vftv.api.Internal;
import java.util.List;
import java.util.UUID;

/**
 * A Step's encounter started, was defeated or reset. See the Eldritch tier spec, "Encounters".
 *
 * @param step the Step, 1 to 3
 * @param outcome what happened to the encounter
 * @param participants the UUIDs of the encounter's participants at that moment, unmodifiable, at
 *     most {@link RoadLimits#MAX_PARTICIPANTS}
 */
public record RoadEncounterPayload(int step, EncounterOutcome outcome, List<UUID> participants) {
    /** Constructed by vftv only. */
    @Internal
    public RoadEncounterPayload {
        if (step < 1 || step > 3) {
            throw new IllegalArgumentException("step out of range: " + step);
        }
        participants = List.copyOf(participants);
    }
}
