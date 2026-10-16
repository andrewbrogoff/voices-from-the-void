package com.andrewbrogoff.vftv.neoforge.armor;

import com.andrewbrogoff.vftv.api.neoforge.armor.ArmorCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.armor.ArmorDataMaps;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * The armor feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 */
public final class ArmorNeoForge {
    private ArmorNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        registrar.dataMap(ArmorDataMaps.WEARABLE);
        // The view for players (API spec, "ResearchView and BaffleView").
        // ponytail: answers null until the armor ticket wires the live Baffle query.
        modBus.addListener(
                RegisterCapabilitiesEvent.class,
                event ->
                        event.registerEntity(
                                ArmorCapabilities.BAFFLE_VIEW,
                                EntityTypes.PLAYER,
                                (player, context) -> null));
    }
}
