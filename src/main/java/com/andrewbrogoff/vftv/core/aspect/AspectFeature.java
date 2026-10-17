package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.core.common.Platform;

/**
 * The aspect feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once, during
 * mod construction. This is the feature's hook for registration (content holders, datapack
 * registries, data slots) and for its payloads (serverbound handlers and clientbound types, through
 * {@code platform.network()}).
 */
public final class AspectFeature {
    private AspectFeature() {}

    /** Registers the feature's content, registries, data slots and payloads. */
    public static void init(Platform platform) {
        platform.registrar()
                .dataPackRegistry(
                        AspectKeys.REGISTRY, AspectEntry.ELEMENT_CODEC, AspectEntry.ELEMENT_CODEC);
        AspectContent.register(platform.registrar());
        KnowledgeService.init(platform.dataSlots());
        Strikes.init(platform.network());
    }
}
