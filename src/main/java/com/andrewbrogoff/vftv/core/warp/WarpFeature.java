package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.core.common.Platform;

/**
 * The warp feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once, during
 * mod construction. This is the feature's hook for registration (content holders, datapack
 * registries, data slots) and for its payloads (serverbound handlers and clientbound types, through
 * {@code platform.network()}).
 */
public final class WarpFeature {
    private WarpFeature() {}

    /** Registers the feature's content, registries, data slots and payloads. */
    public static void init(Platform platform) {
        platform.network().clientbound(WarpRulesPayload.TYPE, WarpRulesPayload.STREAM_CODEC);
        platform.network()
                .clientbound(VoicePayloads.VoiceCue.TYPE, VoicePayloads.VoiceCue.STREAM_CODEC);
        platform.network()
                .clientbound(VoicePayloads.VoiceClear.TYPE, VoicePayloads.VoiceClear.STREAM_CODEC);
        platform.network()
                .serverbound(
                        VoicePayloads.VoiceQuiet.TYPE,
                        VoicePayloads.VoiceQuiet.STREAM_CODEC,
                        (payload, player) -> WarpService.quiet(player, payload.open()));
        WarpRulesService.init(platform.network());
        VoiceChecks.init(platform.network());
        WarpService.init(platform.dataSlots());
        WarpContent.register(platform.registrar());
    }
}
