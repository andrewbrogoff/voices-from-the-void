package com.andrewbrogoff.vftv.core.text.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;

/**
 * The text feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during client
 * mod construction.
 */
public final class TextClientFeature {
    private TextClientFeature() {}

    /** Registers the status line's client handler. */
    public static void init(ClientNetwork network) {
        TextClient.init(network);
    }
}
