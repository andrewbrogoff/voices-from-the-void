package com.andrewbrogoff.vftv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.TestItems;
import com.andrewbrogoff.vftv.core.Vftv;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import org.junit.jupiter.api.Test;

/**
 * The resource-reference validator (quality gates, "E2E", Asset checks): every blockstate, item
 * definition, model, texture, sound and lang key that our registrations or resources reference
 * resolves to a real file. "Has a model" follows each block's render shape, so an invisible block
 * needs no blockstate. Vanilla targets resolve against the Minecraft jar. The client-side half is
 * the {@code harness-scenarios/resources.json} scenario.
 */
class ResourceReferencesTest {
    private static final Path PROJECT = Path.of(System.getProperty("vftv.projectDir", "."));
    private static final List<Path> ROOTS =
            List.of(
                    PROJECT.resolve("src/main/resources"),
                    PROJECT.resolve("src/generated/resources"));

    /** What a namespace registers, as far as its resources are concerned. */
    record Registered(
            Set<String> visibleBlocks,
            Map<String, String> itemModels,
            Set<String> soundEvents,
            Set<String> nameKeys) {}

    /**
     * Every broken reference, sorted. {@code exists} answers for any {@code assets/...} path;
     * {@code read} returns the text of one that exists.
     */
    static List<String> problems(
            String namespace,
            Registered registered,
            Predicate<String> exists,
            Function<String, String> read,
            Set<String> ourFiles) {
        Set<String> problems = new TreeSet<>();
        Deque<String> models = new ArrayDeque<>();
        String lang = "assets/" + namespace + "/lang/en_us.json";
        Set<String> langKeys =
                exists.test(lang)
                        ? JsonParser.parseString(read.apply(lang)).getAsJsonObject().keySet()
                        : Set.of();

        Set<String> roots = new TreeSet<>();
        for (String block : registered.visibleBlocks()) {
            roots.add(path(block, "blockstates", ".json"));
        }
        for (Map.Entry<String, String> item : registered.itemModels().entrySet()) {
            roots.add(path(item.getValue(), "items", ".json"));
        }
        for (String file : ourFiles) {
            if (file.startsWith("assets/" + namespace + "/blockstates/")
                    || file.startsWith("assets/" + namespace + "/items/")) {
                roots.add(file);
            }
        }
        for (String root : roots) {
            if (!exists.test(root)) {
                problems.add("missing " + root);
            } else {
                collectModels(JsonParser.parseString(read.apply(root)), models);
            }
        }

        Set<String> seen = new HashSet<>();
        while (!models.isEmpty()) {
            String model = models.pop();
            if (!seen.add(model) || Identifier.parse(model).getPath().startsWith("builtin/")) {
                continue;
            }
            String file = path(model, "models", ".json");
            if (!exists.test(file)) {
                problems.add("missing " + file + " (model " + model + ")");
                continue;
            }
            JsonObject json = JsonParser.parseString(read.apply(file)).getAsJsonObject();
            if (json.has("parent")) {
                models.push(json.get("parent").getAsString());
            }
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> texture :
                        json.getAsJsonObject("textures").entrySet()) {
                    JsonElement value = texture.getValue();
                    String sprite =
                            value.isJsonObject()
                                    ? value.getAsJsonObject().get("sprite").getAsString()
                                    : value.getAsString();
                    if (!sprite.startsWith("#") && !exists.test(path(sprite, "textures", ".png"))) {
                        problems.add(
                                "missing "
                                        + path(sprite, "textures", ".png")
                                        + " (texture of "
                                        + model
                                        + ")");
                    }
                }
            }
        }

        String soundsFile = "assets/" + namespace + "/sounds.json";
        JsonObject sounds =
                exists.test(soundsFile)
                        ? JsonParser.parseString(read.apply(soundsFile)).getAsJsonObject()
                        : new JsonObject();
        for (String event : registered.soundEvents()) {
            if (!sounds.has(Identifier.parse(event).getPath())) {
                problems.add("missing sounds.json entry for " + event);
            }
        }
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            JsonObject definition = event.getValue().getAsJsonObject();
            if (definition.has("subtitle")
                    && !langKeys.contains(definition.get("subtitle").getAsString())) {
                problems.add("missing lang key " + definition.get("subtitle").getAsString());
            }
            if (definition.has("sounds")) {
                for (JsonElement sound : definition.getAsJsonArray("sounds")) {
                    boolean isEvent =
                            sound.isJsonObject()
                                    && sound.getAsJsonObject().has("type")
                                    && sound.getAsJsonObject()
                                            .get("type")
                                            .getAsString()
                                            .equals("event");
                    String name =
                            sound.isJsonObject()
                                    ? sound.getAsJsonObject().get("name").getAsString()
                                    : sound.getAsString();
                    if (!isEvent && !exists.test(path(name, "sounds", ".ogg"))) {
                        problems.add("missing " + path(name, "sounds", ".ogg"));
                    }
                }
            }
        }

        for (String key : registered.nameKeys()) {
            if (!langKeys.contains(key)) {
                problems.add("missing lang key " + key);
            }
        }
        return List.copyOf(problems);
    }

    private static void collectModels(JsonElement json, Deque<String> models) {
        if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                boolean modelId =
                        (entry.getKey().equals("model") || entry.getKey().equals("base"))
                                && value.isJsonPrimitive();
                if (modelId) {
                    models.push(value.getAsString());
                } else {
                    collectModels(value, models);
                }
            }
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> collectModels(element, models));
        }
    }

    private static String path(String id, String kind, String extension) {
        Identifier identifier = Identifier.parse(id);
        return "assets/"
                + identifier.getNamespace()
                + "/"
                + kind
                + "/"
                + identifier.getPath()
                + extension;
    }

    private static <T> Map<String, T> ours(Registry<T> registry) {
        Map<String, T> out = new TreeMap<>();
        registry.entrySet().stream()
                .filter(e -> e.getKey().identifier().getNamespace().equals(Vftv.MOD_ID))
                .forEach(e -> out.put(e.getKey().identifier().toString(), e.getValue()));
        return out;
    }

    private static boolean existsInProject(String path) {
        return ROOTS.stream().anyMatch(root -> Files.isRegularFile(root.resolve(path)));
    }

    private static String readInProject(String path) {
        for (Path root : ROOTS) {
            Path file = root.resolve(path);
            if (Files.isRegularFile(file)) {
                try {
                    return Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        // Vanilla's assets: FML's minecraft module hides them, the plain classpath jar does not.
        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> ourFiles() throws IOException {
        Set<String> files = new TreeSet<>();
        for (Path root : ROOTS) {
            if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                            .map(p -> root.relativize(p).toString().replace('\\', '/'))
                            .forEach(files::add);
                }
            }
        }
        return files;
    }

    @Test
    void everyReferencedResourceExists() throws IOException {
        TestItems.bindComponents();
        Set<String> visibleBlocks = new TreeSet<>();
        Set<String> nameKeys = new TreeSet<>();
        ours(BuiltInRegistries.BLOCK)
                .forEach(
                        (id, block) -> {
                            nameKeys.add(block.getDescriptionId());
                            if (block.getStateDefinition().getPossibleStates().stream()
                                    .anyMatch(s -> s.getRenderShape() != RenderShape.INVISIBLE)) {
                                visibleBlocks.add(id);
                            }
                        });
        Map<String, String> itemModels = new TreeMap<>();
        ours(BuiltInRegistries.ITEM)
                .forEach(
                        (id, item) -> {
                            nameKeys.add(item.getDescriptionId());
                            Identifier model = new ItemStack(item).get(DataComponents.ITEM_MODEL);
                            itemModels.put(id, model == null ? id : model.toString());
                        });
        ours(BuiltInRegistries.ENTITY_TYPE)
                .values()
                .forEach(t -> nameKeys.add(t.getDescriptionId()));
        Registered registered =
                new Registered(
                        visibleBlocks,
                        itemModels,
                        ours(BuiltInRegistries.SOUND_EVENT).keySet(),
                        nameKeys);
        Predicate<String> exists =
                path ->
                        existsInProject(path)
                                || Thread.currentThread().getContextClassLoader().getResource(path)
                                        != null;
        assertTrue(
                exists.test("assets/minecraft/models/block/cube_all.json"),
                "vanilla client assets are visible, so vanilla parents resolve");
        assertEquals(
                List.of(),
                problems(
                        Vftv.MOD_ID,
                        registered,
                        exists,
                        ResourceReferencesTest::readInProject,
                        ourFiles()));
    }

    @Test
    void theValidatorRejectsEveryKindOfBrokenReference() {
        Map<String, String> files = new TreeMap<>();
        files.put("assets/t/lang/en_us.json", "{\"block.t.ok\":\"Ok\",\"subtitles.t.ok\":\"Ok\"}");
        files.put(
                "assets/t/blockstates/ok.json",
                "{\"variants\":{\"\":[{\"model\":\"t:block/ok\"},{\"model\":\"t:block/gone\"}]}}");
        files.put(
                "assets/t/models/block/ok.json",
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"t:block/ok\","
                        + "\"side\":\"#all\",\"top\":{\"sprite\":\"t:block/no_top\"}}}");
        files.put("assets/minecraft/models/block/cube_all.json", "{\"parent\":\"builtin/x\"}");
        files.put("assets/t/textures/block/ok.png", "");
        files.put(
                "assets/t/items/ok.json",
                "{\"model\":{\"type\":\"minecraft:special\",\"base\":\"t:item/no_base\","
                        + "\"model\":{\"type\":\"minecraft:chest\"}}}");
        files.put(
                "assets/t/sounds.json",
                "{\"ok\":{\"subtitle\":\"subtitles.t.ok\",\"sounds\":[]},"
                        + "\"loud\":{\"subtitle\":\"subtitles.t.loud\",\"sounds\":[\"t:loud\","
                        + "{\"name\":\"t:other\",\"type\":\"event\"}]}}");
        Registered registered =
                new Registered(
                        Set.of("t:ok", "t:no_state"),
                        Map.of("t:ok", "t:ok", "t:plain", "t:plain"),
                        Set.of("t:ok", "t:unlisted"),
                        Set.of("block.t.ok", "item.t.plain"));
        assertEquals(
                List.of(
                        "missing assets/t/blockstates/no_state.json",
                        "missing assets/t/items/plain.json",
                        "missing assets/t/models/block/gone.json (model t:block/gone)",
                        "missing assets/t/models/item/no_base.json (model t:item/no_base)",
                        "missing assets/t/sounds/loud.ogg",
                        "missing assets/t/textures/block/no_top.png (texture of t:block/ok)",
                        "missing lang key item.t.plain",
                        "missing lang key subtitles.t.loud",
                        "missing sounds.json entry for t:unlisted"),
                problems("t", registered, files::containsKey, files::get, files.keySet()));
    }
}
