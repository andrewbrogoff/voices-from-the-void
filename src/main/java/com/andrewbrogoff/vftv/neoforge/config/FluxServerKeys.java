package com.andrewbrogoff.vftv.neoforge.config;

import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.HushConfig;
import com.andrewbrogoff.vftv.neoforge.common.ConfigKeys;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 13-15 ({@code hush.*}) of {@code vftv-server.toml} (configuration, "Server
 * keys"), and the HushConfig in core.flux they fill. {@link VftvServerConfig} calls {@link #define}
 * once.
 */
final class FluxServerKeys {
    private FluxServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        builder.translation("options.vftv.hush").push("hush");
        ModConfigSpec.ConfigValue<Boolean> spread =
                ConfigKeys.bool(
                        builder,
                        "spread",
                        HushConfig.SPREAD_COMMENT,
                        "options.vftv.hush.spread",
                        HushConfig.DEFAULT.spread());
        ModConfigSpec.ConfigValue<Boolean> hollows =
                ConfigKeys.bool(
                        builder,
                        "hollows",
                        HushConfig.HOLLOWS_COMMENT,
                        "options.vftv.hush.hollows",
                        HushConfig.DEFAULT.hollows());
        ModConfigSpec.ConfigValue<Boolean> unattended =
                ConfigKeys.bool(
                        builder,
                        "unattended",
                        HushConfig.UNATTENDED_COMMENT,
                        "options.vftv.hush.unattended",
                        HushConfig.DEFAULT.unattended());
        builder.pop();
        return List.of(
                new ServerConfigPublisher.ServerRecord<>(
                        () -> new HushConfig(spread.get(), hollows.get(), unattended.get()),
                        FluxRulesService::config,
                        FluxRulesService::installConfig,
                        HushConfig.DEFAULT,
                        FluxRulesService::sendToAll));
    }
}
