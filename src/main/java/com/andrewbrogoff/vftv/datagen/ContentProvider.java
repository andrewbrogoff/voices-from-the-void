package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * The datagen provider base for authored content (conventions, "Authored content"): it reads every
 * {@code content/<dir>/**.json} envelope, rejects the common list through {@link Schema#check}, and
 * hands the valid envelopes to the owner's {@link #generate}. Any rejection fails the run, listing
 * every problem at once.
 *
 * <p>The content root is the system property {@value #CONTENT_DIR}, which the data runs set.
 */
public abstract class ContentProvider implements DataProvider {
    /** The system property naming the {@code content/} directory. */
    public static final String CONTENT_DIR = "vftv.contentDir";

    /** The pack output this provider writes into. */
    protected final PackOutput output;

    private final String dir;
    private final Map<String, Schema> schemas = new LinkedHashMap<>();

    /**
     * One valid envelope.
     *
     * @param path the file's path relative to {@code content/<dir>}, with {@code /} separators
     * @param text the raw file text
     * @param json the parsed envelope
     * @param schema the schema it names
     */
    public record Envelope(String path, String text, JsonElement json, Schema schema) {}

    /** A provider for {@code content/<dir>}, accepting the given schemas. */
    protected ContentProvider(PackOutput output, String dir, List<Schema> schemas) {
        this.output = output;
        this.dir = dir;
        schemas.forEach(s -> this.schemas.put(s.id(), s));
    }

    @Override
    public final CompletableFuture<?> run(CachedOutput cache) {
        String root = System.getProperty(CONTENT_DIR);
        if (root == null) {
            throw new IllegalStateException("the system property " + CONTENT_DIR + " is not set");
        }
        return generate(read(Path.of(root).resolve(dir), schemas), cache);
    }

    /** Writes the owner's outputs from the valid envelopes, in path order. */
    protected abstract CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache);

    /**
     * Reads and checks every envelope under {@code dir}; a missing directory holds none. Throws
     * {@link IllegalStateException} listing every rejection.
     */
    public static List<Envelope> read(Path dir, Map<String, Schema> schemas) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Envelope> envelopes = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String relative = dir.relativize(file).toString().replace('\\', '/');
            String where = dir.getFileName() + "/" + relative;
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonElement json = StrictJson.parse(text);
                List<String> found = Schema.check(json, schemas);
                if (found.isEmpty()) {
                    String id = json.getAsJsonObject().get("schema").getAsString();
                    envelopes.add(
                            new Envelope(
                                    relative, text, json, Objects.requireNonNull(schemas.get(id))));
                } else {
                    found.forEach(p -> problems.add(where + ": " + p));
                }
            } catch (IOException | StrictJson.MalformedJsonException e) {
                problems.add(where + ": " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Rejected content:\n" + String.join("\n", problems));
        }
        return envelopes;
    }
}
