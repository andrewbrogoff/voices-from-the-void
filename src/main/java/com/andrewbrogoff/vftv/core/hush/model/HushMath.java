package com.andrewbrogoff.vftv.core.hush.model;

import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;

/**
 * The Hush's small pure functions (Hush spec, "Scheduling", "An attempt", "Reading", "Depositing"):
 * the salted phases, the attempt stream's seed, the aligned due tick, the threshold, the HUD's
 * block bound and the Hollow turn. Allocation-free.
 */
public final class HushMath {
    private static final long HUSH = Salts.word(Salts.HUSH);
    private static final long PEAL = Salts.word(Salts.PEAL);

    private HushMath() {}

    /** {@code fold([HUSH, pack])}: a chunk's phase hash. */
    public static long chunkFold(long pack) {
        return step(step(0, HUSH), pack);
    }

    /** {@code phase(HUSH, [pack], interval)}. */
    public static int chunkPhase(long pack, int interval) {
        return (int) Long.remainderUnsigned(chunkFold(pack), interval);
    }

    /** {@code phase(PEAL, [asLong], interval)}: a Pealing Bell's phase. */
    public static int bellPhase(long pos, int interval) {
        return (int) Long.remainderUnsigned(step(step(0, PEAL), pos), interval);
    }

    /** {@code fold([HUSH, world seed, pack, steps])}: the seed of one attempt's stream. */
    public static long attemptSeed(long worldSeed, long pack, long steps) {
        return step(step(step(step(0, HUSH), worldSeed), pack), steps);
    }

    private static long step(long h, long word) {
        return Hashes.mix64((h + Hashes.GOLDEN) ^ word);
    }

    /** The first tick {@code t > g} with {@code t mod interval = phase}. */
    public static long nextDue(long g, int interval, int phase) {
        long t = g + 1;
        return t + Math.floorMod(phase - t, (long) interval);
    }

    /** {@code max(hush.onset, hush.block_cost)}: the least flux that can hush. */
    public static int threshold(int onset, int blockCost) {
        return Math.max(onset, blockCost);
    }

    /**
     * The tight upper bound on blocks the flux can still pay for: 0 below the threshold, else
     * {@code 1 + floorDiv(flux - threshold, block_cost)}.
     */
    public static int blocks(int flux, int onset, int blockCost) {
        int threshold = threshold(onset, blockCost);
        return flux < threshold ? 0 : 1 + Math.floorDiv(flux - threshold, blockCost);
    }

    /**
     * Whether the next conversion is a Hollow turn: {@code (taken + 1) mod every = 0}, unsigned.
     */
    public static boolean hollowTurn(long taken, int every) {
        return Long.remainderUnsigned(taken + 1, every) == 0;
    }

    /** The part of {@code generated} an aura holding {@code flux} can retain. */
    public static int retain(int flux, int generated) {
        return Math.min(generated, HushLimits.FLUX_FORMAT_MAX - flux);
    }
}
