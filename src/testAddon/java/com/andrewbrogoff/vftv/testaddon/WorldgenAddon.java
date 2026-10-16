package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/** The example addon's use of the worldgen API: its keys and limits (it posts no event). */
final class WorldgenAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    WorldgenKeys.CHOIR_SITE,
                    WorldgenKeys.CHOIR_RUIN_SITE,
                    WorldgenKeys.CHOIR_RUIN,
                    WorldgenKeys.CHOIR_SITES,
                    WorldgenKeys.ANSWERERS,
                    WorldgenKeys.CHOIR_WEATHERING,
                    WorldgenKeys.CHOIR_NODE_TYPE,
                    WorldgenKeys.CHOIR_CLAIMS_ATTACHMENT,
                    WorldgenLimits.RUIN_NODES,
                    WorldgenLimits.MAX_TEMPLATE_SIDE,
                    WorldgenLimits.MAX_WEATHERING_RULES,
                    WorldgenLimits.MAX_PAGES_PER_SITE,
                    WorldgenLimits.MAX_PAGES_PER_CLAIM,
                    WorldgenLimits.MAX_CLAIM_SITES,
                    WorldgenLimits.MAX_USES_PER_PLAYER_PER_TICK,
                    WorldgenLimits.MAX_SITE_STRUCTURES,
                    WorldgenLimits.MAX_ALERT,
                    WorldgenLimits.CHANT_SHOW_TICKS);

    private WorldgenAddon() {}

    /** The feature has no event to subscribe to and no addon-owned value; reads the constants. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        VftvAddon.consume(CONSTANTS);
    }
}
