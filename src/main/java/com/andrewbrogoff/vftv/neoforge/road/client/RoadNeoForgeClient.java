package com.andrewbrogoff.vftv.neoforge.road.client;

import net.neoforged.bus.api.IEventBus;

/**
 * The road feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class RoadNeoForgeClient {
    private RoadNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {}
}
