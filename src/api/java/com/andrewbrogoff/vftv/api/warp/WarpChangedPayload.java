package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.server.level.ServerPlayer;

/**
 * What one warp change reported. Immutable; the player handle is valid only during dispatch. See
 * the warp spec, "Events".
 *
 * @param player the player whose warp changed
 * @param before the warp before the change
 * @param after the warp after the change
 * @param delta the actual signed change, {@code after} minus {@code before}
 * @param source what made the change
 */
public record WarpChangedPayload(
        ServerPlayer player,
        WarpState before,
        WarpState after,
        WarpState delta,
        WarpSource source) {

    /** Constructed by vftv only. */
    @Internal
    public WarpChangedPayload {}
}
