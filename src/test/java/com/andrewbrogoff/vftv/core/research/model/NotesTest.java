package com.andrewbrogoff.vftv.core.research.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.HiddenPrimal;
import com.andrewbrogoff.vftv.core.aspect.AspectEntry;
import com.andrewbrogoff.vftv.core.aspect.BuiltinAspects;
import com.andrewbrogoff.vftv.core.research.model.Note.Cell;
import com.andrewbrogoff.vftv.core.research.model.Note.Placed;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** The puzzle's model: grid geometry, seeds, generation, solving, debit plans and skip prices. */
class NotesTest {
    private static final UUID PLAYER = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final long WORLD = 0x0123456789ABCDEFL;
    private static final List<String> PRIMALS =
            List.of("aer", "aqua", "ignis", "ordo", "perditio", "terra").stream()
                    .map(a -> "vftv:" + a)
                    .toList();

    /** The 37 built-in aspects, Vacuos revealed by {@code vftv:silence_beneath}. */
    static AspectGraph builtin() {
        Map<String, List<String>> taught = new TreeMap<>();
        Map<String, String> revealedBy = new TreeMap<>();
        for (Map.Entry<ResourceKey<Aspect>, AspectEntry> e : BuiltinAspects.ALL.entrySet()) {
            String id = e.getKey().identifier().toString();
            HiddenPrimal hidden = e.getValue().hiddenPrimal();
            List<ResourceKey<Aspect>> parents =
                    hidden != null ? hidden.taughtParents() : e.getValue().parents();
            taught.put(id, parents.stream().map(k -> k.identifier().toString()).toList());
            if (hidden != null) {
                revealedBy.put(id, hidden.revealedBy().toString());
            }
        }
        return new AspectGraph(taught, revealedBy);
    }

    private static Set<String> all(AspectGraph graph) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<ResourceKey<Aspect>, AspectEntry> e : BuiltinAspects.ALL.entrySet()) {
            out.add(e.getKey().identifier().toString());
        }
        assertTrue(out.stream().allMatch(graph::contains));
        return out;
    }

    private static AspectGraph.LinkView taught(AspectGraph graph) {
        return graph.view(id -> false);
    }

    // ---- Grid --------------------------------------------------------------------------------

    @Test
    void spiralsAreCompleteAndContiguousAtEveryRadius() {
        for (int r = 0; r <= 7; r++) {
            List<Cell> spiral = NoteGrid.spiral(r);
            assertEquals(NoteGrid.cells(r), spiral.size());
            assertEquals(spiral.size(), new HashSet<>(spiral).size());
            for (int i = 1; i < spiral.size(); i++) {
                assertEquals(1, NoteGrid.distance(spiral.get(i - 1), spiral.get(i)));
            }
            int radius = r;
            assertTrue(spiral.stream().allMatch(c -> c.distance() <= radius));
        }
        assertEquals(169, NoteGrid.cells(7));
        assertEquals(
                List.of(
                        new Cell(0, 0),
                        new Cell(-1, 1),
                        new Cell(0, 1),
                        new Cell(1, 0),
                        new Cell(1, -1),
                        new Cell(0, -1),
                        new Cell(-1, 0),
                        new Cell(-2, 1),
                        new Cell(-2, 2),
                        new Cell(-1, 2),
                        new Cell(0, 2),
                        new Cell(1, 1),
                        new Cell(2, 0),
                        new Cell(2, -1),
                        new Cell(2, -2),
                        new Cell(1, -2),
                        new Cell(0, -2),
                        new Cell(-1, -1),
                        new Cell(-2, 0)),
                NoteGrid.spiral(2));
    }

    @Test
    void neighbourOrderIsEastThenCounterClockwise() {
        assertEquals(
                List.of(
                        new Cell(1, 0),
                        new Cell(1, -1),
                        new Cell(0, -1),
                        new Cell(-1, 0),
                        new Cell(-1, 1),
                        new Cell(0, 1)),
                NoteGrid.DIRECTIONS);
    }

    @Test
    void everySymmetryPreservesTheGridAdjacencyAndSpiralContiguity() {
        Set<List<Cell>> images = new HashSet<>();
        for (int t = 0; t < 12; t++) {
            List<Cell> image = new ArrayList<>();
            for (Cell c : NoteGrid.spiral(4)) {
                Cell m = NoteGrid.transform(c, t);
                assertEquals(c.distance(), m.distance());
                image.add(m);
            }
            assertEquals(new HashSet<>(NoteGrid.spiral(4)), new HashSet<>(image));
            for (int i = 1; i < image.size(); i++) {
                assertEquals(1, NoteGrid.distance(image.get(i - 1), image.get(i)));
            }
            images.add(image);
        }
        assertEquals(12, images.size());
    }

    // ---- Seeds -------------------------------------------------------------------------------

    @Test
    void seedVectorsMatchTheSpec() {
        long first = Notes.seed(WORLD, PLAYER, 0, "vftv:taking_notes");
        assertEquals(0xF206817A7B0403F2L, first);
        assertDraws(first, 23, 4, 22, 32);
        long second = Notes.seed(WORLD, PLAYER, 1, "vftv:taking_notes");
        assertEquals(0x9782C35A1BA5AE07L, second);
        assertDraws(second, 30, 31, 17, 6);
        long wand = Notes.seed(WORLD, PLAYER, 0, "vftv:first_wand");
        assertEquals(0x234A7342996DEC4DL, wand);
        assertDraws(wand, 18, 16, 28, 15);
    }

    private static void assertDraws(long seed, int... draws) {
        Random random = new Random(seed);
        for (int d : draws) {
            assertEquals(d, random.nextInt(37));
        }
    }

    // ---- Generation --------------------------------------------------------------------------

    @Test
    void paddingInsertsTheBackStepUntilTheSegmentIsLongEnough() {
        assertEquals(List.of("a", "b", "a", "b"), Notes.pad(List.of("a", "b"), 2));
        assertEquals(List.of("a", "x", "b"), Notes.pad(List.of("a", "x", "b"), 2));
        assertEquals(List.of("a", "x", "a", "x", "b"), Notes.pad(List.of("a", "x", "b"), 3));
    }

    @Test
    void theRadiusIsTheSmallestThatFits() {
        assertEquals(3, Notes.fit(3, 23));
        assertEquals(4, Notes.fit(3, 38));
        assertEquals(8, Notes.fit(1, 170));
    }

    @Test
    void theTutorialNoteNeedsNoPaddingAndIsIssuedAtStandard() {
        AspectGraph graph = builtin();
        ResearchTuning t = ResearchTuning.DEFAULTS;
        List<String> anchors = List.of("vftv:aer", "vftv:ignis");
        Set<String> known = Set.of("vftv:aer", "vftv:ignis", "vftv:lux");
        AspectGraph.LinkView view = taught(graph);
        List<List<String>> segments = view.segments(anchors, known);
        assertNotNull(segments);
        assertTrue(segments.stream().allMatch(s -> s.size() - 1 >= t.standard().minDistance()));
        Notes.Generated g =
                Notes.generate(
                        Notes.seed(WORLD, PLAYER, 0, "vftv:taking_notes"),
                        "vftv:taking_notes",
                        anchors,
                        known,
                        view,
                        t,
                        t.standard(),
                        7,
                        msg -> {
                            throw new AssertionError(msg);
                        });
        Note note = g.note();
        assertNotNull(note);
        assertFalse(g.fellBack());
        assertEquals(3, note.radius());
        assertEquals(7, note.revision());
        assertEquals(List.of(), note.placements());
        assertEquals(3, g.plant().size());
        assertTrue(
                NoteGrid.distance(note.anchors().get(0).cell(), note.anchors().get(1).cell()) >= 2);
        assertFalse(Notes.solved(note, view));
        assertTrue(witnessSolves(note, g.plant(), view));
    }

    @Test
    void thePartialKnowledgeRegressionIsIssuedAtEveryPreset() {
        AspectGraph graph = builtin();
        Set<String> known = new HashSet<>(PRIMALS);
        known.addAll(List.of("vftv:metallum", "vftv:instrumentum", "vftv:sonus", "vftv:vacuos"));
        List<String> anchors =
                List.of(
                        "vftv:ignis",
                        "vftv:perditio",
                        "vftv:metallum",
                        "vftv:vacuos",
                        "vftv:instrumentum");
        AspectGraph.LinkView view = taught(graph);
        List<List<String>> segments = view.segments(anchors, known);
        assertNotNull(segments);
        assertEquals(List.of(8, 7, 6, 5), segments.stream().map(List::size).toList());
        assertEquals(23, AspectGraph.join(anchors, segments).size());
        ResearchTuning t = ResearchTuning.DEFAULTS;
        int[] radii = {2, 4, 4};
        for (int i = 0; i < 3; i++) {
            ResearchTuning.Preset p = t.presets().get(i);
            List<String> used = anchors.subList(0, Math.min(anchors.size(), p.maxUsed()));
            Notes.Generated g =
                    Notes.generate(
                            Notes.seed(WORLD, PLAYER, 0, "vftv:test"),
                            "vftv:test",
                            used,
                            known,
                            view,
                            t,
                            p,
                            1,
                            msg -> {});
            Note note = g.note();
            assertNotNull(note, "preset " + i);
            assertEquals(radii[i], note.radius(), "preset " + i);
            assertEquals(p.silentCells(), note.silent().size());
            assertTrue(witnessSolves(note, g.plant(), view));
        }
    }

    @Test
    void theBuiltInBoundHoldsOnTheShippedGraph() {
        AspectGraph graph = builtin();
        Set<String> all = all(graph);
        // With every aspect known the longest shortest path is short; any chain of five anchors
        // stays within 1 + 4 x 36 = 145 cells, so 145 + 12 silent cells fit radius 7.
        int longest = 0;
        List<String> ids = all.stream().sorted().toList();
        for (String a : ids) {
            for (String b : ids) {
                if (!a.equals(b)) {
                    List<List<String>> s = taught(graph).segments(List.of(a, b), all);
                    assertNotNull(s, a + " to " + b);
                    longest = Math.max(longest, s.getFirst().size() - 1);
                }
            }
        }
        assertTrue(longest <= 5, "longest " + longest);
        assertEquals(37, all.size());
        assertTrue(1 + 4 * (all.size() - 1) + Note.MAX_SILENT <= NoteGrid.cells(7));
    }

    @Test
    void randomNotesAreAlwaysIssuedAndTheirWitnessSolves() {
        AspectGraph graph = builtin();
        List<String> ids = all(graph).stream().sorted().toList();
        Random pick = new Random(42);
        int issued = 0;
        for (int i = 0; i < 1500; i++) {
            Set<String> known = new HashSet<>(PRIMALS);
            for (String id : ids) {
                if (pick.nextInt(3) > 0) {
                    known.add(id);
                }
            }
            boolean revealed = pick.nextBoolean();
            AspectGraph.LinkView view = graph.view(e -> revealed);
            List<String> pool = new ArrayList<>(known.stream().sorted().toList());
            int count = 2 + pick.nextInt(4);
            List<String> anchors = new ArrayList<>();
            while (anchors.size() < count) {
                String a = pool.remove(pick.nextInt(pool.size()));
                anchors.add(a);
            }
            ResearchTuning.Preset p = ResearchTuning.DEFAULTS.presets().get(i % 3);
            List<String> used = anchors.subList(0, Math.min(anchors.size(), p.maxUsed()));
            if (!Notes.canBegin(used, known, view)) {
                continue;
            }
            Notes.Generated g =
                    Notes.generate(
                            pick.nextLong(),
                            "vftv:test",
                            used,
                            known,
                            view,
                            ResearchTuning.DEFAULTS,
                            p,
                            1,
                            msg -> {
                                throw new AssertionError(msg);
                            });
            Note note = g.note();
            assertNotNull(note);
            assertTrue(witnessSolves(note, g.plant(), view));
            Set<Cell> taken = new HashSet<>(g.plant().keySet());
            assertTrue(note.silent().stream().noneMatch(taken::contains));
            issued++;
        }
        assertTrue(issued > 500, "issued " + issued);
    }

    @Test
    void aWalkThatCannotFitFallsBack() {
        AspectGraph graph = Fixtures.aspects();
        ResearchTuning.Preset tight =
                new ResearchTuning.Preset(1, 5, 6, 0, Note.PaletteHelp.PARENTS);
        Notes.Generated g =
                Notes.generate(
                        3,
                        "vftv:test",
                        List.of("vftv:aer", "vftv:ignis"),
                        Set.of("vftv:aer", "vftv:ignis", "vftv:lux"),
                        taught(graph),
                        ResearchTuning.DEFAULTS,
                        tight,
                        1,
                        msg -> {});
        Note note = g.note();
        assertNotNull(note);
        assertTrue(g.fellBack());
        assertEquals(1, note.radius());
        assertTrue(witnessSolves(note, g.plant(), taught(graph)));
    }

    @Test
    void aFallbackAtEveryPreset() {
        AspectGraph graph = Fixtures.aspects();
        for (ResearchTuning.Preset p : ResearchTuning.DEFAULTS.presets()) {
            ResearchTuning one = new ResearchTuning(1, 1, 4, 1, BigDecimal.ONE, p, p, p);
            ResearchTuning.Preset far =
                    new ResearchTuning.Preset(1, p.maxUsed(), 6, p.silentCells(), p.paletteHelp());
            Notes.Generated g =
                    Notes.generate(
                            11,
                            "vftv:test",
                            List.of("vftv:aer", "vftv:ignis"),
                            Set.of("vftv:aer", "vftv:ignis", "vftv:lux"),
                            taught(graph),
                            one,
                            far,
                            1,
                            msg -> {});
            assertNotNull(g.note());
            assertTrue(g.fellBack());
        }
    }

    @Test
    void aChainTooLongForTheLargestGridIsRefused() {
        Map<String, List<String>> line = new TreeMap<>();
        line.put("t:a000", List.of());
        line.put("t:b000", List.of());
        String previous = "t:a000";
        for (int i = 1; i < 200; i++) {
            String id = String.format(java.util.Locale.ROOT, "t:c%03d", i);
            line.put(id, List.of(previous, "t:b000"));
            previous = id;
        }
        AspectGraph graph = new AspectGraph(line);
        Set<String> known = new HashSet<>(line.keySet());
        known.remove("t:b000"); // only the line connects
        for (ResearchTuning.Preset p : ResearchTuning.DEFAULTS.presets()) {
            Notes.Generated g =
                    Notes.generate(
                            1,
                            "vftv:test",
                            List.of("t:a000", previous),
                            known,
                            graph.view(e -> false),
                            ResearchTuning.DEFAULTS,
                            p,
                            1,
                            msg -> {});
            assertNull(g.note());
        }
    }

    @Test
    void unknownOrUnconnectedAnchorsCannotBegin() {
        AspectGraph graph = Fixtures.aspects();
        AspectGraph.LinkView view = taught(graph);
        assertTrue(
                Notes.canBegin(
                        List.of("vftv:aer", "vftv:ignis"),
                        Set.of("vftv:aer", "vftv:ignis", "vftv:lux"),
                        view));
        assertFalse(
                Notes.canBegin(
                        List.of("vftv:aer", "vftv:ignis"), Set.of("vftv:aer", "vftv:ignis"), view));
        assertFalse(Notes.canBegin(List.of("vftv:aer", "vftv:lux"), Set.of("vftv:aer"), view));
    }

    /** An independent check: the plant's cells form one linked component holding every anchor. */
    private static boolean witnessSolves(
            Note note, Map<Cell, String> plant, AspectGraph.LinkView view) {
        Map<Cell, Integer> component = new HashMap<>();
        int next = 0;
        for (Cell start : plant.keySet()) {
            if (component.containsKey(start)) {
                continue;
            }
            List<Cell> stack = new ArrayList<>(List.of(start));
            component.put(start, next);
            while (!stack.isEmpty()) {
                Cell at = stack.removeLast();
                for (Cell other : plant.keySet()) {
                    if (!component.containsKey(other)
                            && NoteGrid.distance(at, other) == 1
                            && view.linked(req(plant.get(at)), req(plant.get(other)))) {
                        component.put(other, next);
                        stack.add(other);
                    }
                }
            }
            next++;
        }
        Set<Integer> anchorComponents = new HashSet<>();
        for (Placed a : note.anchors()) {
            if (!a.aspect().equals(plant.get(a.cell()))) {
                return false;
            }
            anchorComponents.add(component.get(a.cell()));
        }
        boolean disjoint = note.silent().stream().noneMatch(plant::containsKey);
        return anchorComponents.size() == 1 && disjoint;
    }

    // ---- Solving -----------------------------------------------------------------------------

    private static Note note(List<Placed> placements) {
        return new Note(
                "vftv:test",
                ResearchTuning.DEFAULTS.snapshot(ResearchTuning.DEFAULTS.standard()),
                3,
                List.of(
                        new Placed(new Cell(0, 0), "vftv:aer"),
                        new Placed(new Cell(2, 0), "vftv:ignis")),
                List.of(),
                placements,
                1);
    }

    @Test
    void theSolveCheckAcceptsStraysAndBranching() {
        AspectGraph.LinkView view = taught(Fixtures.aspects());
        assertFalse(Notes.solved(note(List.of()), view));
        assertTrue(Notes.solved(note(List.of(new Placed(new Cell(1, 0), "vftv:lux"))), view));
        assertTrue(
                Notes.solved(
                        note(
                                List.of(
                                        new Placed(new Cell(1, 0), "vftv:lux"),
                                        new Placed(new Cell(-3, 0), "vftv:terra"),
                                        new Placed(new Cell(1, -1), "vftv:aer"))),
                        view));
        assertFalse(Notes.solved(note(List.of(new Placed(new Cell(1, 0), "vftv:terra"))), view));
    }

    @Test
    void theRevealAddsLinksSoAnOldNoteStaysSolved() {
        AspectGraph graph = Fixtures.aspects();
        Note n =
                new Note(
                        "vftv:test",
                        ResearchTuning.DEFAULTS.snapshot(ResearchTuning.DEFAULTS.standard()),
                        2,
                        List.of(
                                new Placed(new Cell(0, 0), "vftv:vacuos"),
                                new Placed(new Cell(1, 0), "vftv:aqua")),
                        List.of(),
                        List.of(new Placed(new Cell(-1, 0), "vftv:aer")),
                        1);
        assertFalse(Notes.solved(n, graph.view(e -> false)));
        assertTrue(Notes.solved(n, graph.view(e -> e.equals("vftv:silence_beneath"))));
    }

    // ---- Paying ------------------------------------------------------------------------------

    @Test
    void aPlanTakesBalancesFirstThenSynthesisesDepthFirst() {
        AspectGraph graph = Fixtures.aspects();
        AspectGraph.LinkView view = taught(graph);
        DebitPlan plan =
                DebitPlan.of(
                        Map.of("vftv:lux", 3),
                        Map.of("vftv:lux", 1, "vftv:aer", 5, "vftv:ignis", 5),
                        graph,
                        view,
                        1);
        assertTrue(plan.ok());
        assertEquals(Map.of("vftv:lux", 1, "vftv:aer", 2, "vftv:ignis", 2), plan.debits());
        DebitPlan sonus =
                DebitPlan.of(
                        Map.of("vftv:sonus", 1),
                        Map.of("vftv:aer", 2, "vftv:ordo", 1),
                        graph,
                        view,
                        1);
        assertEquals(Map.of("vftv:aer", 2, "vftv:ordo", 1), sonus.debits());
        DebitPlan doubled =
                DebitPlan.of(
                        Map.of("vftv:lux", 1),
                        Map.of("vftv:aer", 2, "vftv:ignis", 1),
                        graph,
                        view,
                        2);
        assertEquals("vftv:ignis", doubled.shortLeaf());
        assertEquals(Map.of(), doubled.debits());
    }

    @Test
    void vacuosIsPaidFromItsTaughtParentsThenFromThePlentifulPrimals() {
        AspectGraph graph = Fixtures.aspects();
        Map<String, Integer> pool =
                Map.of("vftv:aer", 1, "vftv:perditio", 1, "vftv:terra", 9, "vftv:aqua", 9);
        DebitPlan before = DebitPlan.of(Map.of("vftv:vacuos", 1), pool, graph, taught(graph), 1);
        assertEquals(Map.of("vftv:aer", 1, "vftv:perditio", 1), before.debits());
        AspectGraph.LinkView revealed = graph.view(e -> true);
        DebitPlan after = DebitPlan.of(Map.of("vftv:vacuos", 1), pool, graph, revealed, 1);
        assertEquals(Map.of("vftv:aqua", 1, "vftv:terra", 1), after.debits());
        DebitPlan tied =
                DebitPlan.of(
                        Map.of("vftv:vacuos", 1),
                        Map.of("vftv:ordo", 3, "vftv:terra", 3, "vftv:aer", 3),
                        graph,
                        revealed,
                        1);
        assertEquals(Map.of("vftv:aer", 1, "vftv:ordo", 1), tied.debits());
    }

    @Test
    void aPlanDeeperThanThreeLevelsIsShortAndDebitsNothing() {
        Map<String, List<String>> deep = new TreeMap<>();
        deep.put("t:p", List.of());
        deep.put("t:q", List.of());
        deep.put("t:a", List.of("t:p", "t:q"));
        deep.put("t:b", List.of("t:a", "t:p"));
        deep.put("t:c", List.of("t:b", "t:p"));
        deep.put("t:d", List.of("t:c", "t:p"));
        AspectGraph graph = new AspectGraph(deep);
        Map<String, Integer> pool = Map.of("t:p", 100, "t:q", 100);
        AspectGraph.LinkView view = graph.view(e -> false);
        assertTrue(DebitPlan.of(Map.of("t:c", 1), pool, graph, view, 1).ok());
        DebitPlan d = DebitPlan.of(Map.of("t:d", 1), pool, graph, view, 1);
        assertEquals("t:a", d.shortLeaf());
        assertEquals(Map.of(), d.debits());
        assertEquals(
                "t:p",
                DebitPlan.of(Map.of("t:a", 1), Map.of("t:q", 1), graph, view, 1).shortLeaf());
    }

    @Test
    void aPlanConservesWhatItTakes() {
        AspectGraph graph = builtin();
        Map<String, Integer> pool = new HashMap<>();
        PRIMALS.forEach(p -> pool.put(p, 50));
        DebitPlan plan =
                DebitPlan.of(
                        Map.of("vftv:lux", 2, "vftv:silentium", 1), pool, graph, taught(graph), 1);
        assertTrue(plan.ok());
        plan.debits().forEach((a, n) -> assertTrue(n <= req(pool.get(a))));
        int sum = plan.debits().values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(sum >= 3);
    }

    @Test
    void learnNeedsEveryPlacementStraysIncluded() {
        Note n =
                note(
                        List.of(
                                new Placed(new Cell(1, 0), "vftv:lux"),
                                new Placed(new Cell(-1, 0), "vftv:lux"),
                                new Placed(new Cell(0, 1), "vftv:terra")));
        assertEquals(Map.of("vftv:lux", 2, "vftv:terra", 1), Notes.needs(n));
    }

    @Test
    void theSkipPriceIsTheScaledReferenceVectorUnlessAuthored() {
        AspectGraph graph = builtin();
        assertEquals(
                Map.of("vftv:aer", 1, "vftv:lux", 1, "vftv:vacuos", 1),
                Notes.reference(List.of("vftv:ignis", "vftv:perditio"), graph, 1));
        Fixtures.B b =
                Fixtures.b(1, "vftv:resonance", 0, 0, EntryKind.NOTE)
                        .anchors("vftv:ignis", "vftv:perditio");
        assertEquals(
                Map.of("vftv:aer", 2, "vftv:lux", 2, "vftv:vacuos", 2),
                Notes.skipPrice(b.build(), graph, ResearchTuning.DEFAULTS));
        ResearchTuning doubled =
                new ResearchTuning(
                        2,
                        1,
                        4,
                        32,
                        new BigDecimal("1.1"),
                        ResearchTuning.DEFAULTS.gentle(),
                        ResearchTuning.DEFAULTS.standard(),
                        ResearchTuning.DEFAULTS.hard());
        assertEquals(
                Map.of("vftv:aer", 3, "vftv:lux", 3, "vftv:vacuos", 3),
                Notes.skipPrice(b.build(), graph, doubled));
        b.skip.put("vftv:aer", 7);
        assertEquals(Map.of("vftv:aer", 7), Notes.skipPrice(b.build(), graph, doubled));
    }

    @Test
    void skipEligibilityIsTheBeginPreconditionAndItsPriceIsPayable() {
        AspectGraph graph = builtin();
        Set<String> known = new HashSet<>(PRIMALS);
        known.addAll(List.of("vftv:potentia", "vftv:permutatio"));
        List<String> anchors = List.of("vftv:ignis", "vftv:perditio");
        assertTrue(Notes.canBegin(anchors, known, taught(graph)));
        Fixtures.B b = Fixtures.b(1, "vftv:resonance", 0, 0, EntryKind.NOTE);
        b.anchors.addAll(anchors);
        Map<String, Integer> price = Notes.skipPrice(b.build(), graph, ResearchTuning.DEFAULTS);
        assertTrue(price.containsKey("vftv:lux") && price.containsKey("vftv:vacuos"));
        Map<String, Integer> pool = new HashMap<>();
        PRIMALS.forEach(p -> pool.put(p, 20));
        assertTrue(DebitPlan.of(price, pool, graph, taught(graph), 1).ok());
    }

    // ---- Tuning ------------------------------------------------------------------------------

    @Test
    void theTuningFileRoundTripsAndRejectsBadValues() {
        assertEquals(
                ResearchTuning.DEFAULTS,
                ResearchTuning.parse(ResearchTuning.DEFAULTS.toJson().toString()));
        assertEquals(ResearchTuning.DEFAULTS, ResearchTuning.parse("{}"));
        ResearchTuning hard = ResearchTuning.parse("{\"silent_cells\": {\"hard\": 12}}");
        assertEquals(12, hard.hard().silentCells());
        assertEquals(0, hard.standard().silentCells());
        for (String bad :
                List.of(
                        "{\"place_cost\": 5}",
                        "{\"place_cost\": 1.5}",
                        "{\"skip.multiplier\": 0.9}",
                        "{\"skip.multiplier\": 10.5}",
                        "{\"grid.base_radius\": 3}",
                        "{\"grid.base_radius\": {\"easy\": 3}}",
                        "{\"palette_help\": {\"hard\": \"both\"}}",
                        "{\"anchors.max_used\": {\"gentle\": 1}}",
                        "{\"surprise\": 1}",
                        "[]",
                        "{")) {
            assertThrows(IllegalArgumentException.class, () -> ResearchTuning.parse(bad), bad);
        }
    }

    // ---- Records and reconciliation ----------------------------------------------------------

    @Test
    void noteRevisionsAreNeverReusedAcrossReplacementNotes() {
        Note n = note(List.of());
        ResearchRecord r = ResearchRecord.EMPTY.withNote(n);
        long first = req(r.notes().get("vftv:test")).revision();
        assertEquals(r.revision(), first);
        r = r.abandon("vftv:test");
        assertEquals(1, r.attempts().get("vftv:test"));
        assertTrue(r.notes().isEmpty());
        r = r.withNote(n);
        long second = req(r.notes().get("vftv:test")).revision();
        assertTrue(second > first);
    }

    @Test
    void reconciliationFollowsItsTable() {
        Map<String, ResearchEntry> entries = new TreeMap<>();
        entries.put(
                "vftv:test",
                Fixtures.b(1, "vftv:resonance", 0, 0, EntryKind.NOTE)
                        .anchors("vftv:aer", "vftv:ignis")
                        .build());
        entries.put("vftv:free", Fixtures.b(1, "vftv:resonance", 1, 0, EntryKind.FREE).build());
        Note kept =
                note(
                        List.of(
                                new Placed(new Cell(1, 0), "vftv:lux"),
                                new Placed(new Cell(0, 1), "vftv:gone")));
        Note wrongKind =
                new Note("vftv:free", kept.snapshot(), 3, kept.anchors(), List.of(), List.of(), 1);
        Note missing =
                new Note(
                        "vftv:missing",
                        kept.snapshot(),
                        3,
                        kept.anchors(),
                        List.of(),
                        List.of(),
                        1);
        ResearchRecord r =
                ResearchRecord.EMPTY.withNote(kept).withNote(wrongKind).withNote(missing);
        List<String> log = new ArrayList<>();
        ResearchRecord after = Notes.reconcile(r, entries, a -> !a.equals("vftv:gone"), log::add);
        assertEquals(Set.of("vftv:test"), after.notes().keySet());
        Note fixed = req(after.notes().get("vftv:test"));
        assertEquals(List.of(new Placed(new Cell(1, 0), "vftv:lux")), fixed.placements());
        assertTrue(fixed.revision() > kept.revision());
        assertEquals(3, log.size());
        assertEquals(r.attempts(), after.attempts());
        // A missing anchor drops the note; a learned entry drops it; an unchanged record stays.
        assertTrue(
                Notes.reconcile(after, entries, a -> !a.equals("vftv:ignis"), s -> {})
                        .notes()
                        .isEmpty());
        ResearchRecord learned =
                new ResearchRecord(
                        ResearchRecord.FORMAT,
                        1,
                        new java.util.TreeSet<>(Set.of("vftv:test")),
                        new java.util.TreeSet<>(),
                        new java.util.TreeSet<>(),
                        new java.util.TreeSet<>(),
                        new TreeMap<>(Map.of("vftv:test", fixed)),
                        List.of(),
                        9,
                        new java.util.TreeSet<>(),
                        new TreeMap<>());
        assertTrue(Notes.reconcile(learned, entries, a -> true, s -> {}).notes().isEmpty());
        assertEquals(after, Notes.reconcile(after, entries, a -> true, s -> {}));
    }

    private static <T> T req(@org.jspecify.annotations.Nullable T value) {
        return java.util.Objects.requireNonNull(value);
    }
}
