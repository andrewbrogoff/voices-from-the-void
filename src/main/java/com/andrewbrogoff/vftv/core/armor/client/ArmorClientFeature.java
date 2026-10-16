package com.andrewbrogoff.vftv.core.armor.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;

/**
 * The armor feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during client
 * mod construction. This is the hook for the feature's clientbound payload handlers.
 */
public final class ArmorClientFeature {
    private ArmorClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {}
}
