package com.andrewbrogoff.vftv.neoforge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

class WarpServerKeysTest {
    private static final List<List<String>> KEYS =
            List.of(
                    List.of("warp", "voices", "enabled"),
                    List.of("warp", "tax"),
                    List.of("warp", "amplifier"),
                    List.of("warp", "gear"));

    private static ModConfigSpec spec() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        WarpServerKeys.define(builder);
        return builder.build();
    }

    @Test
    void aMissingFileTakesTheDefaults() {
        CommentedConfig file = CommentedConfig.inMemory();
        spec().correct(file);
        for (List<String> key : KEYS) {
            assertEquals(true, file.<Object>get(key), key.toString());
        }
    }

    /** Configuration, "Validation": {@code warp.tax = "false"} gives {@code true}. */
    @Test
    void aQuotedBooleanCorrectsToItsDefault() {
        CommentedConfig file = CommentedConfig.inMemory();
        file.set(List.of("warp", "tax"), "false");
        file.set(List.of("warp", "gear"), false);
        file.set(List.of("warp", "amplifier"), 7);
        spec().correct(file);
        assertEquals(true, file.<Object>get(List.of("warp", "tax")), "quoted defaulted");
        assertEquals(false, file.<Object>get(List.of("warp", "gear")), "unquoted kept");
        assertEquals(true, file.<Object>get(List.of("warp", "amplifier")), "a number defaulted");
    }

    @Test
    void everyKeyIsAClassAwareBooleanWithItsTranslation() {
        ModConfigSpec spec = spec();
        for (List<String> key : KEYS) {
            ModConfigSpec.ValueSpec value = spec.getSpec().get(key);
            assertSame(Boolean.class, value.getClazz(), key.toString());
            assertEquals(
                    "options.vftv." + String.join(".", key),
                    value.getTranslationKey(),
                    key.toString());
        }
    }
}
