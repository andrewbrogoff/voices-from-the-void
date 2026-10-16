package com.andrewbrogoff.vftv.neoforge.config.client;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code vftv-client.toml} (configuration, "Files"): one spec built from every owner's {@code
 * <F>ClientKeys}, registered only by the client wiring, with one {@link ClientConfigPublisher}. A
 * feature ticket edits only its own keys class.
 */
public final class VftvClientConfig {
    private VftvClientConfig() {}

    /** Builds and registers the client spec, and subscribes its publisher. */
    public static void register(ModContainer container, IEventBus modBus) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        List<ClientConfigPublisher.ClientRecord<?>> records = new ArrayList<>();
        records.addAll(VisClientKeys.define(builder));
        records.addAll(ArmorClientKeys.define(builder));
        records.addAll(HushClientKeys.define(builder));
        records.addAll(WarpClientKeys.define(builder));
        records.addAll(ResearchClientKeys.define(builder));
        ModConfigSpec spec = builder.build();
        if (spec.isEmpty()) {
            return; // no owner has defined a key yet: no file to write
        }
        container.registerConfig(ModConfig.Type.CLIENT, spec);
        new ClientConfigPublisher(spec, records).register(modBus);
    }
}
