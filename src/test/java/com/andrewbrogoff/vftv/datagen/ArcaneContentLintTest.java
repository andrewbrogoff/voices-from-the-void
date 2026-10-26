package com.andrewbrogoff.vftv.datagen;

import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.TestItems;
import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The arcane content lints over every built-in {@code vftv:arcane} recipe in {@code content/}
 * (arcane spec, "Tests"): Stage 1 affordability, {@code void} only behind a Stage 4 entry, no
 * remainder-bearing ingredient; and the aspects spec's non-amplification gate over the arcane round
 * trips (review obligation T12-R1).
 */
class ArcaneContentLintTest {
    private static final Path CONTENT =
            Path.of(System.getProperty("vftv.projectDir", ".")).resolve("content");

    @TempDir Path dir;

    @BeforeAll
    static void bind() {
        TestItems.bindComponents();
        // The Bell's slabs are a vanilla tag; bind vanilla's tags as datagen does, so its round
        // trip has members.
        try (MultiPackResourceManager data =
                new MultiPackResourceManager(
                        PackType.SERVER_DATA,
                        List.of(ServerPacksSource.createVanillaPackSource()))) {
            TagLoader.loadTagsForExistingRegistries(
                            data,
                            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))
                    .forEach(Registry.PendingTags::apply);
        }
    }

    private static Map<String, JsonObject> bodies() {
        Map<String, JsonObject> bodies = ArcaneBodies.bodies(CONTENT);
        assertTrue(bodies.containsKey("vftv:bell"), "the Bell is a built-in arcane recipe");
        return bodies;
    }

    private static int stage(Map<String, ResearchEntry> entries, JsonObject body) {
        ResearchEntry entry = entries.get(body.get("research").getAsString());
        return entry == null ? -1 : entry.stage();
    }

    @Test
    void everyStageOneRecipeIsAffordableFromTheWellspring() {
        Map<String, Integer> wellspring = ArcaneData.wellspring(CONTENT);
        Map<String, ResearchEntry> entries = ResearchContent.read(CONTENT).plainEntries();
        List<String> problems = new ArrayList<>();
        bodies().forEach(
                        (id, body) -> {
                            if (stage(entries, body) != 1) {
                                return;
                            }
                            if (body.has("kind")
                                    && !body.get("kind").getAsString().equals("ordinary")) {
                                problems.add(id + " is a Stage 1 recipe but not ordinary");
                            }
                            body.getAsJsonObject("vis")
                                    .entrySet()
                                    .forEach(
                                            e -> {
                                                int bound = wellspring.getOrDefault(e.getKey(), 0);
                                                if (e.getValue().getAsInt() > bound) {
                                                    problems.add(
                                                            id
                                                                    + ": "
                                                                    + e.getKey()
                                                                    + " over "
                                                                    + bound);
                                                }
                                            });
                        });
        assertEquals(List.of(), problems);
    }

    @Test
    void voidOnlyBehindAStageFourEntry() {
        Map<String, ResearchEntry> entries = ResearchContent.read(CONTENT).plainEntries();
        List<String> problems = new ArrayList<>();
        bodies().forEach(
                        (id, body) -> {
                            if (body.has("kind")
                                    && body.get("kind").getAsString().equals("void")
                                    && stage(entries, body) != 4) {
                                problems.add(id);
                            }
                        });
        assertEquals(List.of(), problems);
    }

    @Test
    void noBuiltInIngredientBearsARemainder() {
        List<String> problems = new ArrayList<>();
        bodies().forEach(
                        (id, body) -> {
                            List<String> cells = new ArrayList<>();
                            if (body.get("key") instanceof JsonObject key) {
                                key.asMap().values().forEach(v -> cells.add(v.getAsString()));
                            }
                            if (body.has("ingredients")) {
                                body.getAsJsonArray("ingredients")
                                        .forEach(v -> cells.add(v.getAsString()));
                            }
                            for (String cell : cells) {
                                for (String item : AspectTable.ids(cell, BuiltInRegistries.ITEM)) {
                                    ItemStack stack =
                                            new ItemStack(
                                                    BuiltInRegistries.ITEM.getValue(
                                                            Identifier.parse(item)));
                                    if (stack.getCraftingRemainder() != null) {
                                        problems.add(id + ": " + item);
                                    }
                                }
                            }
                        });
        assertEquals(List.of(), problems);
    }

    // --- non-amplification (T12-R1) -------------------------------------------------------------

    private static SortedMap<String, Integer> value(String aspect, int n) {
        return new TreeMap<>(Map.of(aspect, n));
    }

    private void write(String recipes) throws IOException {
        Files.createDirectories(dir.resolve("arcane"));
        Files.writeString(
                dir.resolve("arcane/recipes.json"),
                json("{'schema': 'vftv:arcane_recipes/1', 'recipes': {" + recipes + "}}")
                        .toString());
    }

    @Test
    void theRoundTripsHoldEveryArcaneBodyAndTheConversion() throws IOException {
        write(
                "'test:shaped': {'type': 'vftv:arcane', 'pattern': ['ab', 'a '],"
                        + " 'key': {'a': 'minecraft:stick', 'b': 'minecraft:bell'},"
                        + " 'result': {'id': 'minecraft:bell', 'count': 2}},"
                        + " 'test:crucible': {'type': 'vftv:crucible'}");
        List<Derivation.Recipe> trips = ArcaneBodies.roundTrips(dir);
        assertEquals(2, trips.size(), "the one arcane body, then the conversion");
        Derivation.Recipe shaped = trips.get(0);
        assertEquals("test:shaped", shaped.id());
        assertEquals(3, shaped.ingredients().size(), "one ingredient per occupied cell; no vis");
        assertEquals(2, shaped.count());
        Derivation.Recipe conversion = trips.get(1);
        assertEquals(
                List.of(List.of(new Derivation.Alternative("minecraft:crafting_table", null))),
                conversion.ingredients());
        assertEquals("vftv:worktable", conversion.result());
    }

    @Test
    void aGainingArcaneRoundTripFailsTheGate() throws IOException {
        // A captured recipe makes a bell (10) from ten sticks (1 each); an arcane recipe turning
        // the
        // bell back into sticks closes the cycle. One stick back loses; eleven gain.
        Map<String, SortedMap<String, Integer>> values =
                Map.of(
                        "minecraft:stick", value("vftv:arbor", 1),
                        "minecraft:bell", value("vftv:sonus", 10));
        List<List<Derivation.Alternative>> tenSticks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tenSticks.add(List.of(new Derivation.Alternative("minecraft:stick", null)));
        }
        List<Derivation.Recipe> captured =
                List.of(
                        new Derivation.Recipe(
                                "minecraft:test_bell", tenSticks, "minecraft:bell", 1, false));
        write(
                "'vftv:fair': {'type': 'vftv:arcane', 'ingredients': ['minecraft:bell'],"
                        + " 'result': {'id': 'minecraft:stick', 'count': 1}}");
        assertTrue(
                AspectTable.nonAmplification(values, captured, ArcaneBodies.roundTrips(dir))
                        .stream()
                        .noneMatch(p -> p.contains("vftv:fair")),
                "a round trip that loses passes");
        write(
                "'vftv:gaining': {'type': 'vftv:arcane', 'ingredients': ['minecraft:bell'],"
                        + " 'result': {'id': 'minecraft:stick', 'count': 11}}");
        List<String> problems =
                AspectTable.nonAmplification(values, captured, ArcaneBodies.roundTrips(dir));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().startsWith("non-amplification: vftv:gaining: 11"));
        assertFalse(
                AspectTable.nonAmplification(values, captured, List.of()).stream()
                        .anyMatch(p -> p.contains("vftv:gaining")),
                "without the arcane round trips the gate cannot see it");
    }

    /** Datagen runs the gate over these with the captured recipes; a failure fails the data run. */
    @Test
    void theShippedRoundTripsIncludeTheBellAndTheConversion() {
        List<Derivation.Recipe> trips = ArcaneBodies.roundTrips(CONTENT);
        assertTrue(trips.stream().anyMatch(t -> t.id().equals("vftv:bell")));
        assertTrue(trips.stream().anyMatch(t -> t.id().equals(ArcaneBodies.CONVERSION)));
    }
}
