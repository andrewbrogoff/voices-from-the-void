package com.andrewbrogoff.vftv.neoforge.config;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code vftv-server.toml} (configuration, "Files"): one spec built from every owner's {@code
 * <F>ServerKeys}, registered once, with one {@link ServerConfigPublisher} for all their records. A
 * feature ticket edits only its own keys class.
 */
public final class VftvServerConfig {
    private VftvServerConfig() {}

    /** Builds and registers the server spec, and subscribes its publisher. */
    public static void register(ModContainer container, IEventBus modBus, IEventBus gameBus) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        List<ServerConfigPublisher.ServerRecord<?>> records = new ArrayList<>();
        records.addAll(ResearchServerKeys.define(builder));
        records.addAll(WarpServerKeys.define(builder));
        records.addAll(FluxServerKeys.define(builder));
        records.addAll(FocusServerKeys.define(builder));
        records.addAll(GolemServerKeys.define(builder));
        records.addAll(VisServerKeys.define(builder));
        records.addAll(AspectServerKeys.define(builder));
        ModConfigSpec spec = builder.build();
        if (spec.isEmpty()) {
            return; // no owner has defined a key yet: no file to write
        }
        container.registerConfig(ModConfig.Type.SERVER, spec);
        new ServerConfigPublisher(spec, records).register(modBus, gameBus);
    }
}
