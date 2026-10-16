package com.andrewbrogoff.vftv.neoforge.aspect;

import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDataMaps;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import net.neoforged.bus.api.IEventBus;

/**
 * The aspect feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 */
public final class AspectNeoForge {
    private AspectNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        registrar.dataMap(AspectDataMaps.ITEM);
        registrar.dataMap(AspectDataMaps.BLOCK);
        registrar.dataMap(AspectDataMaps.ENTITY_TYPE);
        registrar.dataMap(AspectDataMaps.DEFAULTS);
    }
}
