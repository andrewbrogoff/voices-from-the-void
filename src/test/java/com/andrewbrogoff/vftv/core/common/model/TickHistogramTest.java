package com.andrewbrogoff.vftv.core.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TickHistogramTest {
    @Test
    void emptyHistogramReportsZero() {
        TickHistogram h = new TickHistogram();
        assertEquals(0, h.count());
        assertEquals(0, h.mean());
        assertEquals(0, h.percentile(0.99));
        assertEquals(0, h.max());
    }

    @Test
    void smallValuesAreExact() {
        TickHistogram h = new TickHistogram();
        for (int v = 0; v < 32; v++) {
            h.record(v);
        }
        assertEquals(32, h.count());
        assertEquals(15.5, h.mean());
        assertEquals(0, h.percentile(0));
        assertEquals(15, h.percentile(0.5));
        assertEquals(31, h.percentile(1));
    }

    @Test
    void percentilesAreWithinOneBucketAboveTheTruth() {
        TickHistogram h = new TickHistogram();
        for (long v = 1; v <= 100_000; v++) {
            h.record(v * 1_000); // 1 µs .. 100 ms
        }
        for (double q : new double[] {0.5, 0.9, 0.99, 0.999}) {
            long truth = (long) Math.ceil(q * 100_000) * 1_000;
            long reported = h.percentile(q);
            assertTrue(reported >= truth, () -> q + ": " + reported + " < " + truth);
            assertTrue(reported <= truth + truth / 32, () -> q + ": " + reported + " too high");
        }
        assertEquals(100_000_000, h.percentile(1));
        assertEquals(100_000_000, h.max());
        assertEquals(50_000_500.0, h.mean());
    }

    @Test
    void bucketBoundsAreContiguous() {
        long previous = -1;
        for (int i = 0; i < 1152; i++) {
            long upper = TickHistogram.upperBound(i);
            assertTrue(upper > previous, "bucket " + i);
            assertEquals(i, TickHistogram.index(upper));
            assertEquals(i, TickHistogram.index(previous + 1));
            previous = upper;
        }
        assertEquals(TickHistogram.MAX, previous);
    }

    @Test
    void clampsNegativeAndHugeValues() {
        TickHistogram h = new TickHistogram();
        h.record(-5);
        h.record(Long.MAX_VALUE);
        assertEquals(TickHistogram.MAX, h.total());
        assertEquals(0, h.percentile(0.5));
        assertEquals(TickHistogram.MAX, h.percentile(1));
    }

    @Test
    void resetForgetsEverything() {
        TickHistogram h = new TickHistogram();
        h.record(1_000);
        h.reset();
        assertEquals(0, h.count());
        assertEquals(0, h.total());
        assertEquals(0, h.percentile(1));
    }

    @Test
    void rejectsAQuantileOutsideZeroToOne() {
        TickHistogram h = new TickHistogram();
        assertThrows(IllegalArgumentException.class, () -> h.percentile(-0.1));
        assertThrows(IllegalArgumentException.class, () -> h.percentile(1.1));
    }

    @Test
    void recordingAllocatesNothing() {
        TickHistogram h = new TickHistogram();
        Allocations.assertNone("recording 100,000 values", () -> recordMany(h));
    }

    private static void recordMany(TickHistogram h) {
        for (int i = 0; i < 100_000; i++) {
            h.record(i * 37L);
        }
    }
}
