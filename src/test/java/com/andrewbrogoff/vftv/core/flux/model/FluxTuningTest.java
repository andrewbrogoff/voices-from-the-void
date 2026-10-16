package com.andrewbrogoff.vftv.core.flux.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FluxTuningTest {
    /** The Hush spec's "Tuning and limits" table, in key order: key, default, min, max. */
    private static final List<Object[]> TABLE =
            List.of(
                    new Object[] {"flux.essentia_pm", 1000, 0, 1000},
                    new Object[] {"flux.tax_pm", 1000, 0, 1000},
                    new Object[] {"flux.void_pm", 250, 0, 1000},
                    new Object[] {"hush.onset", 50, 0, 100_000},
                    new Object[] {"hush.block_cost", 2, 1, 10_000},
                    new Object[] {"hush.node_cost", 50, 1, 100_000},
                    new Object[] {"hush.interval", 10, 1, 1200},
                    new Object[] {"hush.walk", 32, 1, 64},
                    new Object[] {"hush.radius", 12, 1, 16},
                    new Object[] {"hush.max_misses", 32, 1, 255},
                    new Object[] {"hush.player_chunks", 8, 1, 32},
                    new Object[] {"hollow.every", 8, 1, 1000},
                    new Object[] {"hollow.flux", 25, 0, 100_000},
                    new Object[] {"hollow.max_near", 3, 0, 16},
                    new Object[] {"hollow.min_player_distance", 8, 0, 32},
                    new Object[] {"bell.flux", 50, 0, 100_000},
                    new Object[] {"bell.restore", 8, 0, 64},
                    new Object[] {"bell.radius", 5, 1, 8},
                    new Object[] {"bell.cooldown", 40, 0, 1200},
                    new Object[] {"peal.interval", 200, 20, 24_000},
                    new Object[] {"peal.flux", 50, 0, 100_000},
                    new Object[] {"peal.restore", 8, 0, 64},
                    new Object[] {"peal.radius", 12, 1, 16},
                    new Object[] {"peal.scan", 1024, 1, 4096},
                    new Object[] {"peal.capacity", 64, 1, 1000},
                    new Object[] {"peal.suction", 16, 0, 128});

    @Test
    void theKeysAreTheSpecTable() {
        assertEquals(TABLE.size(), FluxTuning.KEYS.size());
        for (int i = 0; i < TABLE.size(); i++) {
            Object[] row = TABLE.get(i);
            FluxTuning.Field key = FluxTuning.KEYS.get(i);
            assertEquals(row[0], key.name());
            assertEquals(row[1], key.fallback(), key.name());
            assertEquals(row[2], key.min(), key.name());
            assertEquals(row[3], key.max(), key.name());
        }
    }

    @Test
    void theDefaultRulesCarryEveryDefault() {
        FluxRules rules = FluxTuning.rules(FluxTuning.defaults(), true, true, false);
        int[] values = FluxTuning.values(rules);
        assertEquals(FluxTuning.FORMAT, rules.format());
        for (int i = 0; i < TABLE.size(); i++) {
            assertEquals(TABLE.get(i)[1], values[i + 1], TABLE.get(i)[0].toString());
        }
        assertTrue(rules.spread());
        assertTrue(rules.hollows());
        assertFalse(rules.unattended());
        assertEquals(rules, FluxTuning.fromValues(values), "values() and fromValues() agree");
    }

    @Test
    void eachAccessorReadsItsKey() {
        int[] tuning = new int[TABLE.size()];
        for (int i = 0; i < tuning.length; i++) {
            tuning[i] = (Integer) TABLE.get(i)[3]; // every key at its maximum: all distinct enough
        }
        FluxRules rules = FluxTuning.rules(tuning, false, true, false);
        assertEquals(1000, rules.fluxEssentiaPm());
        assertEquals(1000, rules.fluxTaxPm());
        assertEquals(1000, rules.fluxVoidPm());
        assertEquals(100_000, rules.hushOnset());
        assertEquals(10_000, rules.hushBlockCost());
        assertEquals(100_000, rules.hushNodeCost());
        assertEquals(1200, rules.hushInterval());
        assertEquals(64, rules.hushWalk());
        assertEquals(16, rules.hushRadius());
        assertEquals(255, rules.hushMaxMisses());
        assertEquals(32, rules.hushPlayerChunks());
        assertEquals(1000, rules.hollowEvery());
        assertEquals(100_000, rules.hollowFlux());
        assertEquals(16, rules.hollowMaxNear());
        assertEquals(32, rules.hollowMinPlayerDistance());
        assertEquals(100_000, rules.bellFlux());
        assertEquals(64, rules.bellRestore());
        assertEquals(8, rules.bellRadius());
        assertEquals(1200, rules.bellCooldown());
        assertEquals(24_000, rules.pealInterval());
        assertEquals(100_000, rules.pealFlux());
        assertEquals(64, rules.pealRestore());
        assertEquals(16, rules.pealRadius());
        assertEquals(4096, rules.pealScan());
        assertEquals(1000, rules.pealCapacity());
        assertEquals(128, rules.pealSuction());
        assertFalse(rules.spread());
        assertTrue(rules.hollows());
        assertFalse(rules.unattended());
    }

    /** {@code fold([FLUX, format, every value in key order, switches as 0 or 1])}. */
    @Test
    void theGenerationFoldsTheSaltFormatValuesAndSwitches() {
        int[] tuning = FluxTuning.defaults();
        long[] words = new long[1 + 1 + tuning.length + 3];
        words[0] = 0x464C5558L;
        words[1] = 1;
        for (int i = 0; i < tuning.length; i++) {
            words[i + 2] = tuning[i];
        }
        words[words.length - 3] = 1;
        words[words.length - 2] = 0;
        words[words.length - 1] = 1;
        assertEquals(Salts.word(Salts.FLUX), words[0]);
        assertEquals(Hashes.fold(words), FluxTuning.rules(tuning, true, false, true).generation());
    }

    @Test
    void anyChangeMakesANewGeneration() {
        Set<Long> generations = new HashSet<>();
        int[] tuning = FluxTuning.defaults();
        generations.add(FluxTuning.rules(tuning, true, true, false).generation());
        for (int i = 0; i < tuning.length; i++) {
            int[] changed = tuning.clone();
            FluxTuning.Field key = FluxTuning.KEYS.get(i);
            changed[i] = changed[i] == key.max() ? key.min() : changed[i] + 1;
            assertTrue(generations.add(FluxTuning.rules(changed, true, true, false).generation()));
        }
        assertTrue(generations.add(FluxTuning.rules(tuning, false, true, false).generation()));
        assertTrue(generations.add(FluxTuning.rules(tuning, true, false, false).generation()));
        assertTrue(generations.add(FluxTuning.rules(tuning, true, true, true).generation()));
        assertEquals(
                FluxTuning.rules(tuning, true, true, false),
                FluxTuning.rules(FluxTuning.defaults(), true, true, false),
                "equal rules share a generation");
    }

    @Test
    void anEmptyFileIsEveryDefault() {
        assertArrayEquals(FluxTuning.defaults(), FluxTuning.parse("{}"));
    }

    @Test
    void aKeyOverridesItsDefaultAlone() {
        int[] values = FluxTuning.parse("{\"hush.walk\": 64, \"flux.void_pm\": 0}");
        int[] expected = FluxTuning.defaults();
        expected[7] = 64;
        expected[2] = 0;
        assertArrayEquals(expected, values);
        assertEquals(1000, FluxTuning.parse("{\"flux.tax_pm\": 1000.0}")[1], "an integral number");
    }

    @Test
    void everyKeyAcceptsItsRangeAndRejectsItsNeighbours() {
        for (FluxTuning.Field key : FluxTuning.KEYS) {
            assertEquals(key.min(), only(key, key.min()));
            assertEquals(key.max(), only(key, key.max()));
            assertRejected("{\"" + key.name() + "\": " + (key.min() - 1) + "}", "outside");
            assertRejected("{\"" + key.name() + "\": " + (key.max() + 1L) + "}", "outside");
        }
        assertRejected("{\"hush.walk\": 99999999999999999999}", "outside");
    }

    private static int only(FluxTuning.Field key, int value) {
        return FluxTuning.parse("{\"" + key.name() + "\": " + value + "}")[
                FluxTuning.KEYS.indexOf(key)];
    }

    @Test
    void invalidFilesAreRejected() {
        assertRejected("{\"hush.wlak\": 3}", "unknown key");
        assertRejected("{\"hush.walk\": \"3\"}", "not an integer");
        assertRejected("{\"hush.walk\": true}", "not an integer");
        assertRejected("{\"hush.walk\": null}", "not an integer");
        assertRejected("{\"hush.walk\": 3.5}", "not an integer");
        assertRejected("{\"hush\": {\"walk\": 3}}", "unknown key");
        assertRejected("[]", "not a JSON object");
        assertRejected("{\"hush.walk\": 3,}", "malformed JSON");
        assertRejected("{\"hush.walk\": 3, \"hush.walk\": 4}", "malformed JSON");
        assertRejected("", "malformed JSON");
    }

    @Test
    void everyProblemIsNamed() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> FluxTuning.parse("{\"a\": 1, \"hush.walk\": 0}"));
        String message = String.valueOf(e.getMessage());
        assertTrue(message.contains("a: unknown key"), message);
        assertTrue(message.contains("hush.walk: 0 outside 1..64"), message);
    }

    private static void assertRejected(String json, String why) {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> FluxTuning.parse(json), json);
        String message = String.valueOf(e.getMessage());
        assertTrue(message.contains(why), json + " -> " + message);
    }

    @Test
    void fromValuesRejectsAWrongShapeOrRange() {
        int[] values =
                FluxTuning.values(FluxTuning.rules(FluxTuning.defaults(), true, true, false));
        assertThrows(IllegalArgumentException.class, () -> FluxTuning.fromValues(new int[3]));
        int[] future = values.clone();
        future[0] = 2;
        assertThrows(IllegalArgumentException.class, () -> FluxTuning.fromValues(future));
        int[] badSwitch = values.clone();
        badSwitch[values.length - 1] = 2;
        assertThrows(IllegalArgumentException.class, () -> FluxTuning.fromValues(badSwitch));
        assertThrows(
                IllegalArgumentException.class,
                () -> FluxTuning.rules(new int[3], true, true, true));
        assertNotEquals(0, FluxTuning.fromValues(values).generation());
    }
}
