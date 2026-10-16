package com.andrewbrogoff.vftv.neoforge.essentia.client;

import net.neoforged.bus.api.IEventBus;

/**
 * The essentia feature's client registration, called once from the client wiring on a physical
 * client only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class EssentiaNeoForgeClient {
    private EssentiaNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {}
}
