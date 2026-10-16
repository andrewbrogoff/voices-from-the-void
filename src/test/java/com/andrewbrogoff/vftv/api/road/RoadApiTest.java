package com.andrewbrogoff.vftv.api.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Payload immutability for the road feature: one test per payload type (API spec, "Tests"). */
// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class RoadApiTest {
    @Test
    void roadStepPassedPayloadHoldsItsValuesAndChecksRanges() {
        RoadStepPassedPayload p = new RoadStepPassedPayload(null, 2, 2);
        assertNull(p.player());
        assertEquals(2, p.step());
        assertEquals(2, p.progress());
        assertThrows(IllegalArgumentException.class, () -> new RoadStepPassedPayload(null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RoadStepPassedPayload(null, 4, 3));
        assertThrows(IllegalArgumentException.class, () -> new RoadStepPassedPayload(null, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RoadStepPassedPayload(null, 3, 4));
    }

    @Test
    void roadEncounterPayloadCopiesItsParticipants() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<UUID> input = new ArrayList<>(List.of(a, b));
        RoadEncounterPayload p = new RoadEncounterPayload(1, EncounterOutcome.STARTED, input);

        input.add(UUID.randomUUID());
        input.remove(a);

        assertEquals(List.of(a, b), p.participants());
        assertEquals(EncounterOutcome.STARTED, p.outcome());
        assertEquals(1, p.step());
        assertThrows(UnsupportedOperationException.class, () -> p.participants().add(a));
        assertThrows(UnsupportedOperationException.class, () -> p.participants().remove(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RoadEncounterPayload(0, EncounterOutcome.RESET, List.of()));
    }

    @Test
    void endingChosenPayloadHoldsItsValues() {
        EndingChosenPayload p = new EndingChosenPayload(null, Ending.SILENCED, null);
        assertNull(p.player());
        assertEquals(Ending.SILENCED, p.ending());
        assertNull(p.warpChange());
    }
}
