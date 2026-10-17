package com.andrewbrogoff.vftv.core.essentia.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The essentia spec's pulse vectors, replayed through {@link LineModel} on a fake world. */
class LineModelTest {
    static final int AER = 0;
    static final int IGNIS = 1;
    static final int TERRA = 2;
    static final int ASPECTS = 3;

    /** One endpoint of the fake world; its state is an int[ASPECTS] of amounts. */
    abstract static class End {
        int[] held = new int[ASPECTS];

        abstract int suction(int x);

        int hold(int x) {
            return 0;
        }

        int extract(int x, int n) {
            int k = Math.min(n, held[x]);
            held[x] -= k;
            return k;
        }

        abstract int insert(int x, int n);
    }

    static final class Crucible extends End {
        Crucible(int aer, int ignis, int terra) {
            held = new int[] {aer, ignis, terra};
        }

        @Override
        int suction(int x) {
            return 0;
        }

        @Override
        int insert(int x, int n) {
            return 0;
        }
    }

    static final class Jar extends End {
        final int label;
        final int capacity;

        Jar(int label, int aspect, int amount) {
            this(label, aspect, amount, 100);
        }

        Jar(int label, int aspect, int amount, int capacity) {
            this.label = label;
            this.capacity = capacity;
            if (aspect >= 0) {
                held[aspect] = amount;
            }
        }

        private EssentiaMath.Contents<Integer> contents() {
            for (int x = 0; x < ASPECTS; x++) {
                if (held[x] > 0) {
                    return new EssentiaMath.Contents<>(x, held[x]);
                }
            }
            return EssentiaMath.Contents.empty();
        }

        private @Nullable Integer labelKey() {
            return label < 0 ? null : label;
        }

        @Override
        int suction(int x) {
            return EssentiaMath.jarSuction(labelKey(), contents(), capacity, x, 4, 8);
        }

        @Override
        int hold(int x) {
            return EssentiaMath.jarHold(labelKey(), contents(), x, 4, 8);
        }

        @Override
        int insert(int x, int n) {
            EssentiaMath.Contents<Integer> c = contents();
            if (!EssentiaMath.accepts(labelKey(), c.aspect(), x)) {
                return 0;
            }
            int k = Math.min(n, capacity - c.amount());
            held[x] += k;
            return k;
        }
    }

    /** A Node Tap taking only {@code aspect}, with {@code room} left. */
    static final class Tap extends End {
        final int aspect;
        int room;

        Tap(int aspect, int room) {
            this.aspect = aspect;
            this.room = room;
        }

        @Override
        int suction(int x) {
            return x == aspect && room > 0 ? 16 : 0;
        }

        @Override
        int insert(int x, int n) {
            if (x != aspect) {
                return 0;
            }
            int k = Math.min(n, room);
            room -= k; // the tap drinks it: nothing stays readable
            return k;
        }
    }

    /** A consumer with suction 16 for Aer whose k-th insert call accepts {@code script(k)}. */
    static final class Scripted extends End {
        final IntUnaryOperator script;
        int calls;

        Scripted(IntUnaryOperator script) {
            this.script = script;
        }

        @Override
        int suction(int x) {
            return x == AER ? 16 : 0;
        }

        @Override
        int insert(int x, int n) {
            int k = Math.min(n, script.applyAsInt(calls++));
            held[x] += k;
            return k;
        }

        @Override
        int extract(int x, int n) {
            return 0; // a consumer gives nothing back
        }
    }

    /** The fake world: endpoints, their topology and a snapshot-stack transaction. */
    static final class World implements LineModel.Port {
        final List<End> ends;
        final LineModel model;
        final Deque<int[][]> snapshots = new ArrayDeque<>();
        final List<int[]> taps = new ArrayList<>();
        boolean sleeping;

        World(List<End> ends, int[] node, int[] admit, int[] edgeA, int[] edgeB) {
            this.ends = ends;
            long[] pos = new long[ends.size()];
            for (int i = 0; i < pos.length; i++) {
                pos[i] = 1000L + i;
            }
            this.model = new LineModel(ASPECTS, pos, node, admit, edgeA, edgeB);
        }

        /** One segment holding every endpoint. */
        static World segment(End... ends) {
            return new World(
                    List.of(ends),
                    new int[ends.length],
                    new int[] {LineModel.ADMIT_ALL},
                    new int[0],
                    new int[0]);
        }

        LineModel.Outcome pulse(int idleEvery) {
            model.beginReads();
            for (int e = 0; e < ends.size(); e++) {
                for (int x = 0; x < ASPECTS; x++) {
                    model.read(e, x, ends.get(e).held[x], ends.get(e).hold(x));
                }
            }
            LineModel.Outcome o = model.pulse(this, sleeping, idleEvery, 8);
            sleeping = !o.awake();
            assertTrue(snapshots.isEmpty(), "every transaction closed");
            return o;
        }

        int total(int x) {
            int sum = 0;
            for (End e : ends) {
                sum += e.held[x];
            }
            return sum;
        }

        @Override
        public int suction(int e, int x) {
            return ends.get(e).suction(x);
        }

        private int[][] copy() {
            int[][] c = new int[ends.size() + 1][];
            for (int i = 0; i < ends.size(); i++) {
                c[i] = ends.get(i).held.clone();
            }
            c[ends.size()] = ends.stream().mapToInt(e -> e instanceof Tap t ? t.room : 0).toArray();
            return c;
        }

        private void restore(int[][] c) {
            for (int i = 0; i < ends.size(); i++) {
                ends.get(i).held = c[i];
                if (ends.get(i) instanceof Tap t) {
                    t.room = c[ends.size()][i];
                }
            }
        }

        @Override
        public void beginRoot() {
            assertTrue(snapshots.isEmpty(), "a root opens alone");
            snapshots.push(copy());
        }

        @Override
        public void commitRoot() {
            snapshots.pop();
        }

        @Override
        public void beginNested() {
            snapshots.push(copy());
        }

        @Override
        public void endNested(boolean commit) {
            int[][] before = snapshots.pop();
            if (!commit) {
                restore(before);
            }
        }

        @Override
        public int extract(int e, int x, int amount) {
            return ends.get(e).extract(x, amount);
        }

        @Override
        public int insert(int e, int x, int amount) {
            return ends.get(e).insert(x, amount);
        }
    }

    private static void assertMove(
            LineModel.Outcome o, int sink, int aspect, int source, int amount) {
        assertEquals(sink, o.sink(), "sink");
        assertEquals(aspect, o.aspect(), "aspect");
        assertEquals(source, o.source(), "source");
        assertEquals(amount, o.moved(), "moved");
    }

    /**
     * The main vector: C, J1, J2 (Ignis), J3 (Terra 20), T, J4 (Terra 98), J5 behind an Aer gate.
     */
    @Test
    void pulseVector() {
        Crucible c = new Crucible(30, 5, 12);
        Jar j1 = new Jar(-1, -1, 0);
        Jar j2 = new Jar(IGNIS, -1, 0);
        Jar j3 = new Jar(-1, TERRA, 20);
        Tap t = new Tap(AER, 10);
        Jar j4 = new Jar(TERRA, TERRA, 98);
        Jar j5 = new Jar(-1, -1, 0);
        // Nodes: 0 the main segment, 1 the Aer gate, 2 the segment behind it.
        World w =
                new World(
                        List.of(c, j1, j2, j3, t, j4, j5),
                        new int[] {0, 0, 0, 0, 0, 0, 2},
                        new int[] {LineModel.ADMIT_ALL, AER, LineModel.ADMIT_ALL},
                        new int[] {0, 1},
                        new int[] {1, 2});
        assertMove(w.pulse(10), 4, AER, 0, 8);
        assertMove(w.pulse(10), 4, AER, 0, 2);
        assertMove(w.pulse(10), 2, IGNIS, 0, 5);
        assertMove(w.pulse(10), 5, TERRA, 0, 2);
        assertMove(w.pulse(10), 3, TERRA, 0, 8);
        assertMove(w.pulse(10), 6, AER, 0, 8);
        assertMove(w.pulse(10), 1, AER, 0, 8);
        assertMove(w.pulse(10), 3, TERRA, 0, 2);
        assertMove(w.pulse(10), 1, AER, 0, 4);
        LineModel.Outcome last = w.pulse(10);
        assertEquals(0, last.moved());
        assertFalse(last.awake(), "the line sleeps");

        assertEquals(0, c.held[AER] + c.held[IGNIS] + c.held[TERRA]);
        assertEquals(12, j1.held[AER]);
        assertEquals(5, j2.held[IGNIS]);
        assertEquals(30, j3.held[TERRA]);
        assertEquals(0, t.room);
        assertEquals(100, j4.held[TERRA]);
        assertEquals(8, j5.held[AER]);

        // Suction vector: with the crucible empty, equal ranks never trade.
        LineModel.Outcome idle = w.pulse(10);
        assertEquals(0, idle.moved());
        assertFalse(idle.awake());
    }

    @Test
    void changedAcceptance() {
        Crucible c = new Crucible(10, 0, 0);
        Scripted f = new Scripted(k -> k == 0 ? 2 : 0);
        Jar j1 = new Jar(-1, -1, 0);
        World w = World.segment(c, f, j1);
        LineModel.Outcome o = w.pulse(10);
        assertMove(o, 2, AER, 0, 8);
        assertTrue(w.model.isStalled(1, AER), "F stalled");
        assertEquals(2, c.held[AER]);
        assertEquals(0, f.held[AER]);
        assertEquals(10, w.total(AER), "Aer is conserved");
    }

    @Test
    void fourDeadSinks() {
        Crucible c = new Crucible(20, 0, 0);
        End[] f = new End[4];
        for (int i = 0; i < 4; i++) {
            f[i] = new Scripted(k -> 0);
        }
        Jar j1 = new Jar(-1, -1, 0);
        World w = World.segment(c, f[0], f[1], f[2], f[3], j1);
        LineModel.Outcome p0 = w.pulse(3);
        assertEquals(0, p0.moved());
        assertTrue(p0.awake(), "J1 untried, so the line stays awake");
        for (int i = 1; i <= 4; i++) {
            assertTrue(w.model.isStalled(i, AER));
        }
        assertMove(w.pulse(3), 5, AER, 0, 8);
        assertMove(w.pulse(3), 5, AER, 0, 8);
        int calls = ((Scripted) f[0]).calls;
        assertMove(w.pulse(3), 5, AER, 0, 4);
        assertEquals(calls + 1, ((Scripted) f[0]).calls, "the sweep retried F1");
        LineModel.Outcome p4 = w.pulse(3);
        assertEquals(0, p4.moved());
        assertFalse(p4.awake(), "the line sleeps");
        assertEquals(20, w.total(AER));
    }

    @Test
    void silentRecovery() {
        Crucible c = new Crucible(40, 0, 0);
        Scripted f = new Scripted(k -> k == 0 ? 0 : Integer.MAX_VALUE);
        Jar j1 = new Jar(-1, -1, 0);
        World w = World.segment(c, f, j1);
        assertMove(w.pulse(3), 2, AER, 0, 8);
        assertTrue(w.model.isStalled(1, AER));
        assertMove(w.pulse(3), 2, AER, 0, 8);
        assertMove(w.pulse(3), 2, AER, 0, 8);
        assertMove(w.pulse(3), 1, AER, 0, 8);
        assertFalse(w.model.isStalled(1, AER), "unstalled by the sweep");
        assertMove(w.pulse(3), 1, AER, 0, 8);
        assertMove(w.pulse(3), 1, AER, 2, 8);
        assertEquals(40, w.total(AER));
    }

    @Test
    void recoveryWhileAsleep() {
        Crucible c = new Crucible(12, 0, 0);
        Scripted f = new Scripted(k -> k == 0 ? 0 : Integer.MAX_VALUE);
        Jar j1 = new Jar(IGNIS, -1, 0);
        World w = World.segment(c, f, j1);
        LineModel.Outcome p0 = w.pulse(3);
        assertEquals(0, p0.moved());
        assertFalse(p0.awake(), "nothing else; the line sleeps");
        assertMove(w.pulse(3), 1, AER, 0, 8);
        assertMove(w.pulse(3), 1, AER, 0, 4);
        assertEquals(12, w.total(AER));
    }

    @Test
    void equalRanksNeverTradeAndFullJarNeverDrains() {
        Jar full = new Jar(-1, AER, 100);
        Jar empty = new Jar(-1, -1, 0);
        Jar labelledFull = new Jar(AER, AER, 100);
        World w = World.segment(full, empty, labelledFull);
        LineModel.Outcome o = w.pulse(10);
        assertEquals(0, o.moved(), "rank 4 never gives to rank 4, rank 8 never to rank 4");
        assertFalse(o.awake());
    }

    @Test
    void strictInequalityAtEquality() {
        // An unlabelled jar (hold 4) feeds a labelled one (suction 8), never the reverse.
        Jar source = new Jar(-1, AER, 20);
        Jar sink = new Jar(AER, -1, 0);
        World w = World.segment(source, sink);
        assertMove(w.pulse(10), 1, AER, 0, 8);
        Jar back = new Jar(-1, -1, 0);
        Jar labelled = new Jar(AER, AER, 20);
        assertEquals(0, World.segment(labelled, back).pulse(10).moved());
    }

    @Test
    void rotationCountsExecutedPulses() {
        // Two tied sinks: run at every second due tick, the line still alternates.
        Crucible c = new Crucible(100, 0, 0);
        Jar a = new Jar(-1, -1, 0);
        Jar b = new Jar(-1, -1, 0);
        World w = World.segment(c, a, b);
        assertEquals(1, w.pulse(10).sink());
        assertEquals(2, w.pulse(10).sink());
        assertEquals(1, w.pulse(10).sink());
        assertEquals(3, w.model.executed());
    }

    @Test
    void multiSourceWithShortSource() {
        Crucible c1 = new Crucible(3, 0, 0);
        Crucible c2 = new Crucible(10, 0, 0);
        Tap t = new Tap(AER, 100);
        World w = World.segment(c1, c2, t);
        LineModel.Outcome o = w.pulse(10);
        assertMove(o, 2, AER, 0, 8);
        assertEquals(3, o.sourceAmount(), "the first source contributed 3");
        assertEquals(0, c1.held[AER]);
        assertEquals(5, c2.held[AER]);
    }

    @Test
    void neverYieldingSlotIsStalledThenNextCandidate() {
        End liar =
                new End() {
                    @Override
                    int suction(int x) {
                        return 0;
                    }

                    @Override
                    int extract(int x, int n) {
                        return 0;
                    }

                    @Override
                    int insert(int x, int n) {
                        return 0;
                    }
                };
        liar.held[AER] = 50;
        Tap t = new Tap(AER, 10);
        Jar j = new Jar(-1, -1, 0);
        World w = World.segment(liar, t, j);
        // T (16) is tried first and stalled, then J (4), the next candidate; then nothing is left.
        LineModel.Outcome o = w.pulse(10);
        assertEquals(0, o.moved());
        assertTrue(w.model.isStalled(1, AER));
        assertTrue(w.model.isStalled(2, AER));
        assertFalse(o.awake());
    }

    @Test
    void fourTryCap() {
        Crucible c = new Crucible(20, 0, 0);
        List<End> ends = new ArrayList<>();
        ends.add(c);
        for (int i = 0; i < 6; i++) {
            ends.add(new Scripted(k -> 0));
        }
        World w =
                new World(
                        ends,
                        new int[ends.size()],
                        new int[] {LineModel.ADMIT_ALL},
                        new int[0],
                        new int[0]);
        LineModel.Outcome o = w.pulse(10);
        assertEquals(0, o.moved());
        assertEquals(4, w.model.stalledCount(), "at most four tries");
        assertTrue(o.awake(), "two unstalled candidates remain");
    }

    @Test
    void sweepCursorPersistsAndWraps() {
        Crucible c = new Crucible(20, 0, 0);
        Scripted[] f = new Scripted[3];
        List<End> ends = new ArrayList<>(List.of(c));
        for (int i = 0; i < 3; i++) {
            f[i] = new Scripted(k -> 0);
            ends.add(f[i]);
        }
        World w =
                new World(
                        ends,
                        new int[ends.size()],
                        new int[] {LineModel.ADMIT_ALL},
                        new int[0],
                        new int[0]);
        w.pulse(1); // stalls all three; with idle_every 1 every pulse sweeps
        int[] base = {f[0].calls, f[1].calls, f[2].calls};
        for (int p = 0; p < 4; p++) {
            w.pulse(1);
        }
        assertEquals(base[0] + 2, f[0].calls, "F1 swept twice: pulses 1 and 4");
        assertEquals(base[1] + 1, f[1].calls);
        assertEquals(base[2] + 1, f[2].calls);
    }

    @Test
    void invalidationUnstallsOnlyItsSink() {
        Crucible c = new Crucible(20, 0, 0);
        World w = World.segment(c, new Scripted(k -> 0), new Scripted(k -> 0));
        w.pulse(10);
        assertTrue(w.model.isStalled(1, AER));
        assertTrue(w.model.isStalled(2, AER));
        w.model.unstallSink(1);
        assertFalse(w.model.isStalled(1, AER));
        assertTrue(w.model.isStalled(2, AER));
    }

    @Test
    void reachabilityGates() {
        // node 0 segment - gate Aer (1) - gate Ignis (2) - segment 3; node 4 unreadable gate.
        LineModel m =
                new LineModel(
                        ASPECTS,
                        new long[] {1, 2, 3, 4},
                        new int[] {0, 3, 4, 0},
                        new int[] {
                            LineModel.ADMIT_ALL,
                            AER,
                            IGNIS,
                            LineModel.ADMIT_ALL,
                            LineModel.ADMIT_NONE
                        },
                        new int[] {0, 1, 2, 0},
                        new int[] {1, 2, 3, 4});
        assertFalse(m.connected(0, 1, AER), "adjacent gates of different aspects block Aer");
        assertFalse(m.connected(0, 1, IGNIS));
        assertFalse(m.connected(0, 2, AER), "an unreadable gate admits nothing");
        assertTrue(m.connected(0, 3, TERRA), "one segment");
        // Endpoints on different nodes at one position are still distinct endpoints.
        LineModel same =
                new LineModel(
                        ASPECTS,
                        new long[] {7, 7},
                        new int[] {0, 1},
                        new int[] {LineModel.ADMIT_ALL, AER},
                        new int[] {0},
                        new int[] {1});
        assertTrue(same.connected(0, 1, AER));
        assertFalse(same.connected(0, 1, TERRA));
    }

    @Test
    void noSelfMoveAcrossSides() {
        // A crucible touched from two sides is two endpoints at one position: no self move.
        Crucible c = new Crucible(20, 0, 0);
        Tap t = new Tap(AER, 10);
        LineModel m =
                new LineModel(
                        ASPECTS,
                        new long[] {5, 5},
                        new int[] {0, 0},
                        new int[] {LineModel.ADMIT_ALL},
                        new int[0],
                        new int[0]);
        m.beginReads();
        m.read(0, AER, 20, 0);
        assertFalse(
                m.feasible(
                        new World(
                                List.of(c, t), new int[2], new int[] {-1}, new int[0], new int[0]),
                        1,
                        AER));
    }

    /** The two-lowest feasibility table agrees with a brute-force O(E²A) search. */
    @Test
    void feasibilityMatchesBruteForce() {
        Random random = new Random(0x45535345L);
        for (int round = 0; round < 300; round++) {
            int e = 1 + random.nextInt(8);
            int nodes = 1 + random.nextInt(4);
            long[] pos = new long[e];
            int[] node = new int[e];
            int[][] amount = new int[e][ASPECTS];
            int[][] hold = new int[e][ASPECTS];
            int[][] suction = new int[e][ASPECTS];
            for (int i = 0; i < e; i++) {
                pos[i] = random.nextInt(4);
                node[i] = random.nextInt(nodes);
                for (int x = 0; x < ASPECTS; x++) {
                    amount[i][x] = random.nextInt(3) == 0 ? 1 + random.nextInt(10) : 0;
                    hold[i][x] = random.nextInt(6);
                    suction[i][x] = random.nextInt(6);
                }
            }
            int[] admit = new int[nodes];
            for (int i = 0; i < nodes; i++) {
                admit[i] = random.nextInt(ASPECTS + 2) - 2;
            }
            int edges = random.nextInt(nodes + 1);
            int[] ea = new int[edges];
            int[] eb = new int[edges];
            for (int i = 0; i < edges; i++) {
                ea[i] = random.nextInt(nodes);
                eb[i] = random.nextInt(nodes);
            }
            LineModel m = new LineModel(ASPECTS, pos, node, admit, ea, eb);
            m.beginReads();
            for (int i = 0; i < e; i++) {
                for (int x = 0; x < ASPECTS; x++) {
                    m.read(i, x, amount[i][x], hold[i][x]);
                }
            }
            LineModel.Port port =
                    new LineModel.Port() {
                        @Override
                        public int suction(int i, int x) {
                            return suction[i][x];
                        }

                        @Override
                        public void beginRoot() {}

                        @Override
                        public void commitRoot() {}

                        @Override
                        public void beginNested() {}

                        @Override
                        public void endNested(boolean commit) {}

                        @Override
                        public int extract(int i, int x, int n) {
                            return 0;
                        }

                        @Override
                        public int insert(int i, int x, int n) {
                            return 0;
                        }
                    };
            for (int b = 0; b < e; b++) {
                for (int x = 0; x < ASPECTS; x++) {
                    boolean brute = false;
                    int s = suction[b][x];
                    for (int a = 0; a < e && s > 0; a++) {
                        brute |=
                                pos[a] != pos[b]
                                        && amount[a][x] > 0
                                        && hold[a][x] < s
                                        && m.connected(a, b, x);
                    }
                    assertEquals(
                            brute, m.feasible(port, b, x), "round " + round + " b" + b + " x" + x);
                }
            }
        }
    }
}
