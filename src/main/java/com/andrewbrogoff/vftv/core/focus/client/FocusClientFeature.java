package com.andrewbrogoff.vftv.core.focus.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;

/**
 * The focus feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during client
 * mod construction. This is the hook for the feature's clientbound payload handlers.
 */
public final class FocusClientFeature {
    private FocusClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {}
}
