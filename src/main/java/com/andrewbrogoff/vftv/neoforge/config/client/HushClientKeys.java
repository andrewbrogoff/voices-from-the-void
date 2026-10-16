package com.andrewbrogoff.vftv.neoforge.config.client;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client keys 5-6 ({@code hush.muffle}, {@code hush.hud}) of {@code vftv-client.toml}
 * (configuration, "Client keys"), and the HushClientConfig they fill. {@link VftvClientConfig}
 * calls {@link #define} once.
 */
final class HushClientKeys {
    private HushClientKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ClientConfigPublisher.ClientRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
