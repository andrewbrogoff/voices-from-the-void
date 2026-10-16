package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.flux.FluxClearSource;
import com.andrewbrogoff.vftv.api.flux.FluxSource;
import net.neoforged.bus.api.IEventBus;

/**
 * The example addon's use of the flux API: reading where flux came from and what cleared it. The
 * flux feature has no Stable constant and no event of its own; {@link HushAddon} reads these from
 * {@code FluxEvent}.
 */
final class FluxAddon {
    private FluxAddon() {}

    /** References every clear source; flux has nothing to subscribe or register. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        VftvAddon.consume((Object[]) FluxClearSource.values());
    }

    /** Reads every variant of a deposit's source. */
    static void read(FluxSource source) {
        switch (source) {
            case FluxSource.Vis vis -> VftvAddon.consume(vis);
            case FluxSource.Essentia essentia -> VftvAddon.consume(essentia.cause());
            case FluxSource.Command command -> VftvAddon.consume(command);
        }
    }
}
