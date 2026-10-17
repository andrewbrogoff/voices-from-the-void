package com.andrewbrogoff.vftv.core.warp.model;

import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import java.util.UUID;

/**
 * A player's schedule phase (warp spec, "Schedule"): {@code phase(WARP, [UUID], interval)}, so a
 * step is due on the overworld ticks where {@code gameTime mod interval} equals it.
 */
public final class WarpPhase {
    private WarpPhase() {}

    /** The phase of {@code player} for {@code interval} ≥ 1. */
    public static int of(UUID player, int interval) {
        return (int) Hashes.phase(Salts.WARP, Hashes.uuidWords(player), interval);
    }
}
