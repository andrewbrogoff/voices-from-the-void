package com.andrewbrogoff.vftv.core.flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.mojang.serialization.DataResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FluxRulesServiceTest {
    @AfterEach
    void restore() {
        FluxRulesService.applyTuning(DataResult.success(FluxTuning.defaults()), true);
        FluxRulesService.installConfig(HushConfig.DEFAULT);
    }

    @Test
    void theDefaultsAreInForceBeforeAnyLoad() {
        assertEquals(
                FluxTuning.rules(FluxTuning.defaults(), true, true, false),
                FluxRulesService.current());
        assertEquals(HushConfig.DEFAULT, FluxRulesService.config());
        assertEquals(new HushConfig(true, true, false), HushConfig.DEFAULT);
    }

    @Test
    void aValidFileReplacesTheTuning() {
        FluxRulesService.applyTuning(FluxRulesService.parse("{\"hush.walk\": 7}"), false);
        assertEquals(7, FluxRulesService.current().hushWalk());
        FluxRulesService.applyTuning(FluxRulesService.parse("{}"), true);
        assertEquals(32, FluxRulesService.current().hushWalk());
    }

    @Test
    void anInvalidFileRefusesTheWorldAtOpen() {
        FluxRules before = FluxRulesService.current();
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                FluxRulesService.applyTuning(
                                        FluxRulesService.parse("{\"hush.walk\": 0}"), false));
        String message = String.valueOf(e.getMessage());
        assertTrue(message.contains("data/vftv/tuning/hush.json"), message);
        assertTrue(message.contains("hush.walk"), message);
        assertSame(before, FluxRulesService.current());
    }

    @Test
    void anInvalidReloadKeepsThePreviousTuning() {
        FluxRulesService.applyTuning(FluxRulesService.parse("{\"hush.walk\": 7}"), true);
        FluxRules before = FluxRulesService.current();
        FluxRulesService.applyTuning(FluxRulesService.parse("{\"hush.walk\": \"x\"}"), true);
        assertSame(before, FluxRulesService.current());
        FluxRulesService.applyTuning(DataResult.error(() -> "unreadable"), true);
        assertSame(before, FluxRulesService.current());
    }

    @Test
    void theSwitchesReachTheRulesAndTheirGeneration() {
        FluxRules before = FluxRulesService.current();
        HushConfig off = new HushConfig(false, false, true);
        FluxRulesService.installConfig(off);
        FluxRules after = FluxRulesService.current();
        assertEquals(off, FluxRulesService.config());
        assertFalse(after.spread());
        assertFalse(after.hollows());
        assertTrue(after.unattended());
        assertNotEquals(before.generation(), after.generation());
        assertEquals(FluxTuning.values(before)[1], FluxTuning.values(after)[1], "tuning kept");
    }
}
