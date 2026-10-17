package com.andrewbrogoff.vftv.neoforge.config;

import com.andrewbrogoff.vftv.core.warp.WarpConfig;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.neoforge.common.ConfigKeys;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 2 and 10-12 ({@code warp.voices.enabled}, {@code warp.tax}, {@code
 * warp.amplifier}, {@code warp.gear}) of {@code vftv-server.toml} (configuration, "Server keys"),
 * and the WarpConfig in core.warp they fill. {@link VftvServerConfig} calls {@link #define} once.
 * The per-type Voice switches (keys 3-9) join with the Voice events.
 */
final class WarpServerKeys {
    private WarpServerKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        builder.translation("options.vftv.warp").push("warp");
        builder.translation("options.vftv.warp.voices").push("voices");
        ModConfigSpec.ConfigValue<Boolean> voices =
                ConfigKeys.bool(
                        builder,
                        "enabled",
                        WarpConfig.VOICES_COMMENT,
                        "options.vftv.warp.voices.enabled",
                        WarpConfig.DEFAULT.voices());
        builder.pop();
        ModConfigSpec.ConfigValue<Boolean> tax =
                ConfigKeys.bool(
                        builder,
                        "tax",
                        WarpConfig.TAX_COMMENT,
                        "options.vftv.warp.tax",
                        WarpConfig.DEFAULT.tax());
        ModConfigSpec.ConfigValue<Boolean> amplifier =
                ConfigKeys.bool(
                        builder,
                        "amplifier",
                        WarpConfig.AMPLIFIER_COMMENT,
                        "options.vftv.warp.amplifier",
                        WarpConfig.DEFAULT.amplifier());
        ModConfigSpec.ConfigValue<Boolean> gear =
                ConfigKeys.bool(
                        builder,
                        "gear",
                        WarpConfig.GEAR_COMMENT,
                        "options.vftv.warp.gear",
                        WarpConfig.DEFAULT.gear());
        builder.pop();
        return List.of(
                new ServerConfigPublisher.ServerRecord<>(
                        () -> new WarpConfig(voices.get(), tax.get(), amplifier.get(), gear.get()),
                        WarpRulesService::config,
                        WarpRulesService::installConfig,
                        WarpConfig.DEFAULT,
                        WarpRulesService::sendToAll));
    }
}
