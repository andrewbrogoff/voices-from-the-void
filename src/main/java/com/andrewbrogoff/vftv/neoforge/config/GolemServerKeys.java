package com.andrewbrogoff.vftv.neoforge.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 17 ({@code golems.max_per_chunk}) of {@code vftv-server.toml} (configuration,
 * "Server keys"), and the GolemConfig in core.golem they fill. {@link VftvServerConfig} calls
 * {@link #define} once.
 */
final class GolemServerKeys {
    private GolemServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
