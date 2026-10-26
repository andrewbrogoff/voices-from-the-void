package com.andrewbrogoff.vftv.core.warp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The colour drain's ramp: peaks, timing, and at most 1.3% change a tick, on or off. */
class ColourRampTest {
    private static final float EPS = 1e-6F;

    private static void step(ColourRamp ramp, boolean on, int ticks) {
        for (int i = 0; i < ticks; i++) {
            float before = ramp.intensity();
            ramp.tick(on);
            assertTrue(
                    Math.abs(ramp.intensity() - before) <= ColourRamp.OFF_STEP + EPS, "step " + i);
        }
    }

    @Test
    void rampHoldAndRampOut() {
        assertEquals(0F, ColourRamp.peak(0));
        for (int band = 1; band <= 3; band++) {
            ColourRamp ramp = new ColourRamp();
            ramp.start(band);
            step(ramp, true, ColourRamp.IN);
            assertEquals(ColourRamp.peak(band), ramp.intensity(), EPS);
            step(ramp, true, ColourRamp.HOLD);
            assertEquals(ColourRamp.peak(band), ramp.intensity(), EPS);
            step(ramp, true, ColourRamp.OUT);
            assertEquals(0F, ramp.intensity(), EPS);
            step(ramp, true, 5);
            assertEquals(0F, ramp.intensity(), EPS);
        }
    }

    @Test
    void aNewCueRampsFromTheCurrentIntensity() {
        ColourRamp ramp = new ColourRamp();
        ramp.start(3);
        step(ramp, true, 60);
        ramp.start(1);
        step(ramp, true, ColourRamp.IN);
        assertEquals(ColourRamp.peak(1), ramp.intensity(), EPS);
    }

    @Test
    void offRampsOutWithinFiftyTicks() {
        ColourRamp ramp = new ColourRamp();
        ramp.start(3);
        step(ramp, true, 60);
        step(ramp, false, 50);
        assertEquals(0F, ramp.intensity(), EPS);
        ramp.start(3);
        step(ramp, true, 60);
        step(ramp, false, 20);
        step(ramp, true, 40);
        assertEquals(0F, ramp.intensity(), EPS);
        step(ramp, true, 10);
        assertEquals(0F, ramp.intensity(), EPS);
        ramp.start(2);
        ramp.clear();
        assertEquals(0F, ramp.intensity());
    }
}
