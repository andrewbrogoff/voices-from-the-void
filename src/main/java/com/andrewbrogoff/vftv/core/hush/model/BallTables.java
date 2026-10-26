package com.andrewbrogoff.vftv.core.hush.model;

import java.util.Arrays;

/**
 * The precomputed ball tables (Hush spec, "Ringing" and "Commands"): for each radius 0 to 16, every
 * offset with {@code dx² + dy² + dz² ≤ r²}, in ascending {@code (d², dy, dz, dx)}. Built at class
 * load; reading them allocates nothing.
 */
public final class BallTables {
    /** The largest radius tabled. */
    public static final int MAX = 16;

    private static final int[][] TABLES = new int[MAX + 1][];

    static {
        for (int r = 0; r <= MAX; r++) {
            long[] keys = new long[(2 * r + 1) * (2 * r + 1) * (2 * r + 1)];
            int n = 0;
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        int d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 <= r * r) {
                            keys[n++] =
                                    (long) d2 << 24
                                            | (long) (dy + 64) << 16
                                            | (long) (dz + 64) << 8
                                            | (dx + 64);
                        }
                    }
                }
            }
            long[] sorted = Arrays.copyOf(keys, n);
            Arrays.sort(sorted);
            int[] table = new int[n];
            for (int i = 0; i < n; i++) {
                table[i] = (int) (sorted[i] & 0xFFFFFF);
            }
            TABLES[r] = table;
        }
    }

    private BallTables() {}

    /** The number of offsets in the ball of radius {@code r}. */
    public static int size(int r) {
        return TABLES[r].length;
    }

    /** The {@code i}-th offset's x. */
    public static int dx(int r, int i) {
        return (TABLES[r][i] & 0xFF) - 64;
    }

    /** The {@code i}-th offset's y. */
    public static int dy(int r, int i) {
        return (TABLES[r][i] >>> 16 & 0xFF) - 64;
    }

    /** The {@code i}-th offset's z. */
    public static int dz(int r, int i) {
        return (TABLES[r][i] >>> 8 & 0xFF) - 64;
    }
}
