package com.andrewbrogoff.vftv.core.aspect.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.aspect.model.AspectGraph.Node;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class AspectGraphTest {
    /** A small valid graph: three primals, three tier-1 compounds, and a hidden primal. */
    private static Map<String, Node> base() {
        Map<String, Node> g = new HashMap<>();
        g.put("a", Node.of());
        g.put("b", Node.of());
        g.put("c", Node.of());
        g.put("ab", Node.of("a", "b"));
        g.put("bc", Node.of("b", "c"));
        g.put("ac", Node.of("a", "c"));
        g.put("v", new Node(List.of(), List.of("a", "c2")));
        g.put("c2", Node.of("c", "ab"));
        return g;
    }

    private static List<AspectGraph.Problem> check(Map<String, Node> g) {
        return AspectGraph.validate(g, List.of());
    }

    private static boolean fatal(List<AspectGraph.Problem> p, String text) {
        return p.stream().anyMatch(x -> x.fatal() && x.message().contains(text));
    }

    private static boolean soft(List<AspectGraph.Problem> p, String text) {
        return p.stream().anyMatch(x -> !x.fatal() && x.message().contains(text));
    }

    @Test
    void aValidGraphHasNoProblems() {
        assertEquals(List.of(), check(base()));
    }

    @Test
    void shapeRulesAreFatal() {
        Map<String, Node> g = base();
        g.put("one", new Node(List.of("a"), List.of()));
        assertTrue(fatal(check(g), "one has 1 parents"));
        g = base();
        g.put("twice", Node.of("a", "a"));
        assertTrue(fatal(check(g), "same aspect twice"));
        g = base();
        g.put("ghost", Node.of("a", "nowhere"));
        assertTrue(fatal(check(g), "unknown aspect nowhere"));
        g = base();
        g.put("self", Node.of("a", "self"));
        assertTrue(fatal(check(g), "its own parent"));
        g = base();
        g.put("v", new Node(List.of("a", "b"), List.of("a", "c")));
        assertTrue(fatal(check(g), "hidden primal with parents"));
        g = base();
        g.put("v", new Node(List.of(), List.of("a", "x", "c")));
        assertTrue(fatal(check(g), "3 taught parents"));
    }

    @Test
    void aMissingBuiltInIsFatal() {
        assertTrue(fatal(AspectGraph.validate(base(), List.of("a", "gone")), "gone is missing"));
    }

    @Test
    void cyclesAreFatalInEitherView() {
        Map<String, Node> g = base();
        g.put("x", Node.of("a", "y"));
        g.put("y", Node.of("b", "x"));
        assertTrue(fatal(check(g), "a cycle in the true view"));
        g = base();
        // Only the taught view closes this loop: v is taught as a + w, and w needs v.
        g.put("v", new Node(List.of(), List.of("a", "w")));
        g.put("w", Node.of("b", "v"));
        List<AspectGraph.Problem> p = check(g);
        assertTrue(fatal(p, "a cycle in the taught view"));
        assertFalse(fatal(p, "true view"));
    }

    @Test
    void duplicatePairsAreFatalInEitherView() {
        Map<String, Node> g = base();
        g.put("ba", Node.of("b", "a"));
        assertTrue(fatal(check(g), "share the parents a+b in the true view"));
        g = base();
        g.put("v", new Node(List.of(), List.of("a", "b")));
        assertTrue(fatal(check(g), "share the parents a+b in the taught view"));
    }

    @Test
    void depthAboveThreeIsSoft() {
        Map<String, Node> g = base();
        g.put("t2", Node.of("ab", "bc"));
        g.put("t3", Node.of("t2", "a"));
        g.put("t4", Node.of("t3", "b"));
        List<AspectGraph.Problem> p = check(g);
        assertTrue(soft(p, "t4 is at depth 4"));
        assertFalse(p.stream().anyMatch(AspectGraph.Problem::fatal));
    }

    @Test
    void fanOutAboveFiveIsSoftAndPrimalsAreExempt() {
        Map<String, Node> g = base();
        for (String p : List.of("a", "b", "bc", "ac", "c2")) {
            g.put("k" + p, Node.of("ab", p));
        }
        List<AspectGraph.Problem> problems = check(g);
        assertTrue(soft(problems, "ab is a parent of 6 aspects"));
        assertFalse(problems.stream().anyMatch(AspectGraph.Problem::fatal));
        assertFalse(soft(problems, "a is a parent of"));
    }

    @Test
    void anExposedPrimalNeedsTwoTierOneChildrenAndHiddenOnesAreExempt() {
        Map<String, Node> g = base();
        g.put("d", Node.of());
        g.put("ad", Node.of("a", "d"));
        List<AspectGraph.Problem> p = check(g);
        assertTrue(soft(p, "d is a parent of 1 tier-1 aspects in the true view"));
        assertFalse(soft(p, "v is a parent"));
    }

    @Test
    void tiersFollowTheView() {
        Map<String, Node> g = base();
        g.put("vt", Node.of("v", "b"));
        assertEquals(1, AspectGraph.tiers(g, false).get("vt"));
        assertEquals(4, AspectGraph.tiers(g, true).get("vt"));
        Node v = Objects.requireNonNull(g.get("v"));
        assertEquals(List.of("a", "c2"), v.prerequisites());
        assertEquals(List.of("a", "b"), Node.of("a", "b").prerequisites());
        assertTrue(v.hidden());
    }
}
