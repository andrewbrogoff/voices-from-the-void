package com.andrewbrogoff.vftv.core.research.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class FactSpanTest {
    @Test
    void parsesEachSourceAtItsArity() {
        assertEquals(
                new FactSpan("pool", List.of("vftv", "aer")), FactSpan.parse("fact:pool:vftv:aer"));
        assertEquals(
                new FactSpan("entry", List.of("vftv", "silence_beneath", "warp")),
                FactSpan.parse("fact:entry:vftv:silence_beneath:warp"));
        assertEquals(
                new FactSpan("essentia", List.of("tube.pulse")),
                FactSpan.parse("fact:essentia:tube.pulse"));
        assertEquals(
                new FactSpan("focus_rank", List.of("2", "cost")),
                FactSpan.parse("fact:focus_rank:2:cost"));
        assertEquals(22, FactSpan.ARITY.size());
    }

    @Test
    void malformedSpansParseToNull() {
        assertNull(FactSpan.parse("pool:vftv:aer"), "no fact: prefix");
        assertNull(FactSpan.parse("fact:nothing:x"), "an unknown source");
        assertNull(FactSpan.parse("fact:pool:vftv"), "too few arguments");
        assertNull(FactSpan.parse("fact:warp:total:extra"), "too many arguments");
        assertNull(FactSpan.parse("fact:pool:vftv:"), "an empty argument");
    }
}
