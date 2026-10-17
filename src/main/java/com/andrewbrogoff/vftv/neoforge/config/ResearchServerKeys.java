package com.andrewbrogoff.vftv.neoforge.config;

import com.andrewbrogoff.vftv.core.research.ResearchConfig;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.electronwill.nightconfig.core.EnumGetMethod;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * The server keys 1 ({@code research.mode}) of {@code vftv-server.toml} (configuration, "Server
 * keys"), and the ResearchConfig in core.research they fill. {@link VftvServerConfig} calls {@link
 * #define} once.
 */
final class ResearchServerKeys {
    private ResearchServerKeys() {}

    /**
     * {@code research.mode}'s display adapter (configuration, "The config screen"): the TOML values
     * are these lowercase names, read case-insensitively, each shown through its translated name.
     */
    enum ModeOption implements TranslatableEnum {
        gentle,
        standard,
        hard,
        skip;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("options.vftv.research.mode." + name());
        }

        ResearchConfig.Mode mode() {
            return ResearchConfig.Mode.valueOf(name().toUpperCase(Locale.ROOT));
        }
    }

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ServerConfigPublisher.ServerRecord<?>> define(ModConfigSpec.Builder builder) {
        builder.translation("options.vftv.research").push("research");
        ModConfigSpec.EnumValue<ModeOption> mode =
                builder.comment(ResearchConfig.MODE_COMMENT)
                        .translation("options.vftv.research.mode")
                        .defineEnum("mode", ModeOption.standard, EnumGetMethod.NAME_IGNORECASE);
        builder.pop();
        return List.of(
                new ServerConfigPublisher.ServerRecord<>(
                        () -> new ResearchConfig(mode.get().mode()),
                        ResearchService::config,
                        ResearchService::installConfig,
                        ResearchConfig.DEFAULT,
                        ResearchService::resendQuotes));
    }
}
