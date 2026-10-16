package com.andrewbrogoff.vftv.neoforge.research;

import com.andrewbrogoff.vftv.api.neoforge.research.ResearchCapabilities;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * The research feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus
 * and mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one
 * line.
 */
public final class ResearchNeoForge {
    private ResearchNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        // The view for players (API spec, "ResearchView and BaffleView").
        // ponytail: answers null until the research foundation ticket wires the live core query.
        modBus.addListener(
                RegisterCapabilitiesEvent.class,
                event ->
                        event.registerEntity(
                                ResearchCapabilities.VIEW,
                                EntityTypes.PLAYER,
                                (player, context) -> null));
    }
}
