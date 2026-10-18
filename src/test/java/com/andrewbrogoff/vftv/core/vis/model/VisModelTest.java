package com.andrewbrogoff.vftv.core.vis.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The vis model tier: the quote's edges, regeneration, spread, the ring, the index and rules. */
class VisModelTest {
    private static SortedMap<String, Integer> map(Object... kv) {
        SortedMap<String, Integer> m = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    private static QuoteMath.Result quote(
            double cap,
            double w,
            VisKind kind,
            int capacity,
            SortedMap<String, Integer> pools,
            boolean inert,
            SortedMap<String, Integer> base) {
        return QuoteMath.quote(
                new QuoteMath.Inputs(
                        base,
                        cap,
                        0,
                        0.30,
                        w,
                        0.50,
                        4.00,
                        kind,
                        FluxTuning.rules(FluxTuning.defaults(), true, true, false),
                        capacity,
                        pools,
                        inert));
    }

    // --- the quote --------------------------------------------------------------------------

    @Test
    void halfUpAtThePerMilleBoundary() {
        // 0.85 × (1000 − 0) × 1.0005 → raw 850,425,000: m₀ rounds half up to 850 (below .5) ...
        assertEquals(850, QuoteMath.unclamped(850, 0, 1000));
        // ... and exactly half a per mille rounds up: 1000 × 500 × 1001 = 500,500,000 → 501.
        assertEquals(501, QuoteMath.unclamped(1000, 500, 1001));
        // 1000 × 500 × 999 = 499,500,000 is exactly half: up to 500.
        assertEquals(500, QuoteMath.unclamped(1000, 500, 999));
        assertEquals(499, QuoteMath.unclamped(999, 500, 999), "498,501,000 rounds down");
    }

    @Test
    void zeroBaseCostsNothingAndAnythingElseAtLeastOne() {
        assertEquals(0, QuoteMath.effective(0, 4000));
        assertEquals(1, QuoteMath.effective(1, 500));
        assertEquals(1, QuoteMath.effective(1, 1));
    }

    @Test
    void maximumFactorsDoNotOverflow() {
        // cap 2.00, no discount, W 10: raw 2 × 10¹⁰ fits in a long.
        assertEquals(20_000, QuoteMath.unclamped(2000, 0, 10_000));
        assertEquals(4000, QuoteMath.effective(1000, 4000));
    }

    @Test
    void nonFiniteWarpCountsAsOne() {
        assertEquals(1000, QuoteMath.warp(Double.NaN));
        assertEquals(1000, QuoteMath.warp(Double.POSITIVE_INFINITY));
        assertEquals(0, QuoteMath.warp(-3));
        assertEquals(10_000, QuoteMath.warp(25));
        assertEquals(0, QuoteMath.discount(Double.NaN, 0.3));
        assertFalse(QuoteMath.discountCapped(Double.NaN, 0.3));
    }

    @Test
    void affordabilityShortAndCapacity() {
        SortedMap<String, Integer> base = map("vftv:aer", 10, "vftv:ordo", 5);
        QuoteMath.Result r =
                quote(
                        1.0,
                        1.0,
                        VisKind.ORDINARY,
                        40,
                        map("vftv:aer", 12, "vftv:ordo", 4),
                        false,
                        base);
        assertFalse(r.affordable());
        assertEquals(List.of("vftv:ordo"), r.shortAspects());
        assertFalse(r.exceedsCapacity());
        QuoteMath.Result big =
                quote(2.0, 10.0, VisKind.ORDINARY, 1000, map(), false, map("vftv:aer", 1000));
        assertTrue(big.exceedsCapacity());
        QuoteMath.Result inert = quote(1.0, 1.0, VisKind.ORDINARY, 0, map(), true, base);
        assertFalse(inert.affordable());
        assertTrue(inert.inert());
        assertTrue(inert.shortAspects().isEmpty());
        QuoteMath.Result ok =
                quote(
                        1.0,
                        1.0,
                        VisKind.ORDINARY,
                        40,
                        map("vftv:aer", 10, "vftv:ordo", 5),
                        false,
                        base);
        assertTrue(ok.affordable());
    }

    @Test
    void ordinaryAndVoidSpill() {
        SortedMap<String, Integer> base = map("vftv:aer", 40);
        assertEquals(0, quote(1.0, 1.0, VisKind.ORDINARY, 40, map(), false, base).spill());
        assertEquals(10, quote(1.0, 1.0, VisKind.VOID, 40, map(), false, base).spill());
        assertEquals(10, quote(1.0, 1.25, VisKind.ORDINARY, 40, map(), false, base).spill());
    }

    @Test
    void badBaseThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> quote(1, 1, VisKind.ORDINARY, 40, map(), false, map("vftv:aer", 1001)));
        assertThrows(
                IllegalArgumentException.class,
                () -> quote(1, 1, VisKind.ORDINARY, 40, map(), false, map("vftv:aer", -1)));
        SortedMap<String, Integer> nine = new TreeMap<>();
        for (int i = 0; i < 9; i++) {
            nine.put("vftv:a" + i, 1);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> quote(1, 1, VisKind.ORDINARY, 40, map(), false, nine));
    }

    // --- regeneration -------------------------------------------------------------------------

    @Test
    void regenerationKeepsTheRemainderAndHandlesSeveralAspects() {
        int[] max = {40, 8, 1000};
        int[] cur = {0, 0, 990};
        long tick = Regen.settle(max, cur, 0, 119, 40, 20);
        // two whole intervals: 40/20 = 2 per step → 4; 8/20 → step 1 → 2; 1000/20 = 50 → capped
        assertArrayEquals(new int[] {4, 2, 1000}, cur);
        assertEquals(80, tick, "the 39-tick remainder is kept");
        assertEquals(4, Regen.settledCurrent(40, 0, 0, 119, 40, 20), "one aspect, the same value");
        assertEquals(1000, Regen.settledCurrent(1000, 990, 0, 119, 40, 20), "capped");
        assertEquals(7, Regen.settledCurrent(40, 7, 500, 100, 40, 20), "a clock going back");
        assertEquals(40, Regen.settledCurrent(40, 40, 0, 1000, 40, 20), "already full");
        tick = Regen.settle(max, cur, tick, 79, 40, 20);
        assertEquals(79, tick, "a clock going backwards counts as zero elapsed");
        assertArrayEquals(new int[] {4, 2, 1000}, cur);
    }

    @Test
    void settlementAllocatesNothing() {
        int[] max = {40, 40, 40, 40, 40, 40};
        int[] cur = new int[6];
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long allocated = Long.MAX_VALUE;
        long sink = 0;
        for (int round = 0; round < 5; round++) {
            long before = threads.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100_000; i++) {
                cur[i % 6] = 0;
                sink += Regen.settle(max, cur, 0, i, 80, 20);
                sink += QuoteMath.multiplier(850, i % 300, 1000 + i % 9000, 500, 4000);
                sink += QuoteMath.effective(i % 1000, 850);
            }
            allocated = threads.getThreadAllocatedBytes(thread) - before;
        }
        assertTrue(sink > 0);
        assertEquals(0, allocated, "bytes allocated by the kernels");
    }

    // --- natural spread -----------------------------------------------------------------------

    @Test
    void cellKeysForNegativeCoordinates() {
        assertEquals(-1, NaturalSpread.cell(-1));
        assertEquals(-1, NaturalSpread.cell(-64));
        assertEquals(-2, NaturalSpread.cell(-65));
        assertEquals(0, NaturalSpread.cell(63));
        assertEquals(1, NaturalSpread.cell(64));
    }

    @Test
    void spawnRateOverTenThousandCells() {
        NaturalSpread.Placement p = new NaturalSpread.Placement(0.35, 4, 12, 8);
        int spawned = 0;
        for (int rx = -50; rx < 50; rx++) {
            for (int rz = -50; rz < 50; rz++) {
                NaturalSpread.Candidate c = NaturalSpread.candidate(0x0123456789ABCDEFL, rx, rz, p);
                spawned += c.spawns() ? 1 : 0;
                assertEquals(rx, NaturalSpread.cell(c.x()));
                assertEquals(rz, NaturalSpread.cell(c.z()));
                assertTrue(c.offset() >= 4 && c.offset() <= 12);
            }
        }
        assertEquals(3502, spawned, "the reference spawns 35.02%");
    }

    // --- the starter ring ---------------------------------------------------------------------

    @Test
    void everyPerimeterIsCompleteAndContiguous() {
        for (int k = 1; k <= 64; k++) {
            Set<String> seen = new HashSet<>();
            int[] prev = StarterRing.column(k, 8 * k - 1);
            for (int i = 0; i < 8 * k; i++) {
                int[] c = StarterRing.column(k, i);
                assertEquals(k, Math.max(Math.abs(c[0]), Math.abs(c[1])), "on ring " + k);
                assertTrue(seen.add(c[0] + "," + c[1]), "each column once");
                assertEquals(1, Math.abs(c[0] - prev[0]) + Math.abs(c[1] - prev[1]), "contiguous");
                prev = c;
            }
            assertEquals(8 * k, seen.size());
        }
    }

    @Test
    void effectiveRadiusForEveryCombination() {
        for (boolean dedicated : new boolean[] {false, true}) {
            for (boolean overworld : new boolean[] {false, true}) {
                for (boolean ops : new boolean[] {false, true}) {
                    for (int radius : new int[] {0, 16}) {
                        int expected = dedicated && overworld && ops && radius > 0 ? radius : 0;
                        assertEquals(
                                expected,
                                StarterRing.effectiveRadius(dedicated, overworld, ops, radius));
                    }
                }
            }
        }
        assertEquals(8, StarterRing.firstRing(8, 0));
        assertEquals(17, StarterRing.firstRing(8, 16));
        assertEquals(121, StarterRing.firstRing(8, 120));
        assertEquals(181, StarterRing.firstRing(8, Integer.MAX_VALUE), "no wrap below 180");
    }

    /** A flat world: ground top at y 63 (surface 64), air above, room everywhere. */
    private static final class Flat implements StarterRing.Terrain {
        int surface = 64;
        boolean admits = true;
        boolean border = true;
        Set<String> blocked = new HashSet<>();

        @Override
        public boolean insideBorder(int x, int z) {
            return border;
        }

        @Override
        public int surface(int x, int z) {
            return surface;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 319;
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return y >= surface && !blocked.contains(x + "," + y + "," + z);
        }

        @Override
        public boolean sturdyDry(int x, int y, int z) {
            return y < surface;
        }

        @Override
        public boolean openDry(int x, int y, int z) {
            return y >= surface;
        }

        @Override
        public boolean admits(int x, int y, int z) {
            return admits;
        }
    }

    @Test
    void candidateRules() {
        Flat flat = new Flat();
        assertArrayEquals(new int[] {8, 67, 3}, StarterRing.candidate(flat, 8, 3, 0, 64, 0, 3));
        // height 5 is still within reach: eye at 65.62, centre at 69.5 → 3.88
        assertArrayEquals(new int[] {8, 69, 3}, StarterRing.candidate(flat, 8, 3, 0, 64, 0, 5));
        flat.blocked.add("8,67,3");
        assertNull(StarterRing.candidate(flat, 8, 3, 0, 64, 0, 3), "the node position must be air");
        flat.blocked.clear();
        flat.border = false;
        assertNull(StarterRing.candidate(flat, 8, 3, 0, 64, 0, 3), "inside the border");
        flat.border = true;
        flat.admits = false;
        assertNull(StarterRing.candidate(flat, 8, 3, 0, 64, 0, 3), "cell full or spacing");
        flat.admits = true;
        assertNull(StarterRing.candidate(flat, 180, 0, 0, 0, 0, 3), "beyond 180 in 3-D");
        flat.surface = 400;
        assertNull(StarterRing.candidate(flat, 8, 3, 0, 64, 0, 3), "inside the build height");
    }

    @Test
    void searchHonoursTheFirstRingAndThe180Cap() {
        Flat flat = new Flat();
        int[] found = StarterRing.search(flat, 0x0123456789ABCDEFL, 0, 64, 0, 8, 24, 3);
        assertArrayEquals(new int[] {8, 67, 3}, found, "ring 8's first column, (8, 3)");
        assertNull(StarterRing.search(flat, 1, 0, 64, 0, 181, 24, 3), "a first ring past 180");
        flat.admits = false;
        assertNull(StarterRing.search(flat, 1, 0, 64, 0, 8, 2, 3));
    }

    // --- the index ----------------------------------------------------------------------------

    @Test
    void nearestRadiusEdgesTiesAndLimits() {
        NodeIndex index = new NodeIndex();
        index.add(5, 10, 64, 0);
        index.add(3, -10, 64, 0);
        // Equal distance (10) from the origin's centre: the lower id wins.
        assertEquals(3, index.nearest(0.5, 64.5, 0.5, 10));
        assertEquals(-1, index.nearest(0.5, 64.5, 0.5, 9), "the radius is inclusive, 9 < 10");
        assertThrows(IllegalArgumentException.class, () -> index.nearest(0, 0, 0, 257));
        assertEquals(-1, new NodeIndex().nearest(0, 0, 0, 256));
        assertArrayEquals(new int[] {10, 64, 0}, index.position(5));
        assertEquals(10.0, index.closest(0.5, 64.5, 0.5, 16), 1e-9);
        assertTrue(index.remove(5, 10, 0));
        assertFalse(index.remove(5, 10, 0));
        assertEquals(1, index.size());
        assertTrue(Double.isInfinite(index.closest(1000, 0, 1000, 16)));
    }

    @Test
    void cellCapAndFullWindow() {
        NodeIndex index = new NodeIndex();
        long id = 1;
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                for (int n = 0; n < 8; n++) {
                    index.add(id++, cx * 64 + n, 70, cz * 64 + n);
                }
            }
        }
        assertEquals(392, index.size(), "a full 7 × 7 × 8 window");
        assertEquals(8, index.count(0, 0));
        assertEquals(8, index.idsIn(-1, -1).length);
        assertThrows(IllegalStateException.class, () -> index.add(9999, 5, 5, 5), "the cell cap");
        long nearest = index.nearest(0.5, 70.5, 0.5, 192);
        assertArrayEquals(new int[] {0, 70, 0}, index.position(nearest), "the node at the origin");
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long allocated = Long.MAX_VALUE;
        long sink = 0;
        for (int round = 0; round < 5; round++) {
            long before = threads.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 20_000; i++) {
                sink += index.nearest(i % 100, 70, -(i % 77), 192);
            }
            allocated = threads.getThreadAllocatedBytes(thread) - before;
        }
        assertTrue(sink > 0);
        assertEquals(0, allocated, "nearest allocates nothing");
        index.clear();
        assertEquals(0, index.size());
    }

    // --- reconciliation -----------------------------------------------------------------------

    @Test
    void everyRowOfTheReconciliationTable() {
        Reconcile.Cell empty = new Reconcile.Cell(0, 0, 0);
        Reconcile.Cell full = new Reconcile.Cell(8, 1, 2);
        Reconcile.Origin natural = Reconcile.Origin.NATURAL;
        assertEquals(
                Reconcile.Action.MINT,
                Reconcile.decide(false, natural, Reconcile.Entry.NONE, empty));
        assertEquals(
                Reconcile.Action.REMOVE,
                Reconcile.decide(false, natural, Reconcile.Entry.NONE, new Reconcile.Cell(1, 1, 0)),
                "a second natural");
        assertEquals(
                Reconcile.Action.MINT,
                Reconcile.decide(
                        false,
                        Reconcile.Origin.STRUCTURE,
                        Reconcile.Entry.NONE,
                        new Reconcile.Cell(2, 1, 1)));
        assertEquals(
                Reconcile.Action.REMOVE,
                Reconcile.decide(
                        false,
                        Reconcile.Origin.STRUCTURE,
                        Reconcile.Entry.NONE,
                        new Reconcile.Cell(2, 0, 2)),
                "a third structure");
        assertEquals(
                Reconcile.Action.MINT,
                Reconcile.decide(
                        false,
                        Reconcile.Origin.STARTER,
                        Reconcile.Entry.NONE,
                        new Reconcile.Cell(7, 1, 2)));
        assertEquals(
                Reconcile.Action.REMOVE,
                Reconcile.decide(false, Reconcile.Origin.STARTER, Reconcile.Entry.NONE, full));
        assertEquals(
                Reconcile.Action.NOTHING,
                Reconcile.decide(true, natural, Reconcile.Entry.PLACED_HERE, full));
        assertEquals(
                Reconcile.Action.REMOVE,
                Reconcile.decide(true, natural, Reconcile.Entry.PLACED_ELSEWHERE, empty),
                "a duplicate");
        assertEquals(
                Reconcile.Action.ADOPT,
                Reconcile.decide(true, natural, Reconcile.Entry.CARRIED, empty),
                "the block wins");
        assertEquals(
                Reconcile.Action.REMOVE,
                Reconcile.decide(true, natural, Reconcile.Entry.CARRIED, full),
                "the jar stays valid");
        assertEquals(
                Reconcile.Action.REMOVE,
                Reconcile.decide(true, natural, Reconcile.Entry.REMOVED, empty));
        assertEquals(
                Reconcile.Action.RECOVER,
                Reconcile.decide(true, natural, Reconcile.Entry.NONE, empty));
        assertEquals(
                Reconcile.Action.REMOVE,
                Reconcile.decide(true, natural, Reconcile.Entry.NONE, full));
    }

    // --- pools --------------------------------------------------------------------------------

    @Test
    void poolsClampKeepIgnoredEntriesAndDropPastSixteen() {
        Map<String, Integer> stored = new TreeMap<>(Map.of("vftv:aer", 150, "mod:gone", 7));
        assertEquals(40, Pools.read(stored, "vftv:aer", 40), "reads clamp");
        assertEquals(0, Pools.read(stored, "vftv:ignis", 40), "missing is 0");
        Pools.Written w =
                Pools.write(stored, Set.of("vftv:aer", "vftv:ignis"), Map.of("vftv:ignis", 5), 40);
        assertEquals(
                Map.of("vftv:aer", 40, "vftv:ignis", 5, "mod:gone", 7),
                w.pools(),
                "the clamp is stored");
        Map<String, Integer> many = new TreeMap<>();
        for (int i = 0; i < 16; i++) {
            many.put(String.format(java.util.Locale.ROOT, "mod:x%02d", i), 1);
        }
        Pools.Written dropped = Pools.write(many, Set.of("vftv:aer"), Map.of("vftv:aer", 3), 40);
        assertEquals(16, dropped.pools().size());
        assertEquals(List.of("mod:x15"), dropped.dropped(), "the highest ignored id goes");
        assertTrue(
                Pools.write(Map.of("vftv:aer", 3), Set.of("vftv:aer"), Map.of("vftv:aer", 0), 40)
                        .pools()
                        .isEmpty(),
                "an empty pool is omitted");
    }

    // --- registry rules -----------------------------------------------------------------------

    private static VisRegistryRules.Type wellspring(
            int range, boolean anchored, boolean natural, List<Integer> clarity, String... extra) {
        List<VisRegistryRules.TypeAspect> aspects = new java.util.ArrayList<>();
        for (String p : VisRegistryRules.BUILT_IN_PRIMALS) {
            aspects.add(new VisRegistryRules.TypeAspect(p, range, range));
        }
        for (String e : extra) {
            aspects.add(new VisRegistryRules.TypeAspect(e, 32, 32));
        }
        return new VisRegistryRules.Type(
                aspects,
                "vftv:aer",
                clarity,
                natural,
                natural ? List.of("minecraft:is_overworld") : List.of(),
                natural ? 1 : null,
                anchored);
    }

    @Test
    void wellspringRules() {
        Set<String> eight = new HashSet<>(VisRegistryRules.BUILT_IN_PRIMALS);
        eight.add("mod:lux");
        eight.add("mod:umbra");
        assertTrue(
                VisRegistryRules.wellspring(wellspring(32, true, false, List.of(0, 0, 1)), eight)
                        .isEmpty(),
                "seven or eight vis aspects are fine");
        assertFalse(
                VisRegistryRules.wellspring(wellspring(31, true, false, List.of(0, 0, 1)), eight)
                        .isEmpty(),
                "another range");
        assertFalse(
                VisRegistryRules.wellspring(
                                wellspring(32, true, false, List.of(0, 0, 1), "mod:lux"), eight)
                        .isEmpty(),
                "a seventh aspect");
        assertFalse(
                VisRegistryRules.wellspring(wellspring(32, false, false, List.of(0, 0, 1)), eight)
                        .isEmpty(),
                "not anchored");
        assertFalse(
                VisRegistryRules.wellspring(wellspring(32, true, false, List.of(1, 0, 1)), eight)
                        .isEmpty(),
                "weight off clear");
        Set<String> five = new HashSet<>(VisRegistryRules.BUILT_IN_PRIMALS);
        five.remove("vftv:ordo");
        assertFalse(
                VisRegistryRules.wellspring(wellspring(32, true, false, List.of(0, 0, 1)), five)
                        .isEmpty(),
                "ordo is no longer a vis aspect");
        Set<String> nine = new HashSet<>(eight);
        nine.add("mod:a");
        assertFalse(
                VisRegistryRules.types(Map.of(), nine, nine).isEmpty(),
                "more than 8 vis aspects, and no Wellspring");
    }

    @Test
    void typeRules() {
        Set<String> aspects = Set.of("vftv:aer", "vftv:terra");
        var ok =
                new VisRegistryRules.Type(
                        List.of(new VisRegistryRules.TypeAspect("vftv:aer", 1, 2)),
                        "vftv:aer",
                        List.of(1, 0, 0),
                        false,
                        List.of(),
                        null,
                        false);
        assertTrue(VisRegistryRules.type(ok, aspects).isEmpty());
        var bad =
                new VisRegistryRules.Type(
                        List.of(
                                new VisRegistryRules.TypeAspect("vftv:aer", 3, 2),
                                new VisRegistryRules.TypeAspect("vftv:aer", 1, 2),
                                new VisRegistryRules.TypeAspect("vftv:nope", 1, 1)),
                        "vftv:terra",
                        List.of(0, 0, 0),
                        false,
                        List.of("minecraft:is_ocean"),
                        1001,
                        false);
        List<String> problems = VisRegistryRules.type(bad, aspects);
        for (String expected :
                List.of(
                        "range",
                        "twice",
                        "unknown aspect",
                        "main_aspect",
                        "sum above 0",
                        "without natural",
                        "weight 1001")) {
            assertTrue(
                    problems.stream().anyMatch(p -> p.contains(expected)),
                    expected + " in " + problems);
        }
        var natural =
                new VisRegistryRules.Type(
                        List.of(new VisRegistryRules.TypeAspect("vftv:aer", 1, 2)),
                        "vftv:aer",
                        List.of(1, 0, 0),
                        true,
                        List.of(),
                        null,
                        false);
        assertFalse(
                VisRegistryRules.type(natural, aspects).isEmpty(),
                "natural needs biomes and a weight");
    }

    @Test
    void partRules() {
        Map<String, VisRegistryRules.Part> rods =
                Map.of(
                        "vftv:wooden",
                        new VisRegistryRules.Part("minecraft:stick", 40),
                        "vftv:chime",
                        new VisRegistryRules.Part("vftv:chime_rod", 150));
        Map<String, VisRegistryRules.Part> caps =
                Map.of(
                        "vftv:copper",
                        new VisRegistryRules.Part("minecraft:copper_nugget", 1.0),
                        "vftv:resonant_brass",
                        new VisRegistryRules.Part("vftv:resonant_brass_cap", 0.85));
        assertTrue(VisRegistryRules.parts(rods, caps, id -> true).isEmpty());
        Map<String, VisRegistryRules.Part> badCaps =
                Map.of(
                        "vftv:copper",
                        new VisRegistryRules.Part("minecraft:stick", 0.855),
                        "mod:x",
                        new VisRegistryRules.Part("minecraft:air", 3.0));
        List<String> problems =
                VisRegistryRules.parts(
                        Map.of("vftv:wooden", new VisRegistryRules.Part("minecraft:stick", 0)),
                        badCaps,
                        id -> !id.equals("minecraft:air"));
        for (String expected :
                List.of(
                        "also",
                        "2 decimal",
                        "outside 0.25",
                        "air",
                        "capacity 0",
                        "vftv:chime: required",
                        "vftv:resonant_brass: required")) {
            assertTrue(
                    problems.stream().anyMatch(p -> p.contains(expected)),
                    expected + " in " + problems);
        }
    }

    // --- tuning and client numbers ------------------------------------------------------------

    @Test
    void tuningDefaultsRangesAndUnknownKeys() {
        assertEquals(VisTuning.DEFAULTS, VisTuning.parse("{}"));
        VisTuning t = VisTuning.parse("{\"tap.interval\": 8, \"relay.range\": 12.5}");
        assertEquals(8, t.tapInterval());
        assertEquals(12.5, t.decimal("relay.range"));
        assertEquals(2, t.tapAmount());
        assertEquals(160, t.regenInterval(0));
        assertEquals(80, t.regenInterval(1));
        assertEquals(40, t.regenInterval(2));
        assertEquals(20, t.regenDivisor());
        assertEquals(3.0, t.minSpacing());
        for (String bad :
                List.of(
                        "{\"tap.interval\": 0}",
                        "{\"tap.interval\": 1.5}",
                        "{\"nope\": 1}",
                        "{\"tap.amount\": \"2\"}",
                        "[]",
                        "{",
                        "{\"starter.height\": 6}")) {
            assertThrows(IllegalArgumentException.class, () -> VisTuning.parse(bad), bad);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> VisTuning.fromJson(new JsonObject(), true),
                "the rules form needs every key");
        assertThrows(IllegalArgumentException.class, () -> t.integer("no.such"));
        assertEquals(t, VisTuning.fromJson(t.toJson(), true));
    }

    @Test
    void clientNumbers() {
        JsonObject json = VisFixturesTest.load("content/nodes/client.json");
        assertEquals(
                NodeClientNumbers.DEFAULTS,
                NodeClientNumbers.parse(json),
                "the shipped file is the defaults");
        json.getAsJsonObject("hum").addProperty("max_playing", 9);
        assertThrows(IllegalArgumentException.class, () -> NodeClientNumbers.parse(json));
        NodeClientNumbers n = NodeClientNumbers.DEFAULTS;
        assertEquals(0.30 * 0.80, n.coreSize(0, 0), 1e-9);
        assertEquals(1.00, n.coreSize(1_000_000, 2), 1e-9, "capped at max_size");
        assertEquals(1, n.frame(7, 1), "(7 / 4) % 8");
        assertEquals(0, n.frame(16, 2), "(16 / 2) % 8");
    }
}
