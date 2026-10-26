package com.andrewbrogoff.vftv.core.research.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** The affordability path schema and its replay rules, on the model fixtures' small world. */
class AffordabilityPathTest {
    private static final Knowledge.Rules RULES =
            new Knowledge.Rules() {
                @Override
                public List<String> prerequisites(String aspect) {
                    return Fixtures.aspects().parents(aspect);
                }

                @Override
                public int order(String aspect) {
                    return prerequisites(aspect).isEmpty()
                            ? 0
                            : 1
                                    + prerequisites(aspect).stream()
                                            .mapToInt(this::order)
                                            .max()
                                            .orElse(0);
                }

                @Override
                public int bonus(String aspect) {
                    return 0;
                }
            };

    private static AffordabilityPath.World world(Map<String, ResearchEntry> entries) {
        return new AffordabilityPath.World(
                entries,
                Fixtures.aspects(),
                RULES,
                key ->
                        switch (key) {
                            case "item:test:torch" ->
                                    Map.of("vftv:lux", 2, "vftv:aer", 1, "vftv:ignis", 1);
                            case "item:test:stone" -> Map.of("vftv:terra", 2);
                            default -> Map.of();
                        },
                id ->
                        id.equals("vftv:starter")
                                ? Map.of("vftv:aer", 32, "vftv:ignis", 32)
                                : Map.of(),
                (key, tag) -> false,
                WarpTuning.DEFAULTS,
                ResearchTuning.DEFAULTS.standard());
    }

    private static Map<String, ResearchEntry> fixtures() {
        Map<String, Fixtures.B> w = Fixtures.world();
        Fixtures.B gate = Fixtures.b(3, "vftv:ruins", 2, 0, EntryKind.FIND).parents("vftv:ruins");
        gate.triggers.add(new Trigger.WarpAtLeast(Trigger.WarpKind.TOTAL, 3));
        w.put("vftv:gate", gate);
        return Fixtures.build(w);
    }

    private static final String STAGE1 =
            """
            {"schema": "vftv:affordability_path/1", "stage": 1,
             "sources": {
               "geode": {"advancement": "vftv:find/resonance", "count": 1, "why": "geodes"},
               "torch": {"scan": "item:test:torch", "count": 1, "why": "craftable"},
               "starter": {"node": "vftv:starter", "count": 2,
                           "requires": {"research": ["vftv:commonplace_book"]}, "why": "#17"},
               "drip": {"advancement": "vftv:find/distillation", "count": 1, "why": "dripstone"}
             },
             "steps": [STEPS]}
            """;

    private static AffordabilityPath path(String steps) {
        return AffordabilityPath.parse(STAGE1.replace("STEPS", steps));
    }

    @Test
    void aStageOnePathReplaysAndOpensTheNextStage() {
        AffordabilityPath p =
                path(
                        """
                        {"use": "geode"}, {"use": "torch"}, {"use": "starter"},
                        {"learn": "vftv:taking_notes"}, {"use": "drip"}""");
        assertEquals(1, p.stage());
        assertEquals(4, p.sources().size());
        AffordabilityPath.State end = p.replay(world(fixtures()), AffordabilityPath.State.EMPTY);
        assertTrue(end.research().isLearned("vftv:taking_notes"));
        assertTrue(end.research().isLatchedOrLearned("vftv:distillation"));
        assertEquals(2, end.research().stage());
        assertTrue(
                end.knowledge().known().containsAll(List.of("vftv:aer", "vftv:ignis", "vftv:lux")));
    }

    @Test
    void theReplayRejectsEachBrokenRule() {
        AffordabilityPath.World w = world(fixtures());
        Map<String, String> broken =
                Map.of(
                        "{\"use\": \"geode\"}, {\"learn\": \"vftv:resonance\"}",
                        "names no note",
                        "{\"use\": \"geode\"}, {\"use\": \"geode\"}",
                        "beyond its count",
                        "{\"use\": \"starter\"}",
                        "before its requires",
                        "{\"use\": \"geode\"}, {\"learn\": \"vftv:taking_notes\"}",
                        "not eligible",
                        "{\"use\": \"geode\"}, {\"use\": \"drip\"}",
                        "never committed",
                        "{\"use\": \"geode\"}, {\"use\": \"torch\"}, {\"learn\": \"vftv:taking_notes\"}",
                        "opener");
        broken.forEach(
                (steps, why) -> {
                    IllegalArgumentException e =
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> path(steps).replay(w, AffordabilityPath.State.EMPTY),
                                    steps);
                    assertTrue(
                            Objects.requireNonNull(e.getMessage()).contains(why),
                            steps + ": " + e.getMessage());
                });
    }

    @Test
    void nodeUsesGetFreshIdentitiesAndTheRealDiscovery() {
        AffordabilityPath p =
                path(
                        """
                        {"use": "geode"}, {"use": "starter"}, {"use": "starter"}, {"use": "torch"},
                        {"learn": "vftv:taking_notes"}, {"use": "drip"}""");
        AffordabilityPath.State end = p.replay(world(fixtures()), AffordabilityPath.State.EMPTY);
        assertEquals(65, end.knowledge().pool().get("vftv:aer"), "two first strikes and the torch");
        assertTrue(end.knowledge().scans().containsKey("node:starter#2"));
    }

    @Test
    void eachStageStartsFromThePreviousStagesEndAndWarpGatesLatchInOrder() {
        Map<String, ResearchEntry> entries = fixtures();
        AffordabilityPath.World w = world(entries);
        AffordabilityPath.State one =
                path("""
                        {"use": "geode"}, {"use": "torch"}, {"learn": "vftv:taking_notes"},
                        {"use": "drip"}""")
                        .replay(w, AffordabilityPath.State.EMPTY);
        String stage2 =
                """
                {"schema": "vftv:affordability_path/1", "stage": 2,
                 "sources": {"ruin": {"advancement": "vftv:find/ruins", "count": 1, "why": "a ruin"}},
                 "steps": [{"use": "ruin"}]}""";
        AffordabilityPath.State two = AffordabilityPath.parse(stage2).replay(w, one);
        assertEquals(3, two.research().stage());
        assertThrows(
                IllegalArgumentException.class,
                () -> AffordabilityPath.parse(stage2).replay(w, AffordabilityPath.State.EMPTY),
                "Stage 2 can't start from nothing");
        String stage3 =
                """
                {"schema": "vftv:affordability_path/1", "stage": 3,
                 "sources": {
                   "page": {"choir_page": "vftv:choir/beneath_the_notes", "site": "vftv:choir_ruin",
                            "count": 1, "why": "the first batch"},
                   "threshold": {"advancement": "vftv:find/choirs_road", "count": 1, "why": "a bell"}
                 },
                 "steps": [%s]}""";
        String ordered =
                """
                {"use": "page"}, {"listen": "vftv:being_heard"},
                {"listen": "vftv:silence_beneath"}, {"use": "threshold"}""";
        AffordabilityPath.State three =
                AffordabilityPath.parse(stage3.formatted(ordered)).replay(w, two);
        assertEquals(3, three.temporary());
        assertEquals(4, three.lasting());
        assertTrue(three.research().latched().contains("vftv:gate"), "the gate latched");
        IllegalArgumentException missing =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                AffordabilityPath.parse(
                                                stage3.formatted(
                                                        "{\"use\": \"page\"}, {\"use\":"
                                                                + " \"threshold\"}"))
                                        .replay(w, two));
        assertTrue(
                Objects.requireNonNull(missing.getMessage()).contains("warp gate never latches"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AffordabilityPath.parse(
                                        stage3.formatted("{\"listen\": \"vftv:silence_beneath\"}"))
                                .replay(w, two),
                "a secret listen before its page is not eligible");
        Map<String, Fixtures.B> milestoneWorld = Fixtures.world();
        Fixtures.B deeper =
                Fixtures.b(3, "vftv:ruins", 3, 3, EntryKind.LISTEN).parents("vftv:ruins");
        deeper.warp = new ResearchEntry.Warp(0, 0, 2);
        milestoneWorld.put("vftv:deeper", deeper);
        IllegalArgumentException milestone =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                AffordabilityPath.parse(
                                                stage3.formatted("{\"listen\": \"vftv:deeper\"}"))
                                        .replay(world(Fixtures.build(milestoneWorld)), two));
        assertTrue(Objects.requireNonNull(milestone.getMessage()).contains("milestone"));
    }

    @Test
    void rewardsTeachAndTheirDiscoveriesAreEvidence() {
        Map<String, Fixtures.B> w = Fixtures.world();
        Objects.requireNonNull(w.get("vftv:taking_notes")).rewards.put("vftv:motus", 1);
        Fixtures.B moving =
                Fixtures.b(1, "vftv:resonance", 3, 3, EntryKind.FIND).parents("vftv:resonance");
        moving.triggers.add(new Trigger.Discovered("vftv:motus"));
        w.put("vftv:moving", moving);
        AffordabilityPath.State end =
                path("""
                        {"use": "geode"}, {"use": "torch"}, {"learn": "vftv:taking_notes"},
                        {"use": "drip"}""")
                        .replay(world(Fixtures.build(w)), AffordabilityPath.State.EMPTY);
        assertTrue(end.knowledge().known().containsAll(List.of("vftv:ordo", "vftv:motus")));
        assertTrue(end.research().isLearned("vftv:moving"), "the discovery latched a find");
        assertTrue(end.research().rewarded().contains("vftv:taking_notes"));
    }

    @Test
    void aSourceThatCannotGiveIsRejected() {
        String file =
                """
                {"schema": "vftv:affordability_path/1", "stage": 1,
                 "sources": {
                   "page": {"choir_page": "vftv:choir/beneath_the_notes", "site": "vftv:choir_ruin",
                            "count": 1, "why": "too early"},
                   "dust": {"scan": "item:test:nothing", "count": 1, "why": "no values"}
                 },
                 "steps": [{"use": "STEP"}]}""";
        AffordabilityPath.World w = world(fixtures());
        for (String step : List.of("page", "dust")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            AffordabilityPath.parse(file.replace("STEP", step))
                                    .replay(w, AffordabilityPath.State.EMPTY),
                    step);
        }
    }

    @Test
    void theSchemaRejectsMalformedFiles() {
        for (String bad :
                List.of(
                        "{}",
                        "{\"schema\": \"vftv:affordability_path/2\", \"stage\": 1, \"sources\": {}, \"steps\": []}",
                        "{\"schema\": \"vftv:affordability_path/1\", \"stage\": 5, \"sources\": {}, \"steps\": []}",
                        "{\"schema\": \"vftv:affordability_path/1\", \"stage\": 1, \"sources\": {},"
                                + " \"steps\": [{\"use\": \"nowhere\"}]}",
                        "{\"schema\": \"vftv:affordability_path/1\", \"stage\": 1, \"sources\":"
                                + " {\"a\": {\"scan\": \"item:x\", \"node\": \"vftv:y\", \"count\": 1,"
                                + " \"why\": \"w\"}}, \"steps\": []}",
                        "{\"schema\": \"vftv:affordability_path/1\", \"stage\": 1, \"sources\":"
                                + " {\"a\": {\"scan\": \"item:x\", \"count\": 65, \"why\": \"w\"}},"
                                + " \"steps\": []}",
                        "{\"schema\": \"vftv:affordability_path/1\", \"stage\": 1, \"sources\":"
                                + " {\"a\": {\"choir_page\": \"vftv:p\", \"count\": 1, \"why\": \"w\"}},"
                                + " \"steps\": []}",
                        "{\"schema\": \"vftv:affordability_path/1\", \"stage\": 1, \"sources\":"
                                + " {\"a\": {\"scan\": \"item:x\", \"count\": 1, \"why\": \"\"}},"
                                + " \"steps\": []}",
                        "{\"schema\": \"vftv:affordability_path/1\", \"stage\": 1, \"sources\": {},"
                                + " \"steps\": [{\"learn\": \"a:b\", \"use\": \"c\"}]}",
                        "{\"schema\": \"vftv:affordability_path/1\", \"stage\": 1, \"sources\": {},"
                                + " \"steps\": [], \"extra\": 1}")) {
            assertThrows(IllegalArgumentException.class, () -> AffordabilityPath.parse(bad), bad);
        }
    }
}
