package com.andrewbrogoff.vftv.core.aspect.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ScanningTuningTest {
    private static String reason(String json) {
        return String.valueOf(
                assertThrows(IllegalArgumentException.class, () -> ScanningTuning.parse(json))
                        .getMessage());
    }

    @Test
    void theDefaultsAreTheSpecsAndRoundTrip() {
        ScanningTuning d = ScanningTuning.DEFAULTS;
        assertEquals(16, d.strikeCooldownTicks());
        assertEquals(60, d.readoutHoldTicks());
        assertEquals(4, d.discoveryBonus());
        assertEquals(8, d.nodeRewardDivisor());
        assertEquals(192, d.soundingRadius());
        assertEquals(16, d.soundingNear());
        assertEquals(64, d.soundingMid());
        assertEquals(6, d.answerOffset());
        assertEquals(3, d.particles());
        assertEquals(0.75, d.derivationFactor());
        assertEquals(Map.of("vftv:ignis", 1), d.smeltBonus());
        assertEquals(d, ScanningTuning.parse(d.toJson().toString()));
        assertEquals(d, ScanningTuning.parse("{}"), "every key is optional");
    }

    @Test
    void valuesAreRead() {
        ScanningTuning t =
                ScanningTuning.parse(
                        "{\"strike_cooldown_ticks\": 4, \"sounding.radius\": 256,"
                                + " \"sounding.mid\": 256, \"derivation.factor\": 1,"
                                + " \"derivation.smelt_bonus\": {\"vftv:aqua\": 2}}");
        assertEquals(4, t.strikeCooldownTicks());
        assertEquals(256, t.soundingRadius());
        assertEquals(new TreeMap<>(Map.of("vftv:aqua", 2)), t.smeltBonus());
    }

    @Test
    void theRadiusAndBandEdgesAreChecked() {
        assertTrue(reason("{\"sounding.radius\": 257}").contains("sounding.radius"));
        assertTrue(reason("{\"sounding.radius\": 0}").contains("sounding.radius"));
        assertTrue(reason("{\"sounding.near\": 64}").contains("band edges"));
        assertTrue(reason("{\"sounding.mid\": 200}").contains("band edges"));
        assertTrue(reason("{\"sounding.near\": 0}").contains("sounding.near"));
    }

    @Test
    void everyOtherDefectIsRejected() {
        assertTrue(reason("{\"loud\": 1}").contains("unknown key"));
        assertTrue(reason("{\"discovery_bonus\": 1.5}").contains("not an integer"));
        assertTrue(reason("{\"discovery_bonus\": \"4\"}").contains("not an integer"));
        assertTrue(reason("{\"sounding.particles\": 99}").contains("outside"));
        assertTrue(reason("{\"derivation.factor\": 5}").contains("derivation.factor"));
        assertTrue(reason("{\"derivation.smelt_bonus\": 1}").contains("not an object"));
        assertTrue(reason("{\"derivation.smelt_bonus\": {\"Bad\": 1}}").contains("bad aspect"));
        assertTrue(reason("{\"derivation.smelt_bonus\": {\"vftv:a\": 0}}").contains("outside"));
        assertTrue(
                reason(
                                "{\"derivation.smelt_bonus\": {\"v:a\": 1, \"v:b\": 1, \"v:c\": 1,"
                                        + " \"v:d\": 1, \"v:e\": 1, \"v:f\": 1}}")
                        .contains("more than 5"));
        assertTrue(reason("[]").contains("not a JSON object"));
        assertTrue(reason("{").contains("malformed"));
    }
}
