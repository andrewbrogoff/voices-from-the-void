package com.andrewbrogoff.vftv.core.aspect.client;

import com.andrewbrogoff.vftv.core.aspect.ScanResult;
import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;

/**
 * The aspect feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during
 * client mod construction. This is the hook for the feature's clientbound payload handlers.
 */
public final class AspectClientFeature {
    private AspectClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {
        network.handler(ScanResult.TYPE, AspectHud::arrive);
    }
}
