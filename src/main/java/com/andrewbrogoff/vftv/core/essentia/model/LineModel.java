package com.andrewbrogoff.vftv.core.essentia.model;

import java.util.Arrays;
import java.util.BitSet;

/**
 * One built line's selection and moves (essentia spec, "Lines" and "The pulse"), loader-free.
 * Endpoints are indices in endpoint order, aspects indices in ascending id, positions {@code
 * long}s; the adapter supplies the reads and runs each transfer through a {@link Port}.
 *
 * <p>One instance lives for one line generation: its reachability cache, stalled set, sweep cursor
 * and executed-pulse counter are dropped with it. Not thread-safe.
 */
public final class LineModel {
    /** A node that admits every aspect: a segment. */
    public static final int ADMIT_ALL = -1;

    /** A node that admits nothing: an unreadable or missing-aspect gate. */
    public static final int ADMIT_NONE = -2;

    /** What a pulse calls back into: suction queries and transactional transfers. */
    public interface Port {
        /** Endpoint {@code e}'s suction for aspect {@code x}, clamped to 0–128 by the caller. */
        int suction(int e, int x);

        /** Opens the move's root transaction. */
        void beginRoot();

        /** Closes the root, committing. */
        void commitRoot();

        /** Opens a nested transaction under the root. */
        void beginNested();

        /** Closes the nested transaction, committing or aborting it. */
        void endNested(boolean commit);

        /** Extracts up to {@code amount} of {@code x} from endpoint {@code e}; returns how much. */
        int extract(int e, int x, int amount);

        /** Inserts up to {@code amount} of {@code x} into endpoint {@code e}; returns how much. */
        int insert(int e, int x, int amount);
    }

    /**
     * A pulse's outcome.
     *
     * @param moved the units moved, 0 for none
     * @param sink the sink endpoint, or -1
     * @param aspect the aspect moved, or -1
     * @param source the first source that contributed, or -1
     * @param sourceAmount that source's contribution
     * @param awake whether the line stays awake
     */
    public record Outcome(
            int moved, int sink, int aspect, int source, int sourceAmount, boolean awake) {}

    private final int endpoints;
    private final int aspects;
    private final long[] pos;
    private final int[] endpointNode;
    private final int[] admit;
    private final int[] edgeA;
    private final int[] edgeB;
    private final int[][] components;
    private final BitSet stalled = new BitSet();
    private int sweepCursor = -1;
    private int executed;

    // Per-pulse reads, preallocated: one slot per (endpoint, aspect) at most.
    private final int[] amount;
    private final int[] hold;
    private final boolean[] skipped;
    private final boolean[] recorded;
    private final int[] suctionCache;
    private final int[] best1;
    private final int[] best2;
    private final int[] order;

    /**
     * A line's topology.
     *
     * @param aspects how many aspects the registry holds
     * @param pos each endpoint's position, in endpoint order
     * @param endpointNode each endpoint's node
     * @param admit each node's admitted aspect, {@link #ADMIT_ALL} or {@link #ADMIT_NONE}
     * @param edgeA one end of each node adjacency
     * @param edgeB the other end
     */
    public LineModel(
            int aspects, long[] pos, int[] endpointNode, int[] admit, int[] edgeA, int[] edgeB) {
        if (pos.length != endpointNode.length || edgeA.length != edgeB.length) {
            throw new IllegalArgumentException("mismatched arrays");
        }
        this.endpoints = pos.length;
        this.aspects = aspects;
        this.pos = pos.clone();
        this.endpointNode = endpointNode.clone();
        this.admit = admit.clone();
        this.edgeA = edgeA.clone();
        this.edgeB = edgeB.clone();
        this.components = new int[aspects][];
        int cells = endpoints * aspects;
        this.amount = new int[cells];
        this.hold = new int[cells];
        this.skipped = new boolean[endpoints];
        this.recorded = new boolean[aspects];
        this.suctionCache = new int[cells];
        this.best1 = new int[aspects * Math.max(1, admit.length)];
        this.best2 = new int[aspects * Math.max(1, admit.length)];
        this.order = new int[endpoints];
    }

    /** How many endpoints. */
    public int endpoints() {
        return endpoints;
    }

    /** The executed-pulse counter. */
    public int executed() {
        return executed;
    }

    /** How many (sink, aspect) pairs are stalled. */
    public int stalledCount() {
        return stalled.cardinality();
    }

    /** Whether the pair is stalled. */
    public boolean isStalled(int e, int x) {
        return stalled.get(e * aspects + x);
    }

    /** A capability invalidation at sink {@code e}: unstalls that sink's pairs. */
    public void unstallSink(int e) {
        stalled.clear(e * aspects, (e + 1) * aspects);
    }

    /** Endpoint {@code a} and {@code b} are X-connected. */
    public boolean connected(int a, int b, int x) {
        int[] c = component(x);
        int ca = c[endpointNode[a]];
        return ca >= 0 && ca == c[endpointNode[b]];
    }

    /** The X-component of each node, or -1 where the node doesn't admit X; built lazily. */
    int[] component(int x) {
        int[] c = components[x];
        if (c != null) {
            return c;
        }
        int nodes = admit.length;
        c = new int[nodes];
        for (int i = 0; i < nodes; i++) {
            c[i] = admit[i] == ADMIT_ALL || admit[i] == x ? i : -1;
        }
        for (int k = 0; k < edgeA.length; k++) {
            int a = edgeA[k];
            int b = edgeB[k];
            if (c[a] >= 0 && c[b] >= 0) {
                int ra = find(c, a);
                int rb = find(c, b);
                if (ra != rb) {
                    c[Math.max(ra, rb)] = Math.min(ra, rb);
                }
            }
        }
        for (int i = 0; i < nodes; i++) {
            if (c[i] >= 0) {
                c[i] = find(c, i);
            }
        }
        components[x] = c;
        return c;
    }

    private static int find(int[] c, int i) {
        while (c[i] != i) {
            c[i] = c[c[i]];
            i = c[i];
        }
        return i;
    }

    /** Starts a pulse's reads: every endpoint empty and present. */
    public void beginReads() {
        Arrays.fill(amount, 0);
        Arrays.fill(hold, 0);
        Arrays.fill(skipped, false);
        Arrays.fill(recorded, false);
    }

    /** Records a non-empty slot: endpoint {@code e} holds {@code n} of {@code x} at {@code h}. */
    public void read(int e, int x, int n, int h) {
        if (n <= 0) {
            return;
        }
        int cell = e * aspects + x;
        amount[cell] = (int) Math.min(Integer.MAX_VALUE, (long) amount[cell] + n);
        hold[cell] = h;
        recorded[x] = true;
    }

    /** Marks endpoint {@code e} skipped this pulse: a missing or oversized handler. */
    public void skip(int e) {
        skipped[e] = true;
    }

    /**
     * Runs one pulse on the reads just made (essentia spec, "The pulse", steps 2–7).
     *
     * @param sleeping whether the line was asleep, so its first try is a sweep
     * @param idleEvery {@code tube.idle_every}
     * @param rate {@code tube.rate}, the move's need
     */
    public Outcome pulse(Port port, boolean sleeping, int idleEvery, int rate) {
        int n = executed++;
        Arrays.fill(suctionCache, -1);
        buildFeasibility();
        int tries = 0;
        if ((sleeping || Integer.remainderUnsigned(n, idleEvery) == 0) && !stalled.isEmpty()) {
            int pair = stalled.nextSetBit(sweepCursor + 1);
            if (pair < 0) {
                pair = stalled.nextSetBit(0);
            }
            sweepCursor = pair;
            tries++;
            int b = pair / aspects;
            int x = pair % aspects;
            int s = suction(port, b, x);
            if (s > 0 && lowestHold(b, x) < s) {
                Outcome o = move(port, b, x, s, rate);
                if (o.moved() > 0) {
                    stalled.clear(pair);
                    return o;
                }
            }
        }
        while (tries < 4) {
            int bestS = 0;
            int ties = 0;
            for (int b = 0; b < endpoints; b++) {
                int s = bestSuction(port, b);
                if (s > bestS) {
                    bestS = s;
                    ties = 0;
                }
                if (s == bestS && s > 0) {
                    order[ties++] = b;
                }
            }
            if (ties == 0) {
                return new Outcome(0, -1, -1, -1, 0, false);
            }
            tries++;
            int b = order[Integer.remainderUnsigned(n, ties)];
            int x = lowestAspectAt(port, b, bestS);
            Outcome o = move(port, b, x, bestS, rate);
            if (o.moved() > 0) {
                return o;
            }
            stalled.set(b * aspects + x);
        }
        for (int b = 0; b < endpoints; b++) {
            if (bestSuction(port, b) > 0) {
                return new Outcome(0, -1, -1, -1, 0, true);
            }
        }
        return new Outcome(0, -1, -1, -1, 0, false);
    }

    /** Whether (B, X) is a candidate now, not counting the stalled set; reads must be current. */
    public boolean feasible(Port port, int b, int x) {
        Arrays.fill(suctionCache, -1);
        buildFeasibility();
        int s = suction(port, b, x);
        return s > 0 && lowestHold(b, x) < s;
    }

    private int suction(Port port, int e, int x) {
        int cell = e * aspects + x;
        if (suctionCache[cell] < 0) {
            suctionCache[cell] = skipped[e] ? 0 : Math.max(0, Math.min(128, port.suction(e, x)));
        }
        return suctionCache[cell];
    }

    /** B's highest candidate suction over unstalled aspects, or 0. */
    private int bestSuction(Port port, int b) {
        int best = 0;
        for (int x = 0; x < aspects; x++) {
            if (recorded[x] && !stalled.get(b * aspects + x)) {
                int s = suction(port, b, x);
                if (s > best && lowestHold(b, x) < s) {
                    best = s;
                }
            }
        }
        return best;
    }

    private int lowestAspectAt(Port port, int b, int s) {
        for (int x = 0; x < aspects; x++) {
            if (recorded[x]
                    && !stalled.get(b * aspects + x)
                    && suction(port, b, x) == s
                    && lowestHold(b, x) < s) {
                return x;
            }
        }
        throw new IllegalStateException("no aspect at " + s);
    }

    /**
     * Per (X, X-component): the two lowest (hold, endpoint order) sources at distinct positions.
     */
    private void buildFeasibility() {
        Arrays.fill(best1, -1);
        Arrays.fill(best2, -1);
        for (int x = 0; x < aspects; x++) {
            if (!recorded[x]) {
                continue;
            }
            int[] c = component(x);
            for (int e = 0; e < endpoints; e++) {
                int cell = e * aspects + x;
                int comp = c[endpointNode[e]];
                if (skipped[e] || amount[cell] <= 0 || comp < 0) {
                    continue;
                }
                int slot = x * admit.length + comp;
                int b1 = best1[slot];
                if (b1 < 0 || hold[cell] < hold[b1 * aspects + x]) {
                    // The old first becomes the second if it stands elsewhere; otherwise the old
                    // second (already at another position than both) stands.
                    if (b1 >= 0 && pos[b1] != pos[e]) {
                        best2[slot] = b1;
                    }
                    best1[slot] = e;
                } else if (pos[e] != pos[b1]) {
                    int b2 = best2[slot];
                    if (b2 < 0 || hold[cell] < hold[b2 * aspects + x]) {
                        best2[slot] = e;
                    }
                }
            }
        }
    }

    /** The lowest hold of a legal source for sink B and aspect X, or {@code Integer.MAX_VALUE}. */
    private int lowestHold(int b, int x) {
        int comp = component(x)[endpointNode[b]];
        if (comp < 0 || skipped[b]) {
            return Integer.MAX_VALUE;
        }
        int slot = x * admit.length + comp;
        int e = best1[slot];
        if (e >= 0 && pos[e] == pos[b]) {
            e = best2[slot];
        }
        return e < 0 ? Integer.MAX_VALUE : hold[e * aspects + x];
    }

    /** Step 6: one root transaction moving X into B from every legal source, in (hold, order). */
    private Outcome move(Port port, int b, int x, int s, int rate) {
        int count = 0;
        for (int e = 0; e < endpoints; e++) {
            int cell = e * aspects + x;
            if (!skipped[e]
                    && pos[e] != pos[b]
                    && amount[cell] > 0
                    && hold[cell] < s
                    && connected(e, b, x)) {
                order[count++] = e;
            }
        }
        // Insertion sort by (hold, endpoint order): ≤ 64 entries, and no allocation.
        for (int i = 1; i < count; i++) {
            int e = order[i];
            int h = hold[e * aspects + x];
            int j = i - 1;
            while (j >= 0 && hold[order[j] * aspects + x] > h) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = e;
        }
        int need = rate;
        int moved = 0;
        int first = -1;
        int firstAmount = 0;
        port.beginRoot();
        for (int i = 0; i < count && need > 0; i++) {
            int a = order[i];
            port.beginNested();
            int extracted = port.extract(a, x, need);
            int inserted = extracted == 0 ? 0 : port.insert(b, x, extracted);
            if (inserted == extracted) {
                port.endNested(true);
                need -= inserted;
                moved += inserted;
                if (inserted > 0 && first < 0) {
                    first = a;
                    firstAmount = inserted;
                }
                continue;
            }
            port.endNested(false);
            if (inserted > 0) {
                port.beginNested();
                int e2 = port.extract(a, x, inserted);
                int i2 = e2 == 0 ? 0 : port.insert(b, x, e2);
                boolean ok = e2 > 0 && i2 == e2;
                port.endNested(ok);
                if (ok) {
                    moved += i2;
                    if (first < 0) {
                        first = a;
                        firstAmount = i2;
                    }
                }
            }
            break;
        }
        port.commitRoot();
        return new Outcome(moved, b, x, first, firstAmount, true);
    }
}
