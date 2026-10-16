package com.andrewbrogoff.vftv.core.flux.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.flux.FluxRulesPayload;

/**
 * The flux feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during client
 * mod construction. This is the hook for the feature's clientbound payload handlers.
 */
public final class FluxClientFeature {
    private FluxClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {
        network.handler(FluxRulesPayload.TYPE, payload -> ClientFluxRules.set(payload.rules()));
    }
}
