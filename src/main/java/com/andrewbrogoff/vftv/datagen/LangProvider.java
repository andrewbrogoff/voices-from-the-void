package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.andrewbrogoff.vftv.core.text.model.LangValidator;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

/**
 * The lang pipeline (accessibility, "The lang pipeline"): reads {@code content/lang/<code>/*.json},
 * runs the {@link LangValidator} over them and every owner's table, and writes {@code
 * assets/vftv/lang/<code>.json}, sorted by key, with the code-defined keys in {@code en_us}.
 */
public final class LangProvider extends ContentProvider {
    /**
     * Every owner's lang table: the shared messages, then each feature's {@link FeatureData#lang}.
     */
    public static final List<LangEntry> TABLES =
            Stream.concat(
                            SharedMessages.TABLE.stream(),
                            VftvData.FEATURES.stream().flatMap(f -> f.lang().stream()))
                    .toList();

    private final Set<String> catalogue;

    /** A provider checking language codes against the client's catalogue. */
    public LangProvider(PackOutput output, Set<String> catalogue) {
        super(output, "lang", List.of(LangValidator.LANG));
        this.catalogue = Set.copyOf(catalogue);
    }

    @Override
    protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
        List<LangValidator.Input> inputs = new ArrayList<>();
        List<String> misplaced = new ArrayList<>();
        for (Envelope envelope : envelopes) {
            String[] parts = envelope.path().split("/", -1);
            if (parts.length == 2) {
                inputs.add(new LangValidator.Input(parts[0], parts[1], envelope.text()));
            } else {
                misplaced.add(
                        "lang/" + envelope.path() + ": not at content/lang/<code>/<file>.json");
            }
        }
        LangValidator.Result result = LangValidator.validate(inputs, TABLES, catalogue, Map.of());
        List<String> problems = new ArrayList<>(misplaced);
        problems.addAll(result.problems());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Rejected lang:\n" + String.join("\n", problems));
        }
        PackOutput.PathProvider paths =
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "lang");
        List<CompletableFuture<?>> writes = new ArrayList<>();
        result.merged()
                .forEach(
                        (code, strings) -> {
                            JsonObject json = new JsonObject();
                            strings.forEach(json::addProperty);
                            writes.add(
                                    DataProvider.saveStable(
                                            cache,
                                            json,
                                            paths.json(
                                                    Identifier.fromNamespaceAndPath(
                                                            Vftv.MOD_ID, code))));
                        });
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "vftv lang";
    }
}
