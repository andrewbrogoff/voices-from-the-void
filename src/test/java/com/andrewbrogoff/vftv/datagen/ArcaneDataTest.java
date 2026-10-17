package com.andrewbrogoff.vftv.datagen;

import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.arcane.ArcaneTestData;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArcaneDataTest {
    private static final String BELL =
            "'type': 'vftv:arcane', 'research': 'vftv:arcane_crafting', 'kind': 'ordinary',"
                    + " 'pattern': ['sss', 'cac', 'c c'],"
                    + " 'key': {'s': '#minecraft:wooden_slabs', 'c': 'minecraft:copper_ingot',"
                    + " 'a': 'minecraft:amethyst_shard'},"
                    + " 'result': {'id': 'minecraft:bell', 'count': 1},"
                    + " 'vis': {'vftv:aer': 8, 'vftv:ordo': 6, 'vftv:terra': 4}";

    private static final Schema SCHEMA =
            ArcaneData.schema(ArcaneData.wellspring(Path.of("absent")));

    @TempDir Path dir;

    private static List<String> problems(String recipes) {
        return Schema.check(
                json("{'schema': 'vftv:arcane_recipes/1', 'recipes': {" + recipes + "}}"),
                Map.of(SCHEMA.id(), SCHEMA));
    }

    private static void rejects(String recipes, String reason) {
        List<String> found = problems(recipes);
        assertTrue(found.stream().anyMatch(p -> p.contains(reason)), found.toString());
    }

    @Test
    void theExampleBellPasses() {
        assertEquals(List.of(), problems("'vftv:bell': {" + BELL + "}"));
        assertEquals(
                List.of(),
                problems("'vftv:bell': {" + BELL.replace("'kind': 'ordinary',", "") + "}"));
    }

    @Test
    void holdsExactlyTheBell() {
        rejects("'vftv:gong': {" + BELL + "}", "holds exactly vftv:bell");
        rejects("'vftv:bell': {" + BELL + "}, 'vftv:gong': {" + BELL + "}", "outside 1..1");
        rejects("", "outside 1..1");
    }

    @Test
    void theBellsFixedFields() {
        rejects(
                "'vftv:bell': {" + BELL.replace("vftv:arcane_crafting", "vftv:tapping") + "}",
                "research: must be vftv:arcane_crafting");
        rejects(
                "'vftv:bell': {" + BELL.replace("'ordinary'", "'void'") + "}",
                "kind: must be ordinary");
        rejects(
                "'vftv:bell': {" + BELL.replace("minecraft:bell", "minecraft:lantern") + "}",
                "result.id: must be minecraft:bell");
    }

    @Test
    void unknownFieldsAreRejected() {
        for (String field : List.of("group", "category", "show_notification")) {
            rejects("'vftv:bell': {'" + field + "': 'x', " + BELL + "}", field + ": unknown field");
        }
        rejects(
                "'vftv:bell': {" + BELL.replace("'count': 1", "'count': 1, 'components': {}") + "}",
                "components: unknown field");
    }

    @Test
    void shapeAndCostBounds() {
        rejects(
                "'vftv:bell': {"
                        + BELL.replace("['sss', 'cac', 'c c']", "['sss', 'cac', 'c c', 'sss']")
                        + "}",
                "pattern: 4 entries");
        rejects("'vftv:bell': {" + BELL.replace("'sss'", "'ssss'") + "}", "pattern[0]: length 4");
        rejects(
                "'vftv:bell': {" + BELL.replace("'vftv:terra': 4", "'vftv:sonus': 4") + "}",
                "vftv:sonus: not a built-in primal");
        rejects(
                "'vftv:bell': {" + BELL.replace("'vftv:terra': 4", "'vftv:terra': 33") + "}",
                "vftv:terra: over the Wellspring's 32");
    }

    @Test
    void preIronIngredientsOnly() {
        rejects(
                "'vftv:bell': {"
                        + BELL.replace("minecraft:copper_ingot", "minecraft:iron_ingot")
                        + "}",
                "minecraft:iron_ingot is not a pre-iron ingredient");
        for (String ok :
                List.of(
                        "minecraft:copper_ingot",
                        "#c:ingots/copper",
                        "minecraft:amethyst_shard",
                        "#minecraft:planks",
                        "minecraft:oak_planks",
                        "#minecraft:logs",
                        "minecraft:oak_log",
                        "minecraft:stripped_birch_wood",
                        "#minecraft:wooden_slabs",
                        "minecraft:stick")) {
            assertTrue(ArcaneData.PRE_IRON.matcher(ok).matches(), ok);
        }
        for (String no : List.of("minecraft:iron_ingot", "minecraft:sticky_piston", "#c:ingots")) {
            assertFalse(ArcaneData.PRE_IRON.matcher(no).matches(), no);
        }
    }

    @Test
    void theWellspringBoundFollowsTheData() throws IOException {
        assertEquals(32, ArcaneData.wellspring(dir).get("vftv:aer"));
        Files.createDirectories(dir.resolve("nodes"));
        Files.writeString(
                dir.resolve("nodes/types.json"),
                "{\"schema\": \"vftv:node_types/1\", \"types\": {\"vftv:wellspring\": {\"aspects\":"
                        + " [{\"aspect\": \"vftv:aer\", \"range\": [20, 20]}]}}}");
        Map<String, Integer> bound = ArcaneData.wellspring(dir);
        assertEquals(Map.of("vftv:aer", 20), bound);
        Schema tight = ArcaneData.schema(bound);
        List<String> found =
                Schema.check(
                        json(
                                "{'schema': 'vftv:arcane_recipes/1', 'recipes': {'vftv:bell': {"
                                        + BELL
                                        + "}}}"),
                        Map.of(tight.id(), tight));
        assertTrue(
                found.contains("$.recipes.vftv:bell.vis.vftv:ordo: not in the Wellspring"),
                found.toString());
    }

    private static List<String> check(String body) {
        return ArcaneBodies.check(
                "vftv:x",
                json("{" + body + "}"),
                ArcaneTestData.registries(),
                Set.of("vftv:test_entry"));
    }

    @Test
    void bodiesMeetTheRecipeTypesRules() {
        // The test lookup, like datagen's, binds no tags: a tag ingredient still checks.
        String valid = BELL.replace("vftv:arcane_crafting", "vftv:test_entry");
        assertEquals(List.of(), check(valid));
        assertEquals(
                List.of("recipe vftv:x: exactly one of pattern+key or ingredients is required"),
                check(valid + ", 'ingredients': ['minecraft:stick']"));
        assertTrue(
                check(valid.replace("minecraft:copper_ingot", "minecraft:no_such_item"))
                        .getFirst()
                        .startsWith("recipe vftv:x: "));
        assertEquals(
                List.of("recipe vftv:x: unknown research entry vftv:arcane_crafting"), check(BELL));
        assertEquals(
                List.of("recipe vftv:x: vftv:sonus is not a vis aspect"),
                check(valid.replace("vftv:terra", "vftv:sonus")));
        assertTrue(
                check(valid.replace("'count': 1", "'count': 99"))
                        .toString()
                        .contains("empty stack"));
    }

    @Test
    void wideningWritesConventionTags() {
        JsonObject body = json("{" + BELL + ", 'x': ['minecraft:copper_ingot']}");
        JsonObject widened = ArcaneBodies.widen(body);
        assertEquals(
                json(
                        "{'s': '#minecraft:wooden_slabs', 'c': '#c:ingots/copper',"
                                + " 'a': '#c:gems/amethyst'}"),
                widened.get("key"));
        assertEquals(body.get("x"), widened.get("x"), "only ingredients widen");
        assertEquals(body.get("vis"), widened.get("vis"));
        assertEquals("minecraft:copper_ingot", body.getAsJsonObject("key").get("c").getAsString());
        JsonObject shapeless =
                ArcaneBodies.widen(
                        json("{'ingredients': ['minecraft:redstone', 'minecraft:stick']}"));
        assertEquals(json("{'ingredients': ['#c:dusts/redstone', 'minecraft:stick']}"), shapeless);
        assertEquals(13, ArcaneBodies.WIDENED.size());
    }
}
