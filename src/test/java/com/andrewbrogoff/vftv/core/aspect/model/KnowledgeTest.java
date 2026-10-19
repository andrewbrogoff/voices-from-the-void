package com.andrewbrogoff.vftv.core.aspect.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.ScanOutcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class KnowledgeTest {
    /**
     * Aer, Ignis and Perditio are primals; Lux = Aer + Ignis; Vacuos is a hidden primal taught as
     * Aer + Perditio; Tenebrae = Lux + Vacuos. Lux has its own bonus of 9; the default is 4.
     */
    private static final Knowledge.Rules RULES =
            new Knowledge.Rules() {
                private final Map<String, List<String>> prerequisites =
                        Map.of(
                                "lux", List.of("aer", "ignis"),
                                "vacuos", List.of("aer", "perditio"),
                                "tenebrae", List.of("lux", "vacuos"));
                private final Map<String, Integer> order =
                        Map.of("lux", 1, "vacuos", 1, "tenebrae", 2);

                @Override
                public List<String> prerequisites(String aspect) {
                    return prerequisites.getOrDefault(aspect, List.of());
                }

                @Override
                public int order(String aspect) {
                    return order.getOrDefault(aspect, 0);
                }

                @Override
                public int bonus(String aspect) {
                    return aspect.equals("lux") ? 9 : 4;
                }
            };

    @Test
    void aRewardTeachesItsAspectWithEveryMissingPrerequisiteLowestOrderFirst() {
        Knowledge known = Knowledge.EMPTY.discover("ignis", 0);
        Knowledge.Rewarded r = nonNull(known).reward(Map.of("tenebrae", 6, "ignis", 2), RULES);
        assertEquals(
                List.of(
                        new Knowledge.Discovery("aer", 4),
                        new Knowledge.Discovery("perditio", 4),
                        new Knowledge.Discovery("lux", 9),
                        new Knowledge.Discovery("vacuos", 4),
                        new Knowledge.Discovery("tenebrae", 4)),
                r.discovered());
        assertEquals(
                Set.of("aer", "ignis", "perditio", "lux", "vacuos", "tenebrae"), r.next().known());
        assertEquals(
                Map.of("aer", 4, "perditio", 4, "lux", 9, "vacuos", 4, "tenebrae", 10, "ignis", 2),
                r.next().pool());
        assertEquals(Map.of(), r.next().scans());
    }

    @Test
    void aRewardOfKnownAspectsOnlyCredits() {
        Knowledge known = nonNull(Knowledge.EMPTY.discover("aer", 0));
        Knowledge.Rewarded r = known.reward(Map.of("aer", 3), RULES);
        assertEquals(List.of(), r.discovered());
        assertEquals(Map.of("aer", 3), r.next().pool());
        assertEquals(Map.of("aer", 3), nonNull(r.next().delta()).pool());
    }

    @Test
    void aFirstScanOfPrimalsAndTheirCompoundDiscoversAllAtOnce() {
        Knowledge.Strike s =
                Knowledge.EMPTY.strike("item:x", Map.of("lux", 2, "aer", 3, "ignis", 1), RULES);
        assertEquals(ScanOutcome.FIRST, s.outcome());
        assertTrue(s.complete());
        assertEquals(Map.of("aer", 3, "ignis", 1, "lux", 2), s.points());
        assertEquals(
                List.of(
                        new Knowledge.Discovery("aer", 4),
                        new Knowledge.Discovery("ignis", 4),
                        new Knowledge.Discovery("lux", 9)),
                s.discovered());
        assertEquals(Map.of("aer", 7, "ignis", 5, "lux", 11), s.next().pool());
        assertEquals(Knowledge.Scan.COMPLETE, s.next().scans().get("item:x"));
    }

    @Test
    void discoveryOrderIgnoresMapOrder() {
        Map<String, Integer> forward = new LinkedHashMap<>();
        forward.put("aer", 1);
        forward.put("ignis", 1);
        forward.put("lux", 1);
        Map<String, Integer> backward = new LinkedHashMap<>();
        backward.put("lux", 1);
        backward.put("ignis", 1);
        backward.put("aer", 1);
        assertEquals(
                Knowledge.EMPTY.strike("k", forward, RULES).discovered(),
                Knowledge.EMPTY.strike("k", backward, RULES).discovered());
    }

    @Test
    void anUnheardAspectMakesAPartialScanThatCompletesLater() {
        Knowledge.Strike first = Knowledge.EMPTY.strike("item:t", Map.of("tenebrae", 5), RULES);
        assertEquals(ScanOutcome.FIRST, first.outcome());
        assertFalse(first.complete());
        assertEquals(Map.of(), first.points());
        assertTrue(first.discovered().isEmpty());

        Knowledge.Strike again = first.next().strike("item:t", Map.of("tenebrae", 5), RULES);
        assertEquals(ScanOutcome.PARTIAL, again.outcome());

        Knowledge learned = again.next();
        for (String key : List.of("item:lux", "item:void")) {
            Map<String, Integer> values =
                    key.equals("item:lux")
                            ? Map.of("aer", 1, "ignis", 1, "lux", 1)
                            : Map.of("perditio", 1, "vacuos", 1);
            learned = learned.strike(key, values, RULES).next();
        }
        assertTrue(learned.known().containsAll(Set.of("lux", "vacuos")));
        Knowledge.Strike done = learned.strike("item:t", Map.of("tenebrae", 5), RULES);
        assertEquals(ScanOutcome.COMPLETED, done.outcome());
        assertTrue(done.complete());
        assertEquals(Map.of("tenebrae", 5), done.points());
        assertEquals(List.of(new Knowledge.Discovery("tenebrae", 4)), done.discovered());

        Knowledge.Strike repeat = done.next().strike("item:t", Map.of("tenebrae", 5), RULES);
        assertEquals(ScanOutcome.REPEAT, repeat.outcome());
        assertEquals(Map.of(), repeat.points());
        assertEquals(done.next(), repeat.next());
    }

    @Test
    void vacuosIsDiscoveredFromItsTaughtParents() {
        Knowledge known =
                Knowledge.EMPTY.strike("a", Map.of("aer", 1, "perditio", 1), RULES).next();
        Knowledge.Strike s = known.strike("b", Map.of("vacuos", 2), RULES);
        assertEquals(List.of(new Knowledge.Discovery("vacuos", 4)), s.discovered());
        assertEquals(Map.of("vacuos", 2), s.points());
    }

    @Test
    void aPartialPaysFromItsSnapshotWhateverTheValuesBecome() {
        Knowledge.Strike first =
                Knowledge.EMPTY.strike("item:s", Map.of("aer", 2, "tenebrae", 3), RULES);
        assertEquals(Map.of("aer", 2), first.points());
        Knowledge learned =
                first.next()
                        .strike(
                                "x",
                                Map.of("ignis", 1, "lux", 1, "perditio", 1, "vacuos", 1),
                                RULES)
                        .next();
        // A reload changed the thing's values; the promise stands.
        Knowledge.Strike done = learned.strike("item:s", Map.of("aer", 9, "tenebrae", 1), RULES);
        assertEquals(Map.of("tenebrae", 3), done.points());
        assertEquals(Map.of("aer", 2, "tenebrae", 3), done.values());
    }

    @Test
    void discoverAndCredit() {
        Knowledge k = Knowledge.EMPTY.discover("lux", 9);
        assertNotNull(k);
        assertEquals(Map.of("lux", 9), k.pool());
        assertNull(k.discover("lux", 9));
        assertNull(k.credit("aer", 3), "an unknown aspect is never credited");
        assertNull(k.credit("lux", 0));
        Knowledge credited = k.credit("lux", 3);
        assertNotNull(credited);
        assertEquals(Map.of("lux", 12), credited.pool());
        assertEquals(k.known(), credited.known(), "a credit never teaches");
        assertEquals(Set.of(), nonNull(Knowledge.EMPTY.discover("aer", 0)).pool().keySet());
        Knowledge full = nonNull(Knowledge.EMPTY.discover("lux", Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, nonNull(full.credit("lux", 5)).pool().get("lux"));
    }

    private static <T> T nonNull(@Nullable T value) {
        return Objects.requireNonNull(value);
    }

    @Test
    void deltasRebuildTheServersValueAndEqualityIgnoresThem() {
        Knowledge server = Knowledge.EMPTY;
        Knowledge client = Knowledge.EMPTY;
        List<Map<String, Integer>> things =
                List.of(
                        Map.of("tenebrae", 5, "aer", 1),
                        Map.of("aer", 1, "ignis", 2, "lux", 1),
                        Map.of("tenebrae", 5, "aer", 1));
        for (int i = 0; i < things.size(); i++) {
            server = server.strike("item:" + (i % 2), things.get(i), RULES).next();
            client = client.apply(nonNull(server.delta()));
        }
        server = nonNull(server.credit("lux", 2));
        client = client.apply(nonNull(server.delta()));
        assertEquals(server, client);
        assertNotEquals(null, server.delta());
        assertNull(server.baseline().delta());
        assertEquals(server.hashCode(), server.baseline().hashCode());
        assertEquals(server, server.baseline());
        assertNotEquals(server, "not knowledge");
    }

    @Test
    void invariantsAreEnforced() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Knowledge(Set.of(), Map.of(), Map.of("aer", 0), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Knowledge.Scan(true, Map.of("aer", 1), Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Knowledge.Scan(false, Map.of(), Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Knowledge.Scan(false, Map.of("aer", 1), Set.of("aer")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Knowledge.Scan(false, Map.of("aer", 1), Set.of("lux")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Knowledge.Scan(false, Map.of("aer", 0, "lux", 1), Set.of()));
        assertThrows(
                IllegalArgumentException.class, () -> Knowledge.EMPTY.strike("k", Map.of(), RULES));
    }
}
