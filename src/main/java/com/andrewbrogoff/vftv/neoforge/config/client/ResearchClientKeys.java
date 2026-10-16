package com.andrewbrogoff.vftv.neoforge.config.client;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client keys 11 ({@code book.clear_prose}) of {@code vftv-client.toml} (configuration, "Client
 * keys"), and the ResearchClientConfig they fill. {@link VftvClientConfig} calls {@link #define}
 * once.
 */
final class ResearchClientKeys {
    private ResearchClientKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ClientConfigPublisher.ClientRecord<?>> define(ModConfigSpec.Builder builder) {
        return List.of();
    }
}
