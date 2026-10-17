package com.andrewbrogoff.vftv.core.research.model;

import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One change to a player's research record, as the owner's client receives it after the baseline
 * (research spec, "Sync and state"): the changed ids of each set, the new Stage, revision and
 * bookmarks, and the notes changed or dropped. Applying it is idempotent, so a repeated delta
 * changes nothing more.
 *
 * @param stage the new Stage
 * @param revision the new revision
 * @param learned the learned set's change
 * @param revealed the revealed set's change
 * @param latched the latched set's change
 * @param revealLatched the reveal-latched set's change
 * @param bookmarks the bookmarks, whole (at most 9)
 * @param notes the notes created or changed
 * @param dropped the entry ids whose notes were dropped
 */
public record ResearchDelta(
        int stage,
        long revision,
        Ids learned,
        Ids revealed,
        Ids latched,
        Ids revealLatched,
        List<String> bookmarks,
        List<Note> notes,
        List<String> dropped) {
    /** Copies every list. */
    public ResearchDelta {
        bookmarks = List.copyOf(bookmarks);
        notes = List.copyOf(notes);
        dropped = List.copyOf(dropped);
    }

    /**
     * A set's change.
     *
     * @param added ids added
     * @param removed ids removed
     */
    public record Ids(List<String> added, List<String> removed) {
        /** Copies both lists. */
        public Ids {
            added = List.copyOf(added);
            removed = List.copyOf(removed);
        }

        static Ids between(NavigableSet<String> before, NavigableSet<String> after) {
            return new Ids(
                    after.stream().filter(id -> !before.contains(id)).toList(),
                    before.stream().filter(id -> !after.contains(id)).toList());
        }

        NavigableSet<String> applyTo(NavigableSet<String> set) {
            NavigableSet<String> out = new TreeSet<>(set);
            out.removeAll(removed);
            out.addAll(added);
            return out;
        }
    }

    /** The change from {@code before} to {@code after}, over the synced fields only. */
    public static ResearchDelta between(ResearchRecord before, ResearchRecord after) {
        return new ResearchDelta(
                after.stage(),
                after.revision(),
                Ids.between(before.learned(), after.learned()),
                Ids.between(before.revealed(), after.revealed()),
                Ids.between(before.latched(), after.latched()),
                Ids.between(before.revealLatched(), after.revealLatched()),
                after.bookmarks(),
                after.notes().values().stream()
                        .filter(n -> !n.equals(before.notes().get(n.entry())))
                        .toList(),
                before.notes().keySet().stream()
                        .filter(id -> !after.notes().containsKey(id))
                        .toList());
    }

    /** {@code before} with this change applied. */
    public ResearchRecord applyTo(ResearchRecord before) {
        NavigableMap<String, Note> notes = new TreeMap<>(before.notes());
        dropped.forEach(notes::remove);
        this.notes.forEach(n -> notes.put(n.entry(), n));
        return new ResearchRecord(
                before.format(),
                stage,
                learned.applyTo(before.learned()),
                revealed.applyTo(before.revealed()),
                latched.applyTo(before.latched()),
                revealLatched.applyTo(before.revealLatched()),
                notes,
                bookmarks,
                revision,
                before.rewarded(),
                before.attempts());
    }
}
