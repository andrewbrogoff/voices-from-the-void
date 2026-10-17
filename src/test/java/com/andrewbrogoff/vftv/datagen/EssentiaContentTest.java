package com.andrewbrogoff.vftv.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** The shipped essentia content passes every rule, and each "Rejected:" rule rejects. */
class EssentiaContentTest {
    private static final Path CONTENT =
            Path.of(System.getProperty("vftv.projectDir"), "content", "essentia");

    private static JsonObject recipes() throws Exception {
        return StrictJson.parse(Files.readString(CONTENT.resolve("recipes.json")))
                .getAsJsonObject();
    }

    private static List<String> check(JsonElement root) {
        return Schema.check(
                root,
                Map.of(
                        EssentiaData.RECIPES.id(),
                        EssentiaData.RECIPES,
                        EssentiaData.CLIENT.id(),
                        EssentiaData.CLIENT));
    }

    @Test
    void shippedFilesPass() throws Exception {
        assertEquals(List.of(), check(recipes()));
        assertEquals(
                List.of(),
                check(StrictJson.parse(Files.readString(CONTENT.resolve("client.json")))));
    }

    private static void rejects(String why, Consumer<JsonObject> mutate) throws Exception {
        JsonObject root = recipes();
        mutate.accept(root);
        assertFalse(check(root).isEmpty(), why);
    }

    private static JsonObject recipe(JsonObject root, String id) {
        return root.getAsJsonObject("recipes").getAsJsonObject(id);
    }

    @Test
    void everyRejectionRule() throws Exception {
        rejects(
                "an extra id",
                r ->
                        r.getAsJsonObject("recipes")
                                .add("vftv:extra", recipe(r, "vftv:label").deepCopy()));
        rejects("a missing id", r -> r.getAsJsonObject("recipes").remove("vftv:alembic"));
        rejects(
                "another research",
                r -> recipe(r, "vftv:essentia_jar").addProperty("research", "vftv:essentia_tubes"));
        rejects("a void kind", r -> recipe(r, "vftv:essentia_tube").addProperty("kind", "void"));
        rejects(
                "a wrong result",
                r ->
                        recipe(r, "vftv:alembic")
                                .getAsJsonObject("result")
                                .addProperty("id", "minecraft:glass"));
        rejects(
                "a count outside its row",
                r ->
                        recipe(r, "vftv:essentia_tube")
                                .getAsJsonObject("result")
                                .addProperty("count", 2));
        rejects(
                "components",
                r ->
                        recipe(r, "vftv:essentia_jar")
                                .getAsJsonObject("result")
                                .add("components", new JsonObject()));
        rejects(
                "a non-primal vis key",
                r ->
                        recipe(r, "vftv:tube_valve")
                                .getAsJsonObject("vis")
                                .addProperty("vftv:lux", 2));
        rejects(
                "over the Wellspring bound",
                r ->
                        recipe(r, "vftv:tube_valve")
                                .getAsJsonObject("vis")
                                .addProperty("vftv:ordo", 33));
        rejects(
                "a jar ingredient outside minecraft:",
                r ->
                        recipe(r, "vftv:essentia_jar")
                                .getAsJsonObject("key")
                                .addProperty("c", "vftv:resonant_brass_ingot"));
        rejects(
                "label ingredients",
                r ->
                        recipe(r, "vftv:label")
                                .getAsJsonArray("ingredients")
                                .set(1, new JsonPrimitive("minecraft:coal")));
        rejects(
                "a blanking recipe with two ingredients",
                r ->
                        recipe(r, "vftv:label_blank")
                                .getAsJsonArray("ingredients")
                                .add("minecraft:paper"));
        rejects(
                "both shapes",
                r -> {
                    JsonArray i = new JsonArray();
                    i.add("minecraft:glass");
                    recipe(r, "vftv:essentia_jar").add("ingredients", i);
                });
        rejects(
                "an unkeyed symbol",
                r -> recipe(r, "vftv:alembic").getAsJsonObject("key").remove("b"));
        rejects("a group field", r -> recipe(r, "vftv:alembic").addProperty("group", "x"));
        assertTrue(
                check(
                                        StrictJson.parse(
                                                "{\"schema\": \"vftv:essentia_client/1\", \"mote\": {\"speed\": 3, \"size\": 0.25}}"))
                                .size()
                        > 0,
                "speed out of range");
    }
}
