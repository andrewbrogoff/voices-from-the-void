package com.andrewbrogoff.vftv.core.warp.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * What a Listener's client draws from a cue's seed (warp spec, "Cues", "Voice lines", "Perception
 * effects"): one {@code java.util.Random(cueSeed)} per cue, in a fixed order. A pool is sorted by
 * id, and an empty pool or list draws nothing, so {@code nextInt(0)} is never called.
 */
public final class CueDraws {
    /** The apparition's column scan: from the eye + 4 down to the eye − 8, 13 cells. */
    public static final int COLUMN_TOP = 4;

    /** The column scan's lowest offset from the eye. */
    public static final int COLUMN_BOTTOM = -8;

    private CueDraws() {}

    /**
     * A line drawn from a pool.
     *
     * @param line the line's id
     * @param side a whisper's side, 0 left or 1 right; −1 for other types
     */
    public record Line(String line, int side) {}

    /**
     * A phantom sound's draws.
     *
     * @param index the sound's index in the list
     * @param yaw 0–359 degrees
     * @param dist 4–10 blocks
     */
    public record Sound(int index, int yaw, int dist) {}

    /**
     * One apparition placement attempt's draws.
     *
     * @param yaw 0–359 degrees
     * @param dist 12–20 blocks
     */
    public record Attempt(int yaw, int dist) {}

    /** A pool's ids in the draw order: sorted by {@code String.compareTo}. */
    public static List<String> sorted(Collection<String> pool) {
        List<String> out = new ArrayList<>(pool);
        out.sort(null);
        return out;
    }

    /**
     * The line, then (for a whisper) the side; null for an empty pool.
     *
     * @param pool the pool, sorted
     */
    public static @Nullable Line line(Random random, List<String> pool, boolean whisper) {
        if (pool.isEmpty()) {
            return null;
        }
        String line = pool.get(random.nextInt(pool.size()));
        return new Line(line, whisper ? random.nextInt(2) : -1);
    }

    /** A phantom sound over {@code n} sounds: the sound, the yaw, the distance; null for none. */
    public static @Nullable Sound sound(Random random, int n) {
        if (n == 0) {
            return null;
        }
        int index = random.nextInt(n);
        int yaw = random.nextInt(360);
        return new Sound(index, yaw, 4 + random.nextInt(7));
    }

    /** One apparition attempt: the yaw, then the distance. */
    public static Attempt attempt(Random random) {
        int yaw = random.nextInt(360);
        return new Attempt(yaw, 12 + random.nextInt(9));
    }

    /**
     * The column scan: the first cell from {@code eye + 4} down to {@code eye − 8} that is loaded,
     * stands on a full opaque cube and has air in it and above it.
     *
     * @param loaded whether the cell at y is in a loaded chunk
     * @param solid whether the block at y is a full opaque cube
     * @param air whether the block at y is air
     * @return the cell's y, or {@link Integer#MIN_VALUE} when none is found
     */
    public static int column(int eye, IntPredicate loaded, IntPredicate solid, IntPredicate air) {
        for (int y = eye + COLUMN_TOP; y >= eye + COLUMN_BOTTOM; y--) {
            if (loaded.test(y) && solid.test(y - 1) && air.test(y) && air.test(y + 1)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
