package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.crucible.CrucibleKeys;
import com.andrewbrogoff.vftv.api.crucible.CrucibleLimits;
import com.andrewbrogoff.vftv.api.crucible.CruciblePayload;
import com.andrewbrogoff.vftv.api.neoforge.crucible.CrucibleEvent;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/** The example addon's use of the crucible API: its keys, limits and event. */
final class CrucibleAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    CrucibleKeys.CRUCIBLE_HEAT,
                    CrucibleKeys.CRUCIBLE_REFUSES,
                    CrucibleKeys.RESONANT_BRASS_INGOTS,
                    CrucibleKeys.CRUCIBLE_DISSOLVE_ATTACHMENT,
                    CrucibleKeys.CRUCIBLE_REFUSED_ATTACHMENT,
                    CrucibleLimits.MAX_CRUCIBLE_RECIPES,
                    CrucibleLimits.MAX_COST_ENTRIES,
                    CrucibleLimits.MIN_COST,
                    CrucibleLimits.MAX_COST,
                    CrucibleLimits.MAX_ENTITIES_PER_STEP,
                    CrucibleLimits.MAX_POOL_ENTRIES,
                    CrucibleLimits.MAX_POOL_TOTAL,
                    CrucibleLimits.MAX_REFRESHES_PER_TICK,
                    CrucibleLimits.MAX_ID_CHARS,
                    CrucibleLimits.MAX_SPILL_FLUX,
                    CrucibleLimits.MAX_DOSES,
                    CrucibleLimits.MAX_CARD_ENTRIES);

    private CrucibleAddon() {}

    /** Subscribes a listener per event. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(CrucibleEvent.class, e -> read(e.payload()));
    }

    /** Reads every accessor of every variant, as an addon would. */
    private static void read(CruciblePayload p) {
        switch (p) {
            case CruciblePayload.Dissolved d ->
                    VftvAddon.consume(d.level(), d.pos(), d.thrower(), d.item(), d.aspects());
            case CruciblePayload.Crafted c ->
                    VftvAddon.consume(
                            c.level(),
                            c.pos(),
                            c.player(),
                            c.recipe(),
                            c.catalyst(),
                            c.result(),
                            c.aspects());
            case CruciblePayload.Spilled s ->
                    VftvAddon.consume(s.level(), s.pos(), s.player(), s.aspects(), s.cause());
        }
    }
}
