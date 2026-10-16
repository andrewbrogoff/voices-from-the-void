package com.andrewbrogoff.vftv.core.flux.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

class FluxMathTest {
    private static final FluxRules DEFAULTS = rates(1000, 1000, 250);
    private static final FluxRules HALF = rates(500, 1000, 250);

    /** Rules at the default tuning with these three rates. */
    private static FluxRules rates(int essentiaPm, int taxPm, int voidPm) {
        int[] tuning = FluxTuning.defaults();
        tuning[0] = essentiaPm;
        tuning[1] = taxPm;
        tuning[2] = voidPm;
        return FluxTuning.rules(tuning, true, true, false);
    }

    private static int[] a(int... values) {
        return values;
    }

    private static int[] times8(int value) {
        return new int[] {value, value, value, value, value, value, value, value};
    }

    /** The spill table of the Hush spec, "The pinned functions": effective, neutral, spill. */
    @Test
    void spillVectors() {
        assertEquals(0, ordinary(a(10, 0, 1), a(10, 0, 1)));
        assertEquals(0, ordinary(a(6, 2, 1), a(6, 2, 1)));
        assertEquals(7, ordinary(a(31, 7), a(25, 6)));
        assertEquals(2000, ordinary(a(4000), a(2000)));
        assertEquals(0, ordinary(a(3), a(3)));
        assertEquals(0, ordinary(a(50), a(50)), "both clamped to m 500");
        assertEquals(6, ordinary(a(36), a(30)));
        assertEquals(1, ordinary(a(4), a(3)));
        assertEquals(6, ordinary(a(14, 14), a(11, 11)));
        assertEquals(10, FluxMath.visSpill(VisKind.VOID, a(40), a(40), DEFAULTS));
        assertEquals(1, FluxMath.visSpill(VisKind.VOID, a(1), a(1), DEFAULTS));
        assertEquals(16_000, ordinary(times8(4000), times8(2000)));
    }

    private static int ordinary(int[] effective, int[] neutral) {
        return FluxMath.visSpill(VisKind.ORDINARY, effective, neutral, DEFAULTS);
    }

    @Test
    void anOrdinarySpendBelowNeutralSpillsNothingForThatAspect() {
        assertEquals(3, ordinary(a(5, 0), a(2, 9)));
    }

    @Test
    void voidIgnoresNeutral() {
        assertEquals(250, FluxMath.visSpill(VisKind.VOID, a(1000), a(), DEFAULTS));
    }

    @Test
    void theLargestVisSpillIsInRange() {
        FluxRules full = rates(1000, 1000, 1000);
        assertEquals(80_000, FluxMath.visSpill(VisKind.VOID, times8(10_000), a(), full));
        assertEquals(80_000, FluxMath.visSpill(VisKind.ORDINARY, times8(10_000), times8(0), full));
    }

    @Test
    void absentRulesPriceEverySpillAtZero() {
        assertEquals(0, FluxMath.visSpill(VisKind.ORDINARY, a(4000), a(2000), null));
        assertEquals(0, FluxMath.visSpill(VisKind.VOID, a(40), a(40), null));
        assertEquals(0, FluxMath.essentiaFlux(a(640_000), SpillCause.EMPTIED, null));
        assertEquals(0, FluxMath.essentiaFluxBound(50, SpillCause.UPSET, null));
    }

    @Test
    void zeroRatesSpillNothing() {
        FluxRules zero = rates(0, 0, 0);
        assertEquals(0, FluxMath.visSpill(VisKind.ORDINARY, a(4000), a(2000), zero));
        assertEquals(0, FluxMath.visSpill(VisKind.VOID, a(40), a(40), zero));
        assertEquals(0, FluxMath.essentiaFlux(a(1000), SpillCause.REMOVED, zero));
    }

    /** "essentiaFlux at 1000 ‰ / 500 ‰". */
    @Test
    void essentiaVectors() {
        int[][] rows = {
            {0, 0, 0},
            {1, 1, 1},
            {3, 3, 2},
            {200, 200, 100},
            {1000, 1000, 500},
            {10_000, 10_000, 5000},
            {640_000, 640_000, 320_000}
        };
        for (int[] row : rows) {
            assertEquals(row[1], FluxMath.essentiaFlux(a(row[0]), SpillCause.EMPTIED, DEFAULTS));
            assertEquals(row[2], FluxMath.essentiaFlux(a(row[0]), SpillCause.EMPTIED, HALF));
            assertEquals(row[1], FluxMath.essentiaFluxBound(row[0], SpillCause.EMPTIED, DEFAULTS));
            assertEquals(row[2], FluxMath.essentiaFluxBound(row[0], SpillCause.EMPTIED, HALF));
        }
    }

    @Test
    void infusionUpsetBoundAtItsDefaults() {
        assertEquals(300, 6 * FluxMath.essentiaFluxBound(50, SpillCause.UPSET, DEFAULTS));
    }

    @Test
    void everyCausePricesAlike() {
        for (SpillCause cause : SpillCause.values()) {
            assertEquals(2, FluxMath.essentiaFlux(a(1, 2), cause, HALF));
        }
    }

    /**
     * The bound is exact: it equals the flux of every split of its units (every two- and three-part
     * split up to 1,000 units here, at several rates), and is monotone in the units.
     */
    @Test
    void theBoundEqualsEverySplitAndIsMonotone() {
        for (int rate : new int[] {0, 1, 7, 250, 333, 500, 999, 1000}) {
            FluxRules rules = rates(rate, 1000, 250);
            int previous = 0;
            for (int units = 0; units <= 1000; units++) {
                int bound = FluxMath.essentiaFluxBound(units, SpillCause.UPSET, rules);
                assertTrue(bound >= previous, "monotone at " + units);
                previous = bound;
                for (int x = 0; x <= units; x++) {
                    assertEquals(
                            bound, FluxMath.essentiaFlux(a(x, units - x), SpillCause.UPSET, rules));
                }
                if (units <= 60) {
                    for (int x = 0; x <= units; x++) {
                        for (int y = 0; x + y <= units; y++) {
                            int[] split = a(x, y, units - x - y);
                            assertEquals(
                                    bound, FluxMath.essentiaFlux(split, SpillCause.UPSET, rules));
                        }
                    }
                }
            }
        }
    }

    @Test
    void essentiaTotalsAreSummedInLong() {
        int[] split = new int[64];
        java.util.Arrays.fill(split, 10_000);
        assertEquals(640_000, FluxMath.essentiaFlux(split, SpillCause.REMOVED, DEFAULTS));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FluxMath.essentiaFlux(
                                a(Integer.MAX_VALUE, Integer.MAX_VALUE, 2),
                                SpillCause.REMOVED,
                                DEFAULTS));
    }

    @Test
    void essentiaDataErrorsThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxMath.essentiaFlux(a(5, -1), SpillCause.EMPTIED, DEFAULTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxMath.essentiaFlux(a(640_001), SpillCause.EMPTIED, DEFAULTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxMath.essentiaFluxBound(-1, SpillCause.EMPTIED, DEFAULTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxMath.essentiaFluxBound(640_001, SpillCause.EMPTIED, null));
    }

    @Test
    void visDataErrorsThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxMath.visSpill(VisKind.VOID, new int[9], a(), DEFAULTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxMath.visSpill(VisKind.VOID, a(-1), a(), DEFAULTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxMath.visSpill(VisKind.VOID, a(10_001), a(), DEFAULTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxMath.visSpill(VisKind.ORDINARY, a(1, 2), a(1), DEFAULTS));
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxMath.visSpill(VisKind.ORDINARY, a(1), a(10_001), DEFAULTS));
    }

    @Test
    void pricingAllocatesNothing() {
        int[] effective = times8(4000);
        int[] neutral = times8(2000);
        int[] essentia = a(100, 200, 300);
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long allocated = Long.MAX_VALUE;
        long sink = 0;
        // Warm-up rounds first; one-off costs (class loading, compilation) land there.
        for (int round = 0; round < 5; round++) {
            long before = threads.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100_000; i++) {
                sink += FluxMath.visSpill(VisKind.ORDINARY, effective, neutral, DEFAULTS);
                sink += FluxMath.visSpill(VisKind.VOID, effective, neutral, DEFAULTS);
                sink += FluxMath.essentiaFlux(essentia, SpillCause.EMPTIED, DEFAULTS);
                sink += FluxMath.essentiaFluxBound(i, SpillCause.UPSET, DEFAULTS);
            }
            allocated = threads.getThreadAllocatedBytes(thread) - before;
        }
        assertTrue(sink > 0);
        assertEquals(0, allocated, "bytes allocated while pricing 400,000 spills");
    }
}
