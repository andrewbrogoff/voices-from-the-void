package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import com.andrewbrogoff.vftv.core.research.model.Note.Cell;
import com.andrewbrogoff.vftv.core.research.model.Note.Placed;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The puzzle (research spec, "The puzzle"): the begin precondition, the seeded constructive
 * generation that plants a solution first and shows only its anchors, the solve check, a note's
 * Learn needs, the skip price and the login reconciliation of open notes. Pure functions.
 */
public final class Notes {
    private Notes() {}

    // ---- Beginning -----------------------------------------------------------------------------

    /** The anchors a note uses: the entry's first n, with n = min(anchor count, max_used). */
    public static List<String> used(ResearchEntry entry, ResearchTuning.Preset preset) {
        return entry.anchors().subList(0, Math.min(entry.anchors().size(), preset.maxUsed()));
    }

    /**
     * The begin precondition (research spec, "Generating a note"), which is skip eligibility's too:
     * the used anchors are all known, and each consecutive pair connects in the player's known-link
     * graph.
     */
    public static boolean canBegin(
            List<String> anchors, Set<String> known, AspectGraph.LinkView view) {
        return !anchors.isEmpty() && view.segments(anchors, known) != null;
    }

    /**
     * The note seed: {@code fold([NOTE, world seed, UUID, attempt] ++ idWords(entry))}.
     *
     * @param attempt the player's attempt counter for the entry
     */
    public static long seed(long worldSeed, UUID player, int attempt, String entry) {
        return Hashes.fold(
                Hashes.words(
                        new long[] {Salts.word(Salts.NOTE), worldSeed},
                        Hashes.uuidWords(player),
                        new long[] {attempt},
                        Hashes.idWords(entry)));
    }

    /**
     * What generation gave.
     *
     * @param note the issued note, or null when refused
     * @param fellBack whether the walk failed and the fallback laid it
     * @param plant the witness's cells and aspects, anchors included, for tests; empty on refusal
     */
    public record Generated(@Nullable Note note, boolean fellBack, Map<Cell, String> plant) {}

    /**
     * Generates a note (research spec, "Generating a note", steps 1-7): one {@code Random(seed)},
     * every draw {@code nextInt(bound)} even when the bound is 1.
     *
     * @param entry the note entry's id
     * @param anchors the used anchors, in data order
     * @param known the player's known aspects
     * @param view the player's view of the links
     * @param tuning the tuning in force
     * @param preset the preset in force
     * @param revision the revision the issued note takes
     * @param log where a walk plant that fails the solve check is reported, as an error
     */
    public static Generated generate(
            long seed,
            String entry,
            List<String> anchors,
            Set<String> known,
            AspectGraph.LinkView view,
            ResearchTuning tuning,
            ResearchTuning.Preset preset,
            long revision,
            Consumer<String> log) {
        List<List<String>> segments = view.segments(anchors, known);
        if (segments == null) {
            return refused();
        }
        int minDistance = preset.minDistance();
        int silent = preset.silentCells();
        List<List<String>> padded = segments.stream().map(s -> pad(s, minDistance)).toList();
        int unpadded = AspectGraph.join(anchors, segments).size();
        int paddedLength = AspectGraph.join(anchors, padded).size();
        int base =
                Math.min(
                        ResearchLimits.MAX_GRID_RADIUS,
                        preset.baseRadius() + (anchors.size() >= tuning.anchorBonusAt() ? 1 : 0));
        Random random = new Random(seed);
        Note.Snapshot snapshot = tuning.snapshot(preset);
        int walkRadius = fit(base, paddedLength + silent);
        if (walkRadius <= ResearchLimits.MAX_GRID_RADIUS) {
            for (int attempt = 0; attempt < tuning.maxAttempts(); attempt++) {
                Plant plant = walk(random, anchors.getFirst(), padded, walkRadius, minDistance);
                if (plant != null) {
                    Generated issued =
                            issue(
                                    entry,
                                    plant,
                                    walkRadius,
                                    random,
                                    silent,
                                    snapshot,
                                    view,
                                    revision);
                    if (issued != null) {
                        return issued;
                    }
                    log.accept("note " + entry + ": the walk's plant failed the solve check");
                    break;
                }
            }
        }
        int fallbackRadius = fit(base, unpadded + silent);
        if (fallbackRadius > ResearchLimits.MAX_GRID_RADIUS) {
            return refused();
        }
        int t = random.nextInt(NoteGrid.SYMMETRIES);
        List<Cell> spiral = NoteGrid.spiral(fallbackRadius);
        List<String> chain = AspectGraph.join(anchors, segments);
        Set<Integer> anchorAt = new HashSet<>(List.of(0));
        int at = 0;
        for (List<String> segment : segments) {
            at += segment.size() - 1;
            anchorAt.add(at);
        }
        Plant plant = new Plant();
        for (int i = 0; i < chain.size(); i++) {
            Cell cell = NoteGrid.transform(spiral.get(i), t);
            plant.cells.put(cell, chain.get(i));
            if (anchorAt.contains(i)) {
                plant.anchors.add(cell);
            }
        }
        Generated issued =
                issue(entry, plant, fallbackRadius, random, silent, snapshot, view, revision);
        return issued == null ? refused() : new Generated(issued.note(), true, issued.plant());
    }

    private static Generated refused() {
        return new Generated(null, false, Map.of());
    }

    /** {@code [x0, x1, …, xm]} with {@code [x1, x0]} inserted after x0 until m ≥ min_distance. */
    static List<String> pad(List<String> segment, int minDistance) {
        List<String> out = new ArrayList<>(segment);
        while (out.size() - 1 < minDistance) {
            out.add(1, segment.get(0));
            out.add(1, segment.get(1));
        }
        return List.copyOf(out);
    }

    /** The smallest radius at least {@code base} with room for {@code cells}; 8 if none fits. */
    static int fit(int base, int cells) {
        int r = base;
        while (r <= ResearchLimits.MAX_GRID_RADIUS && NoteGrid.cells(r) < cells) {
            r++;
        }
        return r;
    }

    /** A plant: every witness cell with its aspect in placement order, and the anchor cells. */
    private static final class Plant {
        final Map<Cell, String> cells = new LinkedHashMap<>();
        final List<Cell> anchors = new ArrayList<>();
    }

    private static @Nullable Plant walk(
            Random random, String first, List<List<String>> padded, int radius, int minDistance) {
        List<Cell> spiral = NoteGrid.spiral(radius);
        Plant plant = new Plant();
        Cell at = spiral.get(random.nextInt(spiral.size()));
        plant.cells.put(at, first);
        plant.anchors.add(at);
        for (List<String> segment : padded) {
            Cell start = at;
            int m = segment.size() - 1;
            for (int j = 1; j <= m; j++) {
                List<Cell> candidates = new ArrayList<>(6);
                for (int d = 0; d < 6; d++) {
                    Cell c = NoteGrid.step(at, d);
                    if (NoteGrid.inside(c, radius)
                            && !plant.cells.containsKey(c)
                            && NoteGrid.distance(c, start) + (m - j) >= minDistance
                            && (j < m || farFromAll(c, plant.anchors, minDistance))) {
                        candidates.add(c);
                    }
                }
                if (candidates.isEmpty()) {
                    return null;
                }
                at = candidates.get(random.nextInt(candidates.size()));
                plant.cells.put(at, segment.get(j));
            }
            plant.anchors.add(at);
        }
        return plant;
    }

    private static boolean farFromAll(Cell c, List<Cell> anchors, int minDistance) {
        return anchors.stream().allMatch(a -> NoteGrid.distance(c, a) >= minDistance);
    }

    /** Draws the silent cells, verifies the plant and issues the note; null if it fails. */
    private static @Nullable Generated issue(
            String entry,
            Plant plant,
            int radius,
            Random random,
            int silentCount,
            Note.Snapshot snapshot,
            AspectGraph.LinkView view,
            long revision) {
        List<Cell> free = new ArrayList<>();
        for (Cell c : NoteGrid.spiral(radius)) {
            if (!plant.cells.containsKey(c)) {
                free.add(c);
            }
        }
        List<Cell> silent = new ArrayList<>();
        for (int i = 0; i < silentCount; i++) {
            silent.add(free.remove(random.nextInt(free.size())));
        }
        List<Placed> anchors =
                plant.anchors.stream()
                        .map(c -> new Placed(c, Objects.requireNonNull(plant.cells.get(c))))
                        .toList();
        List<Placed> witness =
                plant.cells.entrySet().stream()
                        .filter(e -> !plant.anchors.contains(e.getKey()))
                        .map(e -> new Placed(e.getKey(), e.getValue()))
                        .toList();
        Note planted = new Note(entry, snapshot, radius, anchors, silent, witness, revision);
        if (!solved(planted, view)) {
            return null;
        }
        return new Generated(
                new Note(entry, snapshot, radius, anchors, silent, List.of(), revision),
                false,
                Map.copyOf(plant.cells));
    }

    // ---- Solving -------------------------------------------------------------------------------

    /** Every occupied cell of a note, anchors then placements, with its aspect. */
    public static Map<Cell, String> occupied(Note note) {
        Map<Cell, String> out = new HashMap<>();
        note.anchors().forEach(p -> out.put(p.cell(), p.aspect()));
        note.placements().forEach(p -> out.put(p.cell(), p.aspect()));
        return out;
    }

    /**
     * Solved iff every anchor lies in one connected component of the link graph over the occupied
     * cells (research spec, "Notes"): a BFS over at most 169 cells.
     */
    public static boolean solved(Note note, AspectGraph.LinkView view) {
        Map<Cell, String> occupied = occupied(note);
        Cell start = note.anchors().getFirst().cell();
        Set<Cell> seen = new HashSet<>(List.of(start));
        Deque<Cell> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            Cell at = queue.removeFirst();
            String aspect = Objects.requireNonNull(occupied.get(at));
            for (int d = 0; d < 6; d++) {
                Cell next = NoteGrid.step(at, d);
                String other = occupied.get(next);
                if (other != null && !seen.contains(next) && view.linked(aspect, other)) {
                    seen.add(next);
                    queue.addLast(next);
                }
            }
        }
        return note.anchors().stream().allMatch(p -> seen.contains(p.cell()));
    }

    // ---- Paying --------------------------------------------------------------------------------

    /** What Learn spends: each placed aspect's count × the note's snapshot {@code place_cost}. */
    public static SortedMap<String, Integer> needs(Note note) {
        SortedMap<String, Integer> out = new TreeMap<>();
        note.placements()
                .forEach(p -> out.merge(p.aspect(), note.snapshot().placeCost(), Integer::sum));
        return out;
    }

    /**
     * The skip reference vector (research spec, "Skip mode"): the unpadded chain over every
     * authored anchor in the taught view with every aspect known, each non-anchor cell's aspect ×
     * {@code place_cost}. Empty when the anchors don't connect.
     */
    public static SortedMap<String, Integer> reference(
            List<String> anchors, AspectGraph graph, int placeCost) {
        SortedMap<String, Integer> out = new TreeMap<>();
        if (!anchors.stream().allMatch(graph::contains)) {
            return out;
        }
        List<List<String>> segments = AspectGraph.segments(anchors, graph::taughtLinks);
        if (segments == null) {
            return out;
        }
        for (List<String> segment : segments) {
            segment.subList(1, segment.size() - 1)
                    .forEach(a -> out.merge(a, placeCost, Integer::sum));
        }
        return out;
    }

    /**
     * An entry's skip price: its authored {@code skip_cost}, else per aspect {@code ceil(multiplier
     * × reference[a])}.
     */
    public static SortedMap<String, Integer> skipPrice(
            ResearchEntry entry, AspectGraph graph, ResearchTuning tuning) {
        if (!entry.skipCost().isEmpty()) {
            return new TreeMap<>(entry.skipCost());
        }
        SortedMap<String, Integer> out = new TreeMap<>();
        reference(entry.anchors(), graph, tuning.placeCost())
                .forEach(
                        (a, n) ->
                                out.put(
                                        a,
                                        tuning.skipMultiplier()
                                                .multiply(BigDecimal.valueOf(n))
                                                .setScale(0, RoundingMode.CEILING)
                                                .intValueExact()));
        return out;
    }

    // ---- Reconciliation ------------------------------------------------------------------------

    /**
     * The login reconciliation of open notes (research spec, "Snapshots and reopening"): a note
     * whose entry is gone, is no longer a note or is learned is dropped; a placement naming an
     * aspect that is gone is removed and logged; a note with a missing anchor is dropped. A note
     * that changes takes the record's next revision. Attempts are never touched.
     *
     * @param aspectExists whether an aspect id is in the set
     * @param log where each change is reported
     */
    public static ResearchRecord reconcile(
            ResearchRecord record,
            Map<String, ResearchEntry> entries,
            Predicate<String> aspectExists,
            Consumer<String> log) {
        Map<String, Note> kept = new TreeMap<>();
        boolean changed = false;
        long revision = record.revision() + 1;
        for (Note note : record.notes().values()) {
            ResearchEntry e = entries.get(note.entry());
            if (e == null || e.kind() != EntryKind.NOTE || record.isLearned(note.entry())) {
                log.accept("dropped the note of " + note.entry() + ": its entry is gone or done");
                changed = true;
            } else if (!note.anchors().stream().allMatch(p -> aspectExists.test(p.aspect()))) {
                log.accept("dropped the note of " + note.entry() + ": an anchor's aspect is gone");
                changed = true;
            } else {
                List<Placed> placements =
                        note.placements().stream()
                                .filter(p -> aspectExists.test(p.aspect()))
                                .toList();
                if (placements.size() != note.placements().size()) {
                    log.accept(
                            "removed placements of missing aspects from the note of "
                                    + note.entry());
                    note = with(note, placements, revision);
                    changed = true;
                }
                kept.put(note.entry(), note);
            }
        }
        return changed ? record.withNotes(kept) : record;
    }

    /** {@code note} with {@code placements}, at {@code revision}. */
    public static Note with(Note note, List<Placed> placements, long revision) {
        return new Note(
                note.entry(),
                note.snapshot(),
                note.radius(),
                note.anchors(),
                note.silent(),
                placements,
                revision);
    }
}
