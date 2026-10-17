package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The operator's audit export, {@code /vftv aspects derive} (compatibility, "The derivation
 * export"): the load-time derivation over the live server's data, written as one reviewable
 * datapack per source namespace plus a report, under {@code <world>/vftv/derived/}. Nothing it
 * writes loads unless the operator installs it. Two runs over the same state are byte-identical.
 */
public final class DerivationExport {
    /** The id prefix of an installed derived pack in a world's datapack folder. */
    public static final String INSTALLED_PREFIX = "file/vftv_derived_";

    /** The file operations the export uses; GameTests swap in failing ones. */
    public interface FileOps {
        /** Writes a file, creating its parents. */
        void write(Path file, String text) throws IOException;

        /** Moves a directory, atomically where the file system supports it. */
        void move(Path from, Path to) throws IOException;

        /** Deletes a directory tree; a missing one is no error. */
        void delete(Path tree) throws IOException;
    }

    /** The real file system. */
    public static final FileOps DISK =
            new FileOps() {
                @Override
                public void write(Path file, String text) throws IOException {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, text, StandardCharsets.UTF_8);
                }

                @Override
                public void move(Path from, Path to) throws IOException {
                    try {
                        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(from, to);
                    }
                }

                @Override
                public void delete(Path tree) throws IOException {
                    if (!Files.exists(tree)) {
                        return;
                    }
                    try (Stream<Path> walk = Files.walk(tree)) {
                        for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                            Files.delete(p);
                        }
                    } catch (UncheckedIOException e) {
                        throw e.getCause();
                    }
                }
            };

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile FileOps files = DISK;
    private static volatile Supplier<SortedMap<String, String>> mods = TreeMap::new;

    private DerivationExport() {}

    /** Installs the loaded mods' ids and versions; the platform calls it once. */
    public static void installMods(Supplier<SortedMap<String, String>> loaded) {
        mods = loaded;
    }

    /** Replaces the file operations and returns the previous ones (GameTests). */
    public static FileOps swapFiles(FileOps replacement) {
        FileOps previous = files;
        files = replacement;
        return previous;
    }

    /** What a run did. */
    public sealed interface Outcome {
        /**
         * Refused: derived packs are enabled.
         *
         * @param ids their pack ids, ascending
         */
        record Installed(List<String> ids) implements Outcome {}

        /**
         * Refused: over a bound; nothing written.
         *
         * @param recipes the accepted recipes
         * @param edges the expanded alternative edges
         */
        record TooLarge(int recipes, long edges) implements Outcome {}

        /**
         * A failure: the previous complete export, if any, is at {@code previous}.
         *
         * @param message the exception's message
         * @param previous where the previous complete export is
         */
        record Failed(String message, Path previous) implements Outcome {}

        /**
         * Written.
         *
         * @param values how many values were emitted
         * @param packs how many packs were written
         * @param dir the export's folder
         * @param leftover the previous export that could not be removed, or null
         */
        record Done(int values, int packs, Path dir, @Nullable Path leftover) implements Outcome {}
    }

    /** Runs the export over the server's live data, on the server thread. */
    public static Outcome run(MinecraftServer server) {
        List<String> installed = installed(server.getPackRepository().getSelectedIds());
        if (!installed.isEmpty()) {
            return new Outcome.Installed(installed);
        }
        DerivedAspects.Inputs inputs =
                DerivedAspects.inputs(
                        server.getRecipeManager().getRecipes(), server.registryAccess());
        if (inputs.overLimit()) {
            return new Outcome.TooLarge(inputs.capture().recipes().size(), inputs.edges());
        }
        Derivation.Result result = inputs.derive();
        SortedMap<String, String> loaded = mods.get();
        SortedMap<String, String> out = files(inputs, result, loaded);
        Path root = server.getWorldPath(LevelResource.ROOT).resolve("vftv").normalize();
        int packs = (int) out.keySet().stream().filter(k -> k.endsWith("/pack.mcmeta")).count();
        return write(root, out, result.values().size(), packs);
    }

    /** The selected packs that are installed derived packs: ids starting with the prefix. */
    static List<String> installed(Collection<String> selected) {
        return selected.stream().filter(id -> id.startsWith(INSTALLED_PREFIX)).sorted().toList();
    }

    /**
     * Writes the files through staging and two renames (compatibility, "Output"), reporting each
     * failing phase as the spec's table says.
     */
    static Outcome write(Path root, SortedMap<String, String> out, int values, int packs) {
        FileOps fs = files;
        Path staging = root.resolve("derived.staging");
        Path derived = root.resolve("derived");
        Path previous = root.resolve("derived.previous");
        try {
            if (Files.exists(previous)) {
                // An earlier run's leftover: removed beside a complete export, else restored.
                if (Files.exists(derived)) {
                    fs.delete(previous);
                } else {
                    fs.move(previous, derived);
                }
            }
            fs.delete(staging);
        } catch (IOException e) {
            return new Outcome.Failed(message(e), Files.exists(derived) ? derived : previous);
        }
        try {
            for (Map.Entry<String, String> f : out.entrySet()) {
                fs.write(staging.resolve(f.getKey()), f.getValue());
            }
        } catch (IOException e) {
            quietly(fs, staging);
            return new Outcome.Failed(message(e), derived);
        }
        boolean had = Files.exists(derived);
        if (had) {
            try {
                fs.move(derived, previous);
            } catch (IOException e) {
                quietly(fs, staging);
                return new Outcome.Failed(message(e), derived);
            }
        }
        try {
            fs.move(staging, derived);
        } catch (IOException e) {
            Path left = derived;
            if (had) {
                try {
                    fs.move(previous, derived);
                } catch (IOException back) {
                    left = previous;
                }
            }
            quietly(fs, staging);
            return new Outcome.Failed(message(e), left);
        }
        if (had) {
            try {
                fs.delete(previous);
            } catch (IOException e) {
                return new Outcome.Done(values, packs, derived, previous);
            }
        }
        return new Outcome.Done(values, packs, derived, null);
    }

    /**
     * Every file of an export, by path relative to {@code derived/}: one pack per source namespace
     * and the report. Deterministic: ascending ids, two-space JSON, {@code \n}.
     */
    public static SortedMap<String, String> files(
            DerivedAspects.Inputs inputs,
            Derivation.Result result,
            SortedMap<String, String> loaded) {
        SortedMap<String, SortedMap<String, SortedMap<String, Integer>>> byNamespace =
                new TreeMap<>();
        result.values()
                .forEach(
                        (item, value) ->
                                byNamespace
                                        .computeIfAbsent(
                                                item.substring(0, item.indexOf(':')),
                                                k -> new TreeMap<>())
                                        .put(item, value));
        SortedMap<String, String> out = new TreeMap<>();
        List<String> unconditioned = new ArrayList<>();
        byNamespace.forEach(
                (ns, items) -> {
                    String pack = "vftv_derived_" + ns + "/";
                    out.put(pack + "pack.mcmeta", json(meta(ns)));
                    JsonObject map = new JsonObject();
                    if (loaded.containsKey(ns)) {
                        JsonObject condition = new JsonObject();
                        condition.addProperty("type", "neoforge:mod_loaded");
                        condition.addProperty("modid", ns);
                        JsonArray conditions = new JsonArray();
                        conditions.add(condition);
                        map.add("neoforge:conditions", conditions);
                    } else {
                        unconditioned.add(ns);
                    }
                    JsonObject entries = new JsonObject();
                    items.forEach((item, value) -> entries.add(item, value(value)));
                    map.add("values", entries);
                    out.put(pack + "data/vftv/data_maps/item/aspects.json", json(map));
                });
        out.put("report.txt", report(inputs, result, loaded, unconditioned));
        return out;
    }

    private static JsonElement meta(String namespace) {
        PackMetadataSection section =
                new PackMetadataSection(
                        Component.literal("vftv derived aspect values for " + namespace),
                        new InclusiveRange<>(
                                SharedConstants.getCurrentVersion()
                                        .packVersion(PackType.SERVER_DATA)));
        JsonObject root = new JsonObject();
        root.add(
                PackMetadataSection.SERVER_TYPE.name(),
                PackMetadataSection.SERVER_TYPE
                        .codec()
                        .encodeStart(JsonOps.INSTANCE, section)
                        .getOrThrow());
        return root;
    }

    private static JsonObject value(SortedMap<String, Integer> value) {
        JsonObject o = new JsonObject();
        value.forEach(o::addProperty);
        return o;
    }

    private static String report(
            DerivedAspects.Inputs inputs,
            Derivation.Result result,
            SortedMap<String, String> loaded,
            List<String> unconditioned) {
        StringBuilder b = new StringBuilder();
        b.append("vftv derived aspect values\n\n");
        b.append("Inputs\n");
        b.append("  derivation.factor: ").append(inputs.tuning().factor()).append('\n');
        b.append("  derivation.smelt_bonus: ")
                .append(GSON.toJson(value(inputs.tuning().smeltBonus())).replaceAll("\\s+", " "))
                .append('\n');
        b.append("  mods:\n");
        loaded.forEach(
                (id, version) ->
                        b.append("    ").append(id).append(' ').append(version).append('\n'));
        b.append("\nCounts\n");
        DerivationRecipes.Capture capture = inputs.capture();
        Map<DerivationRecipes.Rejection, Integer> rejected = new TreeMap<>(capture.rejected());
        rejected.put(DerivationRecipes.Rejection.UNVALUED_REMAINDER, result.unvaluedRemainders());
        count(b, "holders seen", capture.seen());
        count(b, "accepted", capture.recipes().size() - result.unvaluedRemainders());
        for (DerivationRecipes.Rejection r : DerivationRecipes.Rejection.values()) {
            count(b, "rejected, " + r.word(), rejected.getOrDefault(r, 0));
        }
        count(b, "candidates", result.candidates());
        count(b, "emitted", result.values().size());
        count(b, "empty", result.empty().size());
        count(b, "excluded", result.excluded().size());
        count(b, "unresolved", result.unresolved().size());
        b.append("\nExcluded: on a conversion cycle of accepted recipes, with its component\n");
        result.excluded()
                .forEach(
                        (item, component) ->
                                b.append("  ")
                                        .append(item)
                                        .append(" [")
                                        .append(String.join(", ", component))
                                        .append("]\n"));
        b.append("\nUnresolved: no usable recipe, with the first blocking item\n");
        result.unresolved()
                .forEach(
                        (item, blocked) ->
                                b.append("  ")
                                        .append(item)
                                        .append(" blocked by ")
                                        .append(blocked.item())
                                        .append(" (")
                                        .append(blocked.reason().word())
                                        .append(")\n"));
        b.append("\nReview\n");
        b.append(
                "  No emitted value lies on a conversion cycle of accepted recipes. Rejected"
                        + " recipes are outside that promise: a machine that turns one item into"
                        + " more may still gain. Review the rejected counts above.\n");
        b.append("  Namespaces with no mod_loaded condition (no loaded mod has that id): ")
                .append(unconditioned.isEmpty() ? "none" : String.join(", ", unconditioned))
                .append('\n');
        b.append("\nRenamed copies\n");
        b.append(
                "  An installed pack is recognised only while its id starts with "
                        + INSTALLED_PREFIX
                        + ". A renamed copy counts as authored data: its items are resolved and"
                        + " left out of later exports.\n");
        return b.toString();
    }

    private static void count(StringBuilder b, String label, long n) {
        b.append("  ")
                .append(label)
                .append(": ")
                .append(String.format(Locale.ROOT, "%d", n))
                .append('\n');
    }

    private static String json(JsonElement element) {
        return GSON.toJson(element) + "\n";
    }

    private static String message(IOException e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }

    private static void quietly(FileOps fs, Path tree) {
        try {
            fs.delete(tree);
        } catch (IOException e) {
            // The staging folder is rebuilt at the next run's start.
        }
    }
}
