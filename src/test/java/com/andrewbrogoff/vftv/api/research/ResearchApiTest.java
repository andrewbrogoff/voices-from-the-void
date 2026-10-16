package com.andrewbrogoff.vftv.api.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** The research API: payload immutability. */
// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class ResearchApiTest {
    @Test
    void researchLearnedPayloadIsImmutable() {
        SequencedMap<ResourceKey<Aspect>, Integer> points = new LinkedHashMap<>();
        points.put(AspectKeys.LUX, 1);
        points.put(AspectKeys.AER, 2);
        WarpPreview warp = new WarpPreview(0, 4, 0, false);
        ResearchLearnedPayload payload =
                new ResearchLearnedPayload(
                        null, ResearchKeys.SILENCE_BENEATH, ResearchSource.LISTENED, warp, points);
        points.put(AspectKeys.IGNIS, 5);
        points.put(AspectKeys.AER, 9);
        assertEquals(
                List.of(AspectKeys.AER, AspectKeys.LUX), List.copyOf(payload.points().keySet()));
        assertEquals(2, payload.points().get(AspectKeys.AER));
        assertEquals(1, payload.points().get(AspectKeys.LUX));
        assertEquals(ResearchKeys.SILENCE_BENEATH, payload.research());
        assertEquals(ResearchSource.LISTENED, payload.source());
        assertEquals(warp, payload.warp());
        assertThrows(
                UnsupportedOperationException.class,
                () -> payload.points().put(AspectKeys.IGNIS, 1));
        assertThrows(UnsupportedOperationException.class, () -> payload.points().clear());
    }

    @Test
    void stageReachedPayloadIsImmutable() {
        StageReachedPayload payload = new StageReachedPayload(null, 3, 2, ResearchSource.FOUND);
        assertEquals(3, payload.stage());
        assertEquals(2, payload.previousStage());
        assertEquals(ResearchSource.FOUND, payload.source());
    }
}
