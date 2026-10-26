package com.andrewbrogoff.vftv.core.worldgen.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The worldgen model's rules beyond the fixtures: every offer row, claims, sites and tuning. */
class WorldgenModelTest {
    private static final Offers.Site SITE = new Offers.Site(List.of("a:1", "a:2", "a:3"), 2);

    private static Offers.State state(Set<String> exists, Set<String> held, Set<String> claimed) {
        return new Offers.State() {
            @Override
            public boolean exists(String page) {
                return exists.contains(page);
            }

            @Override
            public boolean held(String page) {
                return held.contains(page);
            }

            @Override
            public boolean claimedAnywhere(String page) {
                return claimed.contains(page);
            }
        };
    }

    private static final Offers.State FRESH =
            state(Set.of("a:1", "a:2", "a:3"), Set.of(), Set.of());

    @Test
    void everyRowInOrder() {
        assertEquals(
                Offers.Outcome.BUSY,
                Offers.decide(true, true, true, null, null, 0, FRESH).outcome());
        assertEquals(
                Offers.Outcome.ATTEMPT_CAP,
                Offers.decide(false, true, true, null, null, 0, FRESH).outcome());
        assertEquals(
                Offers.Outcome.UNREADABLE,
                Offers.decide(false, false, true, SITE, null, 0, FRESH).outcome());
        assertEquals(
                Offers.Outcome.SILENT,
                Offers.decide(false, false, false, null, List.of("a:1"), 0, FRESH).outcome());
        assertEquals(
                new Offers.Decision(Offers.Outcome.REISSUED, List.of("a:1")),
                Offers.decide(false, false, false, SITE, List.of("a:1"), 1, FRESH));
        assertEquals(
                Offers.Outcome.DONE,
                Offers.decide(
                                false,
                                false,
                                false,
                                SITE,
                                List.of("a:1"),
                                1,
                                state(Set.of("a:1"), Set.of("a:1"), Set.of()))
                        .outcome());
        assertEquals(
                new Offers.Decision(Offers.Outcome.GIVEN, List.of("a:1", "a:2")),
                Offers.decide(false, false, false, SITE, null, 0, FRESH));
        assertEquals(
                Offers.Outcome.FULL,
                Offers.decide(
                                false,
                                false,
                                false,
                                SITE,
                                null,
                                WorldgenLimits.MAX_CLAIM_SITES,
                                FRESH)
                        .outcome());
        assertEquals(
                Offers.Outcome.ALL_HELD,
                Offers.decide(
                                false,
                                false,
                                false,
                                SITE,
                                null,
                                WorldgenLimits.MAX_CLAIM_SITES,
                                state(
                                        Set.of("a:1", "a:2", "a:3"),
                                        Set.of("a:1", "a:2"),
                                        Set.of("a:3")))
                        .outcome(),
                "all_held comes before full and records nothing");
    }

    @Test
    void unknownPagesAreFilteredNeverHandedOut() {
        Offers.State gone = state(Set.of("a:3"), Set.of(), Set.of());
        assertEquals(
                List.of("a:3"), Offers.decide(false, false, false, SITE, null, 0, gone).pages());
        assertEquals(
                Offers.Outcome.DONE,
                Offers.decide(false, false, false, SITE, List.of("a:1", "a:2"), 1, gone).outcome());
        assertEquals("all_held", Offers.Outcome.ALL_HELD.id());
    }

    @Test
    void claimsKeepOrderAndBounds() {
        ChoirClaims c =
                ChoirClaims.EMPTY
                        .with("minecraft:overworld", 5, List.of("a:1"))
                        .with("minecraft:overworld", -7, List.of("a:2", "test:unknown"))
                        .with("minecraft:nether", 100, List.of("a:3"));
        assertEquals(List.of("a:2", "test:unknown"), c.at("minecraft:overworld", -7));
        assertEquals(List.of("a:3"), c.at("minecraft:nether", 100));
        assertNull(c.at("minecraft:overworld", 6));
        assertEquals("minecraft:nether", c.sites().getFirst().dim());
        assertEquals(Set.of("a:1", "a:2", "a:3", "test:unknown"), c.claimedAnywhere());
        assertThrows(
                IllegalArgumentException.class,
                () -> c.with("minecraft:overworld", 5, List.of("a:9")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChoirClaims.Site("Bad Id", 0, List.of("a:1")));
        assertThrows(
                IllegalArgumentException.class, () -> new ChoirClaims.Site("a:b", 0, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChoirClaims.Site("a:b", 0, List.of("a:1", "a:1")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChoirClaims.Site("a:b", 0, Collections.nCopies(9, "a:1")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChoirClaims.Site("a:" + "x".repeat(300), 0, List.of("a:1")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ChoirClaims(
                                List.of(
                                        new ChoirClaims.Site("a:b", 2, List.of("a:1")),
                                        new ChoirClaims.Site("a:b", 1, List.of("a:1")))),
                "unsorted");
        List<ChoirClaims.Site> many = new ArrayList<>();
        for (int i = 0; i <= WorldgenLimits.MAX_CLAIM_SITES; i++) {
            many.add(new ChoirClaims.Site("a:b", i, List.of("a:1")));
        }
        assertThrows(IllegalArgumentException.class, () -> new ChoirClaims(many));
    }

    @Test
    void siteRules() {
        Map<String, SiteRules.Entry> entries =
                Map.of(
                        SiteRules.BENEATH_THE_NOTES,
                        new SiteRules.Entry(SiteRules.CHOIR_PAGES, 3, "find"),
                        "vftv:choir/two",
                        new SiteRules.Entry(SiteRules.CHOIR_PAGES, 3, "find"),
                        "vftv:choir/road",
                        new SiteRules.Entry(SiteRules.CHOIR_PAGES, 4, "find"),
                        "vftv:other",
                        new SiteRules.Entry("vftv:ruins", 3, "find"));
        Offers.Site good =
                new Offers.Site(List.of(SiteRules.BENEATH_THE_NOTES, "vftv:choir/two"), 6);
        assertEquals(List.of(), SiteRules.check(Map.of(SiteRules.CHOIR_RUIN, good), entries));
        assertEquals(List.of(), SiteRules.builtIn(good, entries));
        assertTrue(SiteRules.check(Map.of(), entries).getFirst().contains("required"));
        List<String> bad =
                SiteRules.check(
                        Map.of(
                                SiteRules.CHOIR_RUIN,
                                new Offers.Site(
                                        List.of("vftv:other", "vftv:other", "vftv:none"), 9)),
                        entries);
        assertTrue(bad.stream().anyMatch(p -> p.contains("listed twice")), bad.toString());
        assertTrue(bad.stream().anyMatch(p -> p.contains("vftv:none is not a find entry")));
        assertTrue(bad.stream().anyMatch(p -> p.contains("vftv:other is not a find entry")));
        assertTrue(bad.stream().anyMatch(p -> p.contains("pages_per_claim")));
        assertTrue(
                SiteRules.check(
                                Map.of(SiteRules.CHOIR_RUIN, new Offers.Site(List.of(), 1)),
                                entries)
                        .stream()
                        .anyMatch(p -> p.contains("pages: 0")));
        List<String> builtIn =
                SiteRules.builtIn(
                        new Offers.Site(List.of("vftv:choir/two", "vftv:choir/road"), 6), entries);
        assertTrue(builtIn.stream().anyMatch(p -> p.contains("must be first")), builtIn.toString());
        assertTrue(builtIn.stream().anyMatch(p -> p.contains("outside Stage 3")));
        assertTrue(builtIn.stream().anyMatch(p -> p.contains("beneath_the_notes is missing")));
    }

    @Test
    void tuningDefaultsAndRejections() {
        WorldgenTuning t = WorldgenTuning.parse("{}");
        assertEquals(WorldgenTuning.DEFAULTS, t);
        assertEquals(24, t.homeRadius());
        assertEquals(100, t.chantInterval());
        assertEquals(8, t.chantRadius());
        assertEquals(6, t.chantMaxTargets());
        assertEquals(120, t.chantDuration());
        assertEquals(0, t.chantAmplifier());
        assertEquals(16, WorldgenTuning.parse("{\"cantor.radius\": 16}").chantRadius());
        assertEquals(t, WorldgenTuning.parse(t.toJson().toString()));
        for (String bad :
                List.of(
                        "{\"cantor.radius\": 17}",
                        "{\"cantor.radius\": 1}",
                        "{\"cantor.radius\": 2.5}",
                        "{\"cantor.radius\": \"8\"}",
                        "{\"nope\": 1}",
                        "[]",
                        "{")) {
            assertThrows(IllegalArgumentException.class, () -> WorldgenTuning.parse(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> new WorldgenTuning(List.of(1)));
    }
}
