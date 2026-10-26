package com.andrewbrogoff.vftv.core.warp.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.warp.VoicePayloads;
import com.andrewbrogoff.vftv.core.warp.WarpRulesPayload;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;

/**
 * The warp feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during client
 * mod construction. This is the hook for the feature's clientbound payload handlers.
 */
public final class WarpClientFeature {
    private WarpClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {
        network.handler(
                WarpRulesPayload.TYPE, payload -> WarpRulesService.receive(payload.rules()));
        network.handler(VoicePayloads.VoiceCue.TYPE, VoicesClient::onCue);
        network.handler(VoicePayloads.VoiceClear.TYPE, payload -> VoicesClient.clear());
        VoicesClient.init(network);
    }
}
