package com.andrewbrogoff.vftv.core.warp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The client draws: pool order, empty pools, the draw order and the column rule. */
class CueDrawsTest {
    /** A Random that fails if asked for nextInt(0). */
    private static final class Strict extends Random {
        private static final long serialVersionUID = 1L;

        Strict(long seed) {
            super(seed);
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new AssertionError("nextInt(" + bound + ")");
            }
            return super.nextInt(bound);
        }
    }

    @Test
    void poolsSortByIdText() {
        assertEquals(
                List.of("vftv:band0_0", "vftv:band0_10", "vftv:band0_2"),
                CueDraws.sorted(Set.of("vftv:band0_2", "vftv:band0_10", "vftv:band0_0")));
    }

    @Test
    void emptyPoolsAndListsDrawNothing() {
        assertNull(CueDraws.line(new Strict(1), List.of(), true));
        assertNull(CueDraws.sound(new Strict(1), 0));
    }

    @Test
    void theDrawOrder() {
        List<String> pool = List.of("a:0", "a:1", "a:2");
        Random reference = new Random(42);
        int line = reference.nextInt(3);
        int side = reference.nextInt(2);
        assertEquals(
                new CueDraws.Line(pool.get(line), side), CueDraws.line(new Random(42), pool, true));
        reference = new Random(42);
        assertEquals(
                new CueDraws.Line(pool.get(reference.nextInt(3)), -1),
                CueDraws.line(new Random(42), pool, false));
        reference = new Random(7);
        CueDraws.Sound s =
                new CueDraws.Sound(
                        reference.nextInt(4), reference.nextInt(360), 4 + reference.nextInt(7));
        assertEquals(s, CueDraws.sound(new Random(7), 4));
        Random r = new Random(9);
        for (int i = 0; i < 200; i++) {
            CueDraws.Attempt a = CueDraws.attempt(r);
            assertTrue(
                    a.yaw() >= 0 && a.yaw() < 360 && a.dist() >= 12 && a.dist() <= 20,
                    a.toString());
            CueDraws.Sound x = CueDraws.sound(r, 1);
            assertTrue(x != null && x.dist() >= 4 && x.dist() <= 10, String.valueOf(x));
        }
    }

    @Test
    void theColumnRule() {
        // Ground at y 63 (solid), air above: the first standing cell from the top is 64.
        assertEquals(64, CueDraws.column(66, y -> true, y -> y <= 63, y -> y >= 64));
        // Unloaded everywhere: nothing.
        assertEquals(
                Integer.MIN_VALUE, CueDraws.column(66, y -> false, y -> y <= 63, y -> y >= 64));
        // Ground too far below (eye − 8 is the lowest cell): nothing.
        assertEquals(Integer.MIN_VALUE, CueDraws.column(80, y -> true, y -> y <= 63, y -> y >= 64));
        // The first standing cell from the top wins: on the roof at 64, not the floor at 59.
        assertEquals(
                65,
                CueDraws.column(66, y -> true, y -> y == 59 || y == 64, y -> y != 59 && y != 64));
        // A cell needs air above it too: under a roof at 65 nothing stands at 64.
        assertEquals(
                Integer.MIN_VALUE,
                CueDraws.column(60, y -> true, y -> y == 63 || y == 65, y -> y != 63 && y != 65));
    }
}
