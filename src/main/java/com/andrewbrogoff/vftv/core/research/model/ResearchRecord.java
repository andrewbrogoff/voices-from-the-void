package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A player's research (research spec, "Sync and state"): the {@code vftv:research} attachment's
 * record. Every change takes the next {@code revision}. Ids a pack removed stay in the sets, inert,
 * so restoring the pack restores them.
 *
 * @param format the record format, 1
 * @param stage the player's Stage, 0-4
 * @param learned the stored learned set every gate reads
 * @param revealed the secrets revealed
 * @param latched the finds whose trigger fired
 * @param revealLatched the secrets whose reveal fired
 * @param notes the open notes by entry id, at most 16
 * @param bookmarks the bookmarked entries in order, at most 9
 * @param revision the monotonic revision
 * @param rewarded server-only: the entries whose rewards were paid
 * @param attempts server-only: each note entry's attempt counter
 */
public record ResearchRecord(
        int format,
        int stage,
        NavigableSet<String> learned,
        NavigableSet<String> revealed,
        NavigableSet<String> latched,
        NavigableSet<String> revealLatched,
        NavigableMap<String, Note> notes,
        List<String> bookmarks,
        long revision,
        NavigableSet<String> rewarded,
        NavigableMap<String, Integer> attempts) {
    /** The one format this build reads and writes. */
    public static final int FORMAT = 1;

    /** The most ids in one set (a codec bound). */
    public static final int MAX_IDS = 4096;

    /** A player who has heard nothing yet. */
    public static final ResearchRecord EMPTY =
            new ResearchRecord(
                    FORMAT,
                    0,
                    new TreeSet<>(),
                    new TreeSet<>(),
                    new TreeSet<>(),
                    new TreeSet<>(),
                    new TreeMap<>(),
                    List.of(),
                    0,
                    new TreeSet<>(),
                    new TreeMap<>());

    /** Copies every collection and checks the format bounds. */
    public ResearchRecord {
        if (format != FORMAT) {
            throw new IllegalArgumentException("unknown format " + format);
        }
        if (stage < 0 || stage > 4) {
            throw new IllegalArgumentException("stage " + stage + " outside 0..4");
        }
        learned = ids("learned", learned);
        revealed = ids("revealed", revealed);
        latched = ids("latched", latched);
        revealLatched = ids("reveal_latched", revealLatched);
        rewarded = ids("rewarded", rewarded);
        if (notes.size() > ResearchLimits.MAX_OPEN_NOTES) {
            throw new IllegalArgumentException(notes.size() + " notes, over 16");
        }
        notes.forEach(
                (entry, note) -> {
                    if (!entry.equals(note.entry())) {
                        throw new IllegalArgumentException(
                                "note " + note.entry() + " under " + entry);
                    }
                });
        notes = Collections.unmodifiableNavigableMap(new TreeMap<>(notes));
        bookmarks = List.copyOf(bookmarks);
        if (bookmarks.size() > ResearchLimits.MAX_BOOKMARKS
                || new HashSet<>(bookmarks).size() != bookmarks.size()) {
            throw new IllegalArgumentException("bookmarks: at most 9, distinct");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("negative revision");
        }
        if (attempts.size() > MAX_IDS || attempts.values().stream().anyMatch(a -> a < 0)) {
            throw new IllegalArgumentException("attempts: at most 4096, none negative");
        }
        attempts = Collections.unmodifiableNavigableMap(new TreeMap<>(attempts));
    }

    private static NavigableSet<String> ids(String name, Set<String> ids) {
        if (ids.size() > MAX_IDS) {
            throw new IllegalArgumentException(name + ": " + ids.size() + " ids, over 4096");
        }
        return Collections.unmodifiableNavigableSet(new TreeSet<>(ids));
    }

    /** Whether the entry is in the stored learned set. */
    public boolean isLearned(String id) {
        return learned.contains(id);
    }

    /** Whether the entry is latched or learned. */
    public boolean isLatchedOrLearned(String id) {
        return latched.contains(id) || learned.contains(id);
    }

    /**
     * This record with {@code ids} learned and their open notes dropped (a note never outlives its
     * entry's learning, by any path), at the next revision.
     */
    public ResearchRecord learn(String... ids) {
        NavigableMap<String, Note> kept = new TreeMap<>(notes);
        for (String id : ids) {
            kept.remove(id);
        }
        return copy(
                stage,
                add(learned, ids),
                revealed,
                latched,
                revealLatched,
                kept,
                bookmarks,
                rewarded,
                attempts);
    }

    /** This record with {@code ids} no longer learned, at the next revision. */
    public ResearchRecord unlearn(Collection<String> ids) {
        NavigableSet<String> out = new TreeSet<>(learned);
        out.removeAll(ids);
        return copy(
                stage, out, revealed, latched, revealLatched, notes, bookmarks, rewarded, attempts);
    }

    /** This record with {@code ids} latched, at the next revision. */
    public ResearchRecord latch(String... ids) {
        return copy(
                stage,
                learned,
                revealed,
                add(latched, ids),
                revealLatched,
                notes,
                bookmarks,
                rewarded,
                attempts);
    }

    /** This record with {@code ids}' reveals latched, at the next revision. */
    public ResearchRecord latchReveal(String... ids) {
        return copy(
                stage,
                learned,
                revealed,
                latched,
                add(revealLatched, ids),
                notes,
                bookmarks,
                rewarded,
                attempts);
    }

    /** This record with {@code ids} revealed, at the next revision. */
    public ResearchRecord reveal(String... ids) {
        return copy(
                stage,
                learned,
                add(revealed, ids),
                latched,
                revealLatched,
                notes,
                bookmarks,
                rewarded,
                attempts);
    }

    /** This record with {@code ids} in the server-only rewarded ledger, at the next revision. */
    public ResearchRecord reward(String... ids) {
        return copy(
                stage,
                learned,
                revealed,
                latched,
                revealLatched,
                notes,
                bookmarks,
                add(rewarded, ids),
                attempts);
    }

    /** This record at Stage {@code stage}, at the next revision. */
    public ResearchRecord withStage(int stage) {
        return copy(
                stage,
                learned,
                revealed,
                latched,
                revealLatched,
                notes,
                bookmarks,
                rewarded,
                attempts);
    }

    /** This record with the given open notes, at the next revision. */
    public ResearchRecord withNotes(Map<String, Note> notes) {
        return copy(
                stage,
                learned,
                revealed,
                latched,
                revealLatched,
                new TreeMap<>(notes),
                bookmarks,
                rewarded,
                attempts);
    }

    /** This record with the given bookmarks, at the next revision. */
    public ResearchRecord withBookmarks(List<String> bookmarks) {
        return copy(
                stage,
                learned,
                revealed,
                latched,
                revealLatched,
                notes,
                bookmarks,
                rewarded,
                attempts);
    }

    /**
     * The admin reset (research spec, "Admin commands"): every note dropped, then the Stage,
     * learned, revealed, latches, attempts and bookmarks cleared, at the next revision. {@code
     * rewarded} is kept, so no reward is paid twice.
     */
    public ResearchRecord reset() {
        TreeSet<String> none = new TreeSet<>();
        return copy(
                0, none, none, none, none, new TreeMap<>(), List.of(), rewarded, new TreeMap<>());
    }

    /** What the owner's client holds: this record without the server-only fields. */
    public ResearchRecord synced() {
        return new ResearchRecord(
                format,
                stage,
                learned,
                revealed,
                latched,
                revealLatched,
                notes,
                bookmarks,
                revision,
                new TreeSet<>(),
                new TreeMap<>());
    }

    private ResearchRecord copy(
            int stage,
            NavigableSet<String> learned,
            NavigableSet<String> revealed,
            NavigableSet<String> latched,
            NavigableSet<String> revealLatched,
            NavigableMap<String, Note> notes,
            List<String> bookmarks,
            NavigableSet<String> rewarded,
            NavigableMap<String, Integer> attempts) {
        return new ResearchRecord(
                format,
                stage,
                learned,
                revealed,
                latched,
                revealLatched,
                notes,
                bookmarks,
                revision + 1,
                rewarded,
                attempts);
    }

    private static NavigableSet<String> add(NavigableSet<String> set, String... ids) {
        NavigableSet<String> out = new TreeSet<>(set);
        Collections.addAll(out, ids);
        return out;
    }
}
