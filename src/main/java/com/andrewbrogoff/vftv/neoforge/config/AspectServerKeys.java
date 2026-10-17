package com.andrewbrogoff.vftv.neoforge.config;

import com.andrewbrogoff.vftv.core.aspect.AspectConfig;
import com.andrewbrogoff.vftv.core.aspect.DerivedAspects;
import com.andrewbrogoff.vftv.neoforge.common.ConfigKeys;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 19 ({@code aspects.derive_modded}) of {@code vftv-server.toml} (configuration,
 * "Server keys"), and the AspectConfig in core.aspect they fill. {@link VftvServerConfig} calls
 * {@link #define} once. A change applies at the next data load and re-sends nothing.
 */
final class AspectServerKeys {
    private AspectServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        builder.translation("options.vftv.aspects").push("aspects");
        ModConfigSpec.ConfigValue<Boolean> deriveModded =
                ConfigKeys.bool(
                        builder,
                        "derive_modded",
                        AspectConfig.DERIVE_MODDED_COMMENT,
                        "options.vftv.aspects.derive_modded",
                        AspectConfig.DEFAULT.deriveModded());
        builder.pop();
        return List.of(
                new ServerConfigPublisher.ServerRecord<>(
                        () -> new AspectConfig(deriveModded.get()),
                        DerivedAspects::config,
                        DerivedAspects::installConfig,
                        AspectConfig.DEFAULT,
                        server -> {}));
    }
}
