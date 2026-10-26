package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.core.common.Platform;
import com.andrewbrogoff.vftv.core.research.FactSource;

/**
 * The infusion feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once,
 * during mod construction. This is the feature's hook for registration (content holders, datapack
 * registries, data slots) and for its payloads (serverbound handlers and clientbound types, through
 * {@code platform.network()}).
 */
public final class InfusionFeature {
    private InfusionFeature() {}

    /** Registers the altar's content, the recipe type, the payloads and the fact source. */
    public static void init(Platform platform) {
        InfusionContent.register(platform.registrar());
        platform.network()
                .clientbound(InfusionPayloads.Rules.TYPE, InfusionPayloads.Rules.STREAM_CODEC);
        platform.network()
                .clientbound(InfusionPayloads.Readout.TYPE, InfusionPayloads.Readout.STREAM_CODEC);
        platform.network()
                .serverbound(
                        InfusionPayloads.Seen.TYPE,
                        InfusionPayloads.Seen.STREAM_CODEC,
                        InfusionAltars::onSeen);
        InfusionAltars.init(platform.network());
        FactSource.Registry.register("infusion", InfusionFacts.SOURCE);
    }
}
