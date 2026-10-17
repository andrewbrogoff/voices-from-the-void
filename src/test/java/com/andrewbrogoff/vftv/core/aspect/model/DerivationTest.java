package com.andrewbrogoff.vftv.core.aspect.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class DerivationTest {
    private static final Derivation.Tuning TUNING =
            new Derivation.Tuning(0.75, new TreeMap<>(Map.of("vftv:ignis", 1)));

    private static SortedMap<String, Integer> v(Object... pairs) {
        SortedMap<String, Integer> out = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put("vftv:" + pairs[i], (Integer) pairs[i + 1]);
        }
        return out;
    }

    private static Derivation.Alternative alt(String item) {
        return new Derivation.Alternative(item, null);
    }

    private static Derivation.Recipe recipe(String id, String result, int count, String... items) {
        List<List<Derivation.Alternative>> ingredients = new ArrayList<>();
        for (String item : items) {
            ingredients.add(List.of(alt(item)));
        }
        return new Derivation.Recipe(id, ingredients, result, count, false);
    }

    private static Derivation.Result derive(
            Map<String, SortedMap<String, Integer>> resolved, Derivation.Recipe... recipes) {
        return Derivation.derive(resolved, id -> !id.startsWith("v:"), List.of(recipes), TUNING);
    }

    @Test
    void factorDividesByCountAndKeepsOneOfEveryAspect() {
        var r =
                derive(
                        Map.of("v:log", v("arbor", 4, "terra", 1)),
                        recipe("m:planks", "m:planks", 4, "v:log"));
        // 4 * 0.75 / 4 = 0.75 -> 1 (kept); 1 * 0.75 / 4 -> 1 (kept).
        assertEquals(v("arbor", 1, "terra", 1), r.values().get("m:planks"));
    }

    @Test
    void roundsDown() {
        var r = derive(Map.of("v:a", v("terra", 7)), recipe("m:x", "m:x", 1, "v:a", "v:a"));
        assertEquals(v("terra", 10), r.values().get("m:x"), "14 * 0.75 = 10.5 rounds down");
    }

    @Test
    void smeltingAddsTheBonusAfterRounding() {
        var smelt =
                new Derivation.Recipe("m:s", List.of(List.of(alt("v:ore"))), "m:ingot", 1, true);
        var r = derive(Map.of("v:ore", v("metallum", 4)), smelt);
        assertEquals(v("metallum", 3, "ignis", 1), r.values().get("m:ingot"));
    }

    @Test
    void remaindersAreSubtractedTimesTheirCountAndClamped() {
        var withBucket =
                new Derivation.Alternative("v:milk", new Derivation.Remainder("v:bucket", 2));
        var r =
                derive(
                        Map.of(
                                "v:milk", v("aqua", 8, "metallum", 5, "victus", 2),
                                "v:bucket", v("metallum", 3)),
                        new Derivation.Recipe(
                                "m:r", List.of(List.of(withBucket)), "m:cake", 1, false));
        // metallum 5 - 2*3 clamps to 0: only aqua 8 and victus 2 remain.
        assertEquals(v("aqua", 6, "victus", 1), r.values().get("m:cake"));
    }

    @Test
    void anEmptyRemainderSubtractsNothing() {
        var alt = new Derivation.Alternative("v:a", new Derivation.Remainder("v:empty", 1));
        var r =
                derive(
                        Map.of("v:a", v("aqua", 4), "v:empty", v()),
                        new Derivation.Recipe("m:r", List.of(List.of(alt)), "m:x", 1, false));
        assertEquals(v("aqua", 3), r.values().get("m:x"));
        assertEquals(0, r.unvaluedRemainders());
    }

    @Test
    void anUnvaluedRemainderRejectsTheRecipeBeforeTheGraph() {
        var alt = new Derivation.Alternative("v:a", new Derivation.Remainder("m:candidate", 1));
        var r =
                derive(
                        Map.of("v:a", v("aqua", 4), "v:b", v("terra", 1)),
                        new Derivation.Recipe("m:r", List.of(List.of(alt)), "m:x", 1, false),
                        recipe("m:c", "m:candidate", 1, "v:b"));
        assertEquals(1, r.unvaluedRemainders());
        assertFalse(r.values().containsKey("m:x"));
        assertTrue(r.values().containsKey("m:candidate"));
    }

    @Test
    void anAlternativeTakesTheLowestTotalThenTheLowestId() {
        var ingredient = List.of(alt("v:b"), alt("v:c"), alt("v:d"), alt("v:none"));
        var r =
                derive(
                        Map.of(
                                "v:b", v("aer", 2),
                                "v:c", v("terra", 1),
                                "v:d", v("ordo", 1)),
                        new Derivation.Recipe("m:r", List.of(ingredient), "m:x", 1, false));
        assertEquals(v("terra", 1), r.values().get("m:x"), "c and d tie at 1; c's id is lower");
    }

    @Test
    void severalRecipesTakeTheLowestResultThenTheLowestRecipeId() {
        var r =
                derive(
                        Map.of("v:a", v("aer", 8), "v:b", v("terra", 4), "v:c", v("ordo", 4)),
                        recipe("m:3", "m:x", 1, "v:a"),
                        recipe("m:2", "m:x", 1, "v:c"),
                        recipe("m:1", "m:x", 1, "v:b"));
        assertEquals(v("terra", 3), r.values().get("m:x"));
    }

    @Test
    void keepsTheTopFiveByAmountThenId() {
        assertEquals(
                v("a", 5, "b", 5, "c", 4, "d", 4, "e", 3),
                Derivation.top(v("a", 5, "b", 5, "c", 4, "d", 4, "e", 3, "f", 3, "g", 1)));
        var r =
                derive(
                        Map.of("v:a", v("a", 4, "b", 4, "c", 4, "d", 4, "e", 4), "v:b", v("f", 8)),
                        recipe("m:r", "m:x", 1, "v:a", "v:b"));
        assertEquals(v("a", 3, "b", 3, "c", 3, "d", 3, "f", 6), r.values().get("m:x"));
    }

    @Test
    void anInversePairIsExcluded() {
        var r =
                derive(
                        Map.of("v:seed", v("herba", 4)),
                        recipe("m:1", "m:a", 1, "v:seed"),
                        recipe("m:2", "m:b", 1, "m:a"),
                        recipe("m:3", "m:a", 1, "m:b"));
        assertEquals(List.of("m:a", "m:b"), r.excluded().get("m:a"));
        assertEquals(List.of("m:a", "m:b"), r.excluded().get("m:b"));
        assertTrue(r.values().isEmpty());
    }

    @Test
    void aThreeStepLoopIsExcludedThoughNoPairInvertsIt() {
        var r =
                derive(
                        Map.of("v:seed", v("herba", 4)),
                        recipe("m:1", "m:a", 1, "v:seed"),
                        recipe("m:2", "m:b", 2, "m:a"),
                        recipe("m:3", "m:c", 2, "m:b", "m:b"),
                        recipe("m:4", "m:a", 2, "m:c", "m:c"));
        assertEquals(3, r.excluded().size());
    }

    @Test
    void aSelfEdgeIsExcluded() {
        var r =
                derive(
                        Map.of("v:dye", v("herba", 1)),
                        recipe("m:1", "m:wool", 1, "m:wool", "v:dye"));
        assertEquals(List.of("m:wool"), r.excluded().get("m:wool"));
    }

    @Test
    void aCandidateFedOnlyByAnExcludedOneIsUnresolvedWithThatReason() {
        var r =
                derive(
                        Map.of("v:seed", v("herba", 4)),
                        recipe("m:1", "m:a", 1, "v:seed", "m:b"),
                        recipe("m:2", "m:b", 1, "m:a"),
                        recipe("m:3", "m:down", 1, "m:a"));
        assertEquals(
                new Derivation.Blocked("m:a", Derivation.Reason.EXCLUDED),
                r.unresolved().get("m:down"));
    }

    @Test
    void blockersAreUnresolvedOrHaveNoValue() {
        var r =
                derive(
                        Map.of(),
                        recipe("m:1", "m:a", 1, "v:nothing"),
                        recipe("m:2", "m:b", 1, "m:a"));
        assertEquals(
                new Derivation.Blocked("v:nothing", Derivation.Reason.NO_VALUE),
                r.unresolved().get("m:a"));
        assertEquals(
                new Derivation.Blocked("m:a", Derivation.Reason.UNRESOLVED),
                r.unresolved().get("m:b"));
        assertEquals("unresolved", Derivation.Reason.UNRESOLVED.word());
        assertEquals("excluded", Derivation.Reason.EXCLUDED.word());
        assertEquals("no value", Derivation.Reason.NO_VALUE.word());
    }

    @Test
    void anEmptyDerivedValueIsCountedNotEmittedAndHasNoValueDownstream() {
        var r =
                derive(
                        Map.of("v:nothing", v()),
                        recipe("m:1", "m:a", 1, "v:nothing"),
                        recipe("m:2", "m:b", 1, "m:a"));
        assertTrue(r.empty().contains("m:a"));
        assertFalse(r.values().containsKey("m:a"));
        assertEquals(
                new Derivation.Blocked("m:a", Derivation.Reason.NO_VALUE),
                r.unresolved().get("m:b"));
    }

    @Test
    void emptyAndDefaultsResolvedItemsAreNeverCandidates() {
        var r =
                derive(
                        Map.of(
                                "v:a",
                                v("terra", 4),
                                "m:suppressed",
                                v(),
                                "m:defaulted",
                                v("ordo", 1)),
                        recipe("m:1", "m:suppressed", 1, "v:a"),
                        recipe("m:2", "m:defaulted", 1, "v:a"));
        assertEquals(0, r.candidates());
        assertTrue(r.values().isEmpty());
    }

    @Test
    void aDiamondEvaluatesInIdOrderWhateverTheInputOrder() {
        List<Derivation.Recipe> recipes =
                new ArrayList<>(
                        List.of(
                                recipe("m:1", "m:b", 1, "v:a"),
                                recipe("m:2", "m:c", 1, "v:a"),
                                recipe("m:3", "m:d", 1, "m:b", "m:c")));
        var expected = Derivation.derive(Map.of("v:a", v("terra", 8)), id -> true, recipes, TUNING);
        Collections.reverse(recipes);
        var reversed = Derivation.derive(Map.of("v:a", v("terra", 8)), id -> true, recipes, TUNING);
        assertEquals(expected, reversed);
        assertEquals(v("terra", 9), expected.values().get("m:d"));
    }

    @Test
    void theAdmitPredicateLimitsCandidates() {
        var r =
                Derivation.derive(
                        Map.of("v:a", v("terra", 4)),
                        id -> id.startsWith("mod:"),
                        List.of(
                                recipe("m:1", "minecraft:x", 1, "v:a"),
                                recipe("m:2", "mod:y", 1, "v:a")),
                        TUNING);
        assertEquals(1, r.candidates());
        assertTrue(r.values().containsKey("mod:y"));
    }

    @Test
    void theLimitsRefuseAtExactlyLimitPlusOne() {
        assertFalse(Derivation.overLimit(Derivation.MAX_RECIPES, Derivation.MAX_EDGES));
        assertTrue(Derivation.overLimit(Derivation.MAX_RECIPES + 1L, 0));
        assertTrue(Derivation.overLimit(0, Derivation.MAX_EDGES + 1));
        assertEquals(
                3,
                Derivation.edges(
                        List.of(
                                new Derivation.Recipe(
                                        "m:r",
                                        List.of(List.of(alt("a"), alt("b")), List.of(alt("c"))),
                                        "x",
                                        1,
                                        false))));
    }

    @Test
    void tarjanCopesWithAHundredThousandNodeChain() {
        List<Derivation.Recipe> chain = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            chain.add(recipe("m:r" + i, "m:i" + (i + 1), 1, "m:i" + i));
        }
        chain.add(recipe("m:back", "m:i0", 1, "m:i100000"));
        var components = Derivation.Graph.of(chain).cyclicComponents();
        assertEquals(1, components.size());
        assertEquals(100_001, components.getFirst().size());
    }

    @Test
    void amplificationIsFoundOnCyclesOnly() {
        Map<String, SortedMap<String, Integer>> values =
                Map.of(
                        "n", v("metallum", 1),
                        "i", v("metallum", 9),
                        "b", v("metallum", 82),
                        "x", v("metallum", 100));
        List<Derivation.Recipe> recipes =
                List.of(
                        recipe("m:i_from_n", "i", 1, "n", "n", "n", "n", "n", "n", "n", "n", "n"),
                        recipe("m:n_from_i", "n", 9, "i"),
                        recipe("m:b_from_i", "b", 1, "i", "i", "i", "i", "i", "i", "i", "i", "i"),
                        recipe("m:i_from_b", "i", 9, "b"),
                        recipe("m:x_from_n", "x", 1, "n"));
        List<String> gains = Derivation.amplifying(values, recipes);
        assertEquals(1, gains.size(), gains.toString());
        assertTrue(gains.getFirst().startsWith("m:b_from_i"), gains.toString());
    }

    @Test
    void amplificationSkipsRecipesWithAnUnvaluedIngredient() {
        Map<String, SortedMap<String, Integer>> values = Map.of("a", v("terra", 5));
        assertTrue(
                Derivation.amplifying(
                                values,
                                List.of(recipe("m:1", "a", 1, "b"), recipe("m:2", "b", 1, "a")))
                        .isEmpty());
    }

    @Test
    void evaluateIsNullWithoutAValuedAlternative() {
        assertNull(
                Derivation.evaluate(
                        recipe("m:1", "x", 1, "a"),
                        new Derivation.Lookup(Map.of(), new TreeMap<>()),
                        TUNING));
    }

    @Test
    void totalsAreExact() {
        assertEquals(
                Integer.MAX_VALUE + 1L, Derivation.total(Map.of("a", Integer.MAX_VALUE, "b", 1)));
    }

    @Test
    void recipesCopyTheirIngredients() {
        List<List<Derivation.Alternative>> ingredients = new ArrayList<>();
        ingredients.add(new ArrayList<>(List.of(alt("a"))));
        var r = new Derivation.Recipe("m:1", ingredients, "x", 1, false);
        ingredients.getFirst().add(alt("b"));
        assertEquals(1, r.ingredients().getFirst().size());
        assertThrows(UnsupportedOperationException.class, () -> r.ingredients().add(List.of()));
    }
}
