package com.andrewbrogoff.vftv.neoforge.config;

import com.andrewbrogoff.vftv.core.vis.VisConfig;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server key 18 ({@code nodes.starter}) of {@code vftv-server.toml} (configuration, "Server
 * keys"), and the VisConfig in core.vis it fills. {@link VftvServerConfig} calls {@link #define}
 * once.
 */
final class VisServerKeys {
    private VisServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        builder.translation("options.vftv.nodes").push("nodes");
        ModConfigSpec.EnumValue<VisConfig.StarterMode> starter =
                builder.comment(VisConfig.STARTER_COMMENT)
                        .translation("options.vftv.nodes.starter")
                        .worldRestart()
                        .defineEnum("starter", VisConfig.DEFAULT.starter());
        builder.pop();
        return List.of(
                new ServerConfigPublisher.ServerRecord<>(
                        () -> new VisConfig(starter.get()),
                        VisRulesService::config,
                        VisRulesService::installConfig,
                        VisConfig.DEFAULT,
                        server -> {}));
    }
}
