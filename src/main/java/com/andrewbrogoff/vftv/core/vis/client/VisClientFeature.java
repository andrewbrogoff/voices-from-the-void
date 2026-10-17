package com.andrewbrogoff.vftv.core.vis.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.flux.client.ClientFluxRules;
import com.andrewbrogoff.vftv.core.vis.NodeBlockEntity;
import com.andrewbrogoff.vftv.core.vis.VisClientHooks;
import com.andrewbrogoff.vftv.core.vis.VisRulesPayload;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.VisService;
import com.andrewbrogoff.vftv.core.vis.WandParts;

/**
 * The vis feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during client
 * mod construction. It takes the rules payload and installs the client's hooks.
 */
public final class VisClientFeature {
    private VisClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {
        network.handler(
                VisRulesPayload.TYPE,
                payload -> {
                    VisRulesService.receive(payload.rules());
                    VisHud.invalidate();
                });
        NodeBlockEntity.installClientTicker(NodeAmbience::tick);
        VisClientHooks.installUseTick(NodeAmbience::useTick);
        VisService.installClientFlux(ClientFluxRules::get);
    }

    /** Drops everything the client derived from the server, at disconnect. */
    public static void onLogout() {
        VisRulesService.clearClient();
        VisHud.invalidate();
        NodeAmbience.clear();
        NodeGlintParticle.reset();
        WandParts.clear();
    }
}
