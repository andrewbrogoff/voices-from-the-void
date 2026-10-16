package com.andrewbrogoff.vftv.core.worldgen.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;

/**
 * The worldgen feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during
 * client mod construction. This is the hook for the feature's clientbound payload handlers.
 */
public final class WorldgenClientFeature {
    private WorldgenClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {}
}
