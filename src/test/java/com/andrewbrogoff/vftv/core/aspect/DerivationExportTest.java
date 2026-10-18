package com.andrewbrogoff.vftv.core.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DerivationExportTest {
    @TempDir Path root;

    private static SortedMap<String, Integer> v(String aspect, int n) {
        return new TreeMap<>(Map.of(aspect, n));
    }

    private static DerivedAspects.Inputs inputs() {
        List<Derivation.Recipe> recipes =
                List.of(
                        new Derivation.Recipe(
                                "a:1",
                                List.of(
                                        List.of(
                                                new Derivation.Alternative(
                                                        "minecraft:oak_log", null))),
                                "alpha:widget",
                                1,
                                false),
                        new Derivation.Recipe(
                                "b:1",
                                List.of(
                                        List.of(
                                                new Derivation.Alternative(
                                                        "minecraft:oak_log", null))),
                                "beta:gadget",
                                1,
                                false));
        return new DerivedAspects.Inputs(
                new TreeMap<>(Map.of("minecraft:oak_log", v("vftv:arbor", 4))),
                new DerivationRecipes.Capture(
                        recipes, Map.of(DerivationRecipes.Rejection.ANOTHER_CLASS, 3), 5),
                new Derivation.Tuning(0.75, v("vftv:ignis", 1)));
    }

    @AfterEach
    void restore() {
        DerivationExport.swapFiles(DerivationExport.DISK);
    }

    @Test
    void theFilesAreDeterministicAndConditionOnlyLoadedMods() {
        DerivedAspects.Inputs in = inputs();
        Derivation.Result result = in.derive();
        SortedMap<String, String> mods = new TreeMap<>(Map.of("alpha", "1.0", "minecraft", "26.2"));
        SortedMap<String, String> first = DerivationExport.files(in, result, mods);
        assertEquals(first, DerivationExport.files(in, in.derive(), mods), "byte-identical");
        String alpha =
                Objects.requireNonNull(
                        first.get("vftv_derived_alpha/data/vftv/data_maps/item/aspects.json"));
        String beta =
                Objects.requireNonNull(
                        first.get("vftv_derived_beta/data/vftv/data_maps/item/aspects.json"));
        assertTrue(alpha.contains("\"modid\": \"alpha\""), alpha);
        assertTrue(alpha.contains("\"vftv:arbor\": 3\n    }"), alpha);
        assertFalse(beta.contains("neoforge:conditions"), beta);
        assertTrue(first.containsKey("vftv_derived_alpha/pack.mcmeta"));
        assertTrue(alpha.endsWith("}\n"));
        String report = Objects.requireNonNull(first.get("report.txt"));
        assertTrue(
                report.contains("no mod_loaded condition (no loaded mod has that id): beta"),
                report);
        assertTrue(report.contains("rejected, another class: 3"), report);
        assertTrue(report.contains("emitted: 2"), report);
        assertTrue(report.contains("    alpha 1.0\n"), report);
        assertTrue(
                report.indexOf("Inputs") < report.indexOf("Counts")
                        && report.indexOf("Counts") < report.indexOf("Excluded")
                        && report.indexOf("Excluded") < report.indexOf("Unresolved")
                        && report.indexOf("Unresolved") < report.indexOf("Review")
                        && report.indexOf("Review") < report.indexOf("Renamed copies"),
                report);
    }

    @Test
    void installedPacksAreRecognisedByPrefixOnly() {
        assertEquals(
                List.of("file/vftv_derived_alpha"),
                DerivationExport.installed(
                        List.of(
                                "vanilla",
                                "file/vftv_derived_alpha",
                                "file/my_vftv_derived_copy")));
    }

    private static SortedMap<String, String> files(String text) {
        return new TreeMap<>(Map.of("vftv_derived_x/pack.mcmeta", text, "report.txt", text));
    }

    private Path derived() {
        return root.resolve("derived");
    }

    private String read(Path dir) throws IOException {
        return Files.readString(dir.resolve("report.txt"), StandardCharsets.UTF_8);
    }

    @Test
    void aSuccessfulRunSwapsInTheNewExportAndRemovesThePrevious() throws IOException {
        assertInstanceOf(
                DerivationExport.Outcome.Done.class,
                DerivationExport.write(root, files("one"), 1, 1));
        var done = DerivationExport.write(root, files("two"), 1, 1);
        assertEquals(new DerivationExport.Outcome.Done(1, 1, derived(), null), done);
        assertEquals("two", read(derived()));
        assertFalse(Files.exists(root.resolve("derived.previous")));
        assertFalse(Files.exists(root.resolve("derived.staging")));
    }

    /** File operations that fail at the n-th call of one kind. */
    private static final class Failing implements DerivationExport.FileOps {
        private final String kind;
        private final int at;
        private int calls;

        Failing(String kind, int at) {
            this.kind = kind;
            this.at = at;
        }

        private void maybeFail(String op) throws IOException {
            if (op.equals(kind) && ++calls == at) {
                throw new IOException(op + " failed");
            }
        }

        @Override
        public void write(Path file, String text) throws IOException {
            maybeFail("write");
            DerivationExport.DISK.write(file, text);
        }

        @Override
        public void move(Path from, Path to) throws IOException {
            maybeFail("move");
            DerivationExport.DISK.move(from, to);
        }

        @Override
        public void delete(Path tree) throws IOException {
            maybeFail("delete");
            DerivationExport.DISK.delete(tree);
        }
    }

    private void previous() throws IOException {
        DerivationExport.write(root, files("old"), 1, 1);
    }

    @Test
    void aFailedStagingWriteKeepsThePreviousExport() throws IOException {
        previous();
        DerivationExport.swapFiles(new Failing("write", 1));
        var out = DerivationExport.write(root, files("new"), 1, 1);
        assertEquals(new DerivationExport.Outcome.Failed("write failed", derived()), out);
        assertEquals("old", read(derived()));
        assertFalse(Files.exists(root.resolve("derived.staging")));
    }

    @Test
    void aFailedFirstRenameKeepsThePreviousExport() throws IOException {
        previous();
        DerivationExport.swapFiles(new Failing("move", 1));
        var out = DerivationExport.write(root, files("new"), 1, 1);
        assertEquals(new DerivationExport.Outcome.Failed("move failed", derived()), out);
        assertEquals("old", read(derived()));
        assertFalse(Files.exists(root.resolve("derived.staging")));
    }

    @Test
    void aFailedSecondRenameRenamesThePreviousBack() throws IOException {
        previous();
        DerivationExport.swapFiles(new Failing("move", 2));
        var out = DerivationExport.write(root, files("new"), 1, 1);
        assertEquals(new DerivationExport.Outcome.Failed("move failed", derived()), out);
        assertEquals("old", read(derived()));
        assertFalse(Files.exists(root.resolve("derived.previous")));
    }

    @Test
    void aFailedRenameBackLeavesThePreviousCompleteWhereTheMessageSays() throws IOException {
        previous();
        DerivationExport.swapFiles(
                new DerivationExport.FileOps() {
                    private int moves;

                    @Override
                    public void write(Path file, String text) throws IOException {
                        DerivationExport.DISK.write(file, text);
                    }

                    @Override
                    public void move(Path from, Path to) throws IOException {
                        if (++moves >= 2) {
                            throw new IOException("move failed");
                        }
                        DerivationExport.DISK.move(from, to);
                    }

                    @Override
                    public void delete(Path tree) throws IOException {
                        DerivationExport.DISK.delete(tree);
                    }
                });
        var out = DerivationExport.write(root, files("new"), 1, 1);
        Path previous = root.resolve("derived.previous");
        assertEquals(new DerivationExport.Outcome.Failed("move failed", previous), out);
        assertEquals("old", read(previous));
        // The next run restores it before it starts.
        DerivationExport.swapFiles(DerivationExport.DISK);
        assertInstanceOf(
                DerivationExport.Outcome.Done.class,
                DerivationExport.write(root, files("next"), 1, 1));
        assertEquals("next", read(derived()));
        assertFalse(Files.exists(previous));
    }

    @Test
    void aFailedCleanupIsNotAFailureAndTheNextRunRemovesTheLeftover() throws IOException {
        previous();
        // Deletes: the leftover check is skipped (none), staging (1), then derived.previous (2).
        DerivationExport.swapFiles(new Failing("delete", 2));
        var out = DerivationExport.write(root, files("new"), 1, 1);
        Path previous = root.resolve("derived.previous");
        assertEquals(new DerivationExport.Outcome.Done(1, 1, derived(), previous), out);
        assertEquals("new", read(derived()));
        assertTrue(Files.exists(previous));
        DerivationExport.swapFiles(DerivationExport.DISK);
        DerivationExport.write(root, files("newer"), 1, 1);
        assertFalse(Files.exists(previous));
        assertEquals("newer", read(derived()));
    }
}
