package com.andrewbrogoff.vftv.neoforge.warp;

import com.andrewbrogoff.vftv.api.neoforge.warp.WarpDataMaps;
import com.andrewbrogoff.vftv.neoforge.armor.WornSlots;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import net.neoforged.bus.api.IEventBus;

/**
 * The warp feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 *
 * <p>{@code worn} is the Curios collector, or {@link WornSlots#NONE} without Curios (compatibility,
 * "Wiring the collector"): the gear sample's other slots.
 */
public final class WarpNeoForge {
    private WarpNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(
            IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar, WornSlots worn) {
        registrar.dataMap(WarpDataMaps.GEAR_WARP);
    }
}
