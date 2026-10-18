package com.andrewbrogoff.vftv.neoforge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.andrewbrogoff.vftv.core.aspect.AspectConfig;
import com.andrewbrogoff.vftv.core.aspect.DerivedAspects;
import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

class AspectServerKeysTest {
    private static final List<String> KEY = List.of("aspects", "derive_modded");

    @Test
    void aMissingKeyIsOnAndAQuotedBooleanCorrectsToIt() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        AspectServerKeys.define(builder);
        ModConfigSpec spec = builder.build();
        CommentedConfig missing = CommentedConfig.inMemory();
        spec.correct(missing);
        assertEquals(true, missing.<Object>get(KEY));
        CommentedConfig quoted = CommentedConfig.inMemory();
        quoted.set(KEY, "false");
        spec.correct(quoted);
        assertEquals(true, quoted.<Object>get(KEY), "a quoted boolean is a wrong type");
        ModConfigSpec.ValueSpec value = spec.getSpec().get(KEY);
        assertSame(Boolean.class, value.getClazz());
        assertEquals("options.vftv.aspects.derive_modded", value.getTranslationKey());
    }

    @Test
    void theRecordReadsTheKeyAndResendsNothing() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        List<ServerConfigPublisher.ServerRecord<?>> records = AspectServerKeys.define(builder);
        builder.build();
        assertEquals(1, records.size());
        @SuppressWarnings("unchecked") // define returns the AspectConfig record
        ServerConfigPublisher.ServerRecord<AspectConfig> record =
                (ServerConfigPublisher.ServerRecord<AspectConfig>) records.getFirst();
        assertEquals(AspectConfig.DEFAULT, record.defaultValue());
        AspectConfig before = DerivedAspects.config();
        record.install().accept(new AspectConfig(false));
        assertEquals(new AspectConfig(false), record.current().get());
        record.install().accept(before);
    }
}
