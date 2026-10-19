package com.andrewbrogoff.vftv.core.research.model;

import static com.andrewbrogoff.vftv.core.research.model.Fixtures.b;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class EntryRulesTest {
    private static final Fixtures.B UNUSED = new Fixtures.B();

    /** The world with one change, checked to refuse with an error containing {@code expected}. */
    private static void refuses(Consumer<Map<String, Fixtures.B>> change, String expected) {
        Map<String, Fixtures.B> world = Fixtures.world();
        change.accept(world);
        List<String> errors = Fixtures.validate(world).errors();
        assertTrue(
                errors.stream().anyMatch(e -> e.contains(expected)),
                () -> "expected an error containing \"" + expected + "\", got " + errors);
    }

    private static Fixtures.B get(Map<String, Fixtures.B> world, String id) {
        return world.getOrDefault(id, UNUSED);
    }

    @Test
    void theFixtureIsValid() {
        EntryRules.Report report = Fixtures.validate(Fixtures.world());
        assertEquals(List.of(), report.errors());
        assertEquals(List.of(), report.warnings());
    }

    @Test
    void everyReferenceMustExist() {
        refuses(
                w -> get(w, "vftv:taking_notes").parents("vftv:nowhere"),
                "parent vftv:nowhere does not exist");
        refuses(
                w -> get(w, "vftv:taking_notes").hidden.add("vftv:gone"),
                "parent vftv:gone does not exist");
        refuses(
                w -> get(w, "vftv:ruins").hint("vftv:nobody"),
                "hinted_by vftv:nobody does not exist");
        refuses(
                w ->
                        get(w, "vftv:resonance")
                                .triggers
                                .add(new Trigger.ChoirPage("vftv:choir/lost")),
                "page vftv:choir/lost does not exist");
        refuses(
                w -> get(w, "vftv:resonance").triggers.add(new Trigger.Discovered("vftv:nothing")),
                "aspect vftv:nothing does not exist");
        refuses(
                w -> get(w, "vftv:taking_notes").category = "vftv:elsewhere",
                "category vftv:elsewhere");
        refuses(
                w -> get(w, "vftv:being_heard").rewards.put("vftv:nothing", 1),
                "aspect vftv:nothing");
    }

    @Test
    void parentsAreAcyclicAndNeverLater() {
        refuses(
                w -> get(w, "vftv:commonplace_book").parents("vftv:taking_notes"),
                "parents form a cycle");
        refuses(
                w -> get(w, "vftv:taking_notes").parents("vftv:ruins"),
                "parent vftv:ruins is in a later Stage");
    }

    @Test
    void anchorsBelongToNotesAndMustConnectThroughSomething() {
        refuses(
                w -> get(w, "vftv:commonplace_book").anchors("vftv:aer", "vftv:ignis"),
                "anchors on a free");
        refuses(
                w -> get(w, "vftv:commonplace_book").skip.put("vftv:aer", 1),
                "skip_cost on a free");
        refuses(w -> get(w, "vftv:taking_notes").anchors = List.of("vftv:aer"), "2 to 5 anchors");
        refuses(
                w -> get(w, "vftv:taking_notes").anchors = List.of("vftv:aer", "vftv:aer"),
                "not distinct");
        refuses(
                w -> get(w, "vftv:taking_notes").anchors = List.of("vftv:aer", "vftv:nothing"),
                "are not aspects");
        // Lux's parent is Aer: the chain has no cell between them.
        refuses(
                w -> get(w, "vftv:taking_notes").anchors = List.of("vftv:aer", "vftv:lux"),
                "reference vector is empty");
    }

    @Test
    void anchorsThatDontConnectRefuse() {
        Map<String, Fixtures.B> world = Fixtures.world();
        get(world, "vftv:taking_notes").anchors = List.of("vftv:aer", "vftv:ignis");
        Map<String, java.util.List<String>> taught = new java.util.TreeMap<>();
        taught.put("vftv:aer", List.of());
        taught.put("vftv:ignis", List.of());
        EntryRules.Report report =
                EntryRules.validate(
                        Fixtures.build(world),
                        Fixtures.categories(),
                        new AspectGraph(taught),
                        java.util.Set.of(),
                        java.util.Set.of());
        assertTrue(
                report.errors().stream().anyMatch(e -> e.contains("don't connect")),
                report::toString);
    }

    @Test
    void kindsCarryOnlyTheirFields() {
        refuses(
                w -> get(w, "vftv:commonplace_book").warp = new ResearchEntry.Warp(1, 0, 0),
                "warp on a free");
        refuses(w -> get(w, "vftv:resonance").triggers.clear(), "a find with no triggers");
        refuses(
                w -> get(w, "vftv:taking_notes").triggers.add(new Trigger.Advancement("vftv:x")),
                "triggers on a note");
        refuses(w -> get(w, "vftv:taking_notes").opensStage = 1, "opens_stage on a note");
    }

    @Test
    void secretsAndReveals() {
        refuses(w -> get(w, "vftv:silence_beneath").reveal.clear(), "secret with no reveal");
        refuses(
                w -> get(w, "vftv:being_heard").reveal.add(new Trigger.Advancement("vftv:x")),
                "a reveal but no secret");
        refuses(
                w -> {
                    Fixtures.B page = get(w, "vftv:choir/beneath_the_notes");
                    page.secret = true;
                    page.reveal.add(new Trigger.ChoirPage("vftv:choir/beneath_the_notes"));
                    page.hint("vftv:ruins");
                },
                "it reveals itself");
        refuses(
                // beneath_the_notes waits on silence_beneath, which is revealed by it.
                w -> get(w, "vftv:choir/beneath_the_notes").hidden.add("vftv:silence_beneath"),
                "reveal dependencies form a cycle");
        refuses(w -> get(w, "vftv:silence_beneath").hintedBy = null, "needs a hinted_by");
    }

    @Test
    void hintsAreVisibleAndNeverLater() {
        refuses(w -> get(w, "vftv:silence_beneath").hint("vftv:silence_beneath"), "is secret");
        refuses(w -> get(w, "vftv:ruins").hint("vftv:choirs_road"), "is in a later Stage");
        refuses(w -> get(w, "vftv:ruins").hint("vftv:commonplace_book"), "not in the Stage before");
        refuses(w -> get(w, "vftv:distillation").hintedBy = null, "needs a hinted_by");
    }

    @Test
    void exactlyOneOpenerPerStageAndOpenersOpenTheirOwnStage() {
        refuses(w -> get(w, "vftv:ruins").opensStage = 2, "Stage 3 has 0 openers");
        refuses(
                w -> {
                    Fixtures.B again =
                            b(1, "vftv:resonance", 5, 5, EntryKind.FIND).opens(1, "vftv:x");
                    w.put("vftv:again", again);
                },
                "Stage 1 has 2 openers");
        refuses(
                w -> {
                    Fixtures.B road = get(w, "vftv:choirs_road");
                    road.opensStage = 4;
                    road.stage = 3;
                    road.category = "vftv:ruins";
                    road.x = 9;
                },
                "opens Stage 4 but is in Stage 3");
        refuses(
                w -> {
                    w.put("vftv:late", b(4, "vftv:choirs_road", 3, 3, EntryKind.FREE));
                    get(w, "vftv:choirs_road").parents("vftv:late");
                },
                "an opener's parent vftv:late is not in an earlier Stage");
    }

    @Test
    void positionsAndCategories() {
        refuses(
                w -> get(w, "vftv:taking_notes").y = 1,
                "position collides with vftv:commonplace_book");
        refuses(w -> get(w, "vftv:taking_notes").stage = 2, "differs from its category's 1");
    }

    @Test
    void actionPagesFollowTheKind() {
        refuses(w -> get(w, "vftv:taking_notes").pages.removeLast(), "0 action pages");
        refuses(
                w -> get(w, "vftv:commonplace_book").pages.add(new Page.Action()),
                "1 action pages");
    }

    @Test
    void requiredBuiltInsMustExist() {
        refuses(
                w -> w.remove("vftv:commonplace_book"),
                "required built-in entry vftv:commonplace_book");
        Map<String, Fixtures.B> world = Fixtures.world();
        Map<String, CategoryEntry> categories = Fixtures.categories();
        categories.remove("vftv:choir_pages");
        EntryRules.Report report =
                EntryRules.validate(
                        Fixtures.build(world),
                        categories,
                        Fixtures.aspects(),
                        java.util.Set.of(),
                        java.util.Set.of("vftv:choir_pages"));
        assertTrue(
                report.errors().stream().anyMatch(e -> e.contains("required built-in category")));
    }

    @Test
    void onlyTheChoirPagesCategoryHasNoStage() {
        ResearchEntry.Icon icon = new ResearchEntry.Icon(false, "a:b");
        Map<String, CategoryEntry> categories = Fixtures.categories();
        categories.put("x:extra", new CategoryEntry(null, icon, 9));
        categories.put("vftv:choir_pages", new CategoryEntry(3, icon, 4));
        List<String> errors =
                EntryRules.validate(
                                Fixtures.build(Fixtures.world()),
                                categories,
                                Fixtures.aspects(),
                                java.util.Set.of(),
                                java.util.Set.of())
                        .errors();
        assertTrue(
                errors.stream().anyMatch(e -> e.contains("research category x:extra")),
                errors::toString);
        assertTrue(
                errors.stream().anyMatch(e -> e.contains("research category vftv:choir_pages")),
                errors::toString);
    }

    @Test
    void warnings() {
        Map<String, Fixtures.B> world = Fixtures.world();
        Fixtures.B early =
                b(2, "vftv:distillation", 3, 3, EntryKind.LISTEN).parents("vftv:distillation");
        early.warp = new ResearchEntry.Warp(0, 1, 0);
        world.put("vftv:early", early);
        List<String> warnings = Fixtures.validate(world).warnings();
        assertTrue(
                warnings.stream().anyMatch(w -> w.contains("warp on a Stage 2 entry")),
                warnings::toString);
        assertTrue(
                warnings.stream().anyMatch(w -> w.contains("without vftv:being_heard")),
                warnings::toString);
    }

    @Test
    void aLongChainWarns() {
        // A path graph: each aspect links only to its predecessor, so the chain is 161 cells.
        Map<String, List<String>> line = new java.util.TreeMap<>();
        line.put("x:a0", List.of());
        for (int i = 1; i <= 160; i++) {
            line.put("x:a" + i, List.of("x:a" + (i - 1)));
        }
        Map<String, Fixtures.B> world = Fixtures.world();
        get(world, "vftv:taking_notes").anchors = List.of("x:a0", "x:a160");
        EntryRules.Report report =
                EntryRules.validate(
                        Fixtures.build(world),
                        Fixtures.categories(),
                        new AspectGraph(line),
                        java.util.Set.of(),
                        java.util.Set.of());
        assertTrue(
                report.warnings().stream().anyMatch(w -> w.contains("may not fit")),
                report::toString);
    }

    @Test
    void aRevealedByMustBeAListenWithLastingWarp() {
        refuses(
                w -> w.remove("vftv:silence_beneath"),
                "aspect vftv:vacuos: its revealed_by vftv:silence_beneath does not exist");
        refuses(
                w -> get(w, "vftv:silence_beneath").warp = new ResearchEntry.Warp(4, 0, 0),
                "is not a listen entry with lasting warp");
        refuses(
                w -> get(w, "vftv:silence_beneath").kind = EntryKind.NOTE,
                "is not a listen entry with lasting warp");
        Map<String, Fixtures.B> world = Fixtures.world();
        get(world, "vftv:silence_beneath").warp = new ResearchEntry.Warp(0, 4, 1);
        List<String> warnings = Fixtures.validate(world).warnings();
        assertTrue(
                warnings.stream()
                        .anyMatch(w -> w.contains("revealed_by") && w.contains("permanent")),
                warnings::toString);
    }

    @Test
    void milestoneRules() {
        Map<String, Fixtures.B> world = Fixtures.world();
        Fixtures.B under =
                b(3, "vftv:ruins", 2, 2, EntryKind.LISTEN).parents("vftv:silence_beneath");
        under.warp = new ResearchEntry.Warp(0, 0, 2);
        world.put("vftv:under_the_silence", under);
        Map<String, Integer> milestones = Map.of("vftv:under_the_silence", 2, "vftv:joining", 5);
        assertEquals(
                List.of(),
                EntryRules.milestoneProblems(Fixtures.build(world), milestones, "vftv:joining"));

        under.warp = new ResearchEntry.Warp(1, 1, 3);
        world.put(
                "vftv:after",
                b(3, "vftv:ruins", 3, 3, EntryKind.FREE).parents("vftv:under_the_silence"));
        List<String> problems =
                EntryRules.milestoneProblems(
                        Fixtures.build(world),
                        Map.of("vftv:under_the_silence", 2, "vftv:ruins", 1, "vftv:joining", 5),
                        "vftv:joining");
        assertEquals(
                List.of(
                        "research entry vftv:under_the_silence: its permanent 3 is not a listed"
                                + " milestone of that amount",
                        "research entry vftv:under_the_silence: a milestone carries temporary or"
                                + " lasting warp",
                        "research entry vftv:under_the_silence: a milestone is a parent of"
                                + " vftv:after",
                        "the warp milestone vftv:ruins names no listen entry"),
                problems);
    }

    @Test
    void cycleFindsNothingInADag() {
        assertNull(EntryRules.cycle(Map.of("a", List.of("b"), "b", List.of())));
        assertEquals(
                List.of("a", "b"),
                EntryRules.cycle(
                        new java.util.TreeMap<>(Map.of("a", List.of("b"), "b", List.of("a")))));
    }
}
