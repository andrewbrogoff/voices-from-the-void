package com.andrewbrogoff.vftv.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** The {@code content/worldgen} schemas and every "Rejected:" rule beyond the templates. */
class WorldgenDataTest {
    static Path project() {
        return Path.of(System.getProperty("vftv.projectDir", "."));
    }

    static JsonObject content(String file) {
        try {
            return StrictJson.parse(
                            Files.readString(
                                    project().resolve("content/worldgen").resolve(file),
                                    StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> check(Schema schema, JsonObject json) {
        return Schema.check(json, Map.of(schema.id(), schema));
    }

    private static void rejects(
            Schema schema, String file, Consumer<JsonObject> mutate, String reason) {
        JsonObject json = content(file);
        mutate.accept(json);
        List<String> found = check(schema, json);
        assertTrue(found.stream().anyMatch(p -> p.contains(reason)), found.toString());
    }

    @Test
    void theShippedFilesPass() {
        assertEquals(List.of(), check(WorldgenData.RUIN, content("choir_ruin.json")));
        assertEquals(List.of(), check(WorldgenData.SITES, content("sites.json")));
        assertEquals(List.of(), check(WorldgenData.NODE_TYPES, content("node_types.json")));
        assertEquals(List.of(), check(WorldgenData.LOOT, content("loot.json")));
        assertEquals(List.of(), check(WorldgenData.FIXTURES, content("fixtures.json")));
        assertEquals(55, WorldgenData.overworldBiomes().size(), "#minecraft:is_overworld");
    }

    @Test
    void theRuin() {
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonObject("structure_set").addProperty("salt", 1), "salt");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonObject("structure_set").addProperty("separation", 36),
                "spacing must exceed separation");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonObject("pools").getAsJsonObject("ends").remove("minecraft:empty"),
                "$.pools.ends");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonObject("pools").getAsJsonObject("wings").addProperty("minecraft:empty", 1),
                "$.pools.wings");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonObject("pools").getAsJsonObject("chancel").addProperty("vftv:choir_ruin/chancel_a", 17),
                "17");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonArray("weathering").get(0).getAsJsonObject().addProperty("output", "minecraft:stone"),
                "#vftv:choir_weathering");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonArray("weathering").get(0).getAsJsonObject().addProperty("output", "minecraft:tuff_bricks"),
                "input equals output");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonObject("biomes").getAsJsonObject("exclude").remove("minecraft:deep_dark"),
                "minecraft:deep_dark is in neither list");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonObject("biomes").getAsJsonArray("include").add("minecraft:deep_dark"),
                "in both lists");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonObject("biomes").getAsJsonArray("include").add("minecraft:nether_wastes"),
                "is not in #minecraft:is_overworld");
        rejects(WorldgenData.RUIN, "choir_ruin.json",
                j -> j.getAsJsonObject("biomes").getAsJsonObject("exclude").addProperty("minecraft:the_void", "x"),
                "is not in #minecraft:is_overworld");
        rejects(WorldgenData.RUIN, "choir_ruin.json", j -> j.addProperty("extra", 1), "extra");
    }

    @Test
    void theSites() {
        rejects(WorldgenData.SITES, "sites.json",
                j -> j.getAsJsonObject("sites").add("pack:other", j.getAsJsonObject("sites").get("vftv:choir_ruin")),
                "exactly one site");
        rejects(WorldgenData.SITES, "sites.json",
                j -> j.getAsJsonObject("sites").getAsJsonObject("vftv:choir_ruin").addProperty("pages_per_claim", 3),
                "must be 6");
        System.setProperty(ContentProvider.CONTENT_DIR, project().resolve("content").toString());
        assertEquals(List.of(), WorldgenData.siteContent(content("sites.json")));
        JsonObject reordered = content("sites.json");
        JsonArray pages =
                reordered.getAsJsonObject("sites").getAsJsonObject("vftv:choir_ruin").getAsJsonArray("pages");
        pages.set(0, new com.google.gson.JsonPrimitive("vftv:choir/where_they_went"));
        List<String> found = WorldgenData.siteContent(reordered);
        assertTrue(found.stream().anyMatch(p -> p.contains("must be first")), found.toString());
        assertTrue(found.stream().anyMatch(p -> p.contains("outside Stage 3")), found.toString());
    }

    @Test
    void theNodeType() {
        rejects(WorldgenData.NODE_TYPES, "node_types.json",
                j -> j.getAsJsonObject("types").getAsJsonObject("vftv:choir").addProperty("main_aspect", "vftv:aer"),
                "exactly the shipped vftv:choir entry");
        rejects(WorldgenData.NODE_TYPES, "node_types.json",
                j -> {
                    j.getAsJsonObject("types").add("vftv:other", j.getAsJsonObject("types").get("vftv:choir"));
                    j.getAsJsonObject("types").remove("vftv:choir");
                },
                "exactly the shipped vftv:choir entry");
    }

    @Test
    void theLoot() {
        rejects(WorldgenData.LOOT, "loot.json",
                j -> item(j).addProperty("name", "vftv:choir_page"), "vftv:choir_page is never loot");
        rejects(WorldgenData.LOOT, "loot.json",
                j -> item(j).addProperty("name", "vftv:hush_ash"), "vftv:hush_ash is never loot");
        rejects(WorldgenData.LOOT, "loot.json",
                j -> item(j).addProperty("name", "vftv:hushed_stone"), "is never loot");
        rejects(WorldgenData.LOOT, "loot.json",
                j -> item(j).addProperty("name", "vftv:tuning_fork"), "outside the minecraft namespace");
        rejects(WorldgenData.LOOT, "loot.json",
                j -> j.getAsJsonObject("tables").getAsJsonObject("vftv:chests/choir_ruin").addProperty("type", "minecraft:entity"),
                "must be minecraft:chest");
        rejects(WorldgenData.LOOT, "loot.json",
                j -> {
                    JsonObject t = j.getAsJsonObject("tables");
                    t.add("vftv:chests/other", t.remove("vftv:chests/choir_ruin"));
                },
                "$.tables: exactly");
    }

    private static JsonObject item(JsonObject loot) {
        return loot.getAsJsonObject("tables")
                .getAsJsonObject("vftv:chests/choir_ruin")
                .getAsJsonArray("pools")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("entries")
                .get(0)
                .getAsJsonObject();
    }

    @Test
    void theFixtures() {
        rejects(WorldgenData.FIXTURES, "fixtures.json",
                j -> steps(j).get(3).getAsJsonObject().getAsJsonObject("use").addProperty("tick", 0),
                "ticks never decrease");
        rejects(WorldgenData.FIXTURES, "fixtures.json",
                j -> steps(j).get(3).getAsJsonObject().getAsJsonObject("expect").add("pages", new JsonArray()),
                "pages iff");
        rejects(WorldgenData.FIXTURES, "fixtures.json",
                j -> steps(j).get(0).getAsJsonObject().getAsJsonObject("expect").remove("pages"),
                "pages iff");
        rejects(WorldgenData.FIXTURES, "fixtures.json",
                j -> steps(j).get(1).getAsJsonObject().add("remove", new JsonArray()),
                "exactly one of use, latch and remove");
        rejects(WorldgenData.FIXTURES, "fixtures.json",
                j -> steps(j).get(0).getAsJsonObject().remove("expect"), "expect iff use");
        rejects(WorldgenData.FIXTURES, "fixtures.json", j -> j.remove("roll"), "roll");
        rejects(WorldgenData.FIXTURES, "fixtures.json",
                j -> j.getAsJsonArray("roll").get(0).getAsJsonObject().getAsJsonArray("pos").set(1, new com.google.gson.JsonPrimitive(320)),
                "outside the world");
    }

    private static JsonArray steps(JsonObject fixtures) {
        return fixtures.getAsJsonArray("offers").get(0).getAsJsonObject().getAsJsonArray("steps");
    }
}
