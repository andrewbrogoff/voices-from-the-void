package com.andrewbrogoff.vftv.core.hush.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;

/**
 * The hush feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during client
 * mod construction. This is the hook for the feature's clientbound payload handlers.
 */
public final class HushClientFeature {
    private HushClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {}
}
