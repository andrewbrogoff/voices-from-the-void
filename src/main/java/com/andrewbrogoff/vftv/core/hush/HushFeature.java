package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.core.common.Platform;
import com.andrewbrogoff.vftv.core.research.FactSource;
import com.andrewbrogoff.vftv.core.warp.WarpService;

/**
 * The hush feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once, during
 * mod construction. It registers the hushed forms, the bells, Hush Ash, the Hollow, the sounds and
 * the mote; the {@code vftv:hush_chunk_status} payload; the Hush place query; and the {@code hush}
 * fact source.
 */
public final class HushFeature {
    private HushFeature() {}

    /** Registers the feature's content, registries, data slots and payloads. */
    public static void init(Platform platform) {
        HushContent.register(platform.registrar());
        platform.network().clientbound(HushChunkStatus.TYPE, HushChunkStatus.STREAM_CODEC);
        HushService.install(platform.network());
        WarpService.registerPlaceQuery(HushPlaceQuery.INSTANCE);
        FactSource.Registry.register("hush", HushFacts.SOURCE);
    }
}
