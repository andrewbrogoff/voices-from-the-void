package com.andrewbrogoff.vftv.neoforge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.andrewbrogoff.vftv.core.flux.HushConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

class FluxServerKeysTest {
    private static ModConfigSpec spec() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        FluxServerKeys.define(builder);
        return builder.build();
    }

    private static Object value(CommentedConfig file, String key) {
        return file.<Object>get(List.of("hush", key));
    }

    @Test
    void aMissingFileTakesTheSpecDefaults() {
        CommentedConfig file = CommentedConfig.inMemory();
        spec().correct(file);
        assertEquals(HushConfig.DEFAULT.spread(), value(file, "spread"));
        assertEquals(HushConfig.DEFAULT.hollows(), value(file, "hollows"));
        assertEquals(HushConfig.DEFAULT.unattended(), value(file, "unattended"));
    }

    /**
     * Configuration, "Validation": a quoted boolean is a wrong type and corrects to its default, so
     * the record never reads a string; an unquoted one keeps its value.
     */
    @Test
    void aQuotedBooleanCorrectsToItsDefault() {
        CommentedConfig file = CommentedConfig.inMemory();
        file.set(List.of("hush", "spread"), false);
        file.set(List.of("hush", "hollows"), "false");
        file.set(List.of("hush", "unattended"), "true");
        spec().correct(file);
        assertEquals(false, value(file, "spread"), "unquoted kept");
        assertEquals(true, value(file, "hollows"), "quoted \"false\" defaulted");
        assertEquals(false, value(file, "unattended"), "quoted \"true\" defaulted");
    }

    /** Why the overload matters: the plain boolean define keeps a quoted string. */
    @Test
    void thePlainDefineWouldKeepTheString() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.define("unattended", false);
        CommentedConfig file = CommentedConfig.inMemory();
        file.set("unattended", "true");
        builder.build().correct(file);
        assertEquals("true", file.<Object>get("unattended"));
    }

    @Test
    void everyKeyIsAClassAwareBoolean() {
        ModConfigSpec spec = spec();
        for (String key : List.of("spread", "hollows", "unattended")) {
            ModConfigSpec.ValueSpec value = spec.getSpec().get(List.of("hush", key));
            assertSame(Boolean.class, value.getClazz(), key);
            assertEquals("options.vftv.hush." + key, value.getTranslationKey(), key);
        }
    }
}
