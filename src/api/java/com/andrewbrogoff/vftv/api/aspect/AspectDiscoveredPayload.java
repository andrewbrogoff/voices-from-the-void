package com.andrewbrogoff.vftv.api.aspect;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a committed discovery reported. See the aspects spec, "Events".
 *
 * @param player the player who discovered the aspect; a live handle, valid only during dispatch
 * @param aspect the aspect discovered
 * @param bonus the discovery bonus actually awarded, in points of that aspect
 */
public record AspectDiscoveredPayload(ServerPlayer player, ResourceKey<Aspect> aspect, int bonus) {
    /** Constructed by vftv only. */
    @Internal
    public AspectDiscoveredPayload {}
}
