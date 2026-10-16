package com.andrewbrogoff.vftv.neoforge.common;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Key declarations every config owner shares, server and client alike (so it lives outside {@code
 * neoforge.config}, which client code may not use). Ints take {@code defineInRange} and enums
 * {@code defineEnum} directly (configuration, "Validation").
 */
public final class ConfigKeys {
    private ConfigKeys() {}

    /**
     * A boolean key through the class-aware {@code define} overload (configuration, "Validation"):
     * a quoted {@code "true"} is a wrong type, so the native correction resets it to the default,
     * where the plain boolean {@code define} would keep the string and throw on read. Declared in
     * the builder's current section.
     */
    public static ModConfigSpec.ConfigValue<Boolean> bool(
            ModConfigSpec.Builder builder,
            String name,
            String comment,
            String translation,
            boolean fallback) {
        return builder.comment(comment)
                .translation(translation)
                .define(List.of(name), () -> fallback, v -> v instanceof Boolean, Boolean.class);
    }
}
