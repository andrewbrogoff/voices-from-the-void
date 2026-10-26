package com.andrewbrogoff.vftv.neoforge.config;

import com.andrewbrogoff.vftv.core.warp.WarpConfig;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.neoforge.common.ConfigKeys;
import com.andrewbrogoff.vftv.core.warp.VoiceChecks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server keys 2-12 ({@code warp.voices.enabled}, {@code warp.tax}, {@code
 * warp.amplifier}, {@code warp.gear}) of {@code vftv-server.toml} (configuration, "Server keys"),
 * and the WarpConfig in core.warp they fill. {@link VftvServerConfig} calls {@link #define} once.
 * The per-type Voice switches (keys 3-9) are server-only and fill VoiceChecks' set of types off.
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
        List<ModConfigSpec.ConfigValue<Boolean>> types = new ArrayList<>();
        for (WarpConfig.TypeSwitch t : WarpConfig.TYPE_SWITCHES) {
            types.add(
                    ConfigKeys.bool(
                            builder, t.key(), t.comment(), "options.vftv.warp.voices." + t.key(), true));
        }
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
                        WarpRulesService::sendToAll),
                new ServerConfigPublisher.ServerRecord<Set<String>>(
                        () -> {
                            Set<String> off = new TreeSet<>();
                            for (int i = 0; i < types.size(); i++) {
                                if (!types.get(i).get()) {
                                    off.add(WarpConfig.TYPE_SWITCHES.get(i).type());
                                }
                            }
                            return Set.copyOf(off);
                        },
                        VoiceChecks::typesOff,
                        VoiceChecks::installTypesOff,
                        Set.of(),
                        server -> {}));
    }
}
