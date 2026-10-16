package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.neoforge.research.ResearchCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.research.ResearchLearnedEvent;
import com.andrewbrogoff.vftv.api.neoforge.research.StageReachedEvent;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.research.ResearchLearnedPayload;
import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import com.andrewbrogoff.vftv.api.research.ResearchView;
import com.andrewbrogoff.vftv.api.research.StageReachedPayload;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/** The example addon's use of the research API: the learned and Stage events, and the view. */
final class ResearchAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    ResearchKeys.REGISTRY,
                    ResearchKeys.CATEGORY_REGISTRY,
                    ResearchKeys.RESONANCE,
                    ResearchKeys.DISTILLATION,
                    ResearchKeys.RUINS,
                    ResearchKeys.CHOIRS_ROAD,
                    ResearchKeys.COMMONPLACE_BOOK,
                    ResearchKeys.SILENCE_BENEATH,
                    ResearchKeys.BENEATH_THE_NOTES,
                    ResearchKeys.CATEGORY_RESONANCE,
                    ResearchKeys.CATEGORY_DISTILLATION,
                    ResearchKeys.CATEGORY_RUINS,
                    ResearchKeys.CATEGORY_CHOIRS_ROAD,
                    ResearchKeys.CATEGORY_CHOIR_PAGES,
                    ResearchKeys.PAGE_COMPONENT,
                    ResearchKeys.RESEARCH_ATTACHMENT,
                    ResearchLimits.MIN_ANCHORS,
                    ResearchLimits.MAX_ANCHORS,
                    ResearchLimits.MAX_GRID_RADIUS,
                    ResearchLimits.MAX_SYNTHESIS_DEPTH,
                    ResearchLimits.MAX_ACTIONS_PER_TICK,
                    ResearchLimits.MAX_COMMITS_PER_TICK,
                    ResearchLimits.MAX_OPEN_NOTES,
                    ResearchLimits.MAX_BOOKMARKS,
                    ResearchCapabilities.VIEW);

    private ResearchAddon() {}

    /** Subscribes a listener per event; a Stage step also queries the player's view. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(ResearchLearnedEvent.class, e -> read(e.payload()));
        gameBus.addListener(StageReachedEvent.class, e -> read(e.payload()));
    }

    /** Reads every accessor, as an addon would. */
    private static void read(ResearchLearnedPayload p) {
        WarpPreview warp = p.warp();
        VftvAddon.consume(
                p.player(),
                p.research(),
                p.source(),
                warp.dT(),
                warp.dL(),
                warp.dP(),
                warp.clipped(),
                p.points());
    }

    /** Reads every accessor, as an addon would, then gates on the player's view. */
    private static void read(StageReachedPayload p) {
        VftvAddon.consume(p.player(), p.stage(), p.previousStage(), p.source());
        query(p.player());
    }

    /** Reads every method of a server player's research view; it is null for a client player. */
    static void query(ServerPlayer player) {
        ResearchView view = player.getCapability(ResearchCapabilities.VIEW);
        if (view != null) {
            VftvAddon.consume(
                    view.isLearned(ResearchKeys.SILENCE_BENEATH),
                    view.isLatchedOrLearned(ResearchKeys.RUINS),
                    view.stage());
        }
    }
}
