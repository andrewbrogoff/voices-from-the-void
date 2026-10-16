package com.andrewbrogoff.vftv.neoforge.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 19 ({@code aspects.derive_modded}) of {@code vftv-server.toml} (configuration,
 * "Server keys"), and the AspectConfig in core.aspect they fill. {@link VftvServerConfig} calls
 * {@link #define} once.
 */
final class AspectServerKeys {
    private AspectServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
