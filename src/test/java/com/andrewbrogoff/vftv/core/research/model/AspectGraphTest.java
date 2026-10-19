package com.andrewbrogoff.vftv.core.research.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AspectGraphTest {
    private final AspectGraph graph = Fixtures.aspects();

    @Test
    void compoundsLinkToTheirParentsBothWaysInAscendingOrder() {
        assertEquals(
                List.of("vftv:lux", "vftv:motus", "vftv:sonus", "vftv:vacuos"),
                graph.taughtLinks("vftv:aer"));
        assertEquals(List.of("vftv:aer", "vftv:ignis"), graph.taughtLinks("vftv:lux"));
        assertEquals(List.of(), graph.taughtLinks("vftv:terra"));
        assertEquals(List.of(), graph.taughtLinks("vftv:unknown"));
        assertTrue(graph.contains("vftv:vacuos"));
        assertFalse(graph.contains("vftv:unknown"));
    }

    @Test
    void theHiddenPrimalLinksToItsTaughtParents() {
        assertEquals(
                List.of("vftv:aer", "vftv:alienis", "vftv:perditio", "vftv:silentium"),
                graph.taughtLinks("vftv:vacuos"));
    }

    @Test
    void theTutorialChainRunsThroughLux() {
        assertEquals(
                List.of("vftv:aer", "vftv:lux", "vftv:ignis"),
                graph.chain(List.of("vftv:aer", "vftv:ignis")));
    }

    @Test
    void chainsShareAnchorsAndBreakTiesByAscendingId() {
        // Aer to Perditio: through Vacuos only. Ignis to Ordo: Lux, Aer, Motus.
        assertEquals(
                List.of("vftv:ignis", "vftv:lux", "vftv:aer", "vftv:vacuos", "vftv:perditio"),
                graph.chain(List.of("vftv:ignis", "vftv:aer", "vftv:perditio")));
        // Aer to Silentium: Sonus and Vacuos both reach it in two steps; Sonus has the lower id.
        assertEquals(
                List.of("vftv:aer", "vftv:sonus", "vftv:silentium"),
                graph.chain(List.of("vftv:aer", "vftv:silentium")));
    }

    @Test
    void unknownOrDisconnectedAnchorsHaveNoChain() {
        assertNull(graph.chain(List.of("vftv:aer", "vftv:terra")));
        assertNull(graph.chain(List.of("vftv:aer", "vftv:unknown")));
        assertEquals(List.of(), graph.chain(List.of()));
    }

    @Test
    void theTaughtViewIsTheTaughtGraph() {
        AspectGraph.LinkView taught = graph.view(id -> false);
        assertTrue(taught.taught());
        assertFalse(taught.revealed("vftv:vacuos"));
        assertEquals(graph.taughtLinks("vftv:vacuos"), taught.links("vftv:vacuos"));
        assertEquals(graph.taughtLinks("vftv:terra"), taught.links("vftv:terra"));
        assertTrue(taught.linked("vftv:vacuos", "vftv:aer"));
        assertFalse(taught.linked("vftv:vacuos", "vftv:terra"));
        assertFalse(taught.linked("vftv:aer", "vftv:aer"));
    }

    @Test
    void theRevealLinksTheHiddenPrimalToEveryExposedPrimalAndKeepsTheTaughtLinks() {
        assertEquals(
                List.of(
                        "vftv:aer",
                        "vftv:aqua",
                        "vftv:ignis",
                        "vftv:ordo",
                        "vftv:perditio",
                        "vftv:terra"),
                graph.exposedPrimals());
        assertEquals(Map.of("vftv:vacuos", "vftv:silence_beneath"), graph.revealedBy());
        AspectGraph.LinkView revealed = graph.view("vftv:silence_beneath"::equals);
        assertFalse(revealed.taught());
        assertTrue(revealed.revealed("vftv:vacuos"));
        assertEquals(
                List.of(
                        "vftv:aer",
                        "vftv:alienis",
                        "vftv:aqua",
                        "vftv:ignis",
                        "vftv:ordo",
                        "vftv:perditio",
                        "vftv:silentium",
                        "vftv:terra"),
                revealed.links("vftv:vacuos"));
        assertEquals(List.of("vftv:vacuos"), revealed.links("vftv:terra"));
        assertTrue(revealed.linked("vftv:terra", "vftv:vacuos"));
        // Links only grow: every taught link stays.
        for (String a : List.of("vftv:aer", "vftv:lux", "vftv:silentium", "vftv:vacuos")) {
            assertTrue(revealed.links(a).containsAll(graph.taughtLinks(a)), a);
        }
        assertEquals(graph.taughtLinks("vftv:lux"), revealed.links("vftv:lux"));
    }

    @Test
    void aSelfParentAndAnUnknownParentAddNoLink() {
        AspectGraph odd = new AspectGraph(Map.of("x:a", List.of("x:a", "x:missing")));
        assertEquals(List.of(), odd.taughtLinks("x:a"));
    }
}
