package com.andrewbrogoff.vftv.neoforge.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 13-15 ({@code hush.*}) of {@code vftv-server.toml} (configuration, "Server
 * keys"), and the HushConfig in core.flux they fill. {@link VftvServerConfig} calls {@link #define}
 * once.
 */
final class FluxServerKeys {
    private FluxServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
