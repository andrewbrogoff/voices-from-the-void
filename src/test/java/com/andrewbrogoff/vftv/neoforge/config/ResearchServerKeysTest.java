package com.andrewbrogoff.vftv.neoforge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.andrewbrogoff.vftv.core.research.ResearchConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

class ResearchServerKeysTest {
    private static final List<String> MODE = List.of("research", "mode");

    private static ModConfigSpec spec() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ResearchServerKeys.define(builder);
        return builder.build();
    }

    private static Object corrected(@org.jspecify.annotations.Nullable Object written) {
        CommentedConfig file = CommentedConfig.inMemory();
        if (written != null) {
            file.set(MODE, written);
        }
        spec().correct(file);
        return file.<Object>get(MODE);
    }

    /**
     * Configuration, "Tests": a missing key and {@code "easy"} give the default; {@code "SKIP"}
     * reads as skip.
     */
    @Test
    void nativeCorrection() {
        assertEquals(ResearchServerKeys.ModeOption.standard, corrected(null));
        assertEquals(ResearchServerKeys.ModeOption.standard, corrected("easy"));
        ModConfigSpec.ValueSpec value = spec().getSpec().get(MODE);
        assertEquals(true, value.test("SKIP"), "read case-insensitively");
        assertEquals(true, value.test("gentle"));
        assertEquals(false, value.test("easy"));
    }

    @Test
    void theAdapterMapsOneToOneAndShowsItsTranslation() {
        for (ResearchServerKeys.ModeOption option : ResearchServerKeys.ModeOption.values()) {
            assertEquals(option.name(), option.mode().name().toLowerCase(java.util.Locale.ROOT));
            assertEquals(
                    Component.translatable("options.vftv.research.mode." + option.name()),
                    option.getTranslatedName());
        }
        assertEquals(ResearchConfig.Mode.STANDARD, ResearchConfig.DEFAULT.mode());
        ModConfigSpec.ValueSpec value = spec().getSpec().get(MODE);
        assertEquals("options.vftv.research.mode", value.getTranslationKey());
        // NeoForge appends the allowed values to the verbatim comment.
        assertEquals(
                true, String.valueOf(value.getComment()).startsWith(ResearchConfig.MODE_COMMENT));
    }
}
