package com.andrewbrogoff.vftv.core.hush.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.andrewbrogoff.vftv.core.common.model.Allocations;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The Hush model beyond the fixtures: walk edges, the due queue, the bounds and allocation. */
class HushModelTest {
    @Test
    void nextDueIsTheFirstAlignedTickAfterG() {
        assertEquals(12, HushMath.nextDue(10, 10, 2));
        assertEquals(12, HushMath.nextDue(11, 10, 2));
        assertEquals(22, HushMath.nextDue(12, 10, 2));
        assertEquals(1, HushMath.nextDue(0, 1, 0));
        assertEquals(-8, HushMath.nextDue(-10, 10, 2));
        for (int g = 0; g < 50; g++) {
            long t = HushMath.nextDue(g, 7, 3);
            assertTrue(t > g && t <= g + 7 && Math.floorMod(t, 7) == 3);
        }
    }

    @Test
    void bellPhaseIsSaltedApartFromChunks() {
        assertTrue(HushMath.bellPhase(0, 200) >= 0 && HushMath.bellPhase(0, 200) < 200);
        assertFalse(HushMath.chunkFold(0) == HushMath.attemptSeed(0, 0, 0));
    }

    @Test
    void hollowTurnWrapsUnsigned() {
        assertTrue(HushMath.hollowTurn(7, 8));
        assertFalse(HushMath.hollowTurn(8, 8));
        assertTrue(HushMath.hollowTurn(0, 1));
        // taken = 2^64 - 1 wraps to 0 conversions, so the next one is the turn at every = 1 only
        assertTrue(HushMath.hollowTurn(-2L, 1));
        assertEquals(Long.remainderUnsigned(-1L + 1, 8) == 0, HushMath.hollowTurn(-1L, 8));
    }

    @Test
    void retainClipsAtTheFormatBound() {
        assertEquals(1, HushMath.retain(HushLimits.FLUX_FORMAT_MAX - 1, 10));
        assertEquals(0, HushMath.retain(HushLimits.FLUX_FORMAT_MAX, 10));
        assertEquals(10, HushMath.retain(0, 10));
    }

    @Test
    void walkStaysAtTheBallAndBuildLimits() {
        // Every cell passes: the walk wanders and never leaves the ball or the build height.
        Walk.Cells open = (x, y, z) -> Walk.PASS;
        for (long seed = 0; seed < 200; seed++) {
            long out = Walk.attempt(open, seed, 0, 0, 0, 3, 64, -1, 2, true, true);
            assertEquals(Walk.MISS_NONE, Walk.kind(out));
            assertEquals(64, Walk.used(out));
            int dx = Walk.dx(out);
            int dy = Walk.dy(out);
            int dz = Walk.dz(out);
            assertTrue(dx * dx + dy * dy + dz * dz <= 9);
            assertTrue(dy >= -1 && dy < 2);
        }
    }

    @Test
    void walkTargetsNodesAndPassesWhatItCannotAfford() {
        Walk.Cells nodes = (x, y, z) -> Walk.NODE;
        long out = Walk.attempt(nodes, 1, 0, 0, 0, 4, 8, -64, 320, true, true);
        assertEquals(Walk.NODE_TARGET, Walk.kind(out));
        assertEquals(1, Walk.used(out));
        out = Walk.attempt(nodes, 1, 0, 0, 0, 4, 8, -64, 320, true, false);
        assertEquals(Walk.MISS_NONE, Walk.kind(out));
        Walk.Cells sources = (x, y, z) -> Walk.SOURCE;
        assertEquals(
                Walk.MISS_NONE,
                Walk.kind(Walk.attempt(sources, 1, 0, 0, 0, 4, 8, -64, 320, false, true)));
        // an immune source is classified PASS by the caller, so it is passed through
        Walk.Cells unloaded = (x, y, z) -> Walk.UNLOADED;
        out = Walk.attempt(unloaded, 1, 0, 0, 0, 4, 8, -64, 320, true, true);
        assertEquals(Walk.MISS_UNLOADED, Walk.kind(out));
        assertEquals(1, Walk.used(out));
    }

    @Test
    void ballTablesMatchABruteForceCountForEveryRadius() {
        for (int r = 0; r <= BallTables.MAX; r++) {
            int count = 0;
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        if (x * x + y * y + z * z <= r * r) {
                            count++;
                        }
                    }
                }
            }
            assertEquals(count, BallTables.size(r), "radius " + r);
            long previous = Long.MIN_VALUE;
            for (int i = 0; i < BallTables.size(r); i++) {
                int dx = BallTables.dx(r, i);
                int dy = BallTables.dy(r, i);
                int dz = BallTables.dz(r, i);
                long key =
                        ((long) (dx * dx + dy * dy + dz * dz) << 24)
                                | (dy + 64) << 16
                                | (dz + 64) << 8
                                | (dx + 64);
                assertTrue(key > previous, "ascending (d², dy, dz, dx)");
                previous = key;
            }
        }
        assertEquals(1, BallTables.size(0));
    }

    @Test
    void dueQueueHoldsEachKeyOnceAndServesOldestFirst() {
        DueQueue q = new DueQueue();
        q.offer(5, 30);
        q.offer(5, 10); // repeated deposits keep the due
        q.offer(7, 20);
        q.offer(3, 20);
        assertEquals(3, q.size());
        assertEquals(30L, q.due(5));
        assertEquals(new DueQueue.Entry(20, 3), q.first());
        q.put(5, 1);
        assertEquals(new DueQueue.Entry(1, 5), q.first());
        assertEquals(List.of(5L, 3L, 7L), q.keys());
        assertTrue(q.remove(5));
        assertFalse(q.remove(5));
        assertFalse(q.contains(5));
        assertNull(q.due(5));
        q.clear();
        assertNull(q.first());
        assertEquals(0, q.size());
    }

    @Test
    void dueQueueCarryNeverStarves() {
        // A capped server: each tick serves at most 2 of the due keys and re-dues them at g + 10.
        DueQueue q = new DueQueue();
        for (long k = 0; k < 9; k++) {
            q.offer(k, 0);
        }
        int[] served = new int[9];
        for (long g = 0; g < 200; g++) {
            for (int n = 0; n < 2; n++) {
                DueQueue.Entry e = Objects.requireNonNull(q.first());
                if (e.due() > g) {
                    break;
                }
                served[(int) e.key()]++;
                q.put(e.key(), g + 10);
            }
        }
        for (int s : served) {
            assertTrue(s >= 15, "every key is served");
        }
    }

    @Test
    void conservationOverRandomSequences() {
        // generated = spent + cleared + retained + pending + overflow, with an aura and a pending
        // entry for one chunk, at the format bound.
        Random random = new Random(26);
        for (int run = 0; run < 200; run++) {
            long generated = 0;
            long spent = 0;
            long cleared = 0;
            long overflow = 0;
            int aura = random.nextInt(HushLimits.FLUX_FORMAT_MAX);
            int pending = 0;
            long start = aura;
            for (int i = 0; i < 100; i++) {
                switch (random.nextInt(5)) {
                    case 0 -> { // a deposit into the loaded aura
                        int g = 1 + random.nextInt(50_000);
                        int kept = HushMath.retain(aura, g);
                        aura += kept;
                        overflow += g - kept;
                        generated += g;
                    }
                    case 1 -> { // a deposit while unloaded, into the pending entry
                        int g = 1 + random.nextInt(50_000);
                        int kept = HushMath.retain(pending, g);
                        pending += kept;
                        overflow += g - kept;
                        generated += g;
                    }
                    case 2 -> { // an attempt that converts a block at cost 2
                        if (aura >= HushMath.threshold(50, 2)) {
                            aura -= 2;
                            spent += 2;
                        }
                    }
                    case 3 -> { // a clear of up to 50
                        int c = Math.min(aura, 50);
                        aura -= c;
                        cleared += c;
                    }
                    default -> { // a merge at load
                        int kept = HushMath.retain(aura, pending);
                        aura += kept;
                        overflow += pending - kept;
                        pending = 0;
                    }
                }
                assertEquals(
                        start + generated,
                        spent + cleared + aura + pending + overflow,
                        "conserved");
            }
        }
    }

    @Test
    void walkPhaseAndBallAllocateNothing() {
        Walk.Cells cells = (x, y, z) -> y < 0 ? Walk.SOURCE : Walk.PASS;
        long[] sink = new long[1];
        Allocations.assertNone(
                "walking, phasing and reading the ball",
                () -> {
                    for (int i = 0; i < 20_000; i++) {
                        sink[0] += Walk.attempt(cells, i, 0, 4, 0, 12, 32, -64, 320, true, true);
                        sink[0] += HushMath.chunkPhase(i, 10);
                        sink[0] += HushMath.attemptSeed(i, i, i);
                        sink[0] += BallTables.dx(8, i % BallTables.size(8));
                        sink[0] += HushMath.blocks(i, 50, 2);
                    }
                });
        assertTrue(sink[0] != 0);
    }
}
