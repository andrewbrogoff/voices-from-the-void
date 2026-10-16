package com.andrewbrogoff.vftv.neoforge.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 16 ({@code foci.block_effects}) of {@code vftv-server.toml} (configuration,
 * "Server keys"), and the FocusConfig in core.focus they fill. {@link VftvServerConfig} calls
 * {@link #define} once.
 */
final class FocusServerKeys {
    private FocusServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
