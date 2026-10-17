package com.andrewbrogoff.vftv.core.vis.model;

import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import org.jspecify.annotations.Nullable;

/**
 * The starter's ring search (wands spec, "The starter"): Chebyshev rings around the bootstrap, each
 * walked from a seeded start, and the candidate rules over a {@link Terrain}.
 */
public final class StarterRing {
    /** Vanilla's default block interaction range: the reach of the fork and the wand. */
    public static final double REACH = 4.5;

    /** A standing player's eye height above their feet. */
    public static final double EYE = 1.62;

    private StarterRing() {}

    /**
     * The effective spawn-protection radius for a non-op player: the configured radius iff the
     * server is dedicated, the respawn dimension is the overworld, the operator list is non-empty
     * and the radius is positive; otherwise 0.
     */
    public static int effectiveRadius(
            boolean dedicated, boolean overworld, boolean hasOperators, int radius) {
        return dedicated && overworld && hasOperators && radius > 0 ? radius : 0;
    }

    /** The first ring: {@code max(starter.min_ring, P + 1)}. */
    public static int firstRing(int minRing, int protection) {
        return Math.max(minRing, protection + 1);
    }

    /** Ring {@code k}'s walk start: {@code remainderUnsigned(fold([STAR, seed, k]), 8k)}. */
    public static int start(long seed, int k) {
        return (int) Long.remainderUnsigned(Hashes.fold(Salts.word(Salts.STAR), seed, k), 8L * k);
    }

    /** Column {@code i} of ring {@code k}'s perimeter, as {@code (dx, dz)}: {@code [dx, dz]}. */
    public static int[] column(int k, int i) {
        int side = i / (2 * k);
        int t = i % (2 * k);
        return switch (side) {
            case 0 -> new int[] {-k + t, -k};
            case 1 -> new int[] {k, -k + t};
            case 2 -> new int[] {k - t, k};
            default -> new int[] {-k, k - t};
        };
    }

    /** The world as the candidate rules read it. */
    public interface Terrain {
        /** Whether the column is inside the world border. */
        boolean insideBorder(int x, int z);

        /** {@code WORLD_SURFACE} at the column: the first free y above the top block. */
        int surface(int x, int z);

        /** The lowest y inside the build height. */
        int minY();

        /** The highest y inside the build height. */
        int maxY();

        /** Whether the block is air. */
        boolean isAir(int x, int y, int z);

        /** Whether the block has a sturdy top face and no fluid. */
        boolean sturdyDry(int x, int y, int z);

        /** Whether the block has no collision and no fluid. */
        boolean openDry(int x, int y, int z);

        /** Whether the node position's cell admits a node and no node is within the spacing. */
        boolean admits(int x, int y, int z);
    }

    /**
     * The node position the column {@code (x, z)} would take, or null when the column is not a
     * valid candidate (rules 1 to 6).
     *
     * @param bx the bootstrap's x
     * @param by the bootstrap's y
     * @param bz the bootstrap's z
     * @param height {@code starter.height}
     * @return {@code [x, y, z]} of the node, or null
     */
    public static int @Nullable [] candidate(
            Terrain terrain, int x, int z, int bx, int by, int bz, int height) {
        if (!terrain.insideBorder(x, z)) {
            return null;
        }
        int h = terrain.surface(x, z);
        int ny = h + height;
        if (ny < terrain.minY() || ny > terrain.maxY() || !terrain.isAir(x, ny, z)) {
            return null;
        }
        if (distance(x + 0.5, ny + 0.5, z + 0.5, bx + 0.5, by + 0.5, bz + 0.5)
                > VisLimits.STARTER_MAX_DISTANCE) {
            return null;
        }
        if (h - 1 < terrain.minY()
                || !terrain.sturdyDry(x, h - 1, z)
                || !terrain.openDry(x, h, z)
                || !terrain.openDry(x, h + 1, z)) {
            return null;
        }
        if (distance(x + 0.5, h + EYE, z + 0.5, x + 0.5, ny + 0.5, z + 0.5) > REACH) {
            return null;
        }
        return terrain.admits(x, ny, z) ? new int[] {x, ny, z} : null;
    }

    /**
     * The first valid candidate: rings {@code first … first + ring − 1}, never beyond 180, each
     * walked from its seeded start.
     *
     * @return {@code [x, y, z]} of the node, or null when no candidate is valid
     */
    public static int @Nullable [] search(
            Terrain terrain, long seed, int bx, int by, int bz, int first, int ring, int height) {
        for (int k = first; k < first + ring && k <= VisLimits.STARTER_MAX_DISTANCE; k++) {
            int start = start(seed, k);
            for (int j = 0; j < 8 * k; j++) {
                int[] d = column(k, (start + j) % (8 * k));
                int[] found = candidate(terrain, bx + d[0], bz + d[1], bx, by, bz, height);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static double distance(
            double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
