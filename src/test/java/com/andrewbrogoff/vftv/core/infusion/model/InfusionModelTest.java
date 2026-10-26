package com.andrewbrogoff.vftv.core.infusion.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.Allocations;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The infusion spec's vector tables, replayed against the model tier. */
class InfusionModelTest {
    private static final RunRules DEFAULTS = InfusionTuning.DEFAULTS.runRules();

    private static long asLong(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    private static long[] cells(int[][] offsets) {
        long[] set = new long[InfusionMath.WORDS];
        for (int[] o : offsets) {
            InfusionMath.set(set, InfusionMath.index(o[0], o[1], o[2]));
        }
        return set;
    }

    /** (L, I, P, B, S, p) for one altar at the default tuning. */
    private static int[] assess(long[] pedestals, long[] items, long[] stabilisers, int r, int wp) {
        int l = InfusionMath.lonePedestals(pedestals);
        int i = InfusionMath.loneItems(pedestals, items);
        int p = InfusionMath.matchedPairs(stabilisers);
        int b = InfusionMath.bonus(p, DEFAULTS.maxPairs(), DEFAULTS.perPair());
        int s =
                InfusionMath.stability(
                        InfusionMath.rawStability(
                                r, l, i, wp, b, DEFAULTS.lonePedestal(), DEFAULTS.loneItem()));
        return new int[] {l, i, p, b, s, InfusionMath.chance(s, DEFAULTS.maxChance())};
    }

    private static final int[][] RING = {
        {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}, {2, 0, 2}, {-2, 0, -2}, {2, 0, -2}, {-2, 0, 2}
    };

    @Test
    void stabilityVectors() {
        long[] ring = cells(RING);
        long[] twoPairs = cells(new int[][] {{3, 0, 0}, {-3, 0, 0}, {0, 1, 3}, {0, 1, -3}});
        assertArrayEquals(new int[] {0, 0, 2, 6, 86, 21}, assess(ring, ring, twoPairs, 20, 0));
        long[] b = cells(new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {3, 0, 3}});
        long[] bItems = cells(new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {3, 0, 3}});
        assertArrayEquals(new int[] {1, 1, 0, 0, 80, 30}, assess(b, bItems, new long[6], 10, 0));
        List<int[]> twelve = new ArrayList<>();
        for (int dz = -4; dz <= 4; dz++) {
            twelve.add(new int[] {4, 0, dz});
            twelve.add(new int[] {-4, 0, -dz});
        }
        for (int dz = 0; dz <= 2; dz++) {
            twelve.add(new int[] {3, 1, dz});
            twelve.add(new int[] {-3, 1, -dz});
        }
        assertArrayEquals(
                new int[] {0, 0, 12, 24, 74, 39},
                assess(ring, ring, cells(twelve.toArray(int[][]::new)), 50, 0));
        long[] d = cells(new int[][] {{1, 0, 0}, {0, 0, 1}});
        assertArrayEquals(new int[] {2, 0, 0, 0, 0, 150}, assess(d, d, new long[6], 100, 0));
        long[] e = cells(new int[][] {{0, 1, 0}, {0, 2, 0}, {3, 0, 1}});
        assertArrayEquals(new int[] {0, 0, 0, 0, 100, 0}, assess(ring, ring, e, 0, 0));
        assertArrayEquals(new int[] {0, 0, 2, 6, 76, 36}, assess(ring, ring, twoPairs, 20, 10));
        assertEquals(-12, InfusionMath.rawStability(100, 2, 0, 0, 0, 6, 4), "the clamp input");
    }

    @Test
    void countVectors() {
        int[][] costs = {
            {4},
            {12, 20, 9},
            {12, 20, 9},
            {40, 40},
            {1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000}
        };
        int[] components = {1, 8, 8, 4, 16};
        int[] p = {150, 21, 0, 60, 150};
        int[][] want = { // nominal, full-supply, worst, clean floor, ceiling
            {2, 722, 5, 443, 305},
            {19, 668, 49, 353, 349},
            {19, 1000, 49, 1000, 349},
            {24, 226, 84, 5, 384},
            {2016, 0, 8016, 0, 8316}
        };
        for (int row = 0; row < costs.length; row++) {
            int nominal = InfusionMath.nominal(costs[row], 4, components[row]);
            int worst = InfusionMath.worst(costs[row], components[row]);
            assertArrayEquals(
                    want[row],
                    new int[] {
                        nominal,
                        InfusionMath.clean(p[row], nominal),
                        worst,
                        InfusionMath.clean(p[row], worst),
                        InfusionMath.ceiling(worst, 6, 50)
                    },
                    "row " + row);
        }
        assertEquals(
                18_016,
                InfusionMath.ceiling(
                        InfusionMath.worst(
                                new int[] {1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000}, 16),
                        10,
                        1000));
        assertEquals(1000, InfusionMath.clean(0, 18_016));
        assertEquals(0, InfusionMath.clean(1, 18_016));
        assertEquals(0, InfusionMath.clean(1000, 18_016));
    }

    @Test
    void floorNeverExceedsExact() {
        for (int p = 0; p <= 1000; p += 37) {
            double exact = 1000;
            for (int n = 0; n <= 18_016; n += 211) {
                exact = 1000 * Math.pow((1000 - p) / 1000.0, n);
                assertTrue(InfusionMath.clean(p, n) <= Math.floor(exact + 1e-9), p + " " + n);
            }
        }
    }

    @Test
    void phaseAndOrderVectors() {
        int[][] rows = {
            {0, 64, 0, 7},
            {1, 64, 0, 1},
            {0, 64, 1, 5},
            {-37, 70, 512, 4},
            {-1, -64, -1, 8},
            {29999999, -64, -29999999, 3}
        };
        for (int[] r : rows) {
            assertEquals(r[3], InfusionMath.phase(asLong(r[0], r[1], r[2]), 10));
        }
        long pos = asLong(0, 64, 0);
        long due = InfusionMath.nextDue(100, pos, 10);
        assertTrue(due > 100 && due <= 110 && due % 10 == 7);
        assertEquals(107, InfusionMath.nextDue(97, pos, 10));
        assertEquals(117, InfusionMath.nextDue(107, pos, 10));
        long[] around = {
            asLong(-39, 70, 512), asLong(-37, 70, 510), asLong(-37, 70, 514), asLong(-35, 70, 512)
        };
        long[] sorted = around.clone();
        java.util.Arrays.sort(sorted);
        assertArrayEquals(around, sorted, "signed asLong order");
        long[] near = {asLong(-1, 64, 0), asLong(0, 64, 1), asLong(0, 64, -1), asLong(1, 64, 0)};
        long[] nearSorted = near.clone();
        java.util.Arrays.sort(nearSorted);
        assertArrayEquals(near, nearSorted);
    }

    @Test
    void seedAndRollVectors() {
        assertEquals(0x42B223F609FBAC52L, InfusionMath.seed(0x0123456789ABCDEFL));
        assertEquals(0x4957B49D21C3ABCBL, InfusionMath.seed(1L));
        assertEquals(0x0CC18069B53A003DL, InfusionMath.seed(0xFFFFFFFFFFFFFFFFL));
        long seed = InfusionMath.seed(0x0123456789ABCDEFL);
        List<String> first = new ArrayList<>();
        int upsets = 0;
        for (int k = 0; k < 10_000; k++) {
            if (InfusionMath.upsets(seed, k, 150)) {
                upsets++;
                if (first.size() < 6) {
                    long r = Long.remainderUnsigned(InfusionMath.draw(seed, k, 0), 1000);
                    first.add(
                            k
                                    + " "
                                    + r
                                    + " "
                                    + (InfusionMath.jolts(seed, k, 1, 1)
                                            ? "JOLT " + InfusionMath.target(seed, k, 5)
                                            : "SPILL"));
                }
            }
        }
        assertEquals(
                List.of(
                        "4 108 JOLT 1",
                        "7 136 SPILL",
                        "31 110 JOLT 1",
                        "33 15 SPILL",
                        "52 42 JOLT 2",
                        "53 117 SPILL"),
                first);
        assertEquals(142, upsets * 1000 / 10_000);
        assertFalse(InfusionMath.upsets(seed, 4, 0), "p 0 never upsets");
    }

    @Test
    void geometryCountsEachPairOnce() {
        long[] none = new long[InfusionMath.WORDS];
        assertEquals(
                0, InfusionMath.matchedPairs(cells(new int[][] {{0, 0, 0}, {0, 2, 0}})), "axis");
        long[] full = new long[InfusionMath.WORDS];
        for (int i = 0; i < InfusionMath.CELLS; i++) {
            InfusionMath.set(full, i);
        }
        assertEquals(
                (InfusionMath.CELLS - 4) / 2, InfusionMath.matchedPairs(full), "324 stabilisers");
        assertEquals(0, InfusionMath.loneItems(full, none));
        for (int i = 0; i < InfusionMath.CELLS; i++) {
            assertEquals(
                    i,
                    InfusionMath.index(InfusionMath.dx(i), InfusionMath.dy(i), InfusionMath.dz(i)));
            assertEquals(i, InfusionMath.mirror(InfusionMath.mirror(i)));
        }
    }

    @Test
    void kuhnVectors() {
        // components {iron or gold, iron} against items [iron, gold]
        assertArrayEquals(
                new int[] {1, 0},
                InfusionMath.match(new boolean[][] {{true, true}, {true, false}}, 2));
        // {iron, iron, amethyst} against [amethyst, iron, iron]
        assertArrayEquals(
                new int[] {2, 1, 0},
                InfusionMath.match(
                        new boolean[][] {
                            {false, true, true}, {false, true, true}, {true, false, false}
                        },
                        3));
        // {iron, gold} against [iron, iron]
        assertNull(InfusionMath.match(new boolean[][] {{true, true}, {false, false}}, 2));
        // {iron} against [iron, stick]: a stray
        assertNull(InfusionMath.match(new boolean[][] {{true, false}}, 2));
    }

    @Test
    void enchantVectors() {
        assertEquals(300, 20 * InfusionMath.levels(0, 5));
        assertEquals(360, 24 * InfusionMath.levels(0, 5));
        assertEquals(30, InfusionMath.enchantInstability(10, 5, 0, 5, 0));
        assertEquals(240, 20 * InfusionMath.levels(2, 5));
        assertEquals(288, 24 * InfusionMath.levels(2, 5));
        assertEquals(35, InfusionMath.enchantInstability(10, 5, 3, 5, 2));
        assertEquals(100, 20 * InfusionMath.levels(4, 5));
        assertEquals(120, 24 * InfusionMath.levels(4, 5));
        assertEquals(30, InfusionMath.enchantInstability(10, 5, 4, 5, 4));
        assertEquals(100, InfusionMath.enchantInstability(100, 100, 50, 255, 0));
    }

    @Test
    void tuningParsesAndRejects() {
        assertEquals(InfusionTuning.DEFAULTS, InfusionTuning.parse("{}"));
        assertEquals(
                InfusionTuning.DEFAULTS,
                InfusionTuning.parse(InfusionTuning.DEFAULTS.toJson().toString()));
        assertEquals(7, InfusionTuning.parse("{\"run.rate\": 7}").runRules().rate());
        assertEquals(1200, InfusionTuning.DEFAULTS.armTtl());
        assertEquals(10, InfusionTuning.DEFAULTS.armRefresh());
        assertEquals(100, InfusionTuning.DEFAULTS.abandonWindow());
        assertEquals(5, InfusionTuning.DEFAULTS.instabilityPerLevel());
        assertEquals(10, InfusionTuning.DEFAULTS.interval());
        assertEquals(150, InfusionTuning.DEFAULTS.get("upset.max_chance"));
        for (String bad :
                List.of(
                        "{\"nope\": 1}",
                        "{\"run.rate\": 0}",
                        "{\"run.rate\": 1.5}",
                        "{\"run.rate\": \"4\"}",
                        "{\"upset.jolt_weight\": 0, \"upset.spill_weight\": 0}",
                        "{\"upset.max\": 64, \"upset.spill\": 200}",
                        "[]",
                        "{")) {
            assertThrows(IllegalArgumentException.class, () -> InfusionTuning.parse(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> InfusionTuning.DEFAULTS.get("nope"));
        assertThrows(IllegalArgumentException.class, () -> new InfusionTuning(List.of(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RunRules(10, 4, 6, 4, 3, 8, 150, 6, 50, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RunRules(0, 4, 6, 4, 3, 8, 150, 6, 50, 1, 1));
    }

    @Test
    void hotPathsAllocateNothing() {
        int[] costs = {12, 20, 9};
        long seed = InfusionMath.seed(42);
        long[] ring = cells(RING);
        Allocations.assertNone(
                "stability, counts, clean and rolls",
                () -> {
                    int s =
                            InfusionMath.stability(
                                    InfusionMath.rawStability(
                                            20,
                                            InfusionMath.lonePedestals(ring),
                                            InfusionMath.loneItems(ring, ring),
                                            0,
                                            InfusionMath.bonus(
                                                    InfusionMath.matchedPairs(ring), 8, 3),
                                            6,
                                            4));
                    int p = InfusionMath.chance(s, 150);
                    int n = InfusionMath.nominal(costs, 4, 8);
                    int w = InfusionMath.worst(costs, 8);
                    InfusionMath.clean(p, n);
                    InfusionMath.clean(p, InfusionMath.ceiling(w, 6, 50));
                    for (int k = 0; k < 64; k++) {
                        if (InfusionMath.upsets(seed, k, p) && InfusionMath.jolts(seed, k, 1, 1)) {
                            InfusionMath.target(seed, k, 5);
                        }
                    }
                });
    }
}
