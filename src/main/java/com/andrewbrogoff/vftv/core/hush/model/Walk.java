package com.andrewbrogoff.vftv.core.hush.model;

import com.andrewbrogoff.vftv.core.common.model.Hashes;

/**
 * The walk kernel (Hush spec, "An attempt"): from the seep, {@code walk} steps in the stream's
 * directions, staying at the ball's and the build height's edges, ending at the first unloaded cell
 * or affordable target. Pure and allocation-free: the outcome is packed into a {@code long}.
 */
public final class Walk {
    /** A cell the walk passes through: air, fluids, hushed blocks and everything else. */
    public static final int PASS = 0;

    /** A source of the hushed-form table, not immune. */
    public static final int SOURCE = 1;

    /** A hushable node. */
    public static final int NODE = 2;

    /** A cell whose chunk is missing or below block-ticking. */
    public static final int UNLOADED = 3;

    /** The outcome: a block target. */
    public static final int BLOCK = 1;

    /** The outcome: a node target. */
    public static final int NODE_TARGET = 2;

    /** The outcome: no target after every step. */
    public static final int MISS_NONE = 3;

    /** The outcome: the walk reached a cell that isn't loaded and block-ticking. */
    public static final int MISS_UNLOADED = 4;

    /** Direction offsets in {@code Direction.from3DDataValue} order: D, U, N, S, W, E. */
    private static final int[] DX = {0, 0, 0, 0, -1, 1};

    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};

    /** What the walk reads of the world. */
    @FunctionalInterface
    public interface Cells {
        /** {@link #PASS}, {@link #SOURCE}, {@link #NODE} or {@link #UNLOADED}. */
        int classify(int x, int y, int z);
    }

    private Walk() {}

    /**
     * One attempt's walk.
     *
     * @param seed the stream seed, {@link HushMath#attemptSeed}
     * @param minY the lowest cell inside the build height
     * @param maxY one above the highest
     * @param canBlock whether the aura can pay for a block
     * @param canNode whether it can pay for a node
     * @return the packed outcome: read it with {@link #kind}, {@link #used} and {@link #dx} etc.
     */
    public static long attempt(
            Cells cells,
            long seed,
            int sx,
            int sy,
            int sz,
            int radius,
            int walk,
            int minY,
            int maxY,
            boolean canBlock,
            boolean canNode) {
        long s = seed;
        int px = sx;
        int py = sy;
        int pz = sz;
        long r2 = (long) radius * radius;
        for (int j = 0; j < walk; j++) {
            s += Hashes.GOLDEN;
            int k = (int) ((Hashes.mix64(s) >>> 1) % 6);
            int qx = px + DX[k];
            int qy = py + DY[k];
            int qz = pz + DZ[k];
            long ddx = (long) qx - sx;
            long ddy = (long) qy - sy;
            long ddz = (long) qz - sz;
            if (ddx * ddx + ddy * ddy + ddz * ddz > r2 || qy < minY || qy >= maxY) {
                continue; // stay: the step is spent
            }
            int cell = cells.classify(qx, qy, qz);
            if (cell == UNLOADED) {
                return pack(MISS_UNLOADED, j + 1, qx - sx, qy - sy, qz - sz);
            }
            if (cell == SOURCE && canBlock) {
                return pack(BLOCK, j + 1, qx - sx, qy - sy, qz - sz);
            }
            if (cell == NODE && canNode) {
                return pack(NODE_TARGET, j + 1, qx - sx, qy - sy, qz - sz);
            }
            px = qx;
            py = qy;
            pz = qz;
        }
        return pack(MISS_NONE, walk, px - sx, py - sy, pz - sz);
    }

    private static long pack(int kind, int used, int dx, int dy, int dz) {
        return kind
                | (long) used << 4
                | (long) (dx + 64) << 12
                | (long) (dy + 64) << 20
                | (long) (dz + 64) << 28;
    }

    /** The outcome's kind: {@link #BLOCK}, {@link #NODE_TARGET} or a miss. */
    public static int kind(long outcome) {
        return (int) (outcome & 0xF);
    }

    /** The steps the walk used, 1 to {@code walk}. */
    public static int used(long outcome) {
        return (int) (outcome >>> 4 & 0xFF);
    }

    /** The target's (or last cell's) x offset from the seep. */
    public static int dx(long outcome) {
        return (int) (outcome >>> 12 & 0xFF) - 64;
    }

    /** The y offset from the seep. */
    public static int dy(long outcome) {
        return (int) (outcome >>> 20 & 0xFF) - 64;
    }

    /** The z offset from the seep. */
    public static int dz(long outcome) {
        return (int) (outcome >>> 28 & 0xFF) - 64;
    }
}
