package com.andrewbrogoff.vftv.core.research.model;

import static com.andrewbrogoff.vftv.core.research.model.Fixtures.b;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.research.ResearchSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Triggers, latching and the resolution (research spec, "Triggers" and "Stage gates"). */
class ResolutionTest {
    private static final String PAGE = "vftv:choir/beneath_the_notes";

    /**
     * The fixture world plus a warp-gated secret, a scan find, a discovery find and free chains.
     */
    private static Map<String, ResearchEntry> world() {
        Map<String, Fixtures.B> w = Fixtures.world();
        Fixtures.B gated = b(3, "vftv:ruins", 2, 0, EntryKind.FREE).parents("vftv:ruins");
        gated.secret = true;
        gated.reveal.add(new Trigger.WarpAtLeast(Trigger.WarpKind.LASTING_PLUS_PERMANENT, 4));
        gated.hint("vftv:ruins");
        w.put("vftv:gated", gated);
        Fixtures.B knots = b(1, "vftv:resonance", 1, 3, EntryKind.FIND).parents("vftv:resonance");
        knots.triggers.add(new Trigger.Scanned(null, null, true));
        knots.triggers.add(new Trigger.Scanned("item:minecraft:torch", null, false));
        knots.triggers.add(new Trigger.Scanned(null, "item:#c:gems", false));
        w.put("vftv:knots", knots);
        Fixtures.B heard = b(1, "vftv:resonance", 2, 3, EntryKind.FIND).parents("vftv:resonance");
        heard.triggers.add(new Trigger.Discovered("vftv:lux"));
        w.put("vftv:heard_lux", heard);
        w.put(
                "vftv:a_second",
                b(1, "vftv:resonance", 3, 3, EntryKind.FREE).parents("vftv:commonplace_book"));
        return Fixtures.build(w);
    }

    private final Map<String, ResearchEntry> entries = world();
    private final TriggerIndex index = new TriggerIndex(entries);

    private ResearchRecord resolve(ResearchRecord r) {
        return Resolution.resolve(entries, r).record();
    }

    private ResearchRecord latch(ResearchRecord r, Evidence e) {
        return index.latch(r, e);
    }

    @Test
    void stageOneFromItsFind() {
        ResearchRecord latched =
                latch(ResearchRecord.EMPTY, new Evidence.Advancement("vftv:find/resonance"));
        assertEquals(Set.of("vftv:resonance"), latched.latched());
        Resolution.Outcome o = Resolution.resolve(entries, latched);
        assertEquals(1, o.record().stage());
        assertEquals(
                List.of(
                        new Resolution.Staged(0, 1, ResearchSource.FOUND),
                        new Resolution.Learned("vftv:resonance", ResearchSource.FOUND),
                        new Resolution.Learned("vftv:commonplace_book", ResearchSource.FREE),
                        new Resolution.Learned("vftv:a_second", ResearchSource.FREE)),
                o.steps());
    }

    @Test
    void aFreeEntryWaitsForItsParentWithinTheStep() {
        // a_second sorts before commonplace_book but needs it: it is learned in the next round.
        ResearchRecord r = ResearchRecord.EMPTY.withStage(1).learn("vftv:resonance");
        List<Resolution.Step> steps = Resolution.resolve(entries, r).steps();
        assertEquals(
                List.of(
                        new Resolution.Learned("vftv:commonplace_book", ResearchSource.FREE),
                        new Resolution.Learned("vftv:a_second", ResearchSource.FREE)),
                steps);
    }

    @Test
    void aLaterOpenerLatchedEarlyCascadesInOrder() {
        ResearchRecord r = latch(ResearchRecord.EMPTY, new Evidence.Advancement("vftv:find/ruins"));
        assertEquals(Set.of("vftv:ruins"), r.latched());
        r = resolve(r);
        assertEquals(0, r.stage());
        r = resolve(latch(r, new Evidence.Advancement("vftv:find/resonance")));
        assertEquals(1, r.stage());
        assertFalse(r.isLearned("vftv:ruins"));
        Resolution.Outcome o =
                Resolution.resolve(
                        entries, latch(r, new Evidence.Advancement("vftv:find/distillation")));
        assertEquals(3, o.record().stage());
        assertEquals(
                List.of(
                        new Resolution.Staged(1, 2, ResearchSource.FOUND),
                        new Resolution.Learned("vftv:distillation", ResearchSource.FOUND),
                        new Resolution.Staged(2, 3, ResearchSource.FOUND),
                        new Resolution.Learned("vftv:ruins", ResearchSource.FOUND)),
                o.steps());
    }

    @Test
    void anOpenerWaitsForItsParents() {
        ResearchRecord r =
                ResearchRecord.EMPTY.withStage(1).latch("vftv:distillation", "vftv:ruins");
        assertEquals(1, resolve(r).stage());
        assertEquals(3, resolve(r.learn("vftv:resonance")).stage());
    }

    @Test
    void aTippedPageRevealsItsSecretBeforeVisibilityAndIsLearnedInItsStage() {
        ResearchRecord r = latch(ResearchRecord.EMPTY, new Evidence.ChoirPage(PAGE));
        assertEquals(Set.of(PAGE), r.latched());
        assertEquals(Set.of("vftv:silence_beneath"), r.revealLatched());
        r = resolve(r);
        assertEquals(Set.of("vftv:silence_beneath"), r.revealed());
        assertFalse(r.isLearned(PAGE));
        r = resolve(r.withStage(3));
        assertTrue(r.isLearned(PAGE));
        assertFalse(r.isLearned("vftv:silence_beneath"), "a listen is never learned by resolution");
    }

    @Test
    void warpGatesCountTheirKind() {
        Trigger.WarpAtLeast lpp =
                new Trigger.WarpAtLeast(Trigger.WarpKind.LASTING_PLUS_PERMANENT, 4);
        Trigger.WarpAtLeast total = new Trigger.WarpAtLeast(Trigger.WarpKind.TOTAL, 4);
        assertTrue(new Evidence.Warp(4, 4).matches(lpp));
        assertFalse(new Evidence.Warp(9, 3).matches(lpp));
        assertTrue(new Evidence.Warp(4, 0).matches(total));
        assertFalse(new Evidence.Warp(3, 3).matches(total));
        assertFalse(new Evidence.Warp(9, 9).matches(new Trigger.Discovered("vftv:lux")));
        ResearchRecord r = latch(ResearchRecord.EMPTY, new Evidence.Warp(4, 3));
        assertSame(ResearchRecord.EMPTY, r);
        r = resolve(latch(r, new Evidence.Warp(4, 4)));
        assertEquals(Set.of("vftv:gated"), r.revealed(), "latched in Stage 0, kept");
    }

    @Test
    void scansMatchAKeyATagOrAnyNode() {
        Evidence.Scanned node = new Evidence.Scanned("node:7", tag -> true);
        Evidence.Scanned torch = new Evidence.Scanned("item:minecraft:torch", tag -> false);
        Evidence.Scanned gem =
                new Evidence.Scanned("item:minecraft:emerald", "item:#c:gems"::equals);
        Evidence.Scanned block = new Evidence.Scanned("block:minecraft:emerald_ore", tag -> true);
        assertTrue(node.node());
        assertEquals("item", torch.kind());
        for (Evidence.Scanned e : List.of(node, torch, gem)) {
            assertEquals(Set.of("vftv:knots"), latch(ResearchRecord.EMPTY, e).latched(), e.key());
        }
        assertSame(
                ResearchRecord.EMPTY, latch(ResearchRecord.EMPTY, block), "a tag of another kind");
        assertSame(
                ResearchRecord.EMPTY,
                latch(
                        ResearchRecord.EMPTY,
                        new Evidence.Scanned("item:minecraft:dirt", t -> false)));
        assertFalse(node.matches(new Trigger.Advancement("vftv:find/resonance")));
    }

    @Test
    void discoveriesAndAdvancementsMatchTheirId() {
        assertEquals(
                Set.of("vftv:heard_lux"),
                latch(ResearchRecord.EMPTY, new Evidence.Discovered("vftv:lux")).latched());
        assertSame(
                ResearchRecord.EMPTY,
                latch(ResearchRecord.EMPTY, new Evidence.Discovered("vftv:aer")));
        assertFalse(new Evidence.Discovered("vftv:lux").matches(new Trigger.ChoirPage(PAGE)));
        assertFalse(new Evidence.ChoirPage(PAGE).matches(new Trigger.Discovered("vftv:lux")));
        assertFalse(
                new Evidence.Advancement("x:y")
                        .matches(new Trigger.Advancement("vftv:find/ruins")));
    }

    @Test
    void aRepeatedLatchChangesNothing() {
        ResearchRecord once = latch(ResearchRecord.EMPTY, new Evidence.ChoirPage(PAGE));
        assertSame(once, latch(once, new Evidence.ChoirPage(PAGE)));
    }

    @Test
    void loginCatchUpLatchesWhatHoldsNow() {
        ResearchRecord r =
                index.catchUp(
                        ResearchRecord.EMPTY,
                        t ->
                                t instanceof Trigger.Advancement a
                                        && a.advancement().equals("vftv:find/resonance"));
        assertEquals(Set.of("vftv:resonance"), r.latched());
        assertEquals(Set.of(), r.revealLatched(), "no page is latched or learned");
        ResearchRecord learnedPage = ResearchRecord.EMPTY.learn(PAGE);
        ResearchRecord caught = index.catchUp(learnedPage, t -> false);
        assertEquals(Set.of(PAGE), caught.latched());
        assertEquals(Set.of("vftv:silence_beneath"), caught.revealLatched());
        assertSame(caught, index.catchUp(caught, t -> false));
    }

    @Test
    void inertIdsAndAnEmptyWorldResolveQuietly() {
        ResearchRecord r = ResearchRecord.EMPTY.learn("vftv:gone").latch("vftv:gone_too");
        Resolution.Outcome o = Resolution.resolve(Map.of(), r);
        assertSame(r, o.record());
        assertEquals(List.of(), o.steps());
        assertEquals(List.of(), Resolution.resolve(entries, ResearchRecord.EMPTY).steps());
    }
}
