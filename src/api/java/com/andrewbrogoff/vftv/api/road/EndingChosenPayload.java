package com.andrewbrogoff.vftv.api.road;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import net.minecraft.server.level.ServerPlayer;

/**
 * A Listener made the final choice at the Heart. See the Eldritch tier spec, "The choice".
 *
 * @param player the player who chose; valid only during dispatch
 * @param ending the ending chosen
 * @param warpChange the warp change the choice actually made
 */
public record EndingChosenPayload(ServerPlayer player, Ending ending, WarpPreview warpChange) {
    /** Constructed by vftv only. */
    @Internal
    public EndingChosenPayload {}
}
