package com.andrewbrogoff.vftv.core.research.model;

import java.util.function.Predicate;

/**
 * What the book draws for an entry (research spec, "States"): one total function from the data and
 * the player's record, identical on both sides. It decides drawing only; every gate reads the
 * stored learned set.
 */
public enum RenderState {
    /** Its Stage is above the player's, or it is secret and unrevealed: never drawn or named. */
    HIDDEN,
    /** In the stored learned set. */
    LEARNED,
    /** Its parents are learned and its kind's own condition holds. */
    OPEN,
    /** Drawn with its title and hints only. */
    LOCKED;

    /**
     * Whether the entry is HIDDEN: its Stage is above the player's, or it is secret, unrevealed.
     */
    public static boolean hidden(String id, ResearchEntry entry, ResearchRecord player) {
        return entry.stage() > player.stage()
                || (entry.secret() && !player.revealed().contains(id));
    }

    /**
     * The first matching state.
     *
     * @param id the entry's id
     * @param entry the entry
     * @param player the player's record
     * @param canBegin the note begin precondition (its first anchors known and connected in the
     *     player's known-link graph), which is skip eligibility's too
     */
    public static RenderState of(
            String id,
            ResearchEntry entry,
            ResearchRecord player,
            Predicate<ResearchEntry> canBegin) {
        if (hidden(id, entry, player)) {
            return HIDDEN;
        }
        if (player.isLearned(id)) {
            return LEARNED;
        }
        if (!entry.allParents().stream().allMatch(player::isLearned)) {
            return LOCKED;
        }
        boolean open =
                switch (entry.kind()) {
                    case FREE, LISTEN -> true;
                    case FIND -> player.latched().contains(id);
                    case NOTE -> player.notes().containsKey(id) || canBegin.test(entry);
                };
        return open ? OPEN : LOCKED;
    }
}
