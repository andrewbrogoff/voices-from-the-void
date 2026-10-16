package com.andrewbrogoff.vftv.core.flux;

import com.andrewbrogoff.vftv.core.common.Platform;

/**
 * The flux feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once, during
 * mod construction. This is the feature's hook for registration (content holders, datapack
 * registries, data slots) and for its payloads (serverbound handlers and clientbound types, through
 * {@code platform.network()}).
 */
public final class FluxFeature {
    private FluxFeature() {}

    /** Registers the feature's content, registries, data slots and payloads. */
    public static void init(Platform platform) {
        platform.network().clientbound(FluxRulesPayload.TYPE, FluxRulesPayload.STREAM_CODEC);
    }
}
