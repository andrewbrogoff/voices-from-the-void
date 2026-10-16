package com.andrewbrogoff.vftv.neoforge.hush;

import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import net.neoforged.bus.api.IEventBus;

/**
 * The hush feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 */
public final class HushNeoForge {
    private HushNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {}
}
