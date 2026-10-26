package com.andrewbrogoff.vftv.core.arcane.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WorktableRulesTest {
    @Test
    void taxedAboveNeutralWarpOrWithAnySpill() {
        assertFalse(WorktableRules.taxed(1000, 0), "warp 1000, spill 0");
        assertTrue(WorktableRules.taxed(1001, 0), "warp 1001");
        assertTrue(WorktableRules.taxed(1000, 1), "a VOID spill at warp 1000");
        assertTrue(WorktableRules.taxed(1200, 5), "the Bell at total warp 10");
        assertTrue(WorktableRules.taxed(1500, 9), "the Bell at total warp 25");
        assertFalse(WorktableRules.taxed(850, 0), "an amplifier below 1.0 spills nothing here");
    }

    @Test
    void wandCraftsIsTheLeastPoolOverCost() {
        assertEquals(
                3,
                WorktableRules.wandCrafts(
                        Map.of("vftv:aer", 32, "vftv:ordo", 30),
                        Map.of("vftv:aer", 10, "vftv:ordo", 8)));
        assertEquals(0, WorktableRules.wandCrafts(Map.of(), Map.of("vftv:aer", 1)), "no pool");
        assertEquals(
                Integer.MAX_VALUE,
                WorktableRules.wandCrafts(Map.of(), Map.of("vftv:aer", 0)),
                "a zero cost limits nothing");
    }

    @Test
    void outputRoom() {
        assertEquals(64, WorktableRules.outputRoom(true, 0, 64, 1));
        assertEquals(30, WorktableRules.outputRoom(true, 4, 64, 2));
        assertEquals(0, WorktableRules.outputRoom(false, 0, 64, 1), "a different item");
        assertEquals(0, WorktableRules.outputRoom(true, 64, 64, 1), "full");
        assertEquals(0, WorktableRules.outputRoom(true, 0, 64, 0), "no result");
    }

    @Test
    void batchSizeIsLimitedByEachInTurn() {
        assertEquals(64, WorktableRules.batchSize(64, 100, 100, 100), "the cap");
        assertEquals(3, WorktableRules.batchSize(64, 3, 100, 100), "the wand");
        assertEquals(5, WorktableRules.batchSize(64, 100, 5, 100), "the smallest grid stack");
        assertEquals(7, WorktableRules.batchSize(64, 100, 100, 7), "the output room");
        assertEquals(0, WorktableRules.batchSize(64, -1, 100, 100), "never below 0");
    }
}
