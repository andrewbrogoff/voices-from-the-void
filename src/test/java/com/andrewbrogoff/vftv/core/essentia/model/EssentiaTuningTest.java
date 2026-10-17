package com.andrewbrogoff.vftv.core.essentia.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EssentiaTuningTest {
    @Test
    void defaultsAreTheSpecTable() {
        assertArrayEquals(
                new int[] {100, 4, 8, 10, 8, 10, 20, 4}, EssentiaTuning.DEFAULTS.values());
        assertEquals(100, EssentiaTuning.DEFAULTS.tubeIdleTicks());
        assertEquals(EssentiaTuning.DEFAULTS, EssentiaTuning.parse("{}"));
        assertEquals(
                EssentiaTuning.DEFAULTS,
                EssentiaTuning.parse(EssentiaTuning.DEFAULTS.toJson().toString()));
    }

    @Test
    void everyRangeIsEnforced() {
        for (EssentiaTuning.Key k : EssentiaTuning.KEYS) {
            if (k.name().startsWith("suction")) {
                continue; // the ordering rule interacts; checked below
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> EssentiaTuning.parse("{\"" + k.name() + "\": " + (k.min() - 1) + "}"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> EssentiaTuning.parse("{\"" + k.name() + "\": " + (k.max() + 1) + "}"));
            EssentiaTuning.parse("{\"" + k.name() + "\": " + k.max() + "}");
            EssentiaTuning.parse("{\"" + k.name() + "\": " + k.min() + "}");
        }
        assertThrows(
                IllegalArgumentException.class, () -> EssentiaTuning.parse("{\"suction.jar\": 0}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EssentiaTuning.parse("{\"suction.jar_labelled\": 16}"));
        assertEquals(
                15,
                EssentiaTuning.parse("{\"suction.jar\": 14, \"suction.jar_labelled\": 15}")
                        .suctionJarLabelled());
    }

    @Test
    void rejections() {
        IllegalArgumentException order =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> EssentiaTuning.parse("{\"suction.jar\": 8}"));
        assertTrue(String.valueOf(order.getMessage()).contains("below"));
        assertThrows(
                IllegalArgumentException.class, () -> EssentiaTuning.parse("{\"tube.speed\": 1}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EssentiaTuning.parse("{\"tube.rate\": \"8\"}"));
        assertThrows(
                IllegalArgumentException.class, () -> EssentiaTuning.parse("{\"tube.rate\": 8.5}"));
        assertThrows(IllegalArgumentException.class, () -> EssentiaTuning.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> EssentiaTuning.parse("{"));
        assertThrows(IllegalArgumentException.class, () -> EssentiaTuning.of(new int[3]));
        assertEquals(8, EssentiaTuning.parse("{\"tube.rate\": 8.0}").tubeRate());
    }
}
