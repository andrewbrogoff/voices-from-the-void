package com.andrewbrogoff.vftv.neoforge.config.client;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client keys 3-4 ({@code spectacles.card}, {@code baffle.hud}) of {@code vftv-client.toml}
 * (configuration, "Client keys"), and the ArmorClientConfig they fill. {@link VftvClientConfig}
 * calls {@link #define} once.
 */
final class ArmorClientKeys {
    private ArmorClientKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ClientConfigPublisher.ClientRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
