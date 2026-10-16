package com.andrewbrogoff.vftv.neoforge.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 2-12 ({@code warp.*}) of {@code vftv-server.toml} (configuration, "Server keys"),
 * and the WarpConfig in core.warp they fill. {@link VftvServerConfig} calls {@link #define} once.
 */
final class WarpServerKeys {
    private WarpServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
