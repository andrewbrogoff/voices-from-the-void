package com.andrewbrogoff.vftv.core.research.model;

import java.util.Locale;

/** An entry's {@code kind} (research spec, "Registry format"). */
public enum EntryKind {
    /** Learned as soon as it is OPEN. */
    FREE,
    /** Learned by a trigger. */
    FIND,
    /** The puzzle, or bought in skip mode. */
    NOTE,
    /** An explicit commit. */
    LISTEN;

    /** The JSON name, e.g. {@code note}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether an entry of this kind may carry {@code warp} and needs one {@code action} page. */
    public boolean committed() {
        return this == NOTE || this == LISTEN;
    }
}
