package com.andrewbrogoff.vftv.neoforge.infusion.client;

import net.neoforged.bus.api.IEventBus;

/**
 * The infusion feature's client registration, called once from the client wiring on a physical
 * client only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class InfusionNeoForgeClient {
    private InfusionNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {}
}
