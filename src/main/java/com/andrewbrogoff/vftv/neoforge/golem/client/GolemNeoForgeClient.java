package com.andrewbrogoff.vftv.neoforge.golem.client;

import net.neoforged.bus.api.IEventBus;

/**
 * The golem feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class GolemNeoForgeClient {
    private GolemNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {}
}
