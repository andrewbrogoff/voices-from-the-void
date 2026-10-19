package com.andrewbrogoff.vftv.core.research.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ResearchRecordTest {
    static Note note(String entry, long revision) {
        return new Note(
                entry,
                new Note.Snapshot(2, 0, Note.PaletteHelp.PARENTS, 1, 1),
                3,
                List.of(
                        new Note.Placed(new Note.Cell(0, 0), "vftv:aer"),
                        new Note.Placed(new Note.Cell(2, 0), "vftv:ignis")),
                List.of(new Note.Cell(-1, 0)),
                List.of(new Note.Placed(new Note.Cell(1, 0), "vftv:lux")),
                revision);
    }

    private static ResearchRecord with(Map<String, Integer> attempts, List<String> bookmarks) {
        ResearchRecord e = ResearchRecord.EMPTY;
        return new ResearchRecord(
                1,
                0,
                e.learned(),
                e.revealed(),
                e.latched(),
                e.revealLatched(),
                e.notes(),
                bookmarks,
                0,
                e.rewarded(),
                new TreeMap<>(attempts));
    }

    @Test
    void learningDropsTheNoteAndRevokingRemovesOnlyTheFlag() {
        ResearchRecord r =
                ResearchRecord.EMPTY
                        .withNotes(Map.of("vftv:n", note("vftv:n", 1), "vftv:m", note("vftv:m", 1)))
                        .latch("vftv:n");
        ResearchRecord learned = r.learn("vftv:n");
        assertEquals(List.of("vftv:m"), List.copyOf(learned.notes().keySet()));
        ResearchRecord revoked = learned.unlearn(List.of("vftv:n", "vftv:x"));
        assertFalse(revoked.isLearned("vftv:n"));
        assertTrue(revoked.latched().contains("vftv:n"));
        assertEquals(learned.revision() + 1, revoked.revision());
    }

    @Test
    void revealsRewardsAndBookmarks() {
        ResearchRecord r =
                ResearchRecord.EMPTY
                        .latchReveal("vftv:s")
                        .reveal("vftv:s")
                        .reward("vftv:a")
                        .withBookmarks(List.of("vftv:a", "vftv:b"));
        assertEquals(List.of("vftv:s"), List.copyOf(r.revealLatched()));
        assertEquals(List.of("vftv:s"), List.copyOf(r.revealed()));
        assertEquals(List.of("vftv:a"), List.copyOf(r.rewarded()));
        assertEquals(List.of("vftv:a", "vftv:b"), r.bookmarks());
        assertEquals(4, r.revision());
    }

    @Test
    void resetKeepsRewardedAndNeverReusesARevision() {
        ResearchRecord r =
                with(Map.of("vftv:n", 3), List.of("vftv:a"))
                        .withStage(3)
                        .learn("vftv:a")
                        .latch("vftv:b")
                        .latchReveal("vftv:s")
                        .reveal("vftv:s")
                        .reward("vftv:a")
                        .withNotes(Map.of("vftv:n", note("vftv:n", 1)));
        ResearchRecord reset = r.reset();
        assertEquals(0, reset.stage());
        assertTrue(reset.learned().isEmpty());
        assertTrue(reset.latched().isEmpty());
        assertTrue(reset.revealed().isEmpty());
        assertTrue(reset.revealLatched().isEmpty());
        assertTrue(reset.notes().isEmpty());
        assertTrue(reset.bookmarks().isEmpty());
        assertTrue(reset.attempts().isEmpty());
        assertEquals(List.of("vftv:a"), List.copyOf(reset.rewarded()));
        assertEquals(r.revision() + 1, reset.revision());
    }

    @Test
    void everyChangeTakesTheNextRevision() {
        ResearchRecord r = ResearchRecord.EMPTY.learn("vftv:a").latch("vftv:b").withStage(2);
        assertEquals(3, r.revision());
        assertEquals(2, r.stage());
        assertTrue(r.isLearned("vftv:a"));
        assertFalse(r.isLearned("vftv:b"));
        assertTrue(r.isLatchedOrLearned("vftv:a"));
        assertTrue(r.isLatchedOrLearned("vftv:b"));
        assertFalse(r.isLatchedOrLearned("vftv:c"));
        ResearchRecord n = r.withNotes(Map.of("vftv:n", note("vftv:n", 4)));
        assertEquals(4, n.revision());
        assertEquals(1, n.notes().size());
    }

    @Test
    void syncedDropsTheServerOnlyFields() {
        ResearchRecord r = with(Map.of("vftv:n", 2), List.of("vftv:x"));
        assertEquals(Map.of(), r.synced().attempts());
        assertEquals(List.of("vftv:x"), r.synced().bookmarks());
    }

    @Test
    void formatBoundsAreChecked() {
        ResearchRecord e = ResearchRecord.EMPTY;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ResearchRecord(
                                2,
                                0,
                                e.learned(),
                                e.revealed(),
                                e.latched(),
                                e.revealLatched(),
                                e.notes(),
                                List.of(),
                                0,
                                e.rewarded(),
                                e.attempts()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ResearchRecord(
                                1,
                                5,
                                e.learned(),
                                e.revealed(),
                                e.latched(),
                                e.revealLatched(),
                                e.notes(),
                                List.of(),
                                0,
                                e.rewarded(),
                                e.attempts()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ResearchRecord(
                                1,
                                0,
                                e.learned(),
                                e.revealed(),
                                e.latched(),
                                e.revealLatched(),
                                e.notes(),
                                List.of(),
                                -1,
                                e.rewarded(),
                                e.attempts()));
        assertThrows(IllegalArgumentException.class, () -> with(Map.of("vftv:n", -1), List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> with(Map.of(), List.of("vftv:x", "vftv:x")));
        List<String> ten = IntStream.range(0, 10).mapToObj(i -> "vftv:b" + i).toList();
        assertThrows(IllegalArgumentException.class, () -> with(Map.of(), ten));
        TreeSet<String> many =
                IntStream.range(0, ResearchRecord.MAX_IDS + 1)
                        .mapToObj(i -> "vftv:e" + i)
                        .collect(Collectors.toCollection(TreeSet::new));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ResearchRecord(
                                1,
                                0,
                                many,
                                e.revealed(),
                                e.latched(),
                                e.revealLatched(),
                                e.notes(),
                                List.of(),
                                0,
                                e.rewarded(),
                                e.attempts()));
        TreeMap<String, Note> misfiled = new TreeMap<>(Map.of("vftv:other", note("vftv:n", 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ResearchRecord(
                                1,
                                0,
                                e.learned(),
                                e.revealed(),
                                e.latched(),
                                e.revealLatched(),
                                misfiled,
                                List.of(),
                                0,
                                e.rewarded(),
                                e.attempts()));
        TreeMap<String, Note> seventeen = new TreeMap<>();
        IntStream.range(0, 17).forEach(i -> seventeen.put("vftv:n" + i, note("vftv:n" + i, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ResearchRecord(
                                1,
                                0,
                                e.learned(),
                                e.revealed(),
                                e.latched(),
                                e.revealLatched(),
                                seventeen,
                                List.of(),
                                0,
                                e.rewarded(),
                                e.attempts()));
    }

    @Test
    void notesCheckTheirBoard() {
        Note.Snapshot s = new Note.Snapshot(2, 0, Note.PaletteHelp.NEIGHBOURS, 1, 1);
        List<Note.Placed> two =
                List.of(
                        new Note.Placed(new Note.Cell(0, 0), "a:a"),
                        new Note.Placed(new Note.Cell(1, 0), "a:b"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note("a:n", s, 0, two, List.of(), List.of(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note("a:n", s, 8, two, List.of(), List.of(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note("a:n", s, 1, two.subList(0, 1), List.of(), List.of(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note("a:n", s, 1, two, List.of(new Note.Cell(0, 0)), List.of(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note("a:n", s, 1, two, List.of(new Note.Cell(2, 0)), List.of(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note("a:n", s, 1, two, List.of(), List.of(), -1));
        List<Note.Cell> thirteen =
                IntStream.range(-6, 7).mapToObj(q -> new Note.Cell(q, 7)).toList();
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note("a:n", s, 7, two, thirteen, List.of(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note.Snapshot(0, 0, Note.PaletteHelp.PARENTS, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note.Snapshot(2, 13, Note.PaletteHelp.PARENTS, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note.Snapshot(2, 0, Note.PaletteHelp.PARENTS, 5, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note.Snapshot(2, 0, Note.PaletteHelp.PARENTS, 1, 0));
        assertEquals(3, new Note.Cell(-3, 1).distance());
        // An extreme coordinate is outside the grid, never wrapped to the centre.
        List<Note.Placed> extreme =
                List.of(
                        new Note.Placed(new Note.Cell(Integer.MIN_VALUE, 0), "a:a"),
                        new Note.Placed(new Note.Cell(1, 0), "a:b"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Note("a:n", s, 1, extreme, List.of(), List.of(), 0));
        assertEquals("parents", Note.PaletteHelp.PARENTS.id());
    }

    @Test
    void aDeltaCarriesOnlyTheChangeAndAppliesIdempotently() {
        ResearchRecord before =
                ResearchRecord.EMPTY
                        .learn("vftv:a", "vftv:b")
                        .withNotes(
                                Map.of("vftv:n", note("vftv:n", 1), "vftv:m", note("vftv:m", 1)));
        TreeMap<String, Note> notes = new TreeMap<>(before.notes());
        notes.remove("vftv:m");
        notes.put("vftv:n", note("vftv:n", 9));
        ResearchRecord after =
                new ResearchRecord(
                        1,
                        3,
                        new TreeSet<>(List.of("vftv:a", "vftv:c")),
                        before.revealed(),
                        new TreeSet<>(List.of("vftv:f")),
                        before.revealLatched(),
                        notes,
                        List.of("vftv:a"),
                        9,
                        new TreeSet<>(List.of("vftv:server_only")),
                        before.attempts());
        ResearchDelta delta = ResearchDelta.between(before, after);
        assertEquals(List.of("vftv:c"), delta.learned().added());
        assertEquals(List.of("vftv:b"), delta.learned().removed());
        assertEquals(List.of("vftv:f"), delta.latched().added());
        assertEquals(List.of(note("vftv:n", 9)), delta.notes());
        assertEquals(List.of("vftv:m"), delta.dropped());
        ResearchRecord applied = delta.applyTo(before.synced());
        assertEquals(after.synced(), applied);
        assertEquals(applied, delta.applyTo(applied));
    }
}
