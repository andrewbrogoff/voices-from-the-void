package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.datagen.client.LanguageCatalogue;
import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The data providers, run by the {@code clientData} and {@code serverData} runs into {@code
 * src/generated/resources}. Each system ticket adds its providers here.
 */
public final class VftvData {
    private VftvData() {}

    /** Subscribes the gather listeners on the mod's bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(GatherDataEvent.Client.class, VftvData::gatherClient);
    }

    private static void gatherClient(GatherDataEvent.Client event) {
        event.createProvider(
                output ->
                        new LangProvider(
                                output,
                                LanguageCatalogue.of(
                                        event.getResourceManager(PackType.CLIENT_RESOURCES))));
    }
}
