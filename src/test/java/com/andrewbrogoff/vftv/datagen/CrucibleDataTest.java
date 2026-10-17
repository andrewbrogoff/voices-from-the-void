package com.andrewbrogoff.vftv.datagen;

import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The {@code content/crucible} schemas and their "Rejected:" rules. */
class CrucibleDataTest {
    private static final String VESSEL =
            "'type': 'vftv:arcane', 'research': 'vftv:crucible', 'kind': 'ordinary',"
                    + " 'ingredients': ['minecraft:cauldron', 'minecraft:amethyst_shard',"
                    + " 'minecraft:copper_ingot'],"
                    + " 'result': {'id': 'vftv:crucible', 'count': 1},"
                    + " 'vis': {'vftv:aqua': 12, 'vftv:ignis': 12, 'vftv:terra': 6}";

    private static final String BRASS =
            "'type': 'vftv:crucible', 'research': 'vftv:resonant_brass',"
                    + " 'catalyst': 'minecraft:copper_ingot',"
                    + " 'aspects': {'vftv:metallum': 4, 'vftv:sonus': 6},"
                    + " 'result': {'id': 'vftv:resonant_brass_ingot', 'count': 1}";

    private static final Schema RECIPES =
            CrucibleData.recipesSchema(ArcaneData.wellspring(Path.of("absent")));

    private static List<String> problems(String vessel, String brass) {
        return Schema.check(
                json(
                        "{'schema': 'vftv:crucible_recipes/1', 'recipes': {'vftv:crucible': {"
                                + vessel
                                + "}, 'vftv:resonant_brass_ingot': {"
                                + brass
                                + "}}}"),
                Map.of(RECIPES.id(), RECIPES));
    }

    private static void rejects(String vessel, String brass, String reason) {
        List<String> found = problems(vessel, brass);
        assertTrue(found.stream().anyMatch(p -> p.contains(reason)), found.toString());
    }

    @Test
    void theShippedRecipesPass() {
        assertEquals(List.of(), problems(VESSEL, BRASS));
    }

    @Test
    void exactlyTheTwoRecipes() {
        List<String> found =
                Schema.check(
                        json(
                                "{'schema': 'vftv:crucible_recipes/1', 'recipes': {'vftv:crucible':"
                                        + " {"
                                        + VESSEL
                                        + "}, 'vftv:other': {"
                                        + BRASS
                                        + "}}}"),
                        Map.of(RECIPES.id(), RECIPES));
        assertTrue(found.stream().anyMatch(p -> p.contains("vftv:other")), found.toString());
        assertTrue(
                found.stream().anyMatch(p -> p.contains("vftv:resonant_brass_ingot")),
                found.toString());
    }

    @Test
    void theVesselsRules() {
        rejects(
                VESSEL.replace("'vftv:crucible', 'kind'", "'vftv:x', 'kind'"),
                BRASS,
                "must be vftv:crucible");
        rejects(VESSEL.replace("'ordinary'", "'void'"), BRASS, "kind: must be ordinary");
        rejects(VESSEL.replace("'minecraft:cauldron', ", ""), BRASS, "needs a minecraft:cauldron");
        rejects(
                VESSEL.replace("minecraft:amethyst_shard", "vftv:resonant_brass_ingot"),
                BRASS,
                "not a minecraft: item");
        rejects(
                VESSEL.replace("'vftv:terra': 6", "'vftv:terra': 33"),
                BRASS,
                "over the Wellspring");
        rejects(
                VESSEL.replace("'vftv:terra': 6", "'vftv:sonus': 6"),
                BRASS,
                "not a built-in primal");
        rejects(
                VESSEL.replace("'id': 'vftv:crucible'", "'id': 'minecraft:cauldron'"),
                BRASS,
                "result.id");
    }

    @Test
    void theBrassRules() {
        rejects(
                VESSEL,
                BRASS.replace("vftv:resonant_brass'", "vftv:crucible'"),
                "must be vftv:resonant_brass");
        rejects(
                VESSEL,
                BRASS.replace("minecraft:copper_ingot", "minecraft:iron_ingot"),
                "catalyst");
        rejects(
                VESSEL,
                BRASS.replace("'vftv:sonus': 6", "'vftv:sonus': 6, 'vftv:aer': 1, 'vftv:aqua': 1"),
                "at most 3 aspects");
        rejects(VESSEL, BRASS.replace("'vftv:sonus': 6", "'vftv:sonus': 47"), "over a quarter");
        rejects(VESSEL, BRASS.replace("'count': 1}", "'count': 2}"), "result.count");
        rejects(VESSEL, BRASS.replace("vftv:sonus", "vftv:nowhere"), "not a built-in aspect");
        rejects(
                VESSEL,
                BRASS.replace("'aspects'", "'group': 'x', 'aspects'"),
                "group: unknown field");
    }

    private static List<String> dosePlan(String doses) {
        return Schema.check(
                json(
                        "{'schema': 'vftv:crucible_dose_plan/1', 'recipe':"
                                + " 'vftv:resonant_brass_ingot', 'doses': ["
                                + doses
                                + "]}"),
                Map.of(CrucibleData.DOSE_PLAN.id(), CrucibleData.DOSE_PLAN));
    }

    @Test
    void theDosePlanRules() {
        assertEquals(
                List.of(), dosePlan("{'item': 'minecraft:raw_copper', 'count': 2, 'why': 'ore'}"));
        assertTrue(dosePlan("").stream().anyMatch(p -> p.contains("doses")));
        assertTrue(
                dosePlan("{'item': 'vftv:crucible', 'count': 2, 'why': 'x'}").stream()
                        .anyMatch(p -> p.contains("not a minecraft: item")));
        assertTrue(
                dosePlan("{'item': 'minecraft:raw_copper', 'count': 2, 'why': ' '}").stream()
                        .anyMatch(p -> p.contains("empty why")));
        assertTrue(
                dosePlan("{'item': 'minecraft:raw_copper', 'count': 65, 'why': 'x'}").stream()
                        .anyMatch(p -> p.contains("count")));
        JsonObject other =
                json(
                        "{'schema': 'vftv:crucible_dose_plan/1', 'recipe': 'vftv:crucible', 'doses':"
                                + " [{'item': 'minecraft:raw_copper', 'count': 1, 'why': 'x'}]}");
        assertTrue(
                Schema.check(other, Map.of(CrucibleData.DOSE_PLAN.id(), CrucibleData.DOSE_PLAN))
                        .stream()
                        .anyMatch(p -> p.contains("must be vftv:resonant_brass_ingot")));
    }

    @Test
    void theCatalystIsWidened() {
        assertEquals(
                "#c:ingots/copper",
                CrucibleData.widen(json("{" + BRASS + "}")).get("catalyst").getAsString());
    }
}
