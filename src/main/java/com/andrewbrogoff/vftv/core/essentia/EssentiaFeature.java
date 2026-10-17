package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.core.common.Platform;

/**
 * The essentia feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once,
 * during mod construction. This is the feature's hook for registration (content holders, datapack
 * registries, data slots) and for its payloads (serverbound handlers and clientbound types, through
 * {@code platform.network()}).
 */
public final class EssentiaFeature {
    private EssentiaFeature() {}

    /** Registers the feature's content, registries, data slots and payloads. */
    public static void init(Platform platform) {
        EssentiaContent.register(platform.registrar());
        platform.network()
                .clientbound(EssentiaPayloads.Rules.TYPE, EssentiaPayloads.Rules.STREAM_CODEC);
        platform.network()
                .clientbound(EssentiaPayloads.Flow.TYPE, EssentiaPayloads.Flow.STREAM_CODEC);
        EssentiaService.init(platform.network());
    }
}
