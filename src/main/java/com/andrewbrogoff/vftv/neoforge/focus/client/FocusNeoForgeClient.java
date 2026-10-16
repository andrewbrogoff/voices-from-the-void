package com.andrewbrogoff.vftv.neoforge.focus.client;

import net.neoforged.bus.api.IEventBus;

/**
 * The focus feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class FocusNeoForgeClient {
    private FocusNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {}
}
