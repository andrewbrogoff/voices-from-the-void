package com.andrewbrogoff.vftv.core.hush;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The client's knowledge of chunk statuses (Hush spec, "Sync and state"): the last status received
 * per chunk, from {@code HushChunkStatus} or an aura update, whichever came last. Pruned to the
 * render square around the player and cleared on a level change and at logout. Client thread only;
 * it holds no client classes, so the aura's sync handler can write it.
 */
public final class HushStatuses {
    private static final Map<Long, HushChunkStatus.View> STATUSES = new HashMap<>();

    private HushStatuses() {}

    /** Records the status a chunk was last sent with. */
    public static void put(long pack, HushChunkStatus.View view) {
        STATUSES.put(pack, view);
    }

    /** The last status known for a chunk, or null. */
    public static HushChunkStatus.@Nullable View get(long pack) {
        return STATUSES.get(pack);
    }

    /** Drops every chunk outside the square of {@code radius} chunks around ({@code cx, cz}). */
    public static void prune(int cx, int cz, int radius) {
        STATUSES.keySet()
                .removeIf(
                        p ->
                                Math.abs((int) (long) p - cx) > radius
                                        || Math.abs((int) (p >>> 32) - cz) > radius);
    }

    /** Forgets everything, on a level change and at logout. */
    public static void clear() {
        STATUSES.clear();
    }

    /** The number of chunks known. */
    public static int size() {
        return STATUSES.size();
    }
}
