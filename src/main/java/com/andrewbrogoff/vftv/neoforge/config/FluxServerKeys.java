package com.andrewbrogoff.vftv.neoforge.config;

import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.HushConfig;
import com.andrewbrogoff.vftv.neoforge.flux.FluxNeoForge;
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
        ModConfigSpec.BooleanValue spread =
                builder.comment(HushConfig.SPREAD_COMMENT)
                        .translation("options.vftv.hush.spread")
                        .define("spread", HushConfig.DEFAULT.spread());
        ModConfigSpec.BooleanValue hollows =
                builder.comment(HushConfig.HOLLOWS_COMMENT)
                        .translation("options.vftv.hush.hollows")
                        .define("hollows", HushConfig.DEFAULT.hollows());
        ModConfigSpec.BooleanValue unattended =
                builder.comment(HushConfig.UNATTENDED_COMMENT)
                        .translation("options.vftv.hush.unattended")
                        .define("unattended", HushConfig.DEFAULT.unattended());
        builder.pop();
        return List.of(
                new ServerConfigPublisher.ServerRecord<>(
                        () -> new HushConfig(spread.get(), hollows.get(), unattended.get()),
                        FluxRulesService::config,
                        FluxRulesService::installConfig,
                        HushConfig.DEFAULT,
                        FluxNeoForge::sendRules));
    }
}
