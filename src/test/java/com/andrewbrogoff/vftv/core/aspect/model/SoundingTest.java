package com.andrewbrogoff.vftv.core.aspect.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SoundingTest {
    @Test
    void bandEdgesAreExact() {
        assertEquals(Sounding.Band.NEAR, Sounding.band(15.999, 16, 64, 192));
        assertEquals(Sounding.Band.MID, Sounding.band(16, 16, 64, 192));
        assertEquals(Sounding.Band.MID, Sounding.band(63.999, 16, 64, 192));
        assertEquals(Sounding.Band.FAR, Sounding.band(64, 16, 64, 192));
        assertEquals(Sounding.Band.FAR, Sounding.band(192, 16, 64, 192), "the radius is inclusive");
        assertNull(Sounding.band(192.001, 16, 64, 192));
    }

    @Test
    void directionIsTheCompassPointNorthBeingNegativeZ() {
        assertEquals(0, Sounding.direction(0, -10));
        assertEquals(1, Sounding.direction(10, -10));
        assertEquals(2, Sounding.direction(10, 0));
        assertEquals(3, Sounding.direction(10, 10));
        assertEquals(4, Sounding.direction(0, 10));
        assertEquals(5, Sounding.direction(-10, 10));
        assertEquals(6, Sounding.direction(-10, 0));
        assertEquals(7, Sounding.direction(-10, -10));
        assertEquals(0, Sounding.direction(0, 0), "straight above or below reads as north");
        assertEquals(0, Sounding.direction(-1, -10), "within 22.5 degrees of north");
    }

    @Test
    void verticalSplitsAt22AndAHalfDegrees() {
        assertEquals(Sounding.Vertical.UP, Sounding.vertical(0, 5, 0));
        assertEquals(Sounding.Vertical.DOWN, Sounding.vertical(3, -10, 0));
        assertEquals(Sounding.Vertical.LEVEL, Sounding.vertical(10, 4, 0));
        assertEquals(Sounding.Vertical.LEVEL, Sounding.vertical(10, -4, 0));
        assertEquals(Sounding.Vertical.UP, Sounding.vertical(10, 4.2, 0));
    }
}
