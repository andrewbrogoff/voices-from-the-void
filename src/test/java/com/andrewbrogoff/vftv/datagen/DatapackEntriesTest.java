package com.andrewbrogoff.vftv.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import org.junit.jupiter.api.Test;

class DatapackEntriesTest {
    @Test
    void featuresAddingToOneRegistryShareOneEntryAndRunInOrder() {
        List<String> ran = new ArrayList<>();
        DatapackEntries entries = new DatapackEntries();
        assertTrue(entries.isEmpty());
        entries.add(Registries.CONFIGURED_FEATURE, context -> ran.add("worldgen"));
        entries.add(Registries.DAMAGE_TYPE, context -> ran.add("damage"));
        entries.add(Registries.CONFIGURED_FEATURE, context -> ran.add("hush"));
        assertFalse(entries.isEmpty());

        entries.build().build(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertEquals(List.of("worldgen", "hush", "damage"), ran);
    }
}
