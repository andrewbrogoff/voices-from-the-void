package com.andrewbrogoff.vftv.neoforge.crucible.client;

import net.neoforged.bus.api.IEventBus;

/**
 * The crucible feature's client registration, called once from the client wiring on a physical
 * client only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class CrucibleNeoForgeClient {
    private CrucibleNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {}
}
