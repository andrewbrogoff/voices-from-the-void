package com.andrewbrogoff.vftv.core.essentia.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath.Contents;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EssentiaMathTest {
    private static final int N = EssentiaMath.NONE;
    private static final int C = EssentiaMath.CONNECTED;
    private static final int B = EssentiaMath.BLOCKED;

    private static Contents<String> c(String aspect, int amount) {
        return new Contents<>(aspect, amount);
    }

    @Test
    void contentsInvariant() {
        assertThrows(IllegalArgumentException.class, () -> new Contents<>("aer", 0));
        assertThrows(IllegalArgumentException.class, () -> new Contents<String>(null, 3));
        assertTrue(Contents.empty().isEmpty());
    }

    @Test
    void jarRanks() {
        Contents<String> empty = Contents.empty();
        assertEquals(4, EssentiaMath.jarSuction(null, empty, 100, "aer", 4, 8));
        assertEquals(4, EssentiaMath.jarSuction(null, c("aer", 50), 100, "aer", 4, 8));
        assertEquals(0, EssentiaMath.jarSuction(null, c("aer", 50), 100, "terra", 4, 8));
        assertEquals(0, EssentiaMath.jarSuction(null, c("aer", 100), 100, "aer", 4, 8), "full");
        assertEquals(8, EssentiaMath.jarSuction("aer", empty, 100, "aer", 4, 8));
        assertEquals(0, EssentiaMath.jarSuction("aer", empty, 100, "terra", 4, 8));
        assertEquals(0, EssentiaMath.jarSuction(null, c("aer", 150), 100, "aer", 4, 8), "lowered");
        assertEquals(
                4, EssentiaMath.jarHold(null, c("aer", 100), "aer", 4, 8), "hold ignores fill");
        assertEquals(0, EssentiaMath.jarHold(null, c("aer", 1), "terra", 4, 8));
        assertEquals(8, EssentiaMath.jarHold("aer", c("aer", 1), "aer", 4, 8));
        assertEquals(0, EssentiaMath.jarHold("aer", empty, "aer", 4, 8));
        assertTrue(EssentiaMath.accepts(null, null, "aer"));
        assertFalse(EssentiaMath.accepts("terra", null, "aer"));
    }

    /** The carry vectors (a), (b) and (c). */
    @Test
    void carryVectors() {
        // (a) live Aer 80, snapshots Aer 100 and Aer 50: carry Aer 50.
        Contents<String> carryA =
                EssentiaMath.carry(c("aer", 80), List.of(c("aer", 100), c("aer", 50)));
        assertEquals(c("aer", 50), carryA);
        assertEquals(c("aer", 50), EssentiaMath.spill(c("aer", 100), carryA), "root abort");
        assertEquals(Contents.empty(), EssentiaMath.spill(c("aer", 50), carryA), "root commit");
        // (b) live Ignis 100, snapshot Aer 100: nothing carried.
        Contents<String> carryB = EssentiaMath.carry(c("ignis", 100), List.of(c("aer", 100)));
        assertEquals(Contents.empty(), carryB);
        assertEquals(c("aer", 100), EssentiaMath.spill(c("aer", 100), carryB));
        assertEquals(c("ignis", 100), EssentiaMath.spill(c("ignis", 100), carryB));
        // (c) not busy, Terra 40.
        Contents<String> carryC = EssentiaMath.carry(c("terra", 40), List.of());
        assertEquals(c("terra", 40), carryC);
        assertEquals(Contents.empty(), EssentiaMath.spill(c("terra", 40), carryC));
        assertEquals(Contents.empty(), EssentiaMath.carry(Contents.<String>empty(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> EssentiaMath.spill(c("aer", 1), carryC));
    }

    /**
     * Random nested commit and abort trees with aspect changes: the carry never exceeds any settled
     * outcome, and carried plus spilled always equals settled.
     */
    @Test
    void carryProperty() {
        Random random = new Random(0x43415252L);
        String[] aspects = {"aer", "ignis", "terra"};
        for (int round = 0; round < 2000; round++) {
            List<Contents<String>> stack = new ArrayList<>();
            Contents<String> live = randomContents(random, aspects);
            int depth = 1 + random.nextInt(4);
            for (int d = 0; d < depth; d++) {
                stack.add(live);
                live = randomContents(random, aspects);
            }
            Contents<String> carry = EssentiaMath.carry(live, stack);
            // Any settled outcome is the live state or one of the snapshots.
            List<Contents<String>> outcomes = new ArrayList<>(stack);
            outcomes.add(live);
            for (Contents<String> settled : outcomes) {
                Contents<String> spilled = EssentiaMath.spill(settled, carry);
                int carried = carry.amount();
                assertEquals(settled.amount(), carried + spilled.amount());
                if (!carry.isEmpty()) {
                    assertEquals(carry.aspect(), settled.aspect());
                }
            }
        }
    }

    private static Contents<String> randomContents(Random random, String[] aspects) {
        return random.nextInt(4) == 0
                ? Contents.empty()
                : c(aspects[random.nextInt(aspects.length)], 1 + random.nextInt(100));
    }

    @Test
    void liquidHeightVectors() {
        int[][] at100 = {{0, 0}, {1, 1}, {14, 1}, {15, 2}, {50, 4}, {99, 7}, {100, 7}};
        for (int[] v : at100) {
            assertEquals(v[1], EssentiaMath.liquidHeight(v[0], 100), "amount " + v[0]);
        }
        assertEquals(1, EssentiaMath.liquidHeight(1, 16));
        assertEquals(7, EssentiaMath.liquidHeight(16, 16));
        assertEquals(7, EssentiaMath.liquidHeight(100, 16));
        assertEquals(7, EssentiaMath.liquidHeight(10_000, 16));
    }

    @Test
    void alembicChoice() {
        int none = EssentiaMath.LABEL_NONE;
        // Labelled: only the label, if it counts.
        assertEquals(
                1,
                EssentiaMath.alembicChoice(
                        new int[] {0, 2}, new int[] {5, 5}, new int[] {0, 0}, new int[] {4, 4}, 2));
        assertEquals(
                -1,
                EssentiaMath.alembicChoice(
                        new int[] {0}, new int[] {5}, new int[] {0}, new int[] {4}, 2));
        assertEquals(
                -1,
                EssentiaMath.alembicChoice(
                        new int[] {0},
                        new int[] {5},
                        new int[] {0},
                        new int[] {4},
                        EssentiaMath.LABEL_NOTHING));
        // Unlabelled ties: highest suction, then larger amount, then lowest aspect.
        assertEquals(
                1,
                EssentiaMath.alembicChoice(
                        new int[] {0, 1, 2},
                        new int[] {5, 9, 9},
                        new int[] {0, 0, 0},
                        new int[] {4, 4, 4},
                        none));
        assertEquals(
                2,
                EssentiaMath.alembicChoice(
                        new int[] {0, 1, 2},
                        new int[] {5, 9, 1},
                        new int[] {0, 0, 0},
                        new int[] {4, 4, 8},
                        none));
        // A full tie goes to the lowest aspect, whatever the slot order.
        assertEquals(
                1,
                EssentiaMath.alembicChoice(
                        new int[] {2, 0},
                        new int[] {5, 5},
                        new int[] {0, 0},
                        new int[] {4, 4},
                        none));
        // A target without suction: nothing.
        assertEquals(
                -1,
                EssentiaMath.alembicChoice(
                        new int[] {0}, new int[] {5}, new int[] {0}, new int[] {0}, none));
        // An equal-rank pair is rejected: a base holding X at rank 4 over a target pulling X at 4.
        assertEquals(
                -1,
                EssentiaMath.alembicChoice(
                        new int[] {0}, new int[] {5}, new int[] {4}, new int[] {4}, none));
        // A lower-suction but legal alternative beats a higher-suction aspect held too high.
        assertEquals(
                1,
                EssentiaMath.alembicChoice(
                        new int[] {0, 1},
                        new int[] {5, 5},
                        new int[] {16, 0},
                        new int[] {16, 4},
                        none));
    }

    @Test
    void admission() {
        boolean[] all = {true, true, true, true, true, true};
        boolean[] none = new boolean[6];
        long[] ids = {1, 1, 2, 3, 4, 5};
        int[] counts = {200, 200, 50, 10, 0, 0};
        boolean[] built = {true, true, true, true, true, true};
        // A neighbour joined on two faces counts once: 1 + 200 + 50 + 10 = 261 > 256.
        assertTrue(EssentiaMath.admissionRefused(all, all, ids, counts, built, 256));
        // Only mutually connected neighbours count.
        boolean[] back = {true, true, false, false, false, false};
        assertFalse(EssentiaMath.admissionRefused(all, back, ids, counts, built, 256));
        // A valve joins only through its axis (here down and up), and only if it would pass.
        boolean[] axis = {true, true, false, false, false, false};
        int[] big = {255, 0, 256, 256, 0, 0};
        assertFalse(
                EssentiaMath.admissionRefused(
                        axis, all, new long[] {1, 2, 3, 4, 5, 6}, big, built, 256));
        assertFalse(
                EssentiaMath.admissionRefused(none, all, ids, counts, built, 256), "not passing");
        assertTrue(
                EssentiaMath.admissionRefused(
                        axis,
                        all,
                        new long[] {1, 2, 3, 4, 5, 6},
                        new int[] {255, 1, 0, 0, 0, 0},
                        built,
                        256));
        // An unbuilt joined line means the check can't refuse.
        boolean[] partly = {false, true, true, true, true, true};
        assertFalse(EssentiaMath.admissionRefused(all, all, ids, counts, partly, 256));
    }

    /** Every combination of six faces leaves a free face, blocking the first connected one. */
    @Test
    void labelledNormalisation() {
        for (int mask = 0; mask < 729; mask++) {
            int[] faces = new int[6];
            int m = mask;
            for (int d = 0; d < 6; d++) {
                faces[d] = m % 3;
                m /= 3;
            }
            int[] out = EssentiaMath.normalise(faces, true);
            boolean free = false;
            for (int f : out) {
                free |= f != C;
            }
            assertTrue(free, "a glyph face remains");
            if (mask == 364) { // every face connected
                assertArrayEquals(new int[] {B, C, C, C, C, C}, out);
            } else {
                assertArrayEquals(faces, out);
            }
        }
        assertArrayEquals(
                new int[] {C, C, C, C, C, C},
                EssentiaMath.normalise(new int[] {C, C, C, C, C, C}, false));
    }

    @Test
    void toggle() {
        assertArrayEquals(
                new int[] {B, N, N, N, N, N}, EssentiaMath.toggle(new int[6], 0, N, false));
        assertArrayEquals(
                new int[] {C, N, N, N, N, N},
                EssentiaMath.toggle(new int[] {B, N, N, N, N, N}, 0, C, false));
        // Unblocking the only free face of a labelled tube leaves it blocked.
        assertArrayEquals(
                new int[] {C, C, C, C, C, B},
                EssentiaMath.toggle(new int[] {C, C, C, C, C, B}, 5, C, true));
        assertArrayEquals(
                new int[] {C, C, C, C, C, C},
                EssentiaMath.toggle(new int[] {C, C, C, C, C, B}, 5, C, false));
    }
}
