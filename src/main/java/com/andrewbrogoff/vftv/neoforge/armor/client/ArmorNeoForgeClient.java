package com.andrewbrogoff.vftv.neoforge.armor.client;

import com.andrewbrogoff.vftv.neoforge.armor.WornSlots;
import net.neoforged.bus.api.IEventBus;

/**
 * The armor feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 *
 * <p>{@code worn} is the Curios collector, or {@link WornSlots#NONE} without Curios (compatibility,
 * "Wiring the collector"): the spectacles' worn check.
 */
public final class ArmorNeoForgeClient {
    private ArmorNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus, WornSlots worn) {}
}
