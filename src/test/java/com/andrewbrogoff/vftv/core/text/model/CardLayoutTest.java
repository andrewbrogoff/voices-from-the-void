package com.andrewbrogoff.vftv.core.text.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The shared card geometry: the crucible's capped-card vectors replayed (crucible, "The HUD"), and
 * sheets on a synthetic card taller than any shipped one (accessibility, "Tests").
 */
class CardLayoutTest {
    /** No caption: the whole screen. */
    private static final int NONE = -1;

    /**
     * One crucible vector: a header of {@code header} px, {@code cells} cells of an amount {@code
     * t} px wide, a more line of 100 px if {@code more}, a spill line of {@code spill} px (0 for
     * none).
     */
    private static CardLayout crucible(
            int w, int h, int bandTop, int cells, boolean more, int t, int header, int spill) {
        CardLayout.Card card =
                new CardLayout.Card(
                        1, header, cells, 18 + t, more ? 1 : 0, 100, spill > 0 ? 1 : 0, spill);
        int usable = bandTop == NONE ? h : bandTop - 2;
        return CardLayout.compute(w, h, usable, card, sheets -> 150);
    }

    private static void expect(
            CardLayout l, int cols, int rows, int fw, int fh, int dw, int dh, int x, int y) {
        String at = "vector " + cols + "x" + rows + " " + fw + "x" + fh;
        assertEquals(cols, l.columns(), at);
        assertEquals(rows, l.rows(), at);
        assertEquals(fw, l.width(), at);
        assertEquals(fh, l.height(), at);
        assertEquals(dw, l.drawnWidth(), at);
        assertEquals(dh, l.drawnHeight(), at);
        assertEquals(x, l.x(), at);
        assertEquals(y, l.y(), at);
        assertFalse(l.paged(), "the capped crucible card never pages: " + at);
    }

    @Test
    void theCrucibleVectors() {
        expect(crucible(320, 240, NONE, 12, true, 24, 120, 180), 4, 3, 188, 93, 188, 93, 130, 128);
        expect(crucible(640, 360, NONE, 6, false, 12, 100, 150), 4, 2, 158, 64, 158, 64, 328, 188);
        expect(crucible(640, 360, NONE, 0, false, 0, 90, 0), 0, 0, 98, 17, 98, 17, 328, 188);
        expect(crucible(320, 240, NONE, 1, false, 30, 120, 180), 1, 1, 188, 46, 188, 46, 130, 128);
        expect(crucible(320, 240, NONE, 12, true, 36, 120, 180), 4, 3, 236, 93, 236, 93, 82, 128);
        expect(crucible(320, 240, 130, 12, true, 24, 120, 180), 4, 3, 188, 93, 188, 93, 130, 33);
        expect(crucible(320, 240, 130, 12, true, 36, 120, 180), 4, 3, 236, 93, 236, 93, 82, 33);
        expect(crucible(320, 240, 85, 12, true, 24, 120, 180), 6, 2, 280, 75, 280, 75, 38, 6);
        CardLayout lowest = crucible(320, 240, 85, 12, true, 36, 120, 180);
        expect(lowest, 5, 3, 294, 93, 250, 79, 68, 2);
        assertEquals(79 / 93f, lowest.scale(), 1e-6f, "the lowest scale, 0.8495");
        expect(crucible(320, 240, 130, 1, false, 30, 120, 180), 1, 1, 188, 46, 188, 46, 130, 80);
    }

    @Test
    void unscaledCardsHaveScaleOne() {
        assertEquals(1f, crucible(320, 240, NONE, 12, true, 24, 120, 180).scale());
    }

    @Test
    void aTallCardPagesWithEveryEntryOnExactlyOneSheet() {
        // 200 cells under the band's maximum: far beyond any shipped card.
        CardLayout l = crucible(320, 240, 85, 200, true, 24, 120, 180);
        assertTrue(l.paged());
        int fixed = 8 + 9 + 11 + 11;
        int perSheet = (4 * 79 - 3 * fixed) / 54;
        assertEquals(3, perSheet);
        assertEquals(fixed + 18 * perSheet, l.height(), "every sheet at sheet 1's size");
        assertTrue(l.scale() >= 0.75f, "a sheet draws at 0.75 or above");
        assertEquals(l.height() - fixed, l.bodyHeight());

        int next = 0;
        for (int sheet = 0; sheet < l.sheets(); sheet++) {
            assertEquals(next, l.sheetStart(sheet), "sheets are contiguous, in order");
            assertTrue(l.sheetEnd(sheet) > l.sheetStart(sheet), "no sheet is empty");
            int height = 0;
            for (int u = l.sheetStart(sheet); u < l.sheetEnd(sheet); u++) {
                height += u < l.rows() ? 18 : (u == l.rows() ? 11 : 9);
            }
            assertTrue(height <= 18 * perSheet, "sheet " + sheet + " fits its capacity");
            next = l.sheetEnd(sheet);
        }
        assertEquals(l.units(), next, "every unit is on a sheet");
        assertEquals(l.rows() + 1, l.units(), "the grid rows and the more line");
        assertEquals(l.rows() / perSheet + 1, l.sheets());
    }

    @Test
    void theIndicatorWidensAPagedFrame() {
        CardLayout.Card card = new CardLayout.Card(1, 50, 200, 42, 0, 0, 0, 0);
        CardLayout l = CardLayout.compute(320, 240, 83, card, sheets -> 290);
        assertTrue(l.paged());
        assertEquals(298, l.width());
    }

    @Test
    void aTooWideCardDoesNotPage() {
        CardLayout.Card card = new CardLayout.Card(1, 900, 1, 30, 0, 0, 0, 0);
        CardLayout l = CardLayout.compute(320, 240, 240, card, sheets -> 0);
        assertFalse(l.paged(), "paging only helps height");
        assertEquals(316 / 908f, l.scale(), 1e-6f);
        assertEquals(2, l.x());
    }

    @Test
    void aSheetBelowTheFloorScalesToFit() {
        // A 200-px-tall window: even one row at 0.75 doesn't fit, so the sheet scales to fit.
        CardLayout.Card card = new CardLayout.Card(6, 50, 40, 42, 0, 0, 3, 50);
        CardLayout l = CardLayout.compute(320, 60, 60, card, sheets -> 100);
        assertTrue(l.paged());
        assertEquals(56, l.drawnHeight());
        assertEquals(2, l.y());
    }

    @Test
    void theSheetCursorResetsClampsAndStops() {
        SheetCursor cursor = new SheetCursor();
        cursor.target("a");
        assertFalse(cursor.turn(-1, 4), "stops at the first sheet");
        assertTrue(cursor.turn(1, 4));
        assertTrue(cursor.turn(1, 4));
        assertTrue(cursor.turn(1, 4));
        assertFalse(cursor.turn(1, 4), "stops at the last sheet");
        assertEquals(3, cursor.sheet());
        cursor.target("a");
        assertEquals(3, cursor.sheet(), "the same target keeps its sheet");
        cursor.clamp(2);
        assertEquals(1, cursor.sheet(), "clamped on a relayout");
        cursor.target("b");
        assertEquals(0, cursor.sheet(), "a new target resets");
        cursor.clamp(0);
        assertEquals(0, cursor.sheet());
    }
}
