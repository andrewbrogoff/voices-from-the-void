package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.common.Platform;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;

/**
 * The research feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once,
 * during mod construction. This is the feature's hook for registration (content holders, datapack
 * registries, data slots) and for its payloads (serverbound handlers and clientbound types, through
 * {@code platform.network()}).
 */
public final class ResearchFeature {
    private ResearchFeature() {}

    /** Registers the feature's content, registries, data slots and payloads. */
    public static void init(Platform platform) {
        // Both registries are synced: the book draws from them on the client (API spec).
        platform.registrar()
                .dataPackRegistry(
                        ResearchKeys.REGISTRY, ResearchCodecs.RESEARCH, ResearchCodecs.RESEARCH);
        platform.registrar()
                .dataPackRegistry(
                        ResearchKeys.CATEGORY_REGISTRY,
                        ResearchCodecs.RESEARCH_CATEGORY,
                        ResearchCodecs.RESEARCH_CATEGORY);
        ResearchItems.register(platform.registrar());
        // The milestone rules join this registry to the warp tuning (warp spec, "Permanent warp").
        WarpRulesService.registerTuningCheck(ResearchService::milestoneProblems);
        ResearchService.init(
                platform.dataSlots()
                        .playerWithDeltas(
                                ResearchKeys.RESEARCH_ATTACHMENT.getPath(),
                                ResearchAttachment.CODEC,
                                () -> ResearchAttachment.EMPTY,
                                ResearchAttachment.SYNC));
    }
}
