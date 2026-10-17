package com.andrewbrogoff.vftv.core.warp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The tuning file (warp spec, "Tuning and limits"): defaults, rejections, the record form. */
class WarpTuningTest {
    private static String rejected(String json) {
        return String.valueOf(
                assertThrows(IllegalArgumentException.class, () -> WarpTuning.parse(json))
                        .getMessage());
    }

    @Test
    void anEmptyFileIsEveryDefault() {
        WarpRules r = WarpTuning.parse("{}");
        assertEquals(WarpTuning.DEFAULTS, r);
        assertEquals(List.of(6, 14, 24), r.bands());
        assertEquals(400, r.temporaryDecayInterval());
        assertEquals(20, r.taxPerWarpPm());
        assertEquals(500, r.taxMaxPm());
        assertEquals(500, r.infusionPerWarpPm());
        assertEquals(20, r.infusionMaxPenalty());
        assertEquals(1200, r.checkInterval());
        assertEquals(10, r.counterPerGain());
        assertEquals(30, r.counterPerEvent());
        assertEquals(1000, r.counterMax());
        assertEquals(5, r.counterPerVoidWork());
        assertEquals(15, r.chanceBasePm());
        assertEquals(5, r.chancePerPermanentPm());
        assertEquals(1, r.chancePerCounterPm());
        assertEquals(250, r.chanceMaxPm());
        assertEquals(600, r.grace());
        assertEquals(600, r.contextWindow());
        assertEquals(List.of("vftv:vuulas"), r.contextDimensions());
        assertEquals(10, r.joiningAmplifierBonus());
        assertEquals(12, r.gearMax());
        assertEquals(12_000, r.bargainTtl());
        assertEquals(0, r.listenCostTemporary());
        assertEquals(2, r.listenCostLasting());
        assertEquals(6000, r.listenCooldown());
        assertEquals(4, r.stillAmount());
        assertEquals(10, r.permanentCap());
        assertEquals(
                Map.of(
                        "vftv:under_the_silence",
                        2,
                        "vftv:learning_the_tongue",
                        3,
                        "vftv:joining",
                        5),
                r.permanentMilestones());
        assertTrue(r.voicesEnabled() && r.tax() && r.amplifier() && r.gear());
    }

    @Test
    void everyKeyIsOptionalAndOverridable() {
        WarpRules r =
                WarpTuning.parse(
                        "{\"bands\": [1, 2, 3], \"gear.max\": 0, \"context.dimensions\": [],"
                                + " \"listen.cost\": {\"temporary\": 20},"
                                + " \"permanent.milestones\": {\"vftv:joining\": 1}}");
        assertEquals(List.of(1, 2, 3), r.bands());
        assertEquals(0, r.gearMax());
        assertEquals(List.of(), r.contextDimensions());
        assertEquals(20, r.listenCostTemporary());
        assertEquals(0, r.listenCostLasting());
        assertEquals(Map.of("vftv:joining", 1), r.permanentMilestones());
    }

    @Test
    void theCommonRejections() {
        assertTrue(rejected("{\"nope\": 1}").contains("unknown field"));
        assertTrue(rejected("{\"schema\": 17, \"grace\": 1}").contains("unknown field"));
        assertTrue(rejected("{\"grace\": \"x\"}").contains("not an integer"));
        assertTrue(rejected("{\"grace\": 1.5}").contains("not an integer"));
        assertTrue(rejected("{\"grace\": 6001}").contains("outside"));
        assertTrue(rejected("{\"check.interval\": 199}").contains("outside"));
        assertTrue(rejected("{\"bands\": [1, 2]}").contains("entries"));
        assertTrue(
                rejected("{\"context.dimensions\": [\"Not An Id\"]}").contains("does not match"));
        assertTrue(
                rejected("{\"context.dimensions\": [\"vftv:" + "x".repeat(260) + "\"]}")
                        .contains("length"));
        assertTrue(rejected("{\"grace\": 1, \"grace\": 2}").contains("malformed"));
        assertTrue(rejected("[]").contains("not a JSON object"));
    }

    @Test
    void theRulesBeyondTheTable() {
        assertTrue(rejected("{\"bands\": [6, 6, 24]}").contains("ascending"));
        assertTrue(rejected("{\"bands\": [24, 14, 6]}").contains("ascending"));
        assertTrue(rejected("{\"listen.cost\": {}}").contains("no kind above 0"));
        assertTrue(rejected("{\"listen.cost\": {\"permanent\": 1}}").contains("unknown field"));
        assertTrue(rejected("{\"listen.cost\": {\"lasting\": 21}}").contains("outside"));
        assertTrue(rejected("{\"permanent.cap\": 9}").contains("over permanent.cap"));
        assertTrue(
                rejected("{\"permanent.milestones\": {\"vftv:under_the_silence\": 2}}")
                        .contains("vftv:joining is not listed"));
        assertTrue(
                rejected("{\"permanent.milestones\": {\"vftv:joining\": 101}}")
                        .contains("outside"));
    }

    @Test
    void theRecordFormRoundTripsAndRequiresEveryKey() {
        WarpRules off = WarpTuning.withSwitches(WarpTuning.DEFAULTS, false, true, false, true);
        JsonObject json = WarpTuning.toJson(off, true);
        assertEquals(off, WarpTuning.fromJson(json, true));
        assertFalse(off.voicesEnabled());
        assertFalse(off.amplifier());
        json.remove("grace");
        assertThrows(IllegalArgumentException.class, () -> WarpTuning.fromJson(json, true));
        JsonObject file = WarpTuning.toJson(WarpTuning.DEFAULTS, false);
        assertFalse(file.has("format"));
        assertEquals(WarpTuning.DEFAULTS, WarpTuning.parse(file.toString()));
        assertThrows(IllegalArgumentException.class, () -> WarpTuning.fromJson(file, true));
    }

    @Test
    void theRecordIsImmutable() {
        WarpRules r = WarpTuning.DEFAULTS;
        assertThrows(UnsupportedOperationException.class, () -> r.bands().add(1));
        assertThrows(UnsupportedOperationException.class, () -> r.contextDimensions().add("x:y"));
        assertThrows(
                UnsupportedOperationException.class, () -> r.permanentMilestones().put("x:y", 1));
    }
}
