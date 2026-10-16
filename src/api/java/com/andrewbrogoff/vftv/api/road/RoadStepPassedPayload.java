package com.andrewbrogoff.vftv.api.road;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player passed a Step of the Road: a defeat's credit raised their progress. See the Eldritch
 * tier spec, "Defeat and credit".
 *
 * @param player the credited player; valid only during dispatch
 * @param step the Step passed, 1 to 3
 * @param progress the player's Road progress after the change, 1 to 3
 */
public record RoadStepPassedPayload(ServerPlayer player, int step, int progress) {
    /** Constructed by vftv only. */
    @Internal
    public RoadStepPassedPayload {
        if (step < 1 || step > 3) {
            throw new IllegalArgumentException("step out of range: " + step);
        }
        if (progress < 1 || progress > 3) {
            throw new IllegalArgumentException("progress out of range: " + progress);
        }
    }
}
