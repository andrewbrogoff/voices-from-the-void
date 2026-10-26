package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import com.andrewbrogoff.vftv.core.common.Platform;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.andrewbrogoff.vftv.core.worldgen.model.ChoirClaims;

/**
 * The worldgen feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once,
 * during mod construction. It registers the Reliquary, the Answerers, the sounds and the node roll
 * processor; the server-only {@code vftv:choir_site} registry (no network codec); the {@code
 * vftv:choir_claims} player slot (kept through death, never synced); and the Choir-site place query.
 */
public final class WorldgenFeature {
    private WorldgenFeature() {}

    /** Registers the feature's content, registries, data slots and payloads. */
    public static void init(Platform platform) {
        WorldgenContent.register(platform.registrar());
        platform.registrar()
                .dataPackRegistry(
                        WorldgenKeys.CHOIR_SITE, WorldgenCodecs.SiteEntry.ELEMENT_CODEC, null);
        ReliquaryService.install(
                platform.dataSlots()
                        .player(
                                WorldgenKeys.CHOIR_CLAIMS_ATTACHMENT.getPath(),
                                RawRecordCodec.wrap(WorldgenCodecs.CLAIMS),
                                () -> new Retained.Read<>(ChoirClaims.EMPTY),
                                null));
        WarpService.registerPlaceQuery(ChoirSitePlaceQuery.INSTANCE);
    }
}
