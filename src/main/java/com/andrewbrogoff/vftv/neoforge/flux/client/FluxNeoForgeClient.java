package com.andrewbrogoff.vftv.neoforge.flux.client;

import com.andrewbrogoff.vftv.core.flux.client.ClientFluxRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The flux feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class FluxNeoForgeClient {
    private FluxNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> ClientFluxRules.clear());
    }
}
