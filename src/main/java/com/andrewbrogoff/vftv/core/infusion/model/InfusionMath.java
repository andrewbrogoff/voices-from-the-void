package com.andrewbrogoff.vftv.core.infusion.model;

import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import org.jspecify.annotations.Nullable;

/**
 * The infusion spec's fixed formulas, over primitives only: the box and its point symmetry, the
 * stability, count and clean figures, the upset seed and draws, Kuhn's matching and the enchanting
 * arithmetic. The stability, count, clean and roll methods allocate nothing.
 *
 * <p>The box is a bitset of {@value #CELLS} cells, {@value #WORDS} longs: cell {@link #index} of
 * offset (dx, dy, dz) from the bowl with dx, dz in [−4, 4] and dy in [−1, 2].
 */
public final class InfusionMath {
    /** The box's horizontal radius. */
    public static final int RADIUS = 4;

    /** The box's depth below the bowl. */
    public static final int BELOW = 1;

    /** The box's height above the bowl. */
    public static final int ABOVE = 2;

    private static final int SIDE = 2 * RADIUS + 1;

    /** The box's positions. */
    public static final int CELLS = SIDE * SIDE * (BELOW + ABOVE + 1);

    /** The longs of a box bitset. */
    public static final int WORDS = (CELLS + 63) / 64;

    private InfusionMath() {}

    // --- The box --------------------------------------------------------------------------

    /** The cell of offset (dx, dy, dz); the caller keeps it inside the box. */
    public static int index(int dx, int dy, int dz) {
        return ((dy + BELOW) * SIDE + (dz + RADIUS)) * SIDE + (dx + RADIUS);
    }

    /** A cell's dx. */
    public static int dx(int index) {
        return index % SIDE - RADIUS;
    }

    /** A cell's dz. */
    public static int dz(int index) {
        return index / SIDE % SIDE - RADIUS;
    }

    /** A cell's dy. */
    public static int dy(int index) {
        return index / (SIDE * SIDE) - BELOW;
    }

    /** The mirror's cell: (−dx, dy, −dz), a half turn about the bowl's column. */
    public static int mirror(int index) {
        return index(-dx(index), dy(index), -dz(index));
    }

    /** Whether cell {@code i} is set. */
    public static boolean has(long[] set, int i) {
        return (set[i >>> 6] & (1L << i)) != 0;
    }

    /** Sets cell {@code i}. */
    public static void set(long[] set, int i) {
        set[i >>> 6] |= 1L << i;
    }

    /** {@code L}: pedestals whose mirror isn't a pedestal. */
    public static int lonePedestals(long[] pedestals) {
        int lone = 0;
        for (int i = 0; i < CELLS; i++) {
            if (has(pedestals, i) && !has(pedestals, mirror(i))) {
                lone++;
            }
        }
        return lone;
    }

    /** {@code I}: mirrored pedestal pairs in which exactly one holds an item, each pair once. */
    public static int loneItems(long[] pedestals, long[] items) {
        int lone = 0;
        for (int i = 0; i < CELLS; i++) {
            int m = mirror(i);
            if (i < m && has(pedestals, i) && has(pedestals, m) && has(items, i) != has(items, m)) {
                lone++;
            }
        }
        return lone;
    }

    /** {@code P}: unordered pairs of stabilisers that mirror each other, off the axis. */
    public static int matchedPairs(long[] stabilisers) {
        int pairs = 0;
        for (int i = 0; i < CELLS; i++) {
            int m = mirror(i);
            if (i < m && has(stabilisers, i) && has(stabilisers, m)) {
                pairs++; // i < m excludes the axis, where a cell is its own mirror
            }
        }
        return pairs;
    }

    // --- Stability ------------------------------------------------------------------------

    /** {@code B = min(P, max_pairs) × per_pair}. */
    public static int bonus(int pairs, int maxPairs, int perPair) {
        return Math.min(pairs, maxPairs) * perPair;
    }

    /** The unclamped stability {@code 100 − R − L·lp − I·li − Wp + B}. */
    public static long rawStability(
            int r, int lone, int loneItems, int warp, int bonus, int lonePedestal, int loneItem) {
        return 100L - r - (long) lone * lonePedestal - (long) loneItems * loneItem - warp + bonus;
    }

    /** {@code S}, the raw stability clamped to 0..100. */
    public static int stability(long raw) {
        return (int) Math.max(0, Math.min(100, raw));
    }

    /** {@code p = ceilDiv((100 − S) · max_chance, 100)}, per mille. */
    public static int chance(int stability, int maxChance) {
        return Math.ceilDiv((100 - stability) * maxChance, 100);
    }

    // --- Counting the run -----------------------------------------------------------------

    /** The rolled steps if every draw is full: {@code Σ ceilDiv(cost, rate) + components}. */
    public static int nominal(int[] costs, int rate, int components) {
        int n = components;
        for (int cost : costs) {
            n += Math.ceilDiv(cost, rate);
        }
        return n;
    }

    /** The most rolled steps without an upset: {@code Σ cost + components}. */
    public static int worst(int[] costs, int components) {
        int n = components;
        for (int cost : costs) {
            n += cost;
        }
        return n;
    }

    /** The most rolled steps in any run: {@code worst + max · spill}. */
    public static int ceiling(int worst, int max, int spill) {
        return worst + max * spill;
    }

    /**
     * {@code clean(p, n)}, per mille: {@code c = 10⁹}, n times {@code c = floor(c · (1000 − p) /
     * 1000)}, then {@code floor(c / 10⁶)}.
     */
    public static int clean(int p, int n) {
        long c = 1_000_000_000L;
        for (int i = 0; i < n && c > 0; i++) {
            c = c * (1000 - p) / 1000;
        }
        return (int) (c / 1_000_000L);
    }

    // --- Upsets ---------------------------------------------------------------------------

    /** The upset seed, {@code fold([INFU, nonce])}. */
    public static long seed(long nonce) {
        return Hashes.fold(Salts.word(Salts.INFU), nonce);
    }

    /**
     * Draw {@code j} (0 roll, 1 kind, 2 target) of roll {@code k}: {@code at(seed, 3k + j + 1)}.
     */
    public static long draw(long seed, int k, int j) {
        return Hashes.at(seed, 3L * k + j + 1);
    }

    /** Whether roll {@code k} upsets: {@code u0 mod 1000 < p}, unsigned. */
    public static boolean upsets(long seed, int k, int chance) {
        return Long.remainderUnsigned(draw(seed, k, 0), 1000) < chance;
    }

    /** Whether roll {@code k}'s upset is a jolt rather than a spill. */
    public static boolean jolts(long seed, int k, int joltWeight, int spillWeight) {
        return Long.remainderUnsigned(draw(seed, k, 1), joltWeight + spillWeight) < joltWeight;
    }

    /** Roll {@code k}'s jolt target among {@code count} eligible components. */
    public static int target(long seed, int k, int count) {
        return (int) Long.remainderUnsigned(draw(seed, k, 2), count);
    }

    /** A bowl's step phase at an interval: {@code phase(CRUC, [asLong], interval)}. */
    public static long phase(long pos, int interval) {
        return Hashes.phase(Salts.CRUC, new long[] {pos}, interval);
    }

    /** The first tick after {@code now} on the bowl's phase. */
    public static long nextDue(long now, long pos, int interval) {
        long phase = phase(pos, interval);
        long at = now + 1;
        return at + Math.floorMod(phase - at, (long) interval);
    }

    // --- Matching -------------------------------------------------------------------------

    /**
     * Kuhn's augmenting-path matching: components in recipe order, each searching the items in
     * pedestal order and reassigning earlier components when that frees an item. Every item must be
     * used, so the counts must be equal.
     *
     * @param matches {@code matches[c][i]}: whether component {@code c} accepts item {@code i}
     * @param items the item count
     * @return each component's item, or null for no perfect matching
     */
    public static int @Nullable [] match(boolean[][] matches, int items) {
        if (matches.length != items) {
            return null;
        }
        int[] owner = new int[items];
        java.util.Arrays.fill(owner, -1);
        int[] assigned = new int[matches.length];
        for (int c = 0; c < matches.length; c++) {
            if (!augment(c, matches, owner, assigned, new boolean[items])) {
                return null;
            }
        }
        return assigned;
    }

    private static boolean augment(
            int c, boolean[][] matches, int[] owner, int[] assigned, boolean[] seen) {
        for (int i = 0; i < owner.length; i++) {
            if (matches[c][i] && !seen[i]) {
                seen[i] = true;
                if (owner[i] < 0 || augment(owner[i], matches, owner, assigned, seen)) {
                    owner[i] = c;
                    assigned[c] = i;
                    return true;
                }
            }
        }
        return false;
    }

    // --- Enchanting -----------------------------------------------------------------------

    /** The levels {@code from + 1} to {@code cap} summed: {@code Σ l}. */
    public static int levels(int from, int cap) {
        return cap * (cap + 1) / 2 - from * (from + 1) / 2;
    }

    /**
     * An enchanting run's {@code R}: {@code min(100, base + perLevel × (onItem + cap − from − 1))},
     * the instability the last single-level run would have had.
     */
    public static int enchantInstability(int base, int perLevel, int onItem, int cap, int from) {
        return (int) Math.min(100, base + (long) perLevel * (onItem + cap - from - 1));
    }
}
