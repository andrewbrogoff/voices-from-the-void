package com.andrewbrogoff.vftv.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The migration template (architecture, "State and sync"), over a real storage on disk. */
class WorldMigrationsTest {
    @TempDir Path dir;

    private SavedDataStorage storage() {
        return new SavedDataStorage(null, dir, DataFixers.getDataFixer(), RegistryAccess.EMPTY);
    }

    private static int version(SavedDataStorage storage) {
        return WorldMigrations.VERSION.read(storage) instanceof Retained.Read<Integer> read
                ? read.value()
                : -1;
    }

    @Test
    void runsPendingStepsInOrderOnceAndPersistsTheVersion() {
        List<String> log = new ArrayList<>();
        List<Consumer<List<String>>> steps = List.of(l -> l.add("a"), l -> l.add("b"));
        try (SavedDataStorage storage = storage()) {
            assertEquals(2, WorldMigrations.runPending(storage, steps, log));
            assertEquals(List.of("a", "b"), log);
            assertEquals(
                    0, WorldMigrations.runPending(storage, steps, log), "a rerun runs nothing");
        }
        try (SavedDataStorage reloaded = storage()) {
            assertEquals(2, version(reloaded));
            List<Consumer<List<String>>> more =
                    List.of(l -> l.add("a"), l -> l.add("b"), l -> l.add("c"));
            assertEquals(1, WorldMigrations.runPending(reloaded, more, log));
            assertEquals(List.of("a", "b", "c"), log);
        }
    }

    @Test
    void aCrashMidStepRerunsOnlyThatStep() {
        List<String> log = new ArrayList<>();
        Consumer<List<String>> crash =
                l -> {
                    throw new IllegalStateException("crash");
                };
        try (SavedDataStorage storage = storage()) {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            WorldMigrations.runPending(
                                    storage, List.of(l -> l.add("a"), crash), log));
            assertEquals(1, version(storage));
            assertEquals(
                    1,
                    WorldMigrations.runPending(
                            storage, List.of(l -> l.add("a"), l -> l.add("b")), log));
            assertEquals(List.of("a", "b"), log);
        }
    }

    @Test
    void aNewerOrUnreadableVersionRunsNothingAndIsKept() {
        List<String> log = new ArrayList<>();
        try (SavedDataStorage storage = storage()) {
            WorldMigrations.VERSION.write(storage, new Retained.Read<>(5));
            assertEquals(0, WorldMigrations.runPending(storage, List.of(l -> l.add("a")), log));
            assertEquals(5, version(storage));
        }

        CompoundTag future = new CompoundTag();
        future.putInt("format", 2);
        future.putInt("vftv_data_version", 1);
        try (SavedDataStorage storage = storage()) {
            WorldMigrations.VERSION.write(storage, new Retained.Unreadable<>(future, "test"));
        }
        try (SavedDataStorage storage = storage()) {
            assertEquals(0, WorldMigrations.runPending(storage, List.of(l -> l.add("a")), log));
            assertEquals(List.of(), log);
            if (!(WorldMigrations.VERSION.read(storage)
                    instanceof Retained.Unreadable<Integer> kept)) {
                throw new AssertionError("a future format is unreadable");
            }
            assertEquals(future, kept.raw(), "kept raw");
        }
    }
}
