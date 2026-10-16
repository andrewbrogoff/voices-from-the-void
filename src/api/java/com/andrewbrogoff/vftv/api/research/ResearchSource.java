package com.andrewbrogoff.vftv.api.research;

/**
 * How a research change came about. Grows in minor releases: a switch over it needs a default arm.
 * See the research spec, "Events".
 */
public enum ResearchSource {
    /** A solved note's Learn. */
    SOLVED,
    /** A skip-mode purchase. */
    BOUGHT,
    /** A latched trigger: a {@code find} entry, or a Stage opener's find. */
    FOUND,
    /** A {@code free} entry, learned as soon as it was open. */
    FREE,
    /** A {@code listen} entry's commit. */
    LISTENED,
    /** An admin command. */
    COMMAND
}
