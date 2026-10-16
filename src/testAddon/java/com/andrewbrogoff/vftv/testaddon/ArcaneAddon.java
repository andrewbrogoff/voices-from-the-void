package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.arcane.ArcaneKeys;
import com.andrewbrogoff.vftv.api.arcane.ArcaneLimits;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/**
 * The example addon's use of the arcane API: its tag key and limits. Arcane crafting posts no event
 * of its own; its spends arrive as the vis feature's {@code VisSpentEvent}.
 */
final class ArcaneAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    ArcaneKeys.CONVERTS_TO_WORKTABLE,
                    ArcaneLimits.MAX_ARCANE_RECIPES,
                    ArcaneLimits.GRID_SIZE,
                    ArcaneLimits.MAX_COST_ENTRIES,
                    ArcaneLimits.MIN_BASE_COST,
                    ArcaneLimits.MAX_BASE_COST,
                    ArcaneLimits.MAX_BATCH,
                    ArcaneLimits.MAX_CRAFTS_PER_TICK,
                    ArcaneLimits.MAX_ID_CHARS,
                    ArcaneLimits.MAX_ECHO_EFFECTIVE,
                    ArcaneLimits.MAX_ECHO_MULTIPLIER,
                    ArcaneLimits.MAX_ECHO_SPILL);

    private ArcaneAddon() {}

    /** Nothing to subscribe: the feature has no event. Kept so every feature wires alike. */
    static void register(IEventBus modBus, IEventBus gameBus) {}
}
