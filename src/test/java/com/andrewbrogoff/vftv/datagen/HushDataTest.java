package com.andrewbrogoff.vftv.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushedForm;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

/**
 * The {@code content/hush} schemas and their "Rejected:" rules, and the forms' support-parity tags
 * against vanilla's own tag data.
 */
class HushDataTest {
    private static final Path ROOT = WorldgenDataTest.project();
    private static final Map<String, Schema> SCHEMAS =
            Map.of(
                    HushData.RECIPES.id(), HushData.RECIPES,
                    HushData.NODE_TYPES.id(), HushData.NODE_TYPES,
                    HushData.FIXTURES.id(), HushData.FIXTURES);

    private static JsonObject read(String file) {
        try {
            return StrictJson.parse(
                            Files.readString(
                                    ROOT.resolve("content/hush/" + file), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> check(JsonObject json) {
        return Schema.check(json, SCHEMAS);
    }

    private static void rejects(String file, Consumer<JsonObject> change, String reason) {
        JsonObject json = read(file);
        change.accept(json);
        List<String> found = check(json);
        assertTrue(found.stream().anyMatch(p -> p.contains(reason)), reason + " in " + found);
    }

    private static JsonObject clearing(JsonObject root) {
        return root.getAsJsonObject("recipes").getAsJsonObject("vftv:clearing_bell");
    }

    private static JsonObject pealing(JsonObject root) {
        return root.getAsJsonObject("recipes").getAsJsonObject("vftv:pealing_bell");
    }

    @Test
    void theShippedContentPasses() {
        for (String file : List.of("recipes.json", "node_types.json", "fixtures.json")) {
            assertEquals(List.of(), check(read(file)), file);
        }
    }

    @Test
    void recipeRejections() {
        rejects(
                "recipes.json",
                r -> clearing(r).getAsJsonObject("key").addProperty("S", "vftv:hush_ash"),
                "hush_ash");
        rejects(
                "recipes.json",
                r -> clearing(r).getAsJsonObject("vis").addProperty("vftv:aer", 33),
                "over 32");
        rejects(
                "recipes.json",
                r -> clearing(r).getAsJsonObject("vis").addProperty("vftv:sonus", 1),
                "not a Wellspring primal");
        rejects(
                "recipes.json",
                r ->
                        clearing(r)
                                .getAsJsonObject("key")
                                .addProperty("C", "vftv:resonant_brass_ingot"),
                "at most 2");
        rejects(
                "recipes.json",
                r -> clearing(r).getAsJsonObject("key").addProperty("A", "minecraft:diamond"),
                "not allowed");
        rejects(
                "recipes.json",
                r -> clearing(r).addProperty("research", "vftv:the_hush"),
                "must be vftv:clearing_bell");
        rejects("recipes.json", r -> clearing(r).addProperty("kind", "void"), "must be ordinary");
        rejects(
                "recipes.json",
                r -> pealing(r).getAsJsonArray("components").add("minecraft:gold_ingot"),
                "not allowed");
        rejects(
                "recipes.json",
                r -> pealing(r).getAsJsonObject("essentia").addProperty("vftv:aer", 4),
                "only Sonus and Ordo");
        rejects(
                "recipes.json",
                r -> pealing(r).getAsJsonObject("essentia").addProperty("vftv:sonus", 40),
                "1 to 32");
        rejects("recipes.json", r -> pealing(r).addProperty("instability", 21), "0 to 20");
        rejects(
                "recipes.json",
                r -> pealing(r).addProperty("centre", "minecraft:anvil"),
                "must be minecraft:bell");
        rejects(
                "recipes.json",
                r -> {
                    JsonArray few = new JsonArray();
                    few.add("minecraft:amethyst_shard");
                    pealing(r).add("components", few);
                },
                "4 to 8");
        rejects(
                "recipes.json",
                r -> r.getAsJsonObject("recipes").add("vftv:extra", clearing(r).deepCopy()),
                "unknown");
    }

    @Test
    void nodeTypeRejections() {
        rejects(
                "node_types.json",
                t ->
                        t.getAsJsonObject("types")
                                .getAsJsonObject("vftv:hushed")
                                .getAsJsonObject("clarity")
                                .addProperty("steady", 1),
                "exactly the shipped");
    }

    @Test
    void fixtureRejections() {
        rejects("fixtures.json", f -> f.getAsJsonArray("hud").remove(0), "exactly 7 rows");
        rejects("fixtures.json", f -> f.remove("ball"), "ball");
        rejects(
                "fixtures.json",
                f ->
                        f.getAsJsonArray("kernel")
                                .get(0)
                                .getAsJsonObject()
                                .addProperty("world", "flat64"),
                "not one of [flat63]");
        rejects(
                "fixtures.json",
                f ->
                        f.getAsJsonArray("spill")
                                .get(9)
                                .getAsJsonObject()
                                .getAsJsonObject("expect")
                                .addProperty("surcharge", 0),
                "surcharge iff ordinary");
        rejects(
                "fixtures.json",
                f -> {
                    JsonObject row =
                            f.getAsJsonArray("kernel")
                                    .get(0)
                                    .getAsJsonObject()
                                    .getAsJsonArray("expect")
                                    .get(3)
                                    .getAsJsonObject();
                    row.add("at", StrictJson.parse("[1, 2, 3]"));
                },
                "at iff convert");
    }

    @Test
    void formsJoinExactlyTheSupportTagsTheirSourcesHold() throws IOException {
        Path tags = ROOT.resolve("src/generated/resources/data/minecraft/tags/block");
        for (TagKey<Block> tag : HushData.PARITY_TAGS) {
            Set<String> vanilla = HushData.vanillaMembers(tag.location());
            Path file = tags.resolve(tag.location().getPath() + ".json");
            Set<String> generated = new HashSet<>();
            if (Files.exists(file)) {
                for (JsonElement e :
                        StrictJson.parse(Files.readString(file))
                                .getAsJsonObject()
                                .getAsJsonArray("values")) {
                    generated.add(e.getAsString());
                }
            }
            for (HushedForm f : HushKeys.FORMS) {
                assertEquals(
                        vanilla.contains(f.source().toString()),
                        generated.contains(f.form().toString()),
                        f.form() + " in " + tag.location());
            }
        }
        assertTrue(
                HushData.vanillaMembers(net.minecraft.tags.BlockTags.SUPPORTS_VEGETATION.location())
                        .contains("minecraft:dirt"));
    }
}
