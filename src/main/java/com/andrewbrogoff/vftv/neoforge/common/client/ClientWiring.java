package com.andrewbrogoff.vftv.neoforge.common.client;

import com.andrewbrogoff.vftv.core.Vftv;
import net.neoforged.bus.api.IEventBus;

/** The client half of the entrypoint, loaded only on a physical client. */
public final class ClientWiring {
    private ClientWiring() {}

    /** Builds the client platform implementations and passes them to {@code core}. */
    public static void init(IEventBus modBus) {
        Vftv.initClient(new NeoForgeClientNetwork(modBus));
    }
}
