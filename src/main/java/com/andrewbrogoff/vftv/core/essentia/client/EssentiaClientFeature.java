package com.andrewbrogoff.vftv.core.essentia.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.essentia.EssentiaPayloads;

/**
 * The essentia feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during
 * client mod construction. This is the hook for the feature's clientbound payload handlers.
 */
public final class EssentiaClientFeature {
    private EssentiaClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {
        network.handler(
                EssentiaPayloads.Rules.TYPE, payload -> ClientEssentia.setRules(payload.rules()));
        network.handler(EssentiaPayloads.Flow.TYPE, FlowMotes::add);
    }
}
