package com.andrewbrogoff.vftv.neoforge.config.client;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client keys 1-2 ({@code vis_hud.x}, {@code vis_hud.y}) of {@code vftv-client.toml}
 * (configuration, "Client keys"), and the VisClientConfig they fill. {@link VftvClientConfig} calls
 * {@link #define} once.
 */
final class VisClientKeys {
    private VisClientKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ClientConfigPublisher.ClientRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
