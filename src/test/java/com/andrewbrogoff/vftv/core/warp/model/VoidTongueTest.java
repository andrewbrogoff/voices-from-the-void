package com.andrewbrogoff.vftv.core.warp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The Void-tongue phonology lint (lore, "Void-tongue phonology"). */
class VoidTongueTest {
    @Test
    void validWordsAndTheirStress() {
        assertEquals(1, VoidTongue.stress("uushen"));
        assertEquals(2, VoidTongue.stress("ohshaal"));
        assertEquals(2, VoidTongue.stress("lirvuuthen"));
        assertEquals(1, VoidTongue.stress("thahvis"), "no long vowel: the first syllable");
        assertEquals(1, VoidTongue.stress("a"));
        assertTrue(VoidTongue.valid("ilheh"));
        assertTrue(VoidTongue.valid("wohlis"));
    }

    @Test
    void theLintRefuses() {
        for (String bad :
                List.of(
                        "kadis", // a hard stop
                        "tila", // t alone
                        "chava", // ch
                        "aaah", // three vowels
                        "ulmra", // a cluster with no coda first
                        "silav", // closed by v
                        "hm", // no vowel
                        "Ilheh", // uppercase
                        "", // empty
                        "lirs", // two consonants at the end
                        "shla")) { // two onsets at the start
            assertFalse(VoidTongue.valid(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> VoidTongue.stress("kadis"));
    }

    @Test
    void tokensStripPunctuation() {
        assertEquals(
                List.of("vuuthel", "ilheh", "yehlas", "vuulas"),
                VoidTongue.tokens("vuuthel, ilheh. yehlas vuulas."));
        assertEquals(
                List.of("ilheh", "wohlis", "uushen"),
                VoidTongue.tokens("ilheh wohlis\u2026 uushen"));
        assertEquals(
                List.of("", "uushen"),
                VoidTongue.tokens("\u2014 uushen"),
                "a lone dash leaves an empty token");
    }
}
