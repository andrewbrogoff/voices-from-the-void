package com.andrewbrogoff.vftv.core.text.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

/** The caption band (accessibility, "The caption band"), its revision and the R5 timing rules. */
class CaptionBandTest {
    private static final int H = 240;

    @Test
    void theCrucibleVectorsBands() {
        CaptionBand band = new CaptionBand();
        band.update(H, 49, 49, 1, 1, 1, 1);
        assertEquals(164, band.bottom(), "the action-bar baseline 172, minus 8");
        assertEquals(151, band.statusTop());
        assertEquals(143, band.voiceBottom());
        assertEquals(130, band.bandTop());
        assertEquals(128, band.usableHeight(H));

        band.update(H, 49, 49, 3, 1, 4, 1);
        assertEquals(85, band.bandTop(), "the band's maximum");
        assertEquals(83, band.usableHeight(H));
        assertEquals(1f, band.voiceScale());

        band.update(H, 49, 49, 0, 2, 0, 2);
        assertFalse(band.showing());
        assertEquals(H, band.usableHeight(H), "no caption: the whole screen");
    }

    @Test
    void everyCombinationOfLinesAndRows() {
        for (int rows : new int[] {39, 49, 59, 69}) {
            for (int s = 0; s <= 3; s++) {
                for (int v = 0; v <= 4; v++) {
                    CaptionBand band = new CaptionBand();
                    band.update(H, rows, rows - 10, s, 0, v, 0);
                    int bottom = H - Math.max(rows + 9, 68) - 8;
                    int statusTop = s > 0 ? bottom - 4 - 9 * s : bottom;
                    int voiceBottom = s > 0 ? statusTop - 8 : bottom;
                    int top = v > 0 ? voiceBottom - 4 - 9 * v : statusTop;
                    String at = "rows " + rows + ", status " + s + ", voice " + v;
                    assertEquals(bottom, band.bottom(), at);
                    assertEquals(statusTop, band.statusTop(), at);
                    assertEquals(voiceBottom, band.voiceBottom(), at);
                    assertEquals(Math.max(top, H / 3), band.bandTop(), at);
                    assertTrue(band.bandTop() >= H / 3, at);
                    assertEquals(s > 0 || v > 0, band.showing(), at);
                }
            }
        }
    }

    @Test
    void veryTallRowsShrinkTheVoiceBlockToTheThirdLine() {
        CaptionBand band = new CaptionBand();
        // leftHeight 120: baseline 240 - 129 = 111, bottom 103; status 3 lines tops at 72.
        band.update(H, 120, 39, 3, 0, 4, 0);
        assertEquals(80, band.bandTop(), "never above floor(H / 3)");
        assertEquals(0f, band.voiceScale(), "no room left above the status line");

        band.update(H, 90, 39, 1, 0, 4, 0);
        // baseline 141, bottom 133, status top 120, voice bottom 112, natural top 72.
        assertEquals(80, band.bandTop());
        assertEquals(32 / 40f, band.voiceScale(), 1e-6f);

        band.update(H, 39, 39, 3, 0, 9, 0);
        assertEquals(85, band.bandTop(), "a Voice block is capped at 4 lines");
    }

    @Test
    void theRevisionRisesOnEveryChangeAndOnlyThen() {
        CaptionBand band = new CaptionBand();
        assertTrue(band.update(H, 39, 39, 0, 0, 0, 0), "the first frame");
        int r = band.revision();
        assertFalse(band.update(H, 39, 39, 0, 0, 0, 0), "nothing changed");
        assertEquals(r, band.revision());

        assertTrue(band.update(H, 39, 39, 1, 1, 0, 0), "a status line arrives");
        assertTrue(band.update(H, 39, 39, 1, 2, 0, 0), "a replacement of the same height");
        assertTrue(band.update(H, 39, 39, 0, 3, 0, 0), "it expires");
        assertTrue(band.update(H, 39, 39, 0, 3, 2, 1), "a Voice line arrives");
        assertTrue(band.update(H, 39, 39, 0, 3, 0, 1), "F1 or voices.text hides it");
        assertTrue(band.update(H, 39, 39, 0, 3, 2, 1), "and shows it again");
        assertTrue(band.update(H, 49, 39, 0, 3, 2, 1), "the status rows grow");
        assertTrue(band.update(360, 49, 39, 0, 3, 2, 1), "the screen resizes");
        assertEquals(r + 8, band.revision());
    }

    @Test
    void updatingAllocatesNothing() {
        CaptionBand band = new CaptionBand();
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long allocated = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long before = threads.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 100_000; i++) {
                band.update(H, 39 + (i & 7), 39, i & 3, i, i % 5, i);
                band.update(H, 39 + (i & 7), 39, i & 3, i, i % 5, i);
            }
            allocated = threads.getThreadAllocatedBytes(thread) - before;
        }
        assertEquals(0, allocated, "bytes allocated by 200,000 frames");
    }

    @Test
    void transientTextHolds() {
        assertEquals(60, CaptionBand.hold(CaptionBand.STATUS_TICKS, 0.5), "a minimum of 1");
        assertEquals(60, CaptionBand.hold(CaptionBand.STATUS_TICKS, 1.0));
        assertEquals(120, CaptionBand.hold(CaptionBand.STATUS_TICKS, 2.0));
        assertEquals(90, CaptionBand.hold(CaptionBand.STATUS_TICKS, 1.5));
        assertEquals(
                66, CaptionBand.hold(CaptionBand.STATUS_TICKS, 1.1), "exact for decimal steps");
        assertEquals(63, CaptionBand.hold(CaptionBand.STATUS_TICKS, 1.05));
        assertEquals(87, CaptionBand.hold(29, 3.0));
        assertEquals(8, CaptionBand.hold(7, 1.01), "rounded up");
        assertEquals(1f, CaptionBand.fade(60, 0f));
        assertEquals(1f, CaptionBand.fade(20, 0f));
        assertEquals(0.5f, CaptionBand.fade(10, 0f));
        assertEquals(0f, CaptionBand.fade(0, 0.5f));
    }

    @Test
    void moreThanThreeWrappedLinesShowTwoAndTheHint() {
        for (int wrapped = 0; wrapped <= 3; wrapped++) {
            assertEquals(wrapped, CaptionBand.statusVisibleLines(wrapped));
            assertFalse(CaptionBand.statusTruncated(wrapped));
        }
        assertEquals(3, CaptionBand.statusVisibleLines(4), "2 lines and the hint");
        assertTrue(CaptionBand.statusTruncated(4));
        assertEquals(3, CaptionBand.statusVisibleLines(57));
        assertTrue(CaptionBand.statusTruncated(57));
    }
}
