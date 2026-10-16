package com.andrewbrogoff.vftv.neoforge.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 18 ({@code nodes.starter}) of {@code vftv-server.toml} (configuration, "Server
 * keys"), and the VisConfig in core.vis they fill. {@link VftvServerConfig} calls {@link #define}
 * once.
 */
final class VisServerKeys {
    private VisServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
