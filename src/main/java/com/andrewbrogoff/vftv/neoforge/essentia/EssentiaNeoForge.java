package com.andrewbrogoff.vftv.neoforge.essentia;

import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import net.neoforged.bus.api.IEventBus;

/**
 * The essentia feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus
 * and mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one
 * line.
 */
public final class EssentiaNeoForge {
    private EssentiaNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {}
}
