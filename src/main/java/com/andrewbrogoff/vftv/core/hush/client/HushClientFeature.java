package com.andrewbrogoff.vftv.core.hush.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.hush.HushChunkStatus;
import com.andrewbrogoff.vftv.core.hush.HushStatuses;

/**
 * The hush feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during client
 * mod construction. It takes each watched chunk's status.
 */
public final class HushClientFeature {
    private HushClientFeature() {}

    /** Registers the feature's client payload handlers. */
    public static void init(ClientNetwork network) {
        network.handler(
                HushChunkStatus.TYPE, payload -> HushStatuses.put(payload.chunk(), payload.view()));
    }
}
