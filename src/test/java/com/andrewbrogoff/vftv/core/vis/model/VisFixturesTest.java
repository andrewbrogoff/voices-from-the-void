package com.andrewbrogoff.vftv.core.vis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Replays {@code content/nodes/fixtures.json} (wands spec, "Fixtures") against the model code: the
 * region, ring, quote and regeneration tables, which an independent reference computed. The pulse
 * table is replayed by {@code RelayModelTest}; the coverage table is checked against the biome tags
 * by a GameTest.
 */
class VisFixturesTest {
    private static final JsonObject FIXTURES = load("content/nodes/fixtures.json");
    private static final FluxRules FLUX =
            FluxTuning.rules(FluxTuning.defaults(), true, true, false);

    static JsonObject load(String path) {
        Path file = Path.of(System.getProperty("vftv.projectDir", "."), path);
        try {
            return StrictJson.parse(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<JsonObject> rows(String section) {
        List<JsonObject> rows = new ArrayList<>();
        FIXTURES.getAsJsonArray(section).forEach(row -> rows.add(row.getAsJsonObject()));
        assertFalse(rows.isEmpty(), section);
        return rows;
    }

    static long hex(JsonElement e) {
        return new BigInteger(e.getAsString().substring(2), 16).longValue();
    }

    private static SortedMap<String, Integer> amounts(JsonObject o) {
        SortedMap<String, Integer> m = new TreeMap<>();
        o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsInt()));
        return m;
    }

    @Test
    void region() {
        for (JsonObject row : rows("region")) {
            JsonArray cell = row.getAsJsonArray("cell");
            JsonArray height = row.getAsJsonArray("height");
            JsonObject expect = row.getAsJsonObject("expect");
            NaturalSpread.Placement p =
                    new NaturalSpread.Placement(
                            row.get("chance").getAsDouble(),
                            height.get(0).getAsInt(),
                            height.get(1).getAsInt(),
                            8);
            long seed = hex(row.get("seed"));
            NaturalSpread.Candidate c =
                    NaturalSpread.candidate(
                            seed, cell.get(0).getAsInt(), cell.get(1).getAsInt(), p);
            String where = row.toString();
            assertEquals(hex(expect.get("h")), c.h(), "h " + where);
            assertEquals(hex(expect.get("d0")), Hashes.at(c.h(), 1), "d0 " + where);
            assertEquals(expect.get("spawn").getAsBoolean(), c.spawns(), "spawn " + where);
            assertEquals(expect.getAsJsonArray("column").get(0).getAsInt(), c.x(), where);
            assertEquals(expect.getAsJsonArray("column").get(1).getAsInt(), c.z(), where);
            assertEquals(expect.get("offset").getAsInt(), c.offset(), where);
            List<NaturalSpread.Eligible> eligible = new ArrayList<>();
            for (JsonElement e : row.getAsJsonArray("eligible")) {
                JsonObject t = e.getAsJsonObject();
                SortedMap<String, int[]> aspects = new TreeMap<>();
                for (JsonElement a : t.getAsJsonArray("aspects")) {
                    JsonArray triple = a.getAsJsonArray();
                    aspects.put(
                            triple.get(0).getAsString(),
                            new int[] {triple.get(1).getAsInt(), triple.get(2).getAsInt()});
                }
                List<Integer> clarity = new ArrayList<>();
                t.getAsJsonArray("clarity").forEach(w -> clarity.add(w.getAsInt()));
                eligible.add(
                        new NaturalSpread.Eligible(
                                t.get("type").getAsString(),
                                t.get("weight").getAsInt(),
                                aspects,
                                clarity));
            }
            NaturalSpread.Roll roll = NaturalSpread.roll(c.h(), eligible);
            assertEquals(expect.get("type").getAsString(), roll.type(), where);
            assertEquals(
                    List.of("faint", "steady", "clear")
                            .indexOf(expect.get("clarity").getAsString()),
                    roll.clarity(),
                    where);
            assertEquals(amounts(expect.getAsJsonObject("maxes")), roll.maxes(), where);
        }
    }

    @Test
    void ring() {
        for (JsonObject row : rows("ring")) {
            int k = row.get("k").getAsInt();
            int start = StarterRing.start(hex(row.get("seed")), k);
            assertEquals(row.get("start").getAsInt(), start, row.toString());
            int[] first = StarterRing.column(k, start);
            int[] second = StarterRing.column(k, (start + 1) % (8 * k));
            assertEquals(row.getAsJsonArray("first").get(0).getAsInt(), first[0]);
            assertEquals(row.getAsJsonArray("first").get(1).getAsInt(), first[1]);
            assertEquals(row.getAsJsonArray("second").get(0).getAsInt(), second[0]);
            assertEquals(row.getAsJsonArray("second").get(1).getAsInt(), second[1]);
        }
    }

    @Test
    void quote() {
        for (JsonObject row : rows("quote")) {
            double discount;
            if (row.has("discount_modifiers")) {
                discount = 0;
                for (JsonElement m : row.getAsJsonArray("discount_modifiers")) {
                    discount += m.getAsDouble(); // summed as double, as the attribute does
                }
            } else {
                discount = row.get("discount").getAsDouble();
            }
            SortedMap<String, Integer> base = amounts(row.getAsJsonObject("base"));
            QuoteMath.Result r =
                    QuoteMath.quote(
                            new QuoteMath.Inputs(
                                    base,
                                    row.get("cap").getAsDouble(),
                                    discount,
                                    row.get("max_discount").getAsDouble(),
                                    row.get("w").getAsDouble(),
                                    0.50,
                                    4.00,
                                    VisKind.valueOf(
                                            row.get("kind").getAsString().toUpperCase(Locale.ROOT)),
                                    FLUX,
                                    1000,
                                    new TreeMap<>(),
                                    false));
            JsonObject expect = row.getAsJsonObject("expect");
            String where = row.toString();
            assertEquals(expect.get("cap").getAsInt(), r.cap(), where);
            assertEquals(expect.get("discount").getAsInt(), r.discount(), where);
            assertEquals(expect.get("warp").getAsInt(), r.warp(), where);
            assertEquals(expect.get("m").getAsInt(), r.m(), where);
            assertEquals(amounts(expect.getAsJsonObject("effective")), r.effective(), where);
            assertEquals(expect.get("discount_capped").getAsBoolean(), r.discountCapped(), where);
            assertEquals(expect.get("clamped").getAsBoolean(), r.clamped(), where);
            assertEquals(expect.get("spill").getAsInt(), r.spill(), where);
        }
    }

    @Test
    void regen() {
        for (JsonObject row : rows("regen")) {
            int[] max = {row.get("max").getAsInt()};
            int[] current = {row.get("current").getAsInt()};
            long tick =
                    Regen.settle(
                            max,
                            current,
                            hex(row.get("tick")),
                            hex(row.get("now")),
                            row.get("interval").getAsInt(),
                            row.get("divisor").getAsInt());
            JsonObject expect = row.getAsJsonObject("expect");
            assertEquals(expect.get("current").getAsInt(), current[0], row.toString());
            assertEquals(hex(expect.get("tick")), tick, row.toString());
        }
    }

    @Test
    void everySectionPresentAndCoverageListsEveryBiomeOnce() {
        for (String section : List.of("region", "ring", "quote", "regen", "pulse", "coverage")) {
            assertTrue(FIXTURES.has(section), section);
        }
        Set<String> biomes = new HashSet<>();
        rows("coverage")
                .forEach(r -> assertTrue(biomes.add(r.get("biome").getAsString()), r.toString()));
        assertEquals(55, biomes.size(), "every vanilla overworld biome, once");
    }

    /**
     * Coverage over the shipped types: the built-in natural types together hold the six built-in
     * primals, and each primal is the main aspect of at least one natural type.
     */
    @Test
    void naturalTypesCoverThePrimals() {
        JsonObject types = load("content/nodes/types.json").getAsJsonObject("types");
        Set<String> held = new HashSet<>();
        Set<String> mains = new HashSet<>();
        for (Map.Entry<String, JsonElement> e : types.entrySet()) {
            JsonObject t = e.getValue().getAsJsonObject();
            if (!t.has("natural") || !t.get("natural").getAsBoolean()) {
                continue;
            }
            mains.add(t.get("main_aspect").getAsString());
            t.getAsJsonArray("aspects")
                    .forEach(a -> held.add(a.getAsJsonObject().get("aspect").getAsString()));
        }
        assertTrue(held.containsAll(VisRegistryRules.BUILT_IN_PRIMALS), held.toString());
        assertTrue(mains.containsAll(VisRegistryRules.BUILT_IN_PRIMALS), mains.toString());
    }
}
