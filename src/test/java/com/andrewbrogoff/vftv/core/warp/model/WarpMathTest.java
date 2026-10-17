package com.andrewbrogoff.vftv.core.warp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The warp arithmetic beyond the spec's vector tables: rejections, bounds and the counter. */
class WarpMathTest {
    private static final WarpRules R = WarpTuning.DEFAULTS;
    private static final String SILENCE = "vftv:under_the_silence";

    private static @Nullable WarpPreview preview(
            int dT, int dL, int dP, WarpSourceKind kind, @Nullable String id) {
        return WarpMath.preview(0, 0, 0, Set.of(), dT, dL, dP, kind, id, R);
    }

    @Test
    void aNegativePermanentIsACommandsAlone() {
        assertNull(preview(0, 0, -1, WarpSourceKind.STILLING, null));
        assertNull(preview(0, 0, -1, WarpSourceKind.ENTRY, SILENCE));
        assertEquals(
                new WarpPreview(0, 0, 0, true),
                WarpMath.preview(0, 0, 0, Set.of(), 0, 0, -1, WarpSourceKind.COMMAND, null, R));
    }

    @Test
    void aPositivePermanentNeedsAListedMilestoneAtItsAmount() {
        assertEquals(
                new WarpPreview(0, 0, 2, false), preview(0, 0, 2, WarpSourceKind.ENTRY, SILENCE));
        assertEquals(
                new WarpPreview(0, 0, 2, false),
                preview(0, 0, 2, WarpSourceKind.MILESTONE, SILENCE));
        assertNull(preview(0, 0, 2, WarpSourceKind.BARGAIN, SILENCE), "a bargain");
        assertNull(preview(0, 0, 2, WarpSourceKind.LISTEN, null), "the rite");
        assertNull(preview(0, 0, 2, WarpSourceKind.ENTRY, null), "no id");
        assertNull(preview(0, 0, 3, WarpSourceKind.ENTRY, SILENCE), "the wrong amount");
    }

    @Test
    void aCostPartOver1000IsInvalid() {
        assertNull(preview(1001, 0, 0, WarpSourceKind.COMMAND, null));
        assertNull(preview(0, -1001, 0, WarpSourceKind.COMMAND, null));
        assertNull(preview(0, 0, 1001, WarpSourceKind.COMMAND, null));
        assertEquals(
                new WarpPreview(1000, 0, 0, false),
                preview(1000, 0, 0, WarpSourceKind.COMMAND, null));
    }

    @Test
    void aCommandGainStopsAtTheCapAndAPermanentAboveItIsKept() {
        assertEquals(
                new WarpPreview(0, 0, 10, true), preview(0, 0, 100, WarpSourceKind.COMMAND, null));
        assertEquals(
                new WarpPreview(0, 0, 0, true),
                WarpMath.preview(0, 0, 50, Set.of(), 0, 0, 5, WarpSourceKind.COMMAND, null, R));
    }

    @Test
    void milestones() {
        assertTrue(WarpMath.isMilestone(2, WarpSourceKind.ENTRY, SILENCE, R));
        assertTrue(WarpMath.isMilestone(5, WarpSourceKind.MILESTONE, WarpTuning.JOINING, R));
        assertFalse(WarpMath.isMilestone(0, WarpSourceKind.ENTRY, SILENCE, R), "no gain");
        assertFalse(WarpMath.isMilestone(2, WarpSourceKind.COMMAND, SILENCE, R), "a command");
        assertFalse(WarpMath.isMilestone(2, WarpSourceKind.ENTRY, "vftv:other", R), "unlisted");
        assertFalse(WarpMath.isMilestone(2, WarpSourceKind.ENTRY, null, R), "no id");
    }

    @Test
    void theCounter() {
        assertEquals(40, WarpMath.counterAfterGain(0, 4, R), "silence_beneath's commit");
        assertEquals(1000, WarpMath.counterAfterGain(995, 1, R), "capped");
        assertEquals(60, WarpMath.counterAfterEvent(90, R));
        assertEquals(0, WarpMath.counterAfterEvent(10, R));
        assertEquals(5, WarpMath.counterAfterVoidWork(0, R));
        assertEquals(1000, WarpMath.counter(5000, R), "read under counter.max");
        assertEquals(7, WarpMath.gains(new WarpPreview(3, 4, -2, false)));
        assertEquals(0, WarpMath.gains(new WarpPreview(-3, 0, 0, false)));
    }

    @Test
    void theChance() {
        assertEquals(115, WarpMath.chancePm(9, 2, 90, R), "after the reveal and the milestone");
        assertEquals(85, WarpMath.chancePm(9, 2, 60, R));
        assertEquals(250, WarpMath.chancePm(52, 10, 400, R), "capped");
        assertEquals(0, WarpMath.chancePm(0, 0, 1000, R), "no warp, no chance");
    }

    @Test
    void bandsAndTheAmplifier() {
        assertEquals(0, WarpMath.band(5, R));
        assertEquals(3, WarpMath.band(2200, R));
        assertEquals(22, WarpMath.amplifierWarp(12, true, R));
        assertEquals(12, WarpMath.amplifierWarp(12, false, R));
        WarpRules off = WarpTuning.withSwitches(R, true, false, false, true);
        assertEquals(0, WarpMath.amplifierWarp(12, true, off));
        assertEquals(1000, WarpMath.visFactorPm(24, true, off));
        assertEquals(0, WarpMath.infusionPenalty(24, true, off));
    }
}
